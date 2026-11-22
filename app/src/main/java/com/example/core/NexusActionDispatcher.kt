package com.example.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.accessibility.NexusAccessibilityService
import com.example.data.local.ExecutionLogEntity
import com.example.data.local.NexusDatabase
import com.example.data.system.PermissionManager
import com.example.data.system.ShortcutsDataSource
import com.example.overlay.NexusEdgePanelService
import com.example.overlay.NexusFloatingToolsService
import com.example.security.NexusSecurityManager
import com.example.ui.navigation.NexusRoutes
import com.example.widget.NexusWidgetUpdater
import com.example.widget.engine.MediaControlAction
import com.example.widget.engine.WidgetMediaSessionHub
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONArray
import org.json.JSONObject

object NexusActionType {
  const val OPEN_NEXUS_MODULE = "OPEN_NEXUS_MODULE"
  const val LAUNCH_APP = "LAUNCH_APP"
  const val MEDIA_PLAY_PAUSE = "MEDIA_PLAY_PAUSE"
  const val MEDIA_NEXT = "MEDIA_NEXT"
  const val MEDIA_PREV = "MEDIA_PREV"
  const val SET_BRIGHTNESS = "SET_BRIGHTNESS"
  const val SET_AUTO_ROTATE = "SET_AUTO_ROTATE"
  const val REFRESH_WIDGETS = "REFRESH_WIDGETS"
  const val SHOW_NOTIFICATION = "SHOW_NOTIFICATION"
  const val HAPTIC_PULSE = "HAPTIC_PULSE"
  const val TOGGLE_FLOATING_TOOLS = "TOGGLE_FLOATING_TOOLS"
  const val TOGGLE_EDGE_PANEL = "TOGGLE_EDGE_PANEL"
  const val LOCK_VAULT_AND_APPS = "LOCK_VAULT_AND_APPS"
  const val OPEN_SYSTEM_SETTINGS = "OPEN_SYSTEM_SETTINGS"
  const val GLOBAL_BACK = "GLOBAL_BACK"
  const val GLOBAL_HOME = "GLOBAL_HOME"
  const val GLOBAL_RECENTS = "GLOBAL_RECENTS"
  const val GLOBAL_NOTIFICATIONS = "GLOBAL_NOTIFICATIONS"
  const val COPY_TEXT = "COPY_TEXT"
}

data class NexusActionSpec(
  val type: String,
  val label: String,
  val param1: String = "",
  val param2: String = "",
) {
  fun toJson(): JSONObject = JSONObject().apply {
    put("type", type)
    put("label", label)
    put("param1", param1)
    put("param2", param2)
  }

  companion object {
    fun fromJson(obj: JSONObject): NexusActionSpec {
      return NexusActionSpec(
        type = obj.optString("type", NexusActionType.REFRESH_WIDGETS),
        label = obj.optString("label", "Action"),
        param1 = obj.optString("param1", ""),
        param2 = obj.optString("param2", ""),
      )
    }

    fun parseList(json: String): List<NexusActionSpec> {
      if (json.isBlank()) return emptyList()
      return runCatching {
        val arr = JSONArray(json)
        buildList {
          for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { add(fromJson(it)) }
          }
        }
      }.getOrDefault(emptyList())
    }

    fun serializeList(list: List<NexusActionSpec>): String {
      return JSONArray().apply {
        list.forEach { put(it.toJson()) }
      }.toString()
    }
  }
}

data class ActionDispatchResult(
  val success: Boolean,
  val message: String,
  val missingCapability: String? = null,
)

data class NexusUiEvent(
  val routeTarget: String? = null,
  val statusBanner: String? = null,
  val timestampMs: Long = System.currentTimeMillis(),
)

/**
 * Unified ActionDispatcher shared across Gesture Studio, Floating Tools, and Edge Panel.
 */
