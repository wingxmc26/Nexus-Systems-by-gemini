package com.example.data.model

import android.graphics.drawable.Drawable
import com.example.ui.theme.AccentPalette

sealed interface MetricValue<out T> {
  data class Available<T>(val value: T) : MetricValue<T>
  data class Unavailable(val reason: String = "Unavailable on device") : MetricValue<Nothing>
}

enum class DashboardModuleId(
  val id: String,
  val title: String,
  val subtitle: String,
) {
  BATTERY_CARD("battery_card", "Battery & Charging", "Level, current, temperature & session timer"),
  RAM_CARD("ram_card", "RAM Usage", "Active memory consumption & available RAM"),
  STORAGE_CARD("storage_card", "Storage Usage", "Internal storage capacity & free space"),
  DEVICE_OVERVIEW("device_overview", "Device Information", "Model, Android version, display & processor"),
  FAVORITE_SHORTCUTS("favorite_shortcuts", "Favorite Shortcuts", "Pinned launchable apps & activities"),
  QUICK_MODULES("quick_modules", "System Suite", "Organized access to all Nexus System modules");

  companion object {
    val defaultOrder: List<DashboardModuleId> = entries.toList()
    fun fromId(id: String): DashboardModuleId? = entries.firstOrNull { it.id == id }
  }
}

data class DashboardPreferences(
  val accentPalette: AccentPalette = AccentPalette.CRIMSON_RED,
  val amoledMode: Boolean = true,
  val refreshIntervalMs: Long = 2000L,
  val cardOrder: List<DashboardModuleId> = DashboardModuleId.defaultOrder,
  val hiddenModules: Set<DashboardModuleId> = emptySet(),
)

enum class WallpaperMediaType(val label: String) {
  VIDEO("Video"),
  IMAGE("Image"),
  GIF("Animated GIF"),
}

data class LiveWallpaperConfig(
  val activeWallpaperId: Long = 0L,
  val activeLocalPath: String? = null,
  val videoFileName: String? = null,
  val mediaType: WallpaperMediaType = WallpaperMediaType.VIDEO,
  val muteAudio: Boolean = true,
  val scaleCrop: Boolean = true,
  val loopPlayback: Boolean = true,
  val playbackSpeed: Float = 1.0f,
  val batterySaverPause: Boolean = true,
  val offsetXPercent: Float = 0f,
  val offsetYPercent: Float = 0f,
  val zoomScale: Float = 1.0f,
  val hasLocalVideo: Boolean = false,
  val isCurrentlySetAsWallpaper: Boolean = false,
)

data class BatteryTelemetry(
  val percentage: MetricValue<Int> = MetricValue.Unavailable(),
  val statusLabel: String = "Unknown",
  val isCharging: Boolean = false,
  val chargeSource: String = "Unknown",
  val temperatureCelsius: MetricValue<Float> = MetricValue.Unavailable(),
  val voltageVolts: MetricValue<Float> = MetricValue.Unavailable(),
  val technology: MetricValue<String> = MetricValue.Unavailable(),
  val healthLabel: String = "Unknown",
  val currentMicroAmps: MetricValue<Int> = MetricValue.Unavailable("Not exposed by hardware"),
  val chargeCounterMicroAh: MetricValue<Long> = MetricValue.Unavailable("Not exposed by hardware"),
  val energyCounterNwh: MetricValue<Long> = MetricValue.Unavailable("Not exposed by hardware"),
  val cycleCount: MetricValue<Int> = MetricValue.Unavailable("Not exposed by Android API / device"),
  val timestampMs: Long = System.currentTimeMillis(),
)

data class ActiveChargingSession(
  val startTimeMs: Long,
  val startPercentage: Int,
  val chargeSource: String,
)

data class CpuCoreFreq(
  val coreIndex: Int,
  val currentKhz: MetricValue<Long>,
  val maxKhz: MetricValue<Long>,
)

data class ThermalReading(
  val label: String,
  val temperatureCelsius: Float,
)

data class HardwareTelemetry(
  val manufacturer: String,
  val model: String,
  val deviceCode: String,
  val board: String,
  val hardware: String,
  val androidVersion: String,
  val sdkInt: Int,
  val securityPatch: MetricValue<String>,
  val buildId: String,
  val kernelVersion: MetricValue<String>,
  val cpuAbis: String,
  val cpuCoreCount: Int,
  val cpuHardwareName: MetricValue<String>,
  val cpuCoreFrequencies: List<CpuCoreFreq>,
  val ramTotalBytes: Long,
  val ramAvailableBytes: Long,
  val ramUsedBytes: Long,
  val ramThresholdBytes: Long,
  val isLowMemory: Boolean,
  val gpuVendor: MetricValue<String>,
  val gpuRenderer: MetricValue<String>,
  val gpuVersion: MetricValue<String>,
  val thermalStatus: MetricValue<String>,
  val thermalReadings: List<ThermalReading>,
  val screenResolution: String,
  val screenDensityDpi: Int,
  val refreshRateHz: Float,
  val elapsedUptimeMs: Long,
  val awakeUptimeMs: Long,
)

