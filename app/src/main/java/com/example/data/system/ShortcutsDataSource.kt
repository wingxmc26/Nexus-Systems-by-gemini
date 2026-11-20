package com.example.data.system

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import com.example.data.model.ControlCapabilityType
import com.example.data.model.SystemControlEntry

class ShortcutsDataSource(private val context: Context) {

  fun canWriteSettings(): Boolean = Settings.System.canWrite(context)

  fun buildWriteSettingsIntent(): Intent {
    return Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
      data = Uri.fromParts("package", context.packageName, null)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  fun readBrightnessPercentAndAuto(): Pair<Int, Boolean> {
    val cr = context.contentResolver
    val raw = runCatching { Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(128)
    val auto = runCatching {
      Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE) ==
        Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }.getOrDefault(false)
    val pct = ((raw / 255f) * 100f).toInt().coerceIn(1, 100)
    return pct to auto
  }

  fun setBrightnessPercent(percent: Int): Boolean {
    if (!canWriteSettings()) return false
    return runCatching {
      val cr = context.contentResolver
      Settings.System.putInt(
        cr,
        Settings.System.SCREEN_BRIGHTNESS_MODE,
        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
      )
      val val255 = ((percent.coerceIn(1, 100) / 100f) * 255f).toInt().coerceIn(1, 255)
      Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, val255)
      true
    }.getOrDefault(false)
  }

  fun setAutoBrightness(enabled: Boolean): Boolean {
    if (!canWriteSettings()) return false
    return runCatching {
      Settings.System.putInt(
        context.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS_MODE,
        if (enabled) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
      )
      true
    }.getOrDefault(false)
  }

  fun readScreenTimeoutMs(): Int {
    return runCatching {
      Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
    }.getOrDefault(30_000)
  }

