package com.example.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import android.webkit.JavascriptInterface
import com.example.MainActivity
import com.example.data.model.MetricValue
import com.example.di.NexusContainer
import com.example.util.Formatters
import com.example.widget.engine.CapabilityStatus
import com.example.widget.engine.DiagnosticSeverity
import com.example.widget.engine.MediaControlAction
import com.example.widget.engine.WidgetCapability
import com.example.widget.engine.WidgetDataState
import com.example.widget.engine.WidgetDiagnosticCenter
import com.example.widget.engine.WidgetMediaSessionHub
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * Universal native-to-HTML JavaScript bridge exposed to both Widget Studio's live WebView
 * editor and headless home-screen HTML rendering (`window.NexusWidget` and legacy `window.NexusBridge`).
 *
 * All data originates from [com.example.widget.engine.UniversalWidgetDataHub] and real Android
 * system providers—zero simulated or hardcoded values.
 */
class NexusWidgetJsBridge(
  private val context: Context,
  private val widthDp: Int = 328,
  private val heightDp: Int = 160,
  private val density: Float = context.resources.displayMetrics.density,
  private val widgetId: Int = -1,
  private val onPermissionRequested: ((WidgetCapability) -> Unit)? = null,
  private val onActionExecuted: ((String, JSONObject) -> Unit)? = null,
) {
  private val appContext = context.applicationContext
  private val container = NexusContainer.get(appContext)
  private val hub = container.widgetDataHub

  // ---- Universal Widget Bridge API (All 13 Documented Endpoints) ----

  @JavascriptInterface
  fun getDeviceInfo(): String = runBlocking {
    when (val state = hub.deviceProvider.fetch(appContext)) {
      is WidgetDataState.Available -> JSONObject().apply {
        put("status", "available")
        put("manufacturer", state.data.manufacturer)
        put("model", state.data.model)
        put("brand", state.data.brand)
        put("androidVersion", state.data.androidVersion)
        put("sdkInt", state.data.sdkInt)
        put("uptimeMs", state.data.uptimeMs)
        put("uptimeText", Formatters.formatDuration(state.data.uptimeMs))
        put("widgetId", widgetId)
      }
      else -> errorStateJson("device", state)
    }.toString()
  }

  @JavascriptInterface
  fun getBatteryInfo(): String = runBlocking {
    when (val state = hub.batteryProvider.fetch(appContext)) {
      is WidgetDataState.Available -> {
        val b = state.data
        val pct = (b.percentage as? MetricValue.Available)?.value ?: -1
        val temp = (b.temperatureCelsius as? MetricValue.Available)?.value?.toDouble()
        val voltMv = (b.voltageVolts as? MetricValue.Available)?.value?.let { (it * 1000f).toInt() }
        val curMa = (b.currentMicroAmps as? MetricValue.Available)?.value?.let { it / 1000 }
        val session = (hub.chargingProvider.fetch(appContext) as? WidgetDataState.Available)?.data?.activeSession
        JSONObject().apply {
          put("status", "available")
          put("percentage", pct)
          put("isCharging", b.isCharging)
          put("source", b.chargeSource)
          put("statusLabel", b.statusLabel)
          put("health", b.healthLabel)
          if (temp != null) put("tempC", temp) else put("tempC", JSONObject.NULL)
          if (voltMv != null) put("voltageMv", voltMv) else put("voltageMv", JSONObject.NULL)
          if (curMa != null) put("currentMa", curMa) else put("currentMa", JSONObject.NULL)
          if (session != null && b.isCharging) {
            put("chargingElapsedMs", (System.currentTimeMillis() - session.startTimeMs).coerceAtLeast(0L))
          }
        }
      }
      else -> errorStateJson("battery", state)
    }.toString()
  }

  @JavascriptInterface
  fun getStorageInfo(): String = runBlocking {
    when (val state = hub.storageProvider.fetch(appContext)) {
      is WidgetDataState.Available -> {
        val s = state.data
        val used = (s.internalTotalBytes - s.internalAvailableBytes).coerceAtLeast(0L)
        val pct = if (s.internalTotalBytes > 0L) ((used.toDouble() / s.internalTotalBytes) * 100.0).toInt() else 0
        JSONObject().apply {
          put("status", "available")
          put("availableBytes", s.internalAvailableBytes)
          put("totalBytes", s.internalTotalBytes)
          put("usedBytes", used)
          put("usagePercent", pct)
          put("availableText", Formatters.formatBytes(s.internalAvailableBytes))
          put("totalText", Formatters.formatBytes(s.internalTotalBytes))
        }
      }
      else -> errorStateJson("storage", state)
    }.toString()
  }

  @JavascriptInterface
  fun getMemoryInfo(): String = runBlocking {
    when (val state = hub.memoryProvider.fetch(appContext)) {
      is WidgetDataState.Available -> {
        val m = state.data
        JSONObject().apply {
          put("status", "available")
          put("usagePercent", m.usagePercent.toInt())
          put("availableBytes", m.availableRamBytes)
          put("totalBytes", m.totalRamBytes)
          put("availableText", Formatters.formatBytes(m.availableRamBytes))
          put("totalText", Formatters.formatBytes(m.totalRamBytes))
        }
      }
      else -> errorStateJson("memory", state)
    }.toString()
  }

  @JavascriptInterface
  fun getNetworkInfo(): String = runBlocking {
    when (val state = hub.networkProvider.fetch(appContext)) {
      is WidgetDataState.Available -> {
        val n = state.data
        JSONObject().apply {
          put("status", "available")
          put("isConnected", n.isConnected)
          put("transportType", n.transportType)
          put("isWifi", n.isWifi)
          put("isCellular", n.isCellular)
          put("isMetered", n.isMetered)
          put("hasValidatedInternet", n.hasValidatedInternet)
          put("downstreamKbps", n.downstreamKbps)
          put("upstreamKbps", n.upstreamKbps)
        }
      }
      else -> errorStateJson("network", state)
    }.toString()
  }

  @JavascriptInterface
  fun getCurrentTime(): String = runBlocking {
    when (val state = hub.timeProvider.fetch(appContext)) {
      is WidgetDataState.Available -> {
        val t = state.data
        JSONObject().apply {
          put("status", "available")
          put("epochMs", t.epochMs)
          put("time24", t.time24)
          put("time12", t.time12)
          put("time12NoAmPm", t.time12NoAmPm)
          put("amPm", t.amPm)
          put("seconds", t.seconds)
          put("dateShort", t.dateShort)
          put("dateFull", t.dateFull)
          put("dayOfWeek", t.dayOfWeek)
          put("timezoneId", t.timezoneId)
          put("timezoneDisplay", t.timezoneDisplay)
          put("is24HourFormat", t.is24HourFormat)
        }
      }
      else -> errorStateJson("time", state)
    }.toString()
  }

  @JavascriptInterface
  fun getWeather(): String = runBlocking {
    when (val state = hub.weatherProvider.fetch(appContext)) {
      is WidgetDataState.Available -> {
        val w = state.data
        JSONObject().apply {
          put("status", "available")
          put("temperatureCelsius", w.temperatureCelsius)
          put("conditionLabel", w.conditionLabel)
          put("windKmh", w.windKmh)
          if (w.humidityPercent != null) put("humidityPercent", w.humidityPercent) else put("humidityPercent", JSONObject.NULL)
          put("locationLabel", w.locationLabel)
          put("observedAtMs", w.observedAtMs)
          put("isCached", w.isCached)
        }
      }
      else -> errorStateJson("weather", state)
    }.toString()
  }

  @JavascriptInterface
  fun getMediaSession(): String = runBlocking {
    when (val state = hub.mediaProvider.fetch(appContext)) {
      is WidgetDataState.Available -> {
        val m = state.data
        JSONObject().apply {
          put("status", "available")
          put("hasActiveSession", m.hasActiveSession)
          put("listenerPermissionGranted", m.listenerPermissionGranted)
          put("title", m.title)
          put("artist", m.artist)
          put("album", m.album)
          put("sourceAppName", m.sourceAppName)
          put("sourcePackageName", m.sourcePackageName)
          put("isPlaying", m.isPlaying)
          put("playbackStateCode", m.playbackStateCode)
          put("positionMs", m.positionMs)
          put("durationMs", m.durationMs)
          put("playbackSpeed", m.playbackSpeed.toDouble())
          put("canPlayPause", m.canPlayPause)
          put("canSkipNext", m.canSkipNext)
          put("canSkipPrev", m.canSkipPrev)
          put("canSeek", m.canSeek)
          put("artworkFileUrl", m.artworkFilePath?.let { "file://$it" } ?: JSONObject.NULL)
          put("appIconFileUrl", m.appIconFilePath?.let { "file://$it" } ?: JSONObject.NULL)
        }
      }
      else -> errorStateJson("media_session", state)
    }.toString()
  }

  @JavascriptInterface
  fun getCalendarEvents(): String = runBlocking {
    when (val state = hub.calendarProvider.fetch(appContext)) {
      is WidgetDataState.Available -> {
        val arr = JSONArray()
        state.data.events.forEach { ev ->
          arr.put(
            JSONObject().apply {
              put("eventId", ev.eventId)
              put("title", ev.title)
              put("location", ev.location)
              put("startMs", ev.startMs)
              put("endMs", ev.endMs)
              put("allDay", ev.allDay)
              put("calendarName", ev.calendarName)
            }
          )
        }
        JSONObject().apply {
          put("status", "available")
          put("count", state.data.events.size)
          put("events", arr)
        }
      }
      else -> errorStateJson("calendar", state)
    }.toString()
  }

  @JavascriptInterface
  fun getSelectedPhotos(): String = runBlocking {
    when (val state = hub.photoProvider.fetch(appContext)) {
      is WidgetDataState.Available -> {
        val arr = JSONArray()
        state.data.photos.forEach { p ->
          arr.put(
            JSONObject().apply {
              put("name", p.name)
              put("uriOrPath", p.uriOrPath)
              put("isAnimatedGif", p.isAnimatedGif)
              put("exists", p.exists)
              put("sizeBytes", p.sizeBytes)
            }
          )
        }
        JSONObject().apply {
          put("status", "available")
          put("count", state.data.photos.size)
          put("photos", arr)
        }
      }
      else -> errorStateJson("photos", state)
    }.toString()
  }

  @JavascriptInterface
  fun requestPermission(capabilityId: String): String {
    val cap = WidgetCapability.entries.firstOrNull {
      it.id.equals(capabilityId.trim(), ignoreCase = true) ||
        it.name.equals(capabilityId.trim(), ignoreCase = true)
    } ?: return JSONObject().apply {
      put("ok", false)
      put("error", "Unknown capability: $capabilityId")
    }.toString()

    val status = hub.permissionCoordinator.statusFor(cap)
    if (status == CapabilityStatus.GRANTED) {
      return JSONObject().apply {
        put("ok", true)
        put("capability", cap.id)
        put("status", status.name)
        put("alreadyGranted", true)
      }.toString()
    }

    onPermissionRequested?.invoke(cap) ?: run {
      hub.permissionCoordinator.buildActionableIntent(cap)?.let { intent ->
        runCatching { appContext.startActivity(intent) }
      }
    }
    return JSONObject().apply {
      put("ok", true)
      put("capability", cap.id)
      put("status", status.name)
      put("requested", true)
    }.toString()
  }

  @JavascriptInterface
  fun openApp(packageName: String): String {
    val cleanPkg = packageName.trim()
    if (cleanPkg.isBlank() || !SAFE_PACKAGE_REGEX.matches(cleanPkg)) {
      return JSONObject().apply {
        put("ok", false)
        put("error", "Invalid package name")
      }.toString()
    }
    val launchIntent = appContext.packageManager.getLaunchIntentForPackage(cleanPkg)?.apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    } ?: return JSONObject().apply {
      put("ok", false)
      put("packageName", cleanPkg)
      put("error", "Package not installed or not launchable")
    }.toString()

    val launched = runCatching {
      appContext.startActivity(launchIntent)
      true
    }.getOrDefault(false)

    return JSONObject().apply {
      put("ok", launched)
      put("packageName", cleanPkg)
      if (!launched) put("error", "Unable to launch target package")
    }.toString()
  }

  @JavascriptInterface
  fun executeWidgetAction(actionJson: String): String {
    val obj = runCatching { JSONObject(actionJson) }.getOrElse { err ->
      WidgetDiagnosticCenter.record("custom_studio", "JS_BRIDGE", DiagnosticSeverity.WARN, "Invalid action JSON: ${err.message}", widgetId = widgetId)
      return JSONObject().apply {
        put("ok", false)
        put("error", "Invalid JSON payload")
      }.toString()
    }

    val action = obj.optString("action", "").trim()
    if (!ALLOWLISTED_ACTIONS.contains(action)) {
      WidgetDiagnosticCenter.record(
        widgetKey = "custom_studio",
        category = "JS_BRIDGE",
        severity = DiagnosticSeverity.WARN,
        message = "Blocked non-allowlisted widget action '$action'",
        widgetId = widgetId,
      )
      return JSONObject().apply {
        put("ok", false)
        put("action", action)
        put("error", "Unsupported or blocked widget action: $action")
      }.toString()
    }

    val ok = when (action) {
      "media_play_pause" -> WidgetMediaSessionHub.dispatchTransportCommand(appContext, MediaControlAction.PLAY_PAUSE)
      "media_play" -> WidgetMediaSessionHub.dispatchTransportCommand(appContext, MediaControlAction.PLAY)
      "media_pause" -> WidgetMediaSessionHub.dispatchTransportCommand(appContext, MediaControlAction.PAUSE)
      "media_next" -> WidgetMediaSessionHub.dispatchTransportCommand(appContext, MediaControlAction.NEXT)
      "media_prev" -> WidgetMediaSessionHub.dispatchTransportCommand(appContext, MediaControlAction.PREV)
      "media_fast_forward" -> WidgetMediaSessionHub.dispatchTransportCommand(appContext, MediaControlAction.FAST_FORWARD)
      "media_rewind" -> WidgetMediaSessionHub.dispatchTransportCommand(appContext, MediaControlAction.REWIND)
      "media_seek" -> {
        val pos = obj.optLong("positionMs", -1L)
        if (pos >= 0L) WidgetMediaSessionHub.dispatchTransportCommand(appContext, MediaControlAction.SEEK_TO, pos) else false
      }
      "refresh_widget" -> {
        runBlocking { NexusWidgetUpdater.refreshAll(appContext) }
        true
      }
      "open_route" -> {
        val route = obj.optString("route", "").trim()
        if (!ALLOWLISTED_ROUTES.contains(route)) false
        else {
          runCatching {
            appContext.startActivity(
              Intent(appContext, MainActivity::class.java).apply {
                putExtra("route", route)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
              }
            )
            true
          }.getOrDefault(false)
        }
      }
      "open_app" -> {
        val res = JSONObject(openApp(obj.optString("packageName", "")))
        res.optBoolean("ok", false)
      }
      "request_permission" -> {
        val res = JSONObject(requestPermission(obj.optString("capability", "")))
        res.optBoolean("ok", false)
      }
      "save_custom_data" -> {
        val k = obj.optString("key", "")
        val v = obj.optString("value", "")
        if (k.isNotBlank()) {
          hub.customDataProvider.putValue(appContext, k, v)
          true
        } else false
      }
      else -> false
    }

    if (ok) {
      onActionExecuted?.invoke(action, obj)
    }
    return JSONObject().apply {
      put("ok", ok)
      put("action", action)
    }.toString()
  }

  // ---- Extended Bridge Helpers ----

  @JavascriptInterface
  fun getWidgetId(): Int = widgetId

  @JavascriptInterface
  fun saveCustomData(key: String, value: String) {
    hub.customDataProvider.putValue(appContext, key, value)
  }

  @JavascriptInterface
  fun getCustomData(key: String): String = hub.customDataProvider.getValue(appContext, key)

  @JavascriptInterface
  fun reportJsError(message: String, source: String = "", line: Int = 0) {
    WidgetDiagnosticCenter.record(
      widgetKey = "custom_studio",
      category = "HTML_JS",
      severity = DiagnosticSeverity.ERROR,
      message = "JS error (line $line): ${message.take(220)}",
      suggestion = source.takeIf { it.isNotBlank() }?.let { "Source: ${it.take(80)}" },
      widgetId = widgetId,
    )
  }

  @JavascriptInterface
  fun getCapabilityStatusJson(): String {
    val root = JSONObject()
    WidgetCapability.entries.forEach { cap ->
      root.put(cap.id, hub.permissionCoordinator.statusFor(cap).name)
    }
    return root.toString()
  }

  // ---- Backwards-Compatible Legacy Methods ----

  @JavascriptInterface
  fun getBatteryJson(): String = getBatteryInfo()

  @JavascriptInterface
  fun getMemoryJson(): String = getMemoryInfo()

  @JavascriptInterface
  fun getStorageJson(): String = getStorageInfo()

  @JavascriptInterface
  fun getDeviceJson(): String = getDeviceInfo()

  @JavascriptInterface
  fun getMusicJson(): String = getMediaSession()

  @JavascriptInterface
  fun getClockJson(): String {
    val now = Date()
    val t = runBlocking {
      (hub.timeProvider.fetch(appContext) as? WidgetDataState.Available)?.data
    }
    return JSONObject().apply {
      put("time", t?.time24 ?: SimpleDateFormat("HH:mm", Locale.getDefault()).format(now))
      put("date", t?.dateShort ?: SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now))
      put("time24", t?.time24 ?: SimpleDateFormat("HH:mm", Locale.getDefault()).format(now))
      put("time12", t?.time12 ?: SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now))
      put("amPm", t?.amPm ?: SimpleDateFormat("a", Locale.getDefault()).format(now))
      put("seconds", t?.seconds ?: SimpleDateFormat("ss", Locale.getDefault()).format(now))
      put("timezoneId", t?.timezoneId ?: java.util.TimeZone.getDefault().id)
      put("is24HourFormat", t?.is24HourFormat ?: true)
    }.toString()
  }

  @JavascriptInterface
  fun getBatteryPercent(): Int {
    val snap = container.batteryDataSource.readBatterySnapshot()
    return (snap.percentage as? MetricValue.Available)?.value ?: -1
  }

  @JavascriptInterface
  fun getIsCharging(): Boolean = container.batteryDataSource.readBatterySnapshot().isCharging

  @JavascriptInterface
  fun getChargingSource(): String = container.batteryDataSource.readBatterySnapshot().chargeSource

  @JavascriptInterface
  fun getRamUsagePercent(): Int = runBlocking {
    container.ramManagerDataSource.readSystemMemoryDetails().usagePercent.toInt()
  }

  @JavascriptInterface
  fun getFreeRamText(): String = runBlocking {
    Formatters.formatBytes(container.ramManagerDataSource.readSystemMemoryDetails().availableRamBytes)
  }

  @JavascriptInterface
  fun getFreeStorageText(): String = runBlocking {
    Formatters.formatBytes(container.storageDataSource.readStorageOverview().internalAvailableBytes)
  }

  @JavascriptInterface
  fun getDeviceModel(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

  @JavascriptInterface
  fun getCurrentTimeText(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

  @JavascriptInterface
  fun getCurrentTime12h(): String =
    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

  @JavascriptInterface
  fun getCurrentDate(): String =
    SimpleDateFormat("EEE, MMM dd", Locale.getDefault()).format(Date())

  @JavascriptInterface
  fun getNowPlayingText(): String = runBlocking {
    val state = hub.mediaProvider.fetch(appContext)
    when (state) {
      is WidgetDataState.Available -> "${state.data.title} — ${state.data.artist}"
      is WidgetDataState.PermissionRequired -> "Notification Access required"
      else -> "No active media"
    }
  }

  @JavascriptInterface
  fun getWidgetMetaJson(): String {
    return JSONObject().apply {
      put("widgetId", widgetId)
      put("widthDp", widthDp)
      put("heightDp", heightDp)
      put("density", density.toDouble())
      put("aspectRatio", if (heightDp > 0) widthDp.toDouble() / heightDp.toDouble() else 1.0)
      put("assetsBaseUrl", "file://${File(appContext.filesDir, ASSETS_DIR).absolutePath}/")
    }.toString()
  }

  @JavascriptInterface
  fun getAssetsJson(): String {
    val dir = File(appContext.filesDir, ASSETS_DIR)
    val arr = JSONArray()
    if (dir.exists()) {
      dir.listFiles()?.sortedByDescending { it.lastModified() }?.forEach { file ->
        arr.put(
          JSONObject().apply {
            put("name", file.name)
            put("fileUrl", "file://${file.absolutePath}")
            put("relativeName", file.name)
            put("sizeBytes", file.length())
          }
        )
      }
    }
    return arr.toString()
  }

  @JavascriptInterface
  fun getAllTelemetryJson(): String = runBlocking {
    val time = (hub.timeProvider.fetch(appContext) as? WidgetDataState.Available)?.data
    val bat = (hub.batteryProvider.fetch(appContext) as? WidgetDataState.Available)?.data
    val mem = (hub.memoryProvider.fetch(appContext) as? WidgetDataState.Available)?.data
    val storage = (hub.storageProvider.fetch(appContext) as? WidgetDataState.Available)?.data
    val media = (hub.mediaProvider.fetch(appContext) as? WidgetDataState.Available)?.data
    val net = (hub.networkProvider.fetch(appContext) as? WidgetDataState.Available)?.data
    val now = Date()
    JSONObject().apply {
      put("widgetId", widgetId)
      put("batteryPercent", (bat?.percentage as? MetricValue.Available)?.value ?: -1)
      put("isCharging", bat?.isCharging ?: false)
      put("chargeSource", bat?.chargeSource ?: "Unknown")
      put("batteryTempC", (bat?.temperatureCelsius as? MetricValue.Available)?.value?.toDouble() ?: -1.0)
      put("ramUsagePercent", mem?.usagePercent?.toInt() ?: 0)
      put("freeRam", Formatters.formatBytes(mem?.availableRamBytes ?: 0L))
      put("totalRam", Formatters.formatBytes(mem?.totalRamBytes ?: 0L))
      put("freeStorage", Formatters.formatBytes(storage?.internalAvailableBytes ?: 0L))
      put("totalStorage", Formatters.formatBytes(storage?.internalTotalBytes ?: 0L))
      put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}")
      put("time24", time?.time24 ?: SimpleDateFormat("HH:mm", Locale.getDefault()).format(now))
      put("time12", time?.time12 ?: SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now))
      put("amPm", time?.amPm ?: SimpleDateFormat("a", Locale.getDefault()).format(now))
      put("seconds", time?.seconds ?: SimpleDateFormat("ss", Locale.getDefault()).format(now))
      put("is24HourFormat", time?.is24HourFormat ?: true)
      put("timezoneId", time?.timezoneId ?: java.util.TimeZone.getDefault().id)
      put("dateShort", time?.dateShort ?: SimpleDateFormat("EEE, MMM dd", Locale.getDefault()).format(now))
      put("dateFull", time?.dateFull ?: SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now))
      put("musicTitle", media?.title ?: "No active media session")
      put("musicArtist", media?.artist ?: "")
      put("musicApp", media?.sourceAppName ?: "")
      put("musicIsPlaying", media?.isPlaying ?: false)
      put("networkConnected", net?.isConnected ?: false)
      put("networkType", net?.transportType ?: "Offline")
      put("widthDp", widthDp)
      put("heightDp", heightDp)
    }.toString()
  }

  private fun errorStateJson(capabilityKey: String, state: WidgetDataState<*>): JSONObject {
    return JSONObject().apply {
      put("capability", capabilityKey)
      when (state) {
        is WidgetDataState.PermissionRequired -> {
          put("status", "permission_required")
          put("requiredCapability", state.capability.id)
          put("isSpecialSettings", state.isSpecialSettings)
          put("reason", state.reason)
        }
        is WidgetDataState.Unavailable -> {
          put("status", "unavailable")
          put("reason", state.reason)
        }
        is WidgetDataState.Error -> {
          put("status", "error")
          put("reason", state.message)
          put("recoverable", state.recoverable)
        }
        else -> put("status", "loading")
      }
    }
  }

  companion object {
    const val ASSETS_DIR = "widget_assets"
    private val SAFE_PACKAGE_REGEX = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")
    private val FONT_EXTENSIONS = setOf("ttf", "otf", "woff", "woff2")
    private val ALLOWLISTED_ACTIONS = setOf(
      "media_play_pause",
      "media_play",
      "media_pause",
      "media_next",
      "media_prev",
      "media_fast_forward",
      "media_rewind",
      "media_seek",
      "refresh_widget",
      "open_route",
      "open_app",
      "request_permission",
      "save_custom_data",
    )
    private val ALLOWLISTED_ROUTES = setOf(
      "dashboard",
      "battery",
      "music",
      "hardware",
      "app_lock",
      "studio",
      "widget_studio",
      "widgets",
      "settings",
    )

    fun isSafeWebViewUrl(url: String?): Boolean {
      if (url.isNullOrBlank()) return false
      val lower = url.trim().lowercase(Locale.US)
      return lower.startsWith("about:blank") ||
        lower.startsWith("data:") ||
        lower.startsWith("file://")
    }

    fun buildCustomFontCss(assetsDir: File?): String {
      if (assetsDir == null || !assetsDir.exists()) return ""
      val sb = StringBuilder()
      assetsDir.listFiles()?.forEach { file ->
        val ext = file.extension.lowercase(Locale.US)
        if (FONT_EXTENSIONS.contains(ext) && file.length() > 0L) {
          val family = file.nameWithoutExtension.replace(Regex("[^A-Za-z0-9_-]"), "_")
          sb.append("@font-face { font-family: '").append(family)
            .append("'; src: url('file://").append(file.absolutePath).append("'); font-display: swap; }\n")
        }
      }
      return sb.toString()
    }

    fun composeHtmlDocument(
      rawHtml: String,
      rawCss: String = "",
      rawJs: String = "",
      widthDp: Int,
      heightDp: Int,
      transparentBg: Boolean,
      assetsDir: File? = null,
      isAutoSize: Boolean = false,
    ): String {
      val trimmedHtml = rawHtml.trim()
      if (trimmedHtml.contains("<!-- nexus-runtime -->")) {
        return trimmedHtml
      }
      val bgRule = if (transparentBg) "transparent" else "#050608"
      val fontCss = buildCustomFontCss(assetsDir)
      val scaleFactor = String.format(Locale.US, "%.3f", (widthDp / 328f).coerceIn(0.55f, 2.2f))
      val viewportMeta = if (isAutoSize) {
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">"
      } else {
        "<meta name=\"viewport\" content=\"width=$widthDp, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">"
      }
      val headInject = """
        <!-- nexus-runtime -->
        $viewportMeta
        <style>
          :root {
            --nexus-width-dp: ${widthDp}px;
            --nexus-height-dp: ${heightDp}px;
            --nexus-scale: $scaleFactor;
          }
          $fontCss
          * { box-sizing: border-box; }
          html, body {
            width: 100%;
            min-height: 100%;
            margin: 0;
            padding: 0;
            background: $bgRule;
            overflow-x: hidden;
            -webkit-font-smoothing: antialiased;
          }
          svg { max-width: 100%; height: auto; }
        </style>
        ${if (rawCss.isNotBlank()) "<style>\n$rawCss\n</style>" else ""}
        <script>
          window.NexusRuntime = window.NexusRuntime || {
            dispatchUpdate: function(detail) {
              try {
                window.dispatchEvent(new CustomEvent('nexus:data', { detail: detail || {} }));
              } catch (e) {}
            }
          };
          window.addEventListener('error', function(ev) {
            try {
              if (typeof NexusWidget !== 'undefined' && NexusWidget.reportJsError) {
                NexusWidget.reportJsError(String(ev.message || 'Script error'), String(ev.filename || ''), Number(ev.lineno || 0));
              }
            } catch (ignored) {}
          });
        </script>
      """.trimIndent()

      val scriptInject = if (rawJs.isNotBlank()) "<script>\n$rawJs\n</script>" else ""
      val looksFullDoc = trimmedHtml.contains("<html", ignoreCase = true) ||
        trimmedHtml.contains("<!doctype", ignoreCase = true) ||
        trimmedHtml.contains("<body", ignoreCase = true)

      if (!looksFullDoc) {
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">$headInject</head><body>$trimmedHtml\n$scriptInject</body></html>"
      }

      var result = trimmedHtml
      result = when {
        Regex("</head>", RegexOption.IGNORE_CASE).containsMatchIn(result) ->
          result.replaceFirst(
            Regex("</head>", RegexOption.IGNORE_CASE),
            java.util.regex.Matcher.quoteReplacement("$headInject\n</head>"),
          )
        Regex("<head[^>]*>", RegexOption.IGNORE_CASE).containsMatchIn(result) -> {
          val m = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(result)!!
          result.replaceRange(m.range, "${m.value}\n$headInject")
        }
        Regex("<body[^>]*>", RegexOption.IGNORE_CASE).containsMatchIn(result) -> {
          val m = Regex("<body[^>]*>", RegexOption.IGNORE_CASE).find(result)!!
          result.replaceRange(m.range, "<head><meta charset=\"utf-8\">$headInject</head>${m.value}")
        }
        else -> "$headInject\n$result"
      }

      if (scriptInject.isNotBlank()) {
        result = when {
          Regex("</body>", RegexOption.IGNORE_CASE).containsMatchIn(result) ->
            result.replaceFirst(
              Regex("</body>", RegexOption.IGNORE_CASE),
              java.util.regex.Matcher.quoteReplacement("$scriptInject\n</body>"),
            )
          Regex("</html>", RegexOption.IGNORE_CASE).containsMatchIn(result) ->
            result.replaceFirst(
              Regex("</html>", RegexOption.IGNORE_CASE),
              java.util.regex.Matcher.quoteReplacement("$scriptInject\n</html>"),
            )
          else -> "$result\n$scriptInject"
        }
      }
      return result
    }

    fun wrapHtmlDocument(
      rawHtml: String,
      widthDp: Int,
      heightDp: Int,
      transparentBg: Boolean,
      assetsDir: File? = null,
    ): String = composeHtmlDocument(
      rawHtml = rawHtml,
      rawCss = "",
      rawJs = "",
      widthDp = widthDp,
      heightDp = heightDp,
      transparentBg = transparentBg,
      assetsDir = assetsDir,
      isAutoSize = false,
    )
  }
}
