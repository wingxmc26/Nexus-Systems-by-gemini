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
  DEVICE_OVERVIEW("device_overview", "Device & OS Identity", "Model, Android API, uptime"),
  BATTERY_CARD("battery_card", "Battery Telemetry", "Charge level, thermal & source"),
  MEMORY_STORAGE_CARD("memory_storage_card", "Memory & Storage", "Live RAM & internal storage"),
  CPU_CARD("cpu_card", "CPU Architecture", "Cores, ABI & clock telemetry"),
  QUICK_MODULES("quick_modules", "Diagnostic Modules", "Direct launch to all utilities");

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

data class CategoryBreakdown(
  val category: FileCategory,
  val fileCount: Int,
  val totalBytes: Long,
)

data class SafAnalysisState(
  val treeUriString: String? = null,
  val rootName: String? = null,
  val isScanning: Boolean = false,
  val scannedCount: Int = 0,
  val totalScannedBytes: Long = 0L,
  val categories: List<CategoryBreakdown> = emptyList(),
  val largestFiles: List<SafFileEntry> = emptyList(),
  val allEntries: List<SafFileEntry> = emptyList(),
  val errorMessage: String? = null,
)

data class NetworkInterfaceEntry(
  val name: String,
  val isUp: Boolean,
  val isLoopback: Boolean,
  val mtu: MetricValue<Int>,
  val addresses: List<String>,
)

data class NetworkDiagnosticCheck(
  val title: String,
  val detail: String,
  val passed: Boolean,
)

data class NetworkDiagnostics(
  val isConnected: Boolean,
  val transportLabel: String,
  val isMetered: Boolean,
  val isValidated: Boolean,
  val isAirplaneModeOn: Boolean,
  val wifiLinkSpeedMbps: MetricValue<Int>,
  val wifiFrequencyMhz: MetricValue<Int>,
  val wifiRssiDbm: MetricValue<Int>,
  val wifiStandard: MetricValue<String>,
  val interfaceName: MetricValue<String>,
  val localIpv4Addresses: List<String>,
  val localIpv6Addresses: List<String>,
  val dnsServers: List<String>,
  val totalRxBytes: MetricValue<Long>,
  val totalTxBytes: MetricValue<Long>,
  val mobileRxBytes: MetricValue<Long>,
  val mobileTxBytes: MetricValue<Long>,
  val appRxBytes: MetricValue<Long>,
  val appTxBytes: MetricValue<Long>,
  val interfaces: List<NetworkInterfaceEntry>,
  val checks: List<NetworkDiagnosticCheck>,
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

data class AudioDeviceSummary(
  val id: Int,
  val productName: String,
  val typeLabel: String,
  val isSink: Boolean,
  val isSource: Boolean,
  val sampleRates: List<Int>,
  val channelCounts: List<Int>,
)

data class AudioEnvironmentInfo(
  val ringerModeLabel: String,
  val mediaVolume: Int,
  val mediaMaxVolume: Int,
  val ringVolume: Int,
  val ringMaxVolume: Int,
  val alarmVolume: Int,
  val alarmMaxVolume: Int,
  val isMicrophoneMuted: Boolean,
  val devices: List<AudioDeviceSummary>,
)

data class InstalledAppEntry(
  val appName: String,
  val packageName: String,
  val versionName: String,
  val versionCode: Long,
  val targetSdk: Int,
  val isSystemApp: Boolean,
  val apkSizeBytes: MetricValue<Long>,
  val permissions: List<AppPermissionItem>,
  val canLaunch: Boolean,
  val icon: Drawable?,
)

data class AppPermissionItem(
  val name: String,
  val isGranted: Boolean,
)

data class SystemShortcut(
  val id: String,
  val title: String,
  val subtitle: String,
  val action: String,
  val isAvailable: Boolean,
  val availabilityNote: String? = null,
)
