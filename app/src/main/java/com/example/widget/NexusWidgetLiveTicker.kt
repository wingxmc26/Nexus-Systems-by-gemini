package com.example.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.example.widget.engine.WidgetMediaSessionHub
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-scoped event coordinator for real-time widget refreshes:
 * - Minute clock ticks (ACTION_TIME_TICK), time/timezone/date/locale changes
 * - Screen wake / device unlock
 * - Battery level / charging plug state changes (throttled safely)
 * - ConnectivityManager active network callbacks
 * - MediaSession observer & persisted periodic JobScheduler fallback
 */
object NexusWidgetLiveTicker {
  private val registered = AtomicBoolean(false)
  private val networkCallbackRegistered = AtomicBoolean(false)
  private val lastBatteryRefreshMs = AtomicLong(0L)
  private val lastBatteryLevel = AtomicInteger(-1)
  private val lastBatteryStatus = AtomicInteger(-1)
  private val tickerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val receiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
      val action = intent?.action ?: return
      val appCtx = context.applicationContext

      if (action == Intent.ACTION_SCREEN_OFF) {
        val pending = runCatching { goAsync() }.getOrNull()
        tickerScope.launch {
          try {
            val container = com.example.di.NexusContainer.get(appCtx)
            val appLockScreenOff = container.database.appLockDao().getActiveProfile()?.lockOnScreenOff ?: true
            com.example.security.NexusSecurityManager.onScreenOff(
              config = container.advancedPreferences.getSecuritySnapshot(),
              appLockOnScreenOff = appLockScreenOff,
            )
          } finally {
            runCatching { pending?.finish() }
          }
        }
        return
      }

      val shouldRefresh = when (action) {
        Intent.ACTION_TIME_TICK,
        Intent.ACTION_SCREEN_ON,
        Intent.ACTION_USER_PRESENT,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_DATE_CHANGED,
        Intent.ACTION_LOCALE_CHANGED,
        Intent.ACTION_POWER_CONNECTED,
        Intent.ACTION_POWER_DISCONNECTED -> true

        Intent.ACTION_BATTERY_CHANGED -> {
          val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
          val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
          val prevLvl = lastBatteryLevel.getAndSet(level)
          val prevStatus = lastBatteryStatus.getAndSet(status)
          val now = System.currentTimeMillis()
          val elapsed = now - lastBatteryRefreshMs.get()
          val meaningfulChange = (prevLvl != level || prevStatus != status)
          if (meaningfulChange || elapsed >= 30_000L) {
            lastBatteryRefreshMs.set(now)
            true
          } else {
            false
          }
        }

        else -> false
      }
      if (!shouldRefresh) return

      val pending = runCatching { goAsync() }.getOrNull()
      tickerScope.launch {
        try {
          NexusWidgetUpdater.refreshAll(appCtx)
        } finally {
          runCatching { pending?.finish() }
        }
      }
    }
  }

  private val networkCallback = object : ConnectivityManager.NetworkCallback() {
    private var appCtx: Context? = null

    fun bind(context: Context) {
      appCtx = context.applicationContext
    }

    override fun onAvailable(network: Network) {
      triggerRefresh()
    }

    override fun onLost(network: Network) {
      triggerRefresh()
    }

    private fun triggerRefresh() {
      val ctx = appCtx ?: return
      tickerScope.launch {
        NexusWidgetUpdater.refreshAll(ctx)
      }
    }
  }

  fun ensureRegistered(context: Context) {
    val appCtx = context.applicationContext
    WidgetMediaSessionHub.attachSessionObserver(appCtx)
    NexusWidgetSyncJobService.schedulePeriodic(appCtx)

    if (registered.compareAndSet(false, true)) {
      val filter = IntentFilter().apply {
        addAction(Intent.ACTION_TIME_TICK)
        addAction(Intent.ACTION_SCREEN_ON)
        addAction(Intent.ACTION_SCREEN_OFF)
        addAction(Intent.ACTION_USER_PRESENT)
        addAction(Intent.ACTION_TIME_CHANGED)
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
        addAction(Intent.ACTION_DATE_CHANGED)
        addAction(Intent.ACTION_LOCALE_CHANGED)
        addAction(Intent.ACTION_POWER_CONNECTED)
        addAction(Intent.ACTION_POWER_DISCONNECTED)
        addAction(Intent.ACTION_BATTERY_CHANGED)
      }
      val ok = runCatching {
        ContextCompat.registerReceiver(appCtx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        true
      }.getOrDefault(false)
      if (!ok) {
        registered.set(false)
      }
    }

    if (networkCallbackRegistered.compareAndSet(false, true)) {
      val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
      if (cm != null) {
        networkCallback.bind(appCtx)
        val okNet = runCatching {
          cm.registerDefaultNetworkCallback(networkCallback)
          true
        }.getOrDefault(false)
        if (!okNet) {
          networkCallbackRegistered.set(false)
        }
      } else {
        networkCallbackRegistered.set(false)
      }
    }
  }

  fun isRegistered(): Boolean = registered.get()
}