data class SystemMemoryDetails(
  val totalRamBytes: Long,
  val availableRamBytes: Long,
  val usedRamBytes: Long,
  val usagePercent: Float,
  val lowMemoryThresholdBytes: Long,
  val isLowMemory: Boolean,
  val nexusAppPssKb: MetricValue<Int>,
  val cachedMemoryBytes: MetricValue<Long>,
  val buffersBytes: MetricValue<Long>,
  val swapTotalBytes: MetricValue<Long>,
  val swapFreeBytes: MetricValue<Long>,
  val visibleProcessCount: Int = 0,
)

data class ManagedAppSummary(
  val appName: String,
  val packageName: String,
  val versionName: String,
  val versionCode: Long,
  val isSystemApp: Boolean,
  val isLaunchable: Boolean,
  val apkSizeBytes: MetricValue<Long>,
  val installedAtMs: Long,
  val updatedAtMs: Long,
  val targetSdk: Int,
  val requestedPermissions: List<String>,
  val foregroundUsageMs: Long?,
  val lastUsedMs: Long?,
  val icon: Drawable?,
)

enum class ShortcutShape(val id: String, val label: String) {
  ROUNDED("rounded", "Rounded"),
  CIRCLE("circle", "Circle"),
  SQUIRCLE("squircle", "Squircle"),
  SQUARE("square", "Square");

  companion object {
    fun fromId(id: String?): ShortcutShape = entries.firstOrNull { it.id == id } ?: ROUNDED
  }
}

data class ExportedActivityInfo(
  val className: String,
  val simpleName: String,
  val label: String,
  val permissionRequired: String?,
)

data class LaunchablePackageInfo(
  val appName: String,
  val packageName: String,
  val versionName: String = "N/A",
  val targetSdk: Int = 0,
  val isSystemApp: Boolean,
  val hasLauncherIntent: Boolean,
  val exportedActivities: List<ExportedActivityInfo>,
  val totalActivityCount: Int,
  val icon: Drawable?,
)

data class StorageOverview(
  val internalTotalBytes: Long,
  val internalAvailableBytes: Long,
  val internalUsedBytes: Long,
  val externalLabel: String?,
  val externalTotalBytes: MetricValue<Long>,
  val externalAvailableBytes: MetricValue<Long>,
)

enum class FileCategory(val label: String) {
  IMAGES("Images"),
  VIDEO("Video"),
  AUDIO("Audio"),
  DOCUMENTS("Documents"),
  ARCHIVES("Archives & APKs"),
  OTHER("Other"),
}

data class SafFileEntry(
  val uriString: String,
  val name: String,
  val sizeBytes: Long,
  val mimeType: String,
  val isDirectory: Boolean,
  val lastModifiedMs: Long,
  val category: FileCategory,
  val relativePath: String,
)

data class DirectFileEntry(
  val path: String,
  val name: String,
  val sizeBytes: Long,
  val isDirectory: Boolean,
  val lastModifiedMs: Long,
  val category: FileCategory,
  val isRestricted: Boolean = false,
  val childCount: Int = 0,
)

data class CategoryBreakdown(
  val category: FileCategory,
  val fileCount: Int,
  val totalBytes: Long,
)

data class DuplicateFileGroup(
  val sha256: String,
  val sizeBytes: Long,
  val files: List<SafFileEntry>,
)

data class SafAnalysisState(
  val treeUriString: String? = null,
  val rootName: String? = null,
  val isScanning: Boolean = false,
  val scannedCount: Int = 0,
  val totalScannedBytes: Long = 0L,
  val categories: List<CategoryBreakdown> = emptyList(),
  val largestFiles: List<SafFileEntry> = emptyList(),
  val duplicateGroups: List<DuplicateFileGroup> = emptyList(),
  val emptyFolders: List<SafFileEntry> = emptyList(),
  val allEntries: List<SafFileEntry> = emptyList(),
  val errorMessage: String? = null,
)

