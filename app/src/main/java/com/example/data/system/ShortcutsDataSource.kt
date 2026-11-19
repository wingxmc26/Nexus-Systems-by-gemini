package com.example.data.system

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.example.data.model.SystemShortcut

class ShortcutsDataSource(private val context: Context) {

  fun loadShortcuts(): List<SystemShortcut> {
    val pm = context.packageManager

    fun canResolve(action: String): Boolean {
      val intent = Intent(action)
      return intent.resolveActivity(pm) != null
    }

    val devEnabled = runCatching {
      Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
    }.getOrDefault(false)

    val batteryAction = if (canResolve(Intent.ACTION_POWER_USAGE_SUMMARY)) {
      Intent.ACTION_POWER_USAGE_SUMMARY
    } else {
      Settings.ACTION_BATTERY_SAVER_SETTINGS
    }

    return listOf(
      SystemShortcut(
        id = "display",
        title = "Display Settings",
        subtitle = "Brightness, adaptive refresh rate & screen timeout",
        action = Settings.ACTION_DISPLAY_SETTINGS,
        isAvailable = canResolve(Settings.ACTION_DISPLAY_SETTINGS),
      ),
      SystemShortcut(
        id = "sound",
        title = "Sound & Vibration",
        subtitle = "Media, call, alarm volumes & Do Not Disturb",
        action = Settings.ACTION_SOUND_SETTINGS,
        isAvailable = canResolve(Settings.ACTION_SOUND_SETTINGS),
      ),
      SystemShortcut(
        id = "battery",
        title = "Battery Settings",
        subtitle = "Power usage breakdown & battery saver preferences",
        action = batteryAction,
        isAvailable = canResolve(batteryAction),
      ),
      SystemShortcut(
        id = "wifi",
        title = "Wi-Fi Settings",
        subtitle = "Wireless networks, saved SSIDs & Wi-Fi preferences",
        action = Settings.ACTION_WIFI_SETTINGS,
        isAvailable = canResolve(Settings.ACTION_WIFI_SETTINGS),
      ),
      SystemShortcut(
        id = "apps",
        title = "Application Settings",
        subtitle = "Installed apps, defaults & permission controls",
        action = Settings.ACTION_APPLICATION_SETTINGS,
        isAvailable = canResolve(Settings.ACTION_APPLICATION_SETTINGS),
      ),
      SystemShortcut(
        id = "developer",
        title = "Developer Options",
        subtitle = "USB debugging, animation scale & GPU profiling",
        action = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        isAvailable = devEnabled && canResolve(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        availabilityNote = if (!devEnabled) "Developer options disabled on this device" else null,
      ),
      SystemShortcut(
        id = "accessibility",
        title = "Accessibility Settings",
        subtitle = "TalkBack, display size, contrast & interaction tools",
        action = Settings.ACTION_ACCESSIBILITY_SETTINGS,
        isAvailable = canResolve(Settings.ACTION_ACCESSIBILITY_SETTINGS),
      ),
      SystemShortcut(
        id = "storage",
        title = "Storage Settings",
        subtitle = "System storage manager & cache overview",
        action = Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
        isAvailable = canResolve(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
      ),
      SystemShortcut(
        id = "device_info",
        title = "About Phone",
        subtitle = "Hardware regulatory info, legal & build details",
        action = Settings.ACTION_DEVICE_INFO_SETTINGS,
        isAvailable = canResolve(Settings.ACTION_DEVICE_INFO_SETTINGS),
      ),
    )
  }

  fun openShortcut(shortcut: SystemShortcut): Boolean {
    if (!shortcut.isAvailable) return false
    return runCatching {
      val intent = Intent(shortcut.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(intent)
      true
    }.getOrDefault(false)
  }
}
