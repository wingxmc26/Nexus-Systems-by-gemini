package com.example.data.system

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import com.example.notifications.NexusNotificationListenerService

data class PermissionStatusEntry(
  val id: String,
  val title: String,
  val description: String,
  val granted: Boolean,
  val moduleName: String,
)

class PermissionManager(private val context: Context) {

  fun isAllFilesAccessGranted(): Boolean {
    return runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
      } else {
        hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE) &&
          hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
      }
    }.getOrDefault(false)
  }

  fun isWriteSettingsGranted(): Boolean {
    return runCatching { Settings.System.canWrite(context) }.getOrDefault(false)
  }

  fun isOverlayGranted(): Boolean {
    return runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)
  }

  fun isAccessibilityServiceEnabled(): Boolean {
    return runCatching {
      val enabled = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
      if (!enabled) return@runCatching false
      val flat = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
      ) ?: return@runCatching false
      val target = "${context.packageName}/com.example.accessibility.NexusAccessibilityService"
      val shortTarget = "${context.packageName}/.accessibility.NexusAccessibilityService"
      flat.split(":").any { item ->
        item.equals(target, ignoreCase = true) || item.equals(shortTarget, ignoreCase = true)
      }
    }.getOrDefault(false)
  }

  fun isNotificationAccessEnabled(): Boolean {
    return runCatching {
      val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        ?: return@runCatching false
      val target = ComponentName(context, NexusNotificationListenerService::class.java)
      flat.split(":").any { item ->
        val cn = ComponentName.unflattenFromString(item)
        cn == target || item.contains(context.packageName)
      }
    }.getOrDefault(false)
  }

  @Suppress("DEPRECATION")
  fun isUsageStatsGranted(): Boolean {
    return runCatching {
      val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return@runCatching false
      val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
          AppOpsManager.OPSTR_GET_USAGE_STATS,
          Process.myUid(),
          context.packageName,
        )
      } else {
        appOps.checkOpNoThrow(
          AppOpsManager.OPSTR_GET_USAGE_STATS,
          Process.myUid(),
          context.packageName,
        )
      }
      mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)
  }

  fun canScheduleExactAlarms(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
      runCatching { am?.canScheduleExactAlarms() == true }.getOrDefault(false)
    } else {
      true
    }
  }

  fun isNfcSupported(): Boolean {
    return runCatching { NfcAdapter.getDefaultAdapter(context) != null }.getOrDefault(false)
  }

  fun isNfcEnabled(): Boolean {
    return runCatching { NfcAdapter.getDefaultAdapter(context)?.isEnabled == true }.getOrDefault(false)
  }

  fun canUseBiometrics(): Boolean {
    return runCatching {
      val bm = BiometricManager.from(context)
      bm.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
          BiometricManager.Authenticators.BIOMETRIC_WEAK
      ) == BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)
  }

  fun biometricHardwareState(): Int {
    return runCatching {
      BiometricManager.from(context).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
          BiometricManager.Authenticators.BIOMETRIC_WEAK
      )
    }.getOrDefault(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE)
  }

  fun isDeviceCredentialSecure(): Boolean {
    return runCatching {
      val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
      km?.isDeviceSecure == true
    }.getOrDefault(false)
  }

  fun isKeyguardLocked(): Boolean {
    return runCatching {
      val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
      km?.isKeyguardLocked == true
    }.getOrDefault(false)
  }

  fun isAudioMediaGranted(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      hasPermission(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
      hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
  }

  fun audioMediaPermissionName(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      Manifest.permission.READ_MEDIA_AUDIO
    } else {
      Manifest.permission.READ_EXTERNAL_STORAGE
    }
  }

  fun isVideoMediaGranted(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      hasPermission(Manifest.permission.READ_MEDIA_VIDEO)
    } else {
      hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
  }

  fun videoMediaPermissionName(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      Manifest.permission.READ_MEDIA_VIDEO
    } else {
      Manifest.permission.READ_EXTERNAL_STORAGE
    }
  }

  fun isRecordAudioGranted(): Boolean = hasPermission(Manifest.permission.RECORD_AUDIO)

  fun isPostNotificationsGranted(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      hasPermission(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      true
    }
  }

  fun isCalendarGranted(): Boolean = hasPermission(Manifest.permission.READ_CALENDAR)

  fun isCoarseLocationGranted(): Boolean = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

  fun hasPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
  }

  fun buildAllFilesAccessIntent(): Intent {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val appSpecific = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      if (appSpecific.resolveActivity(context.packageManager) != null) return appSpecific
      return Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    }
    return buildAppDetailsIntent()
  }

  fun buildWriteSettingsIntent(): Intent {
    return Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
      data = Uri.fromParts("package", context.packageName, null)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  fun buildOverlayIntent(): Intent {
    return Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
      data = Uri.fromParts("package", context.packageName, null)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  fun buildAccessibilitySettingsIntent(): Intent {
    return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  fun buildExactAlarmIntent(): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    } else {
      buildAppDetailsIntent()
    }
  }

  fun buildNfcSettingsIntent(): Intent {
    return Intent(Settings.ACTION_NFC_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  fun buildNotificationAccessIntent(): Intent {
    return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  fun buildUsageStatsIntent(): Intent {
    return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  fun buildAppDetailsIntent(): Intent {
    return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
      data = Uri.fromParts("package", context.packageName, null)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  fun buildBatteryOptimizationSettingsIntent(): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    } else {
      buildAppDetailsIntent()
    }
  }

  fun loadAuditSnapshot(): List<PermissionStatusEntry> {
    return listOf(
      PermissionStatusEntry(
        id = "notification_access",
        title = "Notification Listener Access",
        description = "Records incoming notification history locally while enabled and mirrors external media players cleanly.",
        granted = isNotificationAccessEnabled(),
        moduleName = "Notification Studio & Widgets",
      ),
      PermissionStatusEntry(
        id = "write_settings",
        title = "Modify System Settings",
        description = "Allows direct controls for screen brightness, sleep timeout, and auto-rotate.",
        granted = isWriteSettingsGranted(),
        moduleName = "Quick Controls",
      ),
      PermissionStatusEntry(
        id = "overlay",
        title = "Display Over Other Apps",
        description = "Enables Floating Tools bubble, Edge Panel overlay, optional system-wide gesture zones, and external App Lock challenge.",
        granted = isOverlayGranted(),
        moduleName = "Floating Tools, Edge Panel & App Lock",
      ),
      PermissionStatusEntry(
        id = "accessibility",
        title = "Optional Assistant Service (Accessibility)",
        description = "Optional OS-gated service for system Back/Home/Recents gestures and instant foreground App Lock detection. Reads no screen text.",
        granted = isAccessibilityServiceEnabled(),
        moduleName = "Gesture Studio & App Lock",
      ),
      PermissionStatusEntry(
        id = "exact_alarms",
        title = "Exact Time Alarms",
        description = "Keeps minute-accurate custom widgets and background guard recovery scheduling responsive.",
        granted = canScheduleExactAlarms(),
        moduleName = "Widget Engine & App Lock",
      ),
      PermissionStatusEntry(
        id = "audio_media",
        title = "Local Audio Library Access",
        description = "Scans device folders for local music tracks, albums, and artists.",
        granted = isAudioMediaGranted(),
        moduleName = "Music Player",
      ),
      PermissionStatusEntry(
        id = "video_media",
        title = "Local Video Library Access",
        description = "Scans device folders for local videos for offline playback.",
        granted = isVideoMediaGranted(),
        moduleName = "Video Player",
      ),
      PermissionStatusEntry(
        id = "record_audio",
        title = "Microphone Access",
        description = "Optional narration audio track during screen recording.",
        granted = isRecordAudioGranted(),
        moduleName = "Screen Recorder Studio",
      ),
      PermissionStatusEntry(
        id = "usage_stats",
        title = "App Usage Access",
        description = "Foreground usage analytics and background App Lock foreground monitoring.",
        granted = isUsageStatsGranted(),
        moduleName = "App Manager & App Lock",
      ),
      PermissionStatusEntry(
        id = "post_notifications",
        title = "Foreground Notifications",
        description = "Displays media playback controls, active overlay/monitor controls, and timer alerts.",
        granted = isPostNotificationsGranted(),
        moduleName = "Music, Recorder, App Lock & Overlays",
      ),
      PermissionStatusEntry(
        id = "calendar",
        title = "Calendar Events (Widgets)",
        description = "Optional read-only access for Calendar widgets displaying upcoming events.",
        granted = isCalendarGranted(),
        moduleName = "Widget Engine",
      ),
      PermissionStatusEntry(
        id = "location_weather",
        title = "Coarse Location (Weather Widgets)",
        description = "Optional approximate city-level location for Weather widgets when requested.",
        granted = isCoarseLocationGranted(),
        moduleName = "Widget Engine",
      ),
    )
  }
}
