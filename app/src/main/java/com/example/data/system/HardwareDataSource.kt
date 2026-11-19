package com.example.data.system

import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import com.example.data.model.CpuCoreFreq
import com.example.data.model.HardwareTelemetry
import com.example.data.model.MetricValue
import com.example.data.model.ThermalReading
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class HardwareDataSource(private val context: Context) {

  private data class GpuInfo(
    val vendor: MetricValue<String>,
    val renderer: MetricValue<String>,
    val version: MetricValue<String>,
  )

  @Volatile private var cachedGpuInfo: GpuInfo? = null

  suspend fun readHardwareTelemetry(): HardwareTelemetry = withContext(Dispatchers.IO) {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    am?.getMemoryInfo(memInfo)

    val totalRam = memInfo.totalMem.coerceAtLeast(0L)
    val availRam = memInfo.availMem.coerceAtLeast(0L)
    val usedRam = (totalRam - availRam).coerceAtLeast(0L)

    val cpuCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    val cpuHardware = readCpuHardwareName()
    val coreFreqs = readCpuFrequencies(cpuCount)
    val gpu = cachedGpuInfo ?: probeGpuInfo().also { cachedGpuInfo = it }
    val thermalStatus = readThermalStatus()
    val thermalReadings = readThermalReadings()
    val kernel = readKernelVersion()
    val displaySpecs = readCompactDisplayInfo()

    val secPatch = if (Build.VERSION.SECURITY_PATCH.isNotBlank()) {
      MetricValue.Available(Build.VERSION.SECURITY_PATCH)
    } else {
      MetricValue.Unavailable("Not reported")
    }

    HardwareTelemetry(
      manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
      model = Build.MODEL,
      deviceCode = Build.DEVICE,
      board = Build.BOARD,
      hardware = Build.HARDWARE,
      androidVersion = Build.VERSION.RELEASE ?: "Unknown",
      sdkInt = Build.VERSION.SDK_INT,
      securityPatch = secPatch,
      buildId = Build.DISPLAY,
      kernelVersion = kernel,
      cpuAbis = Build.SUPPORTED_ABIS.joinToString(", ").ifBlank { "Unknown" },
      cpuCoreCount = cpuCount,
      cpuHardwareName = cpuHardware,
      cpuCoreFrequencies = coreFreqs,
      ramTotalBytes = totalRam,
      ramAvailableBytes = availRam,
      ramUsedBytes = usedRam,
      ramThresholdBytes = memInfo.threshold.coerceAtLeast(0L),
      isLowMemory = memInfo.lowMemory,
      gpuVendor = gpu.vendor,
      gpuRenderer = gpu.renderer,
      gpuVersion = gpu.version,
      thermalStatus = thermalStatus,
      thermalReadings = thermalReadings,
      screenResolution = displaySpecs.first,
      screenDensityDpi = displaySpecs.second,
      refreshRateHz = displaySpecs.third,
      elapsedUptimeMs = SystemClock.elapsedRealtime(),
      awakeUptimeMs = SystemClock.uptimeMillis(),
    )
  }

  private fun readCpuHardwareName(): MetricValue<String> {
    return runCatching {
      val file = File("/proc/cpuinfo")
      if (!file.canRead()) return@runCatching MetricValue.Unavailable("Restricted by OS")
      val lines = file.readLines()
      val hardware = lines.firstOrNull { it.startsWith("Hardware", ignoreCase = true) }
        ?.substringAfter(":")?.trim()
      val modelName = lines.firstOrNull { it.startsWith("model name", ignoreCase = true) }
        ?.substringAfter(":")?.trim()
      val processor = lines.firstOrNull { it.startsWith("Processor", ignoreCase = true) }
        ?.substringAfter(":")?.trim()
      val resolved = listOfNotNull(hardware, modelName, processor).firstOrNull { it.isNotBlank() }
      if (resolved != null) MetricValue.Available(resolved)
      else MetricValue.Available("${Build.HARDWARE} (${Build.BOARD})")
    }.getOrElse {
      MetricValue.Unavailable("Restricted by SELinux")
    }
  }

  private fun readCpuFrequencies(coreCount: Int): List<CpuCoreFreq> {
    return (0 until coreCount).map { idx ->
      val cur = readLongSysFile("/sys/devices/system/cpu/cpu$idx/cpufreq/scaling_cur_freq")
      val max = readLongSysFile("/sys/devices/system/cpu/cpu$idx/cpufreq/cpuinfo_max_freq")
      CpuCoreFreq(
        coreIndex = idx,
        currentKhz = cur,
        maxKhz = max,
      )
    }
  }

  private fun readLongSysFile(path: String): MetricValue<Long> {
    return runCatching {
      val f = File(path)
      if (!f.exists() || !f.canRead()) {
        return@runCatching MetricValue.Unavailable("Restricted by OS")
      }
      val parsed = f.readText().trim().toLongOrNull()
      if (parsed != null && parsed > 0L) MetricValue.Available(parsed)
      else MetricValue.Unavailable("Unavailable")
    }.getOrElse {
      MetricValue.Unavailable("Restricted by SELinux")
    }
  }

  private fun readKernelVersion(): MetricValue<String> {
    val procVersion = runCatching {
      val f = File("/proc/version")
      if (f.canRead()) f.readText().trim() else null
    }.getOrNull()

    val propVersion = System.getProperty("os.version")?.trim()
    val combined = when {
      !procVersion.isNullOrBlank() -> procVersion.lineSequence().first().take(120)
      !propVersion.isNullOrBlank() -> propVersion
      else -> null
    }
    return if (combined != null) MetricValue.Available(combined)
    else MetricValue.Unavailable("Kernel version unavailable")
  }

  private fun readThermalStatus(): MetricValue<String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      return MetricValue.Unavailable("Thermal API requires Android 10+")
    }
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
      ?: return MetricValue.Unavailable("PowerManager unavailable")
    val label = when (pm.currentThermalStatus) {
      PowerManager.THERMAL_STATUS_NONE -> "Nominal (None)"
      PowerManager.THERMAL_STATUS_LIGHT -> "Light Throttling"
      PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Throttling"
      PowerManager.THERMAL_STATUS_SEVERE -> "Severe Throttling"
      PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
      PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
      PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown Imminent"
      else -> "Unknown"
    }
    return MetricValue.Available(label)
  }

  private suspend fun readThermalReadings(): List<ThermalReading> {
    val results = mutableListOf<ThermalReading>()

    // 1. Read accessible sysfs thermal zones if OS allows
    runCatching {
      val thermalDir = File("/sys/class/thermal")
      if (thermalDir.exists() && thermalDir.canRead()) {
        thermalDir.listFiles { file -> file.name.startsWith("thermal_zone") }
          ?.sortedBy { it.name }
          ?.take(12)
          ?.forEach { zone ->
            val typeFile = File(zone, "type")
            val tempFile = File(zone, "temp")
            if (tempFile.canRead()) {
              val rawTemp = tempFile.readText().trim().toFloatOrNull()
              if (rawTemp != null) {
                val celsius = if (rawTemp > 1000f || rawTemp < -1000f) rawTemp / 1000f else rawTemp
                if (celsius in -40f..150f) {
                  val label = if (typeFile.canRead()) typeFile.readText().trim() else zone.name
                  results.add(ThermalReading(label = label, temperatureCelsius = celsius))
                }
              }
            }
          }
      }
    }

    // 2. Check Sensor.TYPE_AMBIENT_TEMPERATURE if present
    val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    val ambient = sm?.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
    if (ambient != null && sm != null) {
      val reading = withTimeoutOrNull(250L) {
        suspendCancellableCoroutine<Float?> { cont ->
          val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
              val value = event?.values?.firstOrNull()
              sm.unregisterListener(this)
              if (cont.isActive) cont.resume(value)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
          }
          sm.registerListener(listener, ambient, SensorManager.SENSOR_DELAY_NORMAL)
          cont.invokeOnCancellation { sm.unregisterListener(listener) }
        }
      }
      if (reading != null && reading in -60f..120f) {
        results.add(ThermalReading("Ambient Sensor", reading))
      }
    }

    return results
  }

  private fun probeGpuInfo(): GpuInfo {
    return runCatching {
      val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
      if (display == EGL14.EGL_NO_DISPLAY) {
        return GpuInfo(
          MetricValue.Unavailable("EGL unavailable"),
          MetricValue.Unavailable("EGL unavailable"),
          MetricValue.Unavailable("EGL unavailable"),
        )
      }
      val version = IntArray(2)
      if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
        return GpuInfo(
          MetricValue.Unavailable("EGL init unavailable"),
          MetricValue.Unavailable("EGL init unavailable"),
          MetricValue.Unavailable("EGL init unavailable"),
        )
      }
      try {
        val attribList = intArrayOf(
          EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
          EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
          EGL14.EGL_RED_SIZE, 8,
          EGL14.EGL_GREEN_SIZE, 8,
          EGL14.EGL_BLUE_SIZE, 8,
          EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfigs, 0) ||
          numConfigs[0] <= 0 || configs[0] == null
        ) {
          return GpuInfo(
            MetricValue.Unavailable("No EGL config"),
            MetricValue.Unavailable("No EGL config"),
            MetricValue.Unavailable("No EGL config"),
          )
        }
        val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        val surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfaceAttribs, 0)
        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        val eglContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        try {
          if (surface != EGL14.EGL_NO_SURFACE && eglContext != EGL14.EGL_NO_CONTEXT &&
            EGL14.eglMakeCurrent(display, surface, surface, eglContext)
          ) {
            val vendor = GLES20.glGetString(GLES20.GL_VENDOR)?.takeIf { it.isNotBlank() }
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER)?.takeIf { it.isNotBlank() }
            val glVersion = GLES20.glGetString(GLES20.GL_VERSION)?.takeIf { it.isNotBlank() }
            GpuInfo(
              vendor = vendor?.let { MetricValue.Available(it) } ?: MetricValue.Unavailable("Unavailable"),
              renderer = renderer?.let { MetricValue.Available(it) } ?: MetricValue.Unavailable("Unavailable"),
              version = glVersion?.let { MetricValue.Available(it) } ?: MetricValue.Unavailable("Unavailable"),
            )
          } else {
            GpuInfo(
              MetricValue.Unavailable("Context restricted"),
              MetricValue.Unavailable("Context restricted"),
              MetricValue.Unavailable("Context restricted"),
            )
          }
        } finally {
          EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
          if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
          if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
        }
      } finally {
        EGL14.eglTerminate(display)
      }
    }.getOrElse {
      GpuInfo(
        MetricValue.Unavailable("Unavailable in current environment"),
        MetricValue.Unavailable("Unavailable in current environment"),
        MetricValue.Unavailable("Unavailable in current environment"),
      )
    }
  }

  @Suppress("DEPRECATION")
  private fun readCompactDisplayInfo(): Triple<String, Int, Float> {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    val display: Display? = dm?.getDisplay(Display.DEFAULT_DISPLAY) ?: wm?.defaultDisplay
    val refreshRate = display?.refreshRate ?: 60f
    val dpi = context.resources.displayMetrics.densityDpi

    val resolution = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
      val bounds = wm.maximumWindowMetrics.bounds
      "${bounds.width()} × ${bounds.height()} px"
    } else if (display != null) {
      val metrics = DisplayMetrics()
      display.getRealMetrics(metrics)
      "${metrics.widthPixels} × ${metrics.heightPixels} px"
    } else {
      val m = context.resources.displayMetrics
      "${m.widthPixels} × ${m.heightPixels} px"
    }
    return Triple(resolution, dpi, refreshRate)
  }
}
