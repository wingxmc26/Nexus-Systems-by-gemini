package com.example.security

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppLockProfileEntity
import com.example.data.local.NexusGuardPrefs
import com.example.data.local.NexusLockType
import com.example.data.local.SecuritySettingsConfig
import com.example.di.NexusContainer
import com.example.overlay.NexusEdgePanelService
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NumericKeypadGrid
import com.example.ui.components.PatternLockGrid
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusTheme
import com.example.ui.theme.NexusThemeExtras
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray

object AppLockMonitorCoordinator {

  private const val PERIODIC_WATCHDOG_WORK = "nexus_applock_periodic_watchdog"
  private const val IMMEDIATE_WATCHDOG_WORK = "nexus_applock_immediate_watchdog"

  fun parseStringList(json: String): List<String> {
    if (json.isBlank()) return emptyList()
    return runCatching {
      val arr = JSONArray(json)
      buildList {
        for (i in 0 until arr.length()) {
          val s = arr.optString(i)
          if (s.isNotBlank()) add(s)
        }
      }
    }.getOrDefault(emptyList())
  }

  fun serializeStringList(list: List<String>): String {
    return JSONArray().apply { list.distinct().forEach { put(it) } }.toString()
  }

  suspend fun ensureDefaultProfiles(context: Context): List<AppLockProfileEntity> {
    val dao = NexusContainer.get(context).database.appLockDao()
    val existing = dao.getAllProfiles()
    if (existing.isNotEmpty()) {
      syncProfileCache(context)
      return existing
    }
    dao.insertProfile(
      AppLockProfileEntity(
        name = "App Lock",
        isActive = true,
        protectedPackagesJson = "[]",
        protectedNexusRoutesJson = serializeStringList(listOf("secure_vault")),
        autoLockDelayMs = 0L,
        lockOnScreenOff = true,
      )
    )
    syncProfileCache(context)
    return dao.getAllProfiles()
  }

  suspend fun syncProfileCache(context: Context) {
    val container = NexusContainer.get(context.applicationContext)
    val sec = container.advancedPreferences.getSecuritySnapshot()
    NexusGuardPrefs.syncSecurityState(
      context = context,
      appLockEnabled = sec.appLockEnabled,
      monitorRunning = sec.externalMonitorRunning,
      hasPinConfigured = sec.hasPinConfigured,
    )
    val active = container.database.appLockDao().getActiveProfile()
    if (active != null) {
      NexusGuardPrefs.syncActiveProfile(
        context = context,
        protectedPackages = parseStringList(active.protectedPackagesJson).toSet(),
        autoLockDelayMs = active.autoLockDelayMs,
        lockOnScreenOff = active.lockOnScreenOff,
      )
    }
  }