enum class CoreSensorType(
  val androidType: Int,
  val displayName: String,
  val unit: String,
  val axisLabels: List<String>,
) {
  ACCELEROMETER(android.hardware.Sensor.TYPE_ACCELEROMETER, "Accelerometer", "m/s²", listOf("X", "Y", "Z")),
  GYROSCOPE(android.hardware.Sensor.TYPE_GYROSCOPE, "Gyroscope", "rad/s", listOf("X", "Y", "Z")),
  MAGNETOMETER(android.hardware.Sensor.TYPE_MAGNETIC_FIELD, "Magnetometer", "µT", listOf("X", "Y", "Z")),
  PROXIMITY(android.hardware.Sensor.TYPE_PROXIMITY, "Proximity Sensor", "cm", listOf("Dist")),
  LIGHT(android.hardware.Sensor.TYPE_LIGHT, "Light Sensor", "lx", listOf("Illuminance")),
  PRESSURE(android.hardware.Sensor.TYPE_PRESSURE, "Barometer / Pressure", "hPa", listOf("Pressure")),
  ROTATION_VECTOR(android.hardware.Sensor.TYPE_ROTATION_VECTOR, "Rotation Vector", "vec", listOf("X", "Y", "Z", "Cos")),
}

data class SensorAvailability(
  val coreType: CoreSensorType,
  val isAvailable: Boolean,
  val sensorName: String?,
  val vendor: String?,
  val maxRange: Float?,
  val resolution: Float?,
  val powerMa: Float?,
)

data class LiveSensorReading(
  val coreType: CoreSensorType,
  val values: List<Float>,
  val accuracy: Int,
  val history: List<Float>,
)

data class DisplaySpecs(
  val physicalWidthPx: Int,
  val physicalHeightPx: Int,
  val usableWidthPx: Int,
  val usableHeightPx: Int,
  val densityDpi: Int,
  val densityScale: Float,
  val densityBucket: String,
  val currentRefreshRateHz: Float,
  val supportedRefreshRatesHz: List<Float>,
  val hdrTypes: List<String>,
  val isWideColorGamut: Boolean,
)

enum class ControlCapabilityType(val label: String) {
  DIRECT("Direct Control"),
  PERMISSION_REQUIRED("Permission Needed"),
  SETTINGS_SHORTCUT("System Settings"),
  UNSUPPORTED("Not Supported"),
}

data class SystemControlEntry(
  val id: String,
  val title: String,
  val currentStateText: String,
  val description: String,
  val action: String,
  val isAvailable: Boolean,
  val capabilityType: ControlCapabilityType = ControlCapabilityType.SETTINGS_SHORTCUT,
  val requiresPackageUri: Boolean = false,
)

// Media Player Models
data class LocalAudioTrack(
  val id: Long,
  val uriString: String,
  val title: String,
  val artist: String,
  val album: String,
  val albumId: Long,
  val durationMs: Long,
  val folderName: String,
  val filePath: String,
  val mimeType: String,
  val dateAddedSec: Long,
)

data class LocalVideoItem(
  val id: Long,
  val uriString: String,
  val title: String,
  val folderName: String,
  val durationMs: Long,
  val width: Int,
  val height: Int,
  val sizeBytes: Long,
  val mimeType: String,
  val dateAddedSec: Long,
  val isHdrLikely: Boolean = false,
)

enum class VideoAspectMode(val label: String) {
  FIT("Fit"),
  FILL("Fill / Crop"),
  RATIO_16_9("16:9"),
  RATIO_4_3("4:3"),
}

enum class VideoDecoderMode(val label: String) {
  AUTO_HW("Hardware Priority"),
  PREFER_SW("Prefer Software Fallback"),
}

// Widget Studio Models
enum class VisualElementKind(val label: String) {
  TEXT("Custom Label"),
  CLOCK("Live Clock"),
  DATE("Date"),
  BATTERY("Battery Level"),
  CHARGING("Charging Status"),
  RAM("RAM Usage"),
  STORAGE("Storage Free"),
  DEVICE("Device Model"),
  MUSIC("Now Playing"),
  ICON("System Icon"),
  IMAGE("Custom Image"),
}

data class VisualWidgetElement(
  val id: String,
  val kind: VisualElementKind,
  val label: String = "",
  val xFraction: Float = 0.08f,
  val yFraction: Float = 0.15f,
  val widthFraction: Float = 0.84f,
  val heightFraction: Float = 0.22f,
  val fontSizeSp: Float = 16f,
  val fontWeightBold: Boolean = true,
  val textColorArgb: Long = 0xFFFFFFFFL,
  val alignment: Int = 0, // 0=Left, 1=Center, 2=Right
  val paddingDp: Int = 4,
  val backgroundColorArgb: Long = 0x00000000L,
  val cornerRadiusDp: Int = 8,
  val visible: Boolean = true,
  val layerOrder: Int = 0,
  val imageUriOrPath: String? = null,
)