class NexusActionDispatcher(
  private val context: Context,
  private val permissionManager: PermissionManager,
  private val shortcutsDataSource: ShortcutsDataSource,
  private val database: NexusDatabase,
) {
  private val appCtx = context.applicationContext

  private val _uiEvents = MutableSharedFlow<NexusUiEvent>(extraBufferCapacity = 16)
  val uiEvents: SharedFlow<NexusUiEvent> = _uiEvents.asSharedFlow()

  data class ActionCatalogOption(
    val type: String,
    val title: String,
    val subtitle: String,
    val defaultParam1: String = "",
    val defaultParam2: String = "",
  )

  fun availableCatalog(): List<ActionCatalogOption> = listOf(
    ActionCatalogOption(NexusActionType.OPEN_NEXUS_MODULE, "Open NEXUS Module", "Navigate to Battery, Vault, App Lock, etc.", NexusRoutes.BATTERY),
    ActionCatalogOption(NexusActionType.LAUNCH_APP, "Launch Installed App", "Open an installed package by packageName", "com.android.settings"),
    ActionCatalogOption(NexusActionType.MEDIA_PLAY_PAUSE, "Media Play / Pause", "Control active Spotify / YT Music / Nexus Audio"),
    ActionCatalogOption(NexusActionType.MEDIA_NEXT, "Media Next Track", "Skip to next track via active MediaSession"),
    ActionCatalogOption(NexusActionType.MEDIA_PREV, "Media Previous Track", "Return to previous track via MediaSession"),
    ActionCatalogOption(NexusActionType.SET_BRIGHTNESS, "Set Screen Brightness", "Direct system brightness (requires Modify Settings)", "45"),
    ActionCatalogOption(NexusActionType.SET_AUTO_ROTATE, "Set Auto-Rotate", "true / false / toggle (requires Modify Settings)", "toggle"),
    ActionCatalogOption(NexusActionType.SHOW_NOTIFICATION, "Show Alert Notification", "Post local notification (requires Notification permission)", "NEXUS Rule Triggered", "Executed native local action"),
    ActionCatalogOption(NexusActionType.HAPTIC_PULSE, "Haptic Feedback Pulse", "Trigger device vibration impulse", "60"),
    ActionCatalogOption(NexusActionType.REFRESH_WIDGETS, "Refresh Home Widgets", "Force sync Battery, Clock, Music & Custom widgets"),
    ActionCatalogOption(NexusActionType.TOGGLE_FLOATING_TOOLS, "Toggle Floating Tools", "Start/stop Floating Tools overlay (requires Overlay)", "toggle"),
    ActionCatalogOption(NexusActionType.TOGGLE_EDGE_PANEL, "Toggle Edge Panel", "Start/stop Edge Panel overlay (requires Overlay)", "toggle"),
    ActionCatalogOption(NexusActionType.LOCK_VAULT_AND_APPS, "Lock Vault & App Lock", "Immediately re-lock Secure Vault & protected modules"),
    ActionCatalogOption(NexusActionType.OPEN_SYSTEM_SETTINGS, "Open System Settings", "wifi / bluetooth / battery / display / nfc", "display"),
    ActionCatalogOption(NexusActionType.GLOBAL_BACK, "System Back (Accessibility)", "Requires optional Accessibility Service"),
    ActionCatalogOption(NexusActionType.GLOBAL_HOME, "System Home (Accessibility)", "Requires optional Accessibility Service"),
    ActionCatalogOption(NexusActionType.GLOBAL_RECENTS, "System Recents (Accessibility)", "Requires optional Accessibility Service"),
    ActionCatalogOption(NexusActionType.GLOBAL_NOTIFICATIONS, "Notification Shade (Accessibility)", "Requires optional Accessibility Service"),
  )

  suspend fun dispatchAll(
    actions: List<NexusActionSpec>,
    sourceType: String,
    sourceId: Long,
    sourceName: String,
    branch: String = "RUN",
  ): List<ActionDispatchResult> {
    if (actions.isEmpty()) {
      val res = ActionDispatchResult(false, "No actions configured")
      logExecution(sourceType, sourceId, sourceName, false, branch, res.message, res.message)
      return listOf(res)
    }

    val results = mutableListOf<ActionDispatchResult>()
    for (spec in actions) {
      results += dispatchSingle(spec)
    }
    val allOk = results.all { it.success }
    val summary = results.joinToString(" • ") { it.message }
    val err = results.firstOrNull { !it.success }?.message
    logExecution(
      sourceType = sourceType,
      sourceId = sourceId,
      sourceName = sourceName,
      success = allOk,
      branch = branch,
      details = summary,
      error = err,
    )
    return results
  }

  suspend fun dispatchSingle(spec: NexusActionSpec): ActionDispatchResult {
    return when (spec.type) {
      NexusActionType.OPEN_NEXUS_MODULE -> {
        val route = spec.param1.ifBlank { NexusRoutes.DASHBOARD }
        _uiEvents.tryEmit(NexusUiEvent(routeTarget = route))
        runCatching {
          val intent = Intent(appCtx, MainActivity::class.java).apply {
            putExtra("route", route)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
          }
          appCtx.startActivity(intent)
        }
        ActionDispatchResult(true, "Opened module: $route")
      }

      NexusActionType.LAUNCH_APP -> {
        val pkg = spec.param1.trim()
        if (pkg.isBlank()) {
          return ActionDispatchResult(false, "No package name configured")
        }
        val intent = appCtx.packageManager.getLaunchIntentForPackage(pkg)?.apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent == null) {
          ActionDispatchResult(false, "App not installed or no launcher entry: $pkg")
        } else {
          runCatching {
            appCtx.startActivity(intent)
            ActionDispatchResult(true, "Launched $pkg")
          }.getOrElse { ActionDispatchResult(false, "Launch failed: ${it.message}") }
        }
      }

      NexusActionType.MEDIA_PLAY_PAUSE -> {
        val ok = WidgetMediaSessionHub.dispatchTransportCommand(appCtx, MediaControlAction.PLAY_PAUSE)
        ActionDispatchResult(ok, if (ok) "Media play/pause sent" else "No active media session found")
      }

      NexusActionType.MEDIA_NEXT -> {
        val ok = WidgetMediaSessionHub.dispatchTransportCommand(appCtx, MediaControlAction.NEXT)
        ActionDispatchResult(ok, if (ok) "Skipped to next track" else "No active media session found")
      }

      NexusActionType.MEDIA_PREV -> {
        val ok = WidgetMediaSessionHub.dispatchTransportCommand(appCtx, MediaControlAction.PREV)
        ActionDispatchResult(ok, if (ok) "Skipped to previous track" else "No active media session found")
      }

      NexusActionType.SET_BRIGHTNESS -> {
        if (!permissionManager.isWriteSettingsGranted()) {
          return ActionDispatchResult(
            success = false,
            message = "Modify System Settings permission required for brightness",
            missingCapability = "WRITE_SETTINGS",
          )
        }
        val pct = (spec.param1.toIntOrNull() ?: 50).coerceIn(5, 100)
        val ok = shortcutsDataSource.setBrightnessPercent(pct)
        ActionDispatchResult(ok, if (ok) "Brightness set to $pct%" else "Failed to write system brightness")
      }

      NexusActionType.SET_AUTO_ROTATE -> {
        if (!permissionManager.isWriteSettingsGranted()) {
          return ActionDispatchResult(
            success = false,
            message = "Modify System Settings permission required for auto-rotate",
            missingCapability = "WRITE_SETTINGS",
          )
        }
        val target = when (spec.param1.trim().lowercase()) {
          "true", "on", "1" -> true
          "false", "off", "0" -> false
          else -> !shortcutsDataSource.readAutoRotate()
        }
        val ok = shortcutsDataSource.setAutoRotate(target)
        ActionDispatchResult(ok, if (ok) "Auto-rotate ${if (target) "ON" else "OFF"}" else "Failed to set auto-rotate")
      }

      NexusActionType.REFRESH_WIDGETS -> {
        NexusWidgetUpdater.refreshAll(appCtx)
        ActionDispatchResult(true, "Refreshed all home widgets")
      }

      NexusActionType.SHOW_NOTIFICATION -> {
        if (!permissionManager.isPostNotificationsGranted()) {
          return ActionDispatchResult(
            success = false,
            message = "Notification permission required to post alert",
            missingCapability = "POST_NOTIFICATIONS",
          )
        }
        val title = spec.param1.ifBlank { "NEXUS SYSTEM" }
        val body = spec.param2.ifBlank { "Action executed." }
        postLocalAlertNotification(title, body)
        ActionDispatchResult(true, "Notification posted: $title")
      }

      NexusActionType.HAPTIC_PULSE -> {
        val duration = (spec.param1.toLongOrNull() ?: 55L).coerceIn(15L, 400L)
        val ok = triggerVibration(duration)
        ActionDispatchResult(ok, if (ok) "Haptic pulse (${duration}ms)" else "Vibrator unavailable on device")
      }

      NexusActionType.TOGGLE_FLOATING_TOOLS -> {
        if (!permissionManager.isOverlayGranted()) {
          return ActionDispatchResult(
            success = false,
            message = "Display Over Other Apps permission required for Floating Tools",
            missingCapability = "SYSTEM_ALERT_WINDOW",
          )
        }
        val enable = when (spec.param1.lowercase()) {
          "on", "start", "true" -> true
          "off", "stop", "false" -> false
          else -> !NexusFloatingToolsService.isRunning()
        }
        if (enable) {
          NexusFloatingToolsService.start(appCtx)
          ActionDispatchResult(true, "Floating Tools overlay started")
        } else {
          NexusFloatingToolsService.stop(appCtx)
          ActionDispatchResult(true, "Floating Tools overlay stopped")
        }
      }

      NexusActionType.TOGGLE_EDGE_PANEL -> {
        if (!permissionManager.isOverlayGranted()) {
          return ActionDispatchResult(
            success = false,
            message = "Display Over Other Apps permission required for Edge Panel",
            missingCapability = "SYSTEM_ALERT_WINDOW",
          )
        }
        val enable = when (spec.param1.lowercase()) {
          "on", "start", "true" -> true
          "off", "stop", "false" -> false
          else -> !NexusEdgePanelService.isRunning()
        }
        if (enable) {
          NexusEdgePanelService.start(appCtx)
          ActionDispatchResult(true, "Edge Panel overlay started")
        } else {
          NexusEdgePanelService.stop(appCtx)
          ActionDispatchResult(true, "Edge Panel overlay stopped")
        }
      }

      NexusActionType.LOCK_VAULT_AND_APPS -> {
        NexusSecurityManager.lockAllImmediately()
        _uiEvents.tryEmit(NexusUiEvent(statusBanner = "Secure Vault & App Lock locked"))
        ActionDispatchResult(true, "Vault and internal protected modules locked")
      }

      NexusActionType.OPEN_SYSTEM_SETTINGS -> {
        val action = when (spec.param1.trim().lowercase()) {
          "wifi" -> Settings.ACTION_WIFI_SETTINGS
          "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
          "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
          "nfc" -> Settings.ACTION_NFC_SETTINGS
          "sound" -> Settings.ACTION_SOUND_SETTINGS
          else -> Settings.ACTION_DISPLAY_SETTINGS
        }
        runCatching {
          appCtx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
          ActionDispatchResult(true, "Opened system settings: ${spec.param1.ifBlank { "display" }}")
        }.getOrElse { ActionDispatchResult(false, "Settings screen unavailable: $action") }
      }

      NexusActionType.GLOBAL_BACK,
      NexusActionType.GLOBAL_HOME,
      NexusActionType.GLOBAL_RECENTS,
      NexusActionType.GLOBAL_NOTIFICATIONS -> {
        if (!permissionManager.isAccessibilityServiceEnabled()) {
          return ActionDispatchResult(
            success = false,
            message = "Enable optional Nexus Accessibility Service to run global system navigation actions",
            missingCapability = "ACCESSIBILITY",
          )
        }
        val svc = NexusAccessibilityService.currentInstance()
          ?: return ActionDispatchResult(false, "Accessibility service binding not active yet")
        val ok = when (spec.type) {
          NexusActionType.GLOBAL_BACK -> svc.performGlobalBack()
          NexusActionType.GLOBAL_HOME -> svc.performGlobalHome()
          NexusActionType.GLOBAL_RECENTS -> svc.performGlobalRecents()
          else -> svc.performGlobalNotifications()
        }
        ActionDispatchResult(ok, if (ok) "Executed ${spec.type}" else "System rejected global action")
      }

      NexusActionType.COPY_TEXT -> {
        val cm = appCtx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("NEXUS", spec.param1))
        ActionDispatchResult(cm != null, if (cm != null) "Copied snippet to clipboard" else "Clipboard unavailable")
      }

      else -> ActionDispatchResult(false, "Unsupported action type: ${spec.type}")
    }
  }

  private suspend fun logExecution(
    sourceType: String,
    sourceId: Long,
    sourceName: String,
    success: Boolean,
    branch: String,
    details: String,
    error: String?,
  ) {
    runCatching {
      database.executionLogDao().insert(
        ExecutionLogEntity(
          sourceType = sourceType,
          sourceId = sourceId,
          sourceName = sourceName,
          success = success,
          branch = branch,
          details = details,
          errorMessage = error,
          executedAtMs = System.currentTimeMillis(),
        )
      )
    }
  }

  private fun postLocalAlertNotification(title: String, body: String) {
    val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
    val channelId = "nexus_automation_alerts"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val ch = NotificationChannel(channelId, "NEXUS Alerts", NotificationManager.IMPORTANCE_DEFAULT)
      nm.createNotificationChannel(ch)
    }
    val openIntent = PendingIntent.getActivity(
      appCtx,
      7101,
      Intent(appCtx, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notif = NotificationCompat.Builder(appCtx, channelId)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(title)
      .setContentText(body)
      .setStyle(NotificationCompat.BigTextStyle().bigText(body))
      .setContentIntent(openIntent)
      .setAutoCancel(true)
      .build()
    nm.notify((System.currentTimeMillis() and 0xFFFF).toInt(), notif)
  }

  @Suppress("DEPRECATION")
  private fun triggerVibration(durationMs: Long): Boolean {
    return runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = appCtx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager ?: return false
        vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
      } else {
        val vib = ContextCompat.getSystemService(appCtx, Vibrator::class.java) ?: return false
        vib.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
      }
      true
    }.getOrDefault(false)
  }
}
