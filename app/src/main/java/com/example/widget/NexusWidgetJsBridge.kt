package com.example.widget

import android.content.Context
import android.os.Build
import android.webkit.JavascriptInterface
import com.example.data.model.MetricValue
import com.example.di.NexusContainer
import com.example.util.Formatters
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * Unified offline JS interface exposed to both the live Widget Studio WebView editor and
 * headless home-screen HTML rendering (`window.NexusWidget` and legacy `window.NexusBridge`).
 */
class NexusWidgetJsBridge(
  private val context: Context,
  private val widthDp: Int = 328,
  private val heightDp: Int = 160,
  private val density: Float = context.resources.displayMetrics.density,
) {
  private val container = NexusContainer.get(context.applicationContext)

  @JavascriptInterface
  fun getBatteryJson(): String {
    val b = container.batteryDataSource.readBatterySnapshot()
    val pct = (b.percentage as? MetricValue.Available)?.value ?: -1
    val temp = (b.temperatureCelsius as? MetricValue.Available)?.value ?: 0f
    return JSONObject().apply {
      put("percentage", pct)
      put("isCharging", b.isCharging)
      put("source", b.chargeSource)
      put("tempC", temp.toDouble())
    }.toString()
  }

  @JavascriptInterface
  fun getMemoryJson(): String = runBlocking {
    val m = container.ramManagerDataSource.readSystemMemoryDetails()
    JSONObject().apply {
      put("usagePercent", m.usagePercent.toInt())
      put("availableText", Formatters.formatBytes(m.availableRamBytes))
      put("totalText", Formatters.formatBytes(m.totalRamBytes))
    }.toString()
  }

  @JavascriptInterface
  fun getStorageJson(): String = runBlocking {
    val s = container.storageDataSource.readStorageOverview()
    JSONObject().apply {
      put("availableText", Formatters.formatBytes(s.internalAvailableBytes))
      put("totalText", Formatters.formatBytes(s.internalTotalBytes))
    }.toString()
  }

  @JavascriptInterface
  fun getDeviceJson(): String = JSONObject().apply {
    put("manufacturer", Build.MANUFACTURER)
    put("model", Build.MODEL)
    put("androidVersion", Build.VERSION.RELEASE)
  }.toString()

  @JavascriptInterface
  fun getMusicJson(): String = runBlocking {
    val m = container.preferencesRepository.getLastMusicPlaybackSnapshot()
    JSONObject().apply {
      put("title", m.first)
      put("artist", m.second)
      put("isPlaying", m.third)
    }.toString()
  }

  @JavascriptInterface
  fun getClockJson(): String = JSONObject().apply {
    val now = Date()
    put("time", SimpleDateFormat("HH:mm", Locale.getDefault()).format(now))
    put("date", SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now))
  }.toString()

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
  fun getCurrentTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

  @JavascriptInterface
  fun getCurrentTime12h(): String =
    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

  @JavascriptInterface
  fun getCurrentDate(): String =
    SimpleDateFormat("EEE, MMM dd", Locale.getDefault()).format(Date())

  @JavascriptInterface
  fun getNowPlayingText(): String {
    val pair = runBlocking { container.preferencesRepository.getLastMusicPlaybackSnapshot() }
    return "${pair.first} — ${pair.second}"
  }

  @JavascriptInterface
  fun getWidgetMetaJson(): String {
    return JSONObject().apply {
      put("widthDp", widthDp)
      put("heightDp", heightDp)
      put("density", density.toDouble())
      put("aspectRatio", if (heightDp > 0) widthDp.toDouble() / heightDp.toDouble() else 1.0)
      put("assetsBaseUrl", "file://${File(context.filesDir, ASSETS_DIR).absolutePath}/")
    }.toString()
  }

  @JavascriptInterface
  fun getAssetsJson(): String {
    val dir = File(context.filesDir, ASSETS_DIR)
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
    val bat = container.batteryDataSource.readBatterySnapshot()
    val mem = container.ramManagerDataSource.readSystemMemoryDetails()
    val storage = container.storageDataSource.readStorageOverview()
    val music = container.preferencesRepository.getLastMusicPlaybackSnapshot()
    val now = Date()
    JSONObject().apply {
      put("batteryPercent", (bat.percentage as? MetricValue.Available)?.value ?: -1)
      put("isCharging", bat.isCharging)
      put("chargeSource", bat.chargeSource)
      put("batteryTempC", (bat.temperatureCelsius as? MetricValue.Available)?.value?.toDouble() ?: -1.0)
      put("ramUsagePercent", mem.usagePercent.toInt())
      put("freeRam", Formatters.formatBytes(mem.availableRamBytes))
      put("totalRam", Formatters.formatBytes(mem.totalRamBytes))
      put("freeStorage", Formatters.formatBytes(storage.internalAvailableBytes))
      put("totalStorage", Formatters.formatBytes(storage.internalTotalBytes))
      put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}")
      put("time24", SimpleDateFormat("HH:mm", Locale.getDefault()).format(now))
      put("time12", SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now))
      put("seconds", SimpleDateFormat("ss", Locale.getDefault()).format(now))
      put("dateShort", SimpleDateFormat("EEE, MMM dd", Locale.getDefault()).format(now))
      put("dateFull", SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now))
      put("musicTitle", music.first)
      put("musicArtist", music.second)
      put("widthDp", widthDp)
      put("heightDp", heightDp)
    }.toString()
  }

  companion object {
    const val ASSETS_DIR = "widget_assets"

    fun wrapHtmlDocument(
      rawHtml: String,
      widthDp: Int,
      heightDp: Int,
      transparentBg: Boolean,
    ): String {
      val bgRule = if (transparentBg) "transparent" else "#050608"
      val trimmed = rawHtml.trim()
      val baseCss = """
        <meta name="viewport" content="width=$widthDp, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <style>
          * { box-sizing: border-box; }
          html, body {
            width: 100%;
            min-height: 100%;
            margin: 0;
            padding: 0;
            background: $bgRule;
            overflow-x: hidden;
          }
        </style>
      """.trimIndent()
      return if (trimmed.contains("<html", ignoreCase = true)) {
        if (trimmed.contains("<head>", ignoreCase = true)) {
          trimmed.replaceFirst("<head>", "<head>$baseCss", ignoreCase = true)
        } else {
          trimmed
        }
      } else {
        "<!DOCTYPE html><html><head><meta charset=\"utf-8\">$baseCss</head><body>$trimmed</body></html>"
      }
    }
  }
}