data class VisualWidgetSpec(
  val backgroundColorArgb: Long = 0xFF0A0B0EL,
  val gradientEndColorArgb: Long? = null,
  val isTransparent: Boolean = false,
  val htmlTransparent: Boolean = true,
  val isGlassStyle: Boolean = true,
  val borderColorArgb: Long = 0x4DE11D48L,
  val borderWidthDp: Float = 1.5f,
  val cornerRadiusDp: Int = 20,
  val blurIntensity: Float = 0f,
  val sizePresetName: String = "MEDIUM",
  val targetWidthDp: Int = 328,
  val targetHeightDp: Int = 160,
  val containerPaddingDp: Int = 12,
  val refreshIntervalMinutes: Int = 15,
  val elements: List<VisualWidgetElement> = emptyList(),
)

data class BuiltInWidgetStyleConfig(
  val backgroundColorArgb: Long = 0xFF050608L,
  val opacity: Float = 1.0f,
  val accentColorArgb: Long = 0xFFE11D48L,
  val textColorArgb: Long = 0xFFFFFFFFL,
  val secondaryTextColorArgb: Long = 0xFF9CA3AFL,
  val borderColorArgb: Long = 0x26FFFFFFL,
  val fontSizeScale: Float = 1.0f,
  val fontWeightBold: Boolean = true,
  val paddingDp: Int = 14,
  val alignment: Int = 0, // 0=Left, 1=Center, 2=Right
  val cornerRadiusDp: Int = 18,
  val showSecondaryDetails: Boolean = true,
  val isCustomized: Boolean = false,
)

// Depth Clock Models
enum class DepthClockMode(val label: String) {
  DIGITAL("Digital"),
  ANALOG("Analog"),
  DUAL("Layered Dual"),
}

enum class ClockFontChoice(val id: String, val label: String) {
  INTER_BOLD("inter_bold", "Inter Bold"),
  INTER_LIGHT("inter_light", "Inter Clean"),
  SERIF("serif", "Editorial Serif"),
  CONDENSED("condensed", "Condensed"),
}

data class DepthClockConfig(
  val mode: DepthClockMode = DepthClockMode.DIGITAL,
  val fontChoice: ClockFontChoice = ClockFontChoice.INTER_BOLD,
  val showSeconds: Boolean = false,
  val showDate: Boolean = true,
  val use24Hour: Boolean = true,
  val clockColorArgb: Long = 0xFFFFFFFFL,
  val accentColorArgb: Long = 0xFFE11D48L,
  val backgroundColorArgb: Long = 0xFF08090CL,
  val backgroundOpacity: Float = 0.88f,
  val glassBlurOverlay: Boolean = true,
  val backgroundImagePath: String? = null,
  val foregroundMaskImagePath: String? = null,
  val depthLayerStrength: Float = 0.45f,
  val clockScale: Float = 1.0f,
  val offsetYFraction: Float = 0.0f,
)

// Audio Effects / Sound Booster
data class AudioEffectCapabilities(
  val hasEqualizer: Boolean,
  val bandCount: Int,
  val bandCenterHz: List<Int>,
  val minBandLevelMb: Int,
  val maxBandLevelMb: Int,
  val hasBassBoost: Boolean,
  val hasVirtualizer: Boolean,
  val hasLoudnessEnhancer: Boolean,
)

data class SoundBoosterState(
  val enabled: Boolean = false,
  val activePresetName: String = "Normal",
  val tenBandOffsetsMb: List<Int> = List(10) { 0 },
  val bassStrength: Int = 0, // 0..1000
  val trebleBoostMb: Int = 0, // -600..600
  val virtualizerStrength: Int = 0, // 0..1000
  val preampGainMb: Int = 0, // 0..800 safe clamp
  val audioSessionId: Int = 0,
  val capabilities: AudioEffectCapabilities? = null,
  val statusMessage: String = "Attach playback to apply real hardware audio effects.",
)

// Screen Recorder Models
data class ScreenRecorderConfig(
  val resolutionLabel: String = "1080p",
  val fps: Int = 30,
  val bitrateMbps: Int = 8,
  val recordMicrophone: Boolean = false,
  val captureInternalAudioAttempt: Boolean = false,
)

data class RecordedMediaItem(
  val id: String,
  val name: String,
  val pathOrUri: String,
  val sizeBytes: Long,
  val createdAtMs: Long,
  val isScreenshot: Boolean,
  val durationMs: Long = 0L,
)