  fun scheduleWatchdog(context: Context, enable: Boolean) {
    val wm = runCatching { WorkManager.getInstance(context.applicationContext) }.getOrNull() ?: return
    if (enable) {
      val req = PeriodicWorkRequestBuilder<AppLockWatchdogWorker>(15, TimeUnit.MINUTES).build()
      wm.enqueueUniquePeriodicWork(PERIODIC_WATCHDOG_WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    } else {
      wm.cancelUniqueWork(PERIODIC_WATCHDOG_WORK)
      wm.cancelUniqueWork(IMMEDIATE_WATCHDOG_WORK)
    }
  }

  fun scheduleImmediateRecovery(context: Context) {
    val appCtx = context.applicationContext
    val am = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    val restartPi = PendingIntent.getBroadcast(
      appCtx,
      7309,
      Intent(appCtx, NexusAppLockBootReceiver::class.java).setAction(NexusAppLockBootReceiver.ACTION_RESTART_GUARD),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val triggerAt = System.currentTimeMillis() + 650L
    if (am != null) {
      runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
          am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, restartPi)
        } else {
          am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, restartPi)
        }
      }.onFailure {
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, restartPi) }
      }
    }
    runCatching {
      val wm = WorkManager.getInstance(appCtx)
      val req = OneTimeWorkRequestBuilder<AppLockWatchdogWorker>()
        .setInitialDelay(1, TimeUnit.SECONDS)
        .build()
      wm.enqueueUniqueWork(IMMEDIATE_WATCHDOG_WORK, ExistingWorkPolicy.REPLACE, req)
    }
  }

  suspend fun isNexusRouteProtected(context: Context, route: String): Boolean {
    if (route != "secure_vault") return false
    val container = NexusContainer.get(context)
    val sec = container.advancedPreferences.getSecuritySnapshot()
    if (!sec.appLockEnabled || !sec.hasPinConfigured) return false
    val profile = container.database.appLockDao().getActiveProfile() ?: return false
    val protectedRoutes = parseStringList(profile.protectedNexusRoutesJson)
    if (route !in protectedRoutes) return false
    return !NexusSecurityManager.isNexusRouteUnlocked(route, profile.autoLockDelayMs)
  }

  fun onForegroundPackageObserved(context: Context, packageName: String) {
    val appCtx = context.applicationContext
    if (packageName.isBlank() || packageName == "com.android.systemui") return

    if (!AppLockChallengeActivity.isForegroundVisible) {
      NexusSecurityManager.onForegroundPackageTransition(packageName)
    }

    if (packageName == appCtx.packageName || packageName.contains("launcher", ignoreCase = true)) {
      return
    }

    if (!NexusGuardPrefs.isPackageProtectedSync(appCtx, packageName)) {
      CoroutineScope(Dispatchers.IO).launch {
        checkAndLaunchChallenge(appCtx, packageName)
      }
      return
    }

    val fastDelayMs = NexusGuardPrefs.getAutoLockDelaySync(appCtx)
    if (NexusSecurityManager.isExternalPackageUnlocked(packageName, fastDelayMs)) return

    CoroutineScope(Dispatchers.IO).launch {
      checkAndLaunchChallenge(appCtx, packageName)
    }
  }

  private suspend fun checkAndLaunchChallenge(appCtx: Context, packageName: String) {
    val container = NexusContainer.get(appCtx)
    val sec = container.advancedPreferences.getSecuritySnapshot()
    if (!sec.appLockEnabled || !sec.hasPinConfigured) return
    val profile = container.database.appLockDao().getActiveProfile() ?: return
    val protectedPkgs = parseStringList(profile.protectedPackagesJson)
    NexusGuardPrefs.syncActiveProfile(appCtx, protectedPkgs.toSet(), profile.autoLockDelayMs, profile.lockOnScreenOff)
    if (packageName !in protectedPkgs) return
    if (NexusSecurityManager.isExternalPackageUnlocked(packageName, profile.autoLockDelayMs)) return

    val intent = Intent(appCtx, AppLockChallengeActivity::class.java).apply {
      putExtra(AppLockChallengeActivity.EXTRA_TARGET_PACKAGE, packageName)
      addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or
          Intent.FLAG_ACTIVITY_CLEAR_TOP or
          Intent.FLAG_ACTIVITY_SINGLE_TOP or
          Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
      )
    }
    runCatching { appCtx.startActivity(intent) }
  }
}

class AppLockWatchdogWorker(
  context: Context,
  params: WorkerParameters,
) : Worker(context, params) {
  override fun doWork(): Result {
    val appCtx = applicationContext
    val container = NexusContainer.get(appCtx)
    val perm = container.permissionManager
    val canDetect = perm.isUsageStatsGranted() || perm.isAccessibilityServiceEnabled()
    if (NexusGuardPrefs.shouldRunAppLockGuard(appCtx) && canDetect && !NexusAppLockMonitorService.isRunning()) {
      runCatching { NexusAppLockMonitorService.start(appCtx) }
    }
    if (NexusGuardPrefs.shouldRunEdgeBar(appCtx) && perm.isOverlayGranted() && !NexusEdgePanelService.isRunning()) {
      runCatching { NexusEdgePanelService.start(appCtx) }
    }
    return Result.success()
  }
}

class NexusAppLockBootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return
    val appCtx = context.applicationContext

    if (action == Intent.ACTION_SCREEN_OFF) {
      NexusAppLockMonitorService.resetLastObservedPackage()
      NexusSecurityManager.onScreenOffSync(NexusGuardPrefs.getLockOnScreenOffSync(appCtx))
    }

    val perm = NexusContainer.get(appCtx).permissionManager
    val canDetect = perm.isUsageStatsGranted() || perm.isAccessibilityServiceEnabled()
    if (NexusGuardPrefs.shouldRunAppLockGuard(appCtx) && canDetect && !NexusAppLockMonitorService.isRunning()) {
      runCatching { NexusAppLockMonitorService.start(appCtx) }
    }
    if (NexusGuardPrefs.shouldRunEdgeBar(appCtx) && perm.isOverlayGranted() && !NexusEdgePanelService.isRunning()) {
      runCatching { NexusEdgePanelService.start(appCtx) }
    }

    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val container = NexusContainer.get(appCtx)
        val sec = container.advancedPreferences.getSecuritySnapshot()
        val ep = container.advancedPreferences.getEdgePanelSnapshot()
        AppLockMonitorCoordinator.syncProfileCache(appCtx)
        NexusGuardPrefs.syncEdgeBarEnabled(appCtx, ep.enabled)

        if (action == Intent.ACTION_SCREEN_OFF) {
          val profile = container.database.appLockDao().getActiveProfile()
          NexusSecurityManager.onScreenOff(sec, profile?.lockOnScreenOff ?: true)
          NexusAppLockMonitorService.resetLastObservedPackage()
        }
        val shouldRunGuard = sec.appLockEnabled && sec.externalMonitorRunning &&
          (container.permissionManager.isUsageStatsGranted() || container.permissionManager.isAccessibilityServiceEnabled())
        if (shouldRunGuard && !NexusAppLockMonitorService.isRunning()) {
          runCatching { NexusAppLockMonitorService.start(appCtx) }
        }
        if (ep.enabled && container.permissionManager.isOverlayGranted() && !NexusEdgePanelService.isRunning()) {
          runCatching { NexusEdgePanelService.start(appCtx) }
        }
      } finally {
        pendingResult.finish()
      }
    }
  }

  companion object {
    const val ACTION_RESTART_GUARD = "com.example.action.RESTART_APPLOCK_GUARD"
  }
}

