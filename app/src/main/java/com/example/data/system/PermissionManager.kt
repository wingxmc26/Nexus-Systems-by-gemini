package com.example.data.system

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
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

  fun loadAuditSnapshot(): List<PermissionStatusEntry> {
    return listOf(
      PermissionStatusEntry(
        id = "all_files",
        title = "All Files Access",
        description = "Allows full browsing, archive extraction, copy, move, and file management outside app storage (Android still protects /Android/data and /Android/obb).",
        granted = isAllFilesAccessGranted(),
        moduleName = "Storage Manager",
      ),
      PermissionStatusEntry(
        id = "notification_access",
        title = "Notification Listener Access",
        description = "Records incoming notification history locally while enabled. Never reads earlier notifications or sends content off-device.",
        granted = isNotificationAccessEnabled(),
        moduleName = "Notification Studio",
      ),
      PermissionStatusEntry(
        id = "write_settings",
        title = "Modify System Settings",
        description = "Allows direct in-app controls for screen brightness, sleep timeout, and auto-rotate.",
        granted = isWriteSettingsGranted(),
        moduleName = "Quick System Controls",
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
        description = "Optional foreground screen-time and last-used stats inside Advanced App Manager.",
        granted = isUsageStatsGranted(),
        moduleName = "Advanced App Manager",
      ),
      PermissionStatusEntry(
        id = "post_notifications",
        title = "Foreground Notifications",
        description = "Displays media playback controls and active screen-recording status notifications.",
        granted = isPostNotificationsGranted(),
        moduleName = "Music Player & Recorder",
      ),
    )
  }
}