  fun setScreenTimeoutMs(ms: Int): Boolean {
    if (!canWriteSettings()) return false
    return runCatching {
      Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, ms)
      true
    }.getOrDefault(false)
  }

  fun readAutoRotate(): Boolean {
    return runCatching {
      Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
    }.getOrDefault(false)
  }

  fun setAutoRotate(enabled: Boolean): Boolean {
    if (!canWriteSettings()) return false
    return runCatching {
      Settings.System.putInt(
        context.contentResolver,
        Settings.System.ACCELEROMETER_ROTATION,
        if (enabled) 1 else 0,
      )
      true
    }.getOrDefault(false)
  }

  fun loadControls(): List<SystemControlEntry> {
    val pm = context.packageManager
    val cr = context.contentResolver
    val hasWrite = canWriteSettings()

    fun canResolve(action: String, withPackageUri: Boolean = false): Boolean {
      val intent = Intent(action)
      if (withPackageUri) {
        intent.data = Uri.fromParts("package", context.packageName, null)
      }
      return intent.resolveActivity(pm) != null
    }

    val (brightPct, brightAuto) = readBrightnessPercentAndAuto()
    val timeoutMs = readScreenTimeoutMs()
    val timeoutText = when {
      timeoutMs >= 60_000 -> "${timeoutMs / 60_000} min"
      else -> "${timeoutMs / 1000} sec"
    }
    val rotateText = if (readAutoRotate()) "Auto-rotate On" else "Portrait Locked"

    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    val ringerText = when (am?.ringerMode) {
      AudioManager.RINGER_MODE_NORMAL -> "Normal"
      AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
      AudioManager.RINGER_MODE_SILENT -> "Silent"
      else -> "System Default"
    }

    val powerMgr = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val batterySaverActive = powerMgr?.isPowerSaveMode ?: false

    val devEnabled = runCatching {
      Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
    }.getOrDefault(false)

    val directCapability = if (hasWrite) ControlCapabilityType.DIRECT else ControlCapabilityType.PERMISSION_REQUIRED

    return listOf(
      SystemControlEntry(
        id = "brightness",
        title = "Brightness",
        currentStateText = "$brightPct% (${if (brightAuto) "Adaptive" else "Manual"})",
        description = if (hasWrite) "Adjust screen brightness and adaptive mode directly" else "Requires Modify System Settings permission for direct slider",
        action = Settings.ACTION_DISPLAY_SETTINGS,
        isAvailable = canResolve(Settings.ACTION_DISPLAY_SETTINGS),
        capabilityType = directCapability,
      ),
      SystemControlEntry(
        id = "screen_timeout",
        title = "Screen Timeout",
        currentStateText = timeoutText,
        description = if (hasWrite) "Set sleep timeout directly (15s to 10m)" else "Requires Modify System Settings permission for direct timeout",
        action = Settings.ACTION_DISPLAY_SETTINGS,
        isAvailable = canResolve(Settings.ACTION_DISPLAY_SETTINGS),
        capabilityType = directCapability,
      ),
      SystemControlEntry(
        id = "auto_rotate",
        title = "Auto-Rotate",
        currentStateText = rotateText,
        description = if (hasWrite) "Toggle screen auto-rotation directly" else "Requires Modify System Settings permission for direct toggle",
        action = Settings.ACTION_DISPLAY_SETTINGS,
        isAvailable = canResolve(Settings.ACTION_DISPLAY_SETTINGS),
        capabilityType = directCapability,
      ),
      SystemControlEntry(
        id = "sound",
        title = "Sound & Ringer Settings",
        currentStateText = ringerText,
        description = "Opens Android Sound settings for media, ring, alarm & Do Not Disturb",
        action = Settings.ACTION_SOUND_SETTINGS,
        isAvailable = canResolve(Settings.ACTION_SOUND_SETTINGS),
        capabilityType = ControlCapabilityType.SETTINGS_SHORTCUT,
      ),
      SystemControlEntry(
        id = "battery_saver",
        title = "Battery Saver",
        currentStateText = if (batterySaverActive) "Active" else "Off",
        description = "Android restricts non-system apps from toggling Battery Saver directly; opens system Battery Saver page",
        action = Settings.ACTION_BATTERY_SAVER_SETTINGS,
        isAvailable = canResolve(Settings.ACTION_BATTERY_SAVER_SETTINGS),
        capabilityType = ControlCapabilityType.SETTINGS_SHORTCUT,
      ),
      SystemControlEntry(
        id = "display",
        title = "Display & Refresh Rate",
        currentStateText = "Display Settings",
        description = "Configure smoothness / refresh rate, dark theme, and screen resolution where device supports it",
        action = Settings.ACTION_DISPLAY_SETTINGS,
        isAvailable = canResolve(Settings.ACTION_DISPLAY_SETTINGS),
        capabilityType = ControlCapabilityType.SETTINGS_SHORTCUT,
      ),
      SystemControlEntry(
        id = "app_self",
        title = "Nexus System App Settings",
        currentStateText = "Permissions & Storage",
        description = "Manage permissions, battery optimization, and storage for Nexus System",
        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        isAvailable = canResolve(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, withPackageUri = true),
        capabilityType = ControlCapabilityType.SETTINGS_SHORTCUT,
        requiresPackageUri = true,
      ),
      SystemControlEntry(
        id = "all_apps",
        title = "Application Management",
        currentStateText = "System Apps",
        description = "Manage installed apps, default handlers, and permissions",
        action = Settings.ACTION_APPLICATION_SETTINGS,
        isAvailable = canResolve(Settings.ACTION_APPLICATION_SETTINGS),
        capabilityType = ControlCapabilityType.SETTINGS_SHORTCUT,
      ),
      SystemControlEntry(
        id = "storage",
        title = "Device Storage Settings",
        currentStateText = "System Storage",
        description = "Open Android built-in storage manager",
        action = Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
        isAvailable = canResolve(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        capabilityType = ControlCapabilityType.SETTINGS_SHORTCUT,
      ),
      SystemControlEntry(
        id = "accessibility",
        title = "Accessibility Settings",
        currentStateText = "Display & Interaction",
        description = "Display size, contrast, TalkBack, and interaction controls",
        action = Settings.ACTION_ACCESSIBILITY_SETTINGS,
        isAvailable = canResolve(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        capabilityType = ControlCapabilityType.SETTINGS_SHORTCUT,
      ),
      SystemControlEntry(
        id = "developer",
        title = "Developer Options",
        currentStateText = if (devEnabled) "Enabled" else "Disabled",
        description = if (devEnabled) "Open Developer Options (USB debugging, running services)" else "Developer options are disabled in Android About Phone",
        action = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        isAvailable = devEnabled && canResolve(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        capabilityType = if (devEnabled) ControlCapabilityType.SETTINGS_SHORTCUT else ControlCapabilityType.UNSUPPORTED,
      ),
    )
  }

  fun openControl(entry: SystemControlEntry): Boolean {
    if (!entry.isAvailable) return false
    return runCatching {
      val intent = Intent(entry.action).apply {
        if (entry.requiresPackageUri) {
          data = Uri.fromParts("package", context.packageName, null)
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
      true
    }.getOrDefault(false)
  }
}