class NexusAppLockMonitorService : Service() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var pollJob: Job? = null
  private var explicitUserStop = false

  private val screenOffReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action == Intent.ACTION_SCREEN_OFF) {
        resetLastObservedPackage()
        NexusSecurityManager.onScreenOffSync(NexusGuardPrefs.getLockOnScreenOffSync(applicationContext))
        scope.launch {
          val container = NexusContainer.get(applicationContext)
          val sec = container.advancedPreferences.getSecuritySnapshot()
          val profile = container.database.appLockDao().getActiveProfile()
          NexusSecurityManager.onScreenOff(sec, profile?.lockOnScreenOff ?: true)
        }
      }
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  @SuppressLint("UnspecifiedRegisterReceiverFlag")
  override fun onCreate() {
    super.onCreate()
    running.set(true)
    startAsForeground()
    val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      registerReceiver(screenOffReceiver, filter)
    }
    scope.launch {
      AppLockMonitorCoordinator.syncProfileCache(applicationContext)
      AppLockMonitorCoordinator.scheduleWatchdog(applicationContext, true)
    }
    startUsageStatsPolling()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      explicitUserStop = true
      AppLockMonitorCoordinator.scheduleWatchdog(applicationContext, false)
      stopSelf()
      return START_NOT_STICKY
    }
    explicitUserStop = false
    return START_STICKY
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    if (NexusGuardPrefs.shouldRunAppLockGuard(applicationContext)) {
      AppLockMonitorCoordinator.scheduleImmediateRecovery(applicationContext)
    }
    super.onTaskRemoved(rootIntent)
  }

  private fun startUsageStatsPolling() {
    pollJob?.cancel()
    pollJob = scope.launch {
      val container = NexusContainer.get(applicationContext)
      val perm = container.permissionManager
      val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
      while (isActive) {
        if (!perm.isKeyguardLocked() && perm.isUsageStatsGranted() && usm != null) {
          val now = System.currentTimeMillis()
          val events = runCatching { usm.queryEvents(now - 2500L, now) }.getOrNull()
          var topPkg = ""
          if (events != null) {
            val ev = UsageEvents.Event()
            while (events.hasNextEvent()) {
              events.getNextEvent(ev)
              @Suppress("DEPRECATION")
              val isResumed = ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
              if (isResumed && !ev.packageName.isNullOrBlank()) {
                topPkg = ev.packageName
              }
            }
          }
          if (topPkg.isNotBlank() && topPkg != lastCheckedPackage) {
            lastCheckedPackage = topPkg
            AppLockMonitorCoordinator.onForegroundPackageObserved(applicationContext, topPkg)
          }
        }
        delay(300L)
      }
    }
  }

  private fun startAsForeground() {
    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val chId = "nexus_applock_guard"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      nm.createNotificationChannel(
        NotificationChannel(chId, "NEXUS App Lock Guard", NotificationManager.IMPORTANCE_LOW)
      )
    }
    val openIntent = PendingIntent.getActivity(
      this, 7301,
      Intent(this, MainActivity::class.java).putExtra("route", "app_lock"),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val stopIntent = PendingIntent.getService(
      this, 7302,
      Intent(this, NexusAppLockMonitorService::class.java).setAction(ACTION_STOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notif: Notification = NotificationCompat.Builder(this, chId)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("NEXUS App Lock Guard Active")
      .setContentText("Protecting selected apps in the background")
      .setContentIntent(openIntent)
      .addAction(0, "Stop Guard", stopIntent)
      .setOngoing(true)
      .build()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(7301, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    } else {
      startForeground(7301, notif)
    }
  }

  override fun onDestroy() {
    running.set(false)
    runCatching { unregisterReceiver(screenOffReceiver) }
    if (!explicitUserStop && NexusGuardPrefs.shouldRunAppLockGuard(applicationContext)) {
      AppLockMonitorCoordinator.scheduleImmediateRecovery(applicationContext)
    }
    pollJob?.cancel()
    scope.cancel()
    super.onDestroy()
  }

  companion object {
    private const val ACTION_STOP = "com.example.action.STOP_APPLOCK_GUARD"
    private val running = AtomicBoolean(false)

    @Volatile
    private var lastCheckedPackage = ""

    fun resetLastObservedPackage() {
      lastCheckedPackage = ""
    }

    fun isRunning(): Boolean = running.get()

    fun start(context: Context) {
      val intent = Intent(context.applicationContext, NexusAppLockMonitorService::class.java)
      ContextCompat.startForegroundService(context.applicationContext, intent)
    }

    fun stop(context: Context) {
      lastCheckedPackage = ""
      NexusGuardPrefs.syncSecurityState(context, monitorRunning = false)
      AppLockMonitorCoordinator.scheduleWatchdog(context, false)
      context.applicationContext.stopService(
        Intent(context.applicationContext, NexusAppLockMonitorService::class.java)
      )
    }
  }
}

class AppLockChallengeActivity : FragmentActivity() {
  companion object {
    const val EXTRA_TARGET_PACKAGE = "extra_target_package"

    @Volatile
    var isForegroundVisible: Boolean = false
      private set
  }

  private var targetPackageState by mutableStateOf("")

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    targetPackageState = intent.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty()
  }

  override fun onResume() {
    super.onResume()
    isForegroundVisible = true
  }

  override fun onPause() {
    super.onPause()
    isForegroundVisible = false
  }

  override fun onDestroy() {
    isForegroundVisible = false
    super.onDestroy()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    targetPackageState = intent?.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty()
    val container = NexusContainer.get(applicationContext)

    setContent {
      NexusTheme {
        val colors = NexusThemeExtras.colors
        val scope = rememberCoroutineScope()
        val secConfig by container.advancedPreferences.securityConfig.collectAsState(initial = SecuritySettingsConfig())
        val targetPkg = targetPackageState
        val appLabel = remember(targetPkg) {
          runCatching {
            val info = packageManager.getApplicationInfo(targetPkg, 0)
            packageManager.getApplicationLabel(info).toString()
          }.getOrDefault(targetPkg.ifBlank { "Protected App" })
        }

        var pinOrPwdInput by remember(targetPkg, secConfig.activeLockType) { mutableStateOf("") }
        var passwordVisible by remember(targetPkg) { mutableStateOf(false) }
        var patternNodes by remember(targetPkg, secConfig.activeLockType) { mutableStateOf<List<Int>>(emptyList()) }
        var statusMsg by remember(targetPkg, secConfig.activeLockType) {
          mutableStateOf(
            when (secConfig.activeLockType) {
              NexusLockType.PATTERN -> "Draw your pattern or use Face / Fingerprint unlock"
              NexusLockType.PIN -> "Enter your PIN or use Face / Fingerprint unlock"
              NexusLockType.PASSWORD -> "Enter your Password or use Face / Fingerprint unlock"
            }
          )
        }
        var isError by remember(targetPkg) { mutableStateOf(false) }

        val goHomeAndClose = {
          NexusAppLockMonitorService.resetLastObservedPackage()
          startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
          finishAndRemoveTask()
        }

        val launchBiometric: () -> Unit = {
          NexusSecurityManager.showBiometricPrompt(
            activity = this@AppLockChallengeActivity,
            title = "Unlock $appLabel",
            subtitle = "Use Face Lock or Fingerprint",
            fallbackLabel = "Use ${secConfig.activeLockType.label}",
            onSuccess = {
              scope.launch {
                NexusSecurityManager.recordBiometricOutcome(
                  success = true,
                  targetType = "APP_LOCK_EXTERNAL",
                  targetName = appLabel,
                  reason = "",
                  appLockDao = container.database.appLockDao(),
                  externalPackageToUnlock = targetPkg,
                )
                finishAndRemoveTask()
              }
            },
            onFailure = { msg ->
              isError = true
              statusMsg = msg
              scope.launch {
                NexusSecurityManager.recordBiometricOutcome(
                  success = false,
                  targetType = "APP_LOCK_EXTERNAL",
                  targetName = appLabel,
                  reason = msg,
                  appLockDao = container.database.appLockDao(),
                )
              }
            },
          )
        }

        val canUseBio = secConfig.biometricEnabled && container.permissionManager.canUseBiometrics()
        LaunchedEffect(targetPkg, canUseBio) {
          if (targetPkg.isNotBlank() && canUseBio) {
            launchBiometric()
          }
        }

        val submitSecret: (String) -> Unit = { secret ->
          scope.launch {
            val res = NexusSecurityManager.verifyCredential(
              secret = secret,
              targetType = "APP_LOCK_EXTERNAL",
              targetName = appLabel,
              prefs = container.advancedPreferences,
              appLockDao = container.database.appLockDao(),
              externalPackageToUnlock = targetPkg,
            )
            if (res.success) {
              finishAndRemoveTask()
            } else {
              isError = true
              statusMsg = res.message
              pinOrPwdInput = ""
              patternNodes = emptyList()
            }
          }
        }

        BackHandler {
          goHomeAndClose()
        }

        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          NexusGlassCard(
            modifier = Modifier.widthIn(max = 420.dp),
            accentHighlight = true,
            testTag = "external_applock_challenge_card",
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Icon(Icons.Default.Lock, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text("App Protected", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                Text(appLabel, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
              }
              StatusPill(secConfig.activeLockType.label.uppercase(), colors.accent)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              statusMsg,
              style = MaterialTheme.typography.bodyMedium,
              color = if (isError) colors.critical else colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(14.dp))

            when (secConfig.activeLockType) {
              NexusLockType.PATTERN -> {
                Column(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  PatternLockGrid(
                    selectedNodes = patternNodes,
                    onNodesChanged = { patternNodes = it },
                    onPatternCompleted = { nodes ->
                      if (nodes.size >= 4) {
                        submitSecret(nodes.joinToString("-"))
                      } else {
                        isError = true
                        statusMsg = "Connect at least 4 dots."
                        patternNodes = emptyList()
                      }
                    },
                    testTag = "external_applock_pattern_grid",
                  )
                }
              }
              NexusLockType.PIN -> {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.Center,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  val slots = maxOf(4, pinOrPwdInput.length)
                  for (idx in 0 until slots) {
                    val filled = idx < pinOrPwdInput.length
                    Box(
                      modifier = Modifier
                        .padding(horizontal = 5.dp)
                        .size(13.dp)
                        .background(
                          color = if (filled) colors.accent else colors.elevatedSurface,
                          shape = RoundedCornerShape(50),
                        ),
                    )
                  }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                  value = pinOrPwdInput,
                  onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pinOrPwdInput = it },
                  label = { Text("Security PIN") },
                  visualTransformation = PasswordVisualTransformation(),
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("external_applock_pin_input"),
                )
                Spacer(modifier = Modifier.height(10.dp))
                NumericKeypadGrid(
                  onDigit = { if (pinOrPwdInput.length < 8) pinOrPwdInput += it },
                  onClear = { pinOrPwdInput = "" },
                  onBackspace = { pinOrPwdInput = pinOrPwdInput.dropLast(1) },
                  testTagPrefix = "external_applock",
                )
              }
              NexusLockType.PASSWORD -> {
                OutlinedTextField(
                  value = pinOrPwdInput,
                  onValueChange = { if (it.length <= 64) pinOrPwdInput = it },
                  label = { Text("Security Password") },
                  visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                  trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                      Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle password visibility",
                      )
                    }
                  },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("external_applock_password_input"),
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (secConfig.activeLockType == NexusLockType.PATTERN) {
                OutlinedButton(
                  onClick = { patternNodes = emptyList() },
                  modifier = Modifier.weight(1f),
                ) {
                  Text("Clear")
                }
              }
              Button(
                onClick = {
                  val secret = if (secConfig.activeLockType == NexusLockType.PATTERN) {
                    patternNodes.joinToString("-")
                  } else {
                    pinOrPwdInput
                  }
                  submitSecret(secret)
                },
                modifier = Modifier.weight(1f).testTag("external_applock_unlock_button"),
              ) {
                Text("Unlock")
              }
            }
            if (canUseBio) {
              Spacer(modifier = Modifier.height(8.dp))
              OutlinedButton(
                onClick = launchBiometric,
                modifier = Modifier.fillMaxWidth().testTag("external_applock_biometric_button"),
              ) {
                Icon(Icons.Default.Fingerprint, contentDescription = "Face or Fingerprint Unlock", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Face / Fingerprint")
              }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
              onClick = goHomeAndClose,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Go Home")
            }
          }
        }
      }
    }
  }
}
