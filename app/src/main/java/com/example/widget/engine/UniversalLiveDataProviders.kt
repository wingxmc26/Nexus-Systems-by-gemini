package com.example.widget.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import android.provider.CalendarContract
import android.text.format.DateFormat
import com.example.data.model.ActiveChargingSession
import com.example.data.model.BatteryTelemetry
import com.example.data.model.StorageOverview
import com.example.data.model.SystemMemoryDetails
import com.example.data.system.PermissionManager
import com.example.di.NexusContainer
import com.example.notifications.NexusNotificationListenerService
import com.example.widget.AnimatedWidgetEngine
import com.example.widget.NexusWidgetJsBridge
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import org.json.JSONObject

interface LiveWidgetDataProvider<T> {
  val capability: WidgetCapability
  suspend fun fetch(context: Context): WidgetDataState<T>
}

class TimeProvider : LiveWidgetDataProvider<TimeSnapshot> {
  override val capability = WidgetCapability.TIME

  override suspend fun fetch(context: Context): WidgetDataState<TimeSnapshot> {
    val nowMs = System.currentTimeMillis()
    val now = Date(nowMs)
    val tz = TimeZone.getDefault()
    val is24h = DateFormat.is24HourFormat(context)
    val locale = Locale.getDefault()
    return WidgetDataState.Available(
      TimeSnapshot(
        epochMs = nowMs,
        time24 = SimpleDateFormat("HH:mm", locale).format(now),
        time12 = SimpleDateFormat("hh:mm a", locale).format(now),
        time12NoAmPm = SimpleDateFormat("hh:mm", locale).format(now),
        amPm = SimpleDateFormat("a", locale).format(now),
        seconds = SimpleDateFormat("ss", locale).format(now),
        dateShort = SimpleDateFormat("EEE, MMM dd", locale).format(now),
        dateFull = SimpleDateFormat("EEEE, MMMM d", locale).format(now),
        dayOfWeek = SimpleDateFormat("EEEE", locale).format(now),
        timezoneId = tz.id,
        timezoneDisplay = tz.getDisplayName(tz.inDaylightTime(now), TimeZone.SHORT, locale),
        is24HourFormat = is24h,
      ),
      updatedAtMs = nowMs,
    )
  }
}

class BatteryDataProvider : LiveWidgetDataProvider<BatteryTelemetry> {
  override val capability = WidgetCapability.BATTERY

  override suspend fun fetch(context: Context): WidgetDataState<BatteryTelemetry> {
    return runCatching {
      val container = NexusContainer.get(context)
      val snap = container.batteryDataSource.readBatterySnapshot()
      runCatching { container.batterySessionRepository.processBatterySnapshot(snap) }
      WidgetDataState.Available(snap)
    }.getOrElse { err ->
      WidgetDiagnosticCenter.record("battery", "BATTERY", DiagnosticSeverity.ERROR, "Battery read failed: ${err.message}")
      WidgetDataState.Error("Unable to read battery telemetry: ${err.message}")
    }
  }
}

data class ChargingDataSnapshot(
  val isCharging: Boolean,
  val chargeSource: String,
  val statusLabel: String,
  val activeSession: ActiveChargingSession?,
)

class ChargingDataProvider : LiveWidgetDataProvider<ChargingDataSnapshot> {
  override val capability = WidgetCapability.CHARGING

  override suspend fun fetch(context: Context): WidgetDataState<ChargingDataSnapshot> {
    return runCatching {
      val container = NexusContainer.get(context)
      val snap = container.batteryDataSource.readBatterySnapshot()
      val session = container.preferencesRepository.getActiveChargingSessionSnapshot()
      WidgetDataState.Available(
        ChargingDataSnapshot(
          isCharging = snap.isCharging,
          chargeSource = snap.chargeSource,
          statusLabel = snap.statusLabel,
          activeSession = session,
        )
      )
    }.getOrElse { err ->
      WidgetDataState.Error("Unable to read charging state: ${err.message}")
    }
  }
}

class MemoryDataProvider : LiveWidgetDataProvider<SystemMemoryDetails> {
  override val capability = WidgetCapability.MEMORY

  override suspend fun fetch(context: Context): WidgetDataState<SystemMemoryDetails> {
    return runCatching {
      val mem = NexusContainer.get(context).ramManagerDataSource.readSystemMemoryDetails()
      WidgetDataState.Available(mem)
    }.getOrElse { err ->
      WidgetDiagnosticCenter.record("memory", "RAM", DiagnosticSeverity.ERROR, "RAM read failed: ${err.message}")
      WidgetDataState.Error("Unable to read RAM details: ${err.message}")
    }
  }
}

class StorageDataProvider : LiveWidgetDataProvider<StorageOverview> {
  override val capability = WidgetCapability.STORAGE

  override suspend fun fetch(context: Context): WidgetDataState<StorageOverview> {
    return runCatching {
      val sto = NexusContainer.get(context).storageDataSource.readStorageOverview()
      WidgetDataState.Available(sto)
    }.getOrElse { err ->
      WidgetDiagnosticCenter.record("storage", "STORAGE", DiagnosticSeverity.ERROR, "Storage read failed: ${err.message}")
      WidgetDataState.Error("Unable to read storage stats: ${err.message}")
    }
  }
}

data class DeviceInfoSnapshot(
  val manufacturer: String,
  val model: String,
  val brand: String,
  val androidVersion: String,
  val sdkInt: Int,
  val uptimeMs: Long,
)

class DeviceDataProvider : LiveWidgetDataProvider<DeviceInfoSnapshot> {
  override val capability = WidgetCapability.DEVICE

  override suspend fun fetch(context: Context): WidgetDataState<DeviceInfoSnapshot> {
    return WidgetDataState.Available(
      DeviceInfoSnapshot(
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        brand = Build.BRAND,
        androidVersion = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        uptimeMs = SystemClock.elapsedRealtime(),
      )
    )
  }
}

class NetworkDataProvider(
  private val permissionCoordinator: WidgetPermissionCoordinator,
) : LiveWidgetDataProvider<NetworkSnapshot> {
  override val capability = WidgetCapability.NETWORK

  override suspend fun fetch(context: Context): WidgetDataState<NetworkSnapshot> {
    if (!permissionCoordinator.isGranted(WidgetCapability.NETWORK)) {
      return WidgetDataState.PermissionRequired(
        capability = WidgetCapability.NETWORK,
        reason = "ACCESS_NETWORK_STATE permission missing.",
      )
    }
    return runCatching {
      val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return WidgetDataState.Unavailable("ConnectivityManager unavailable")
      val activeNet = cm.activeNetwork
      val caps = activeNet?.let { cm.getNetworkCapabilities(it) }
      if (caps == null) {
        WidgetDataState.Available(
          NetworkSnapshot(
            isConnected = false,
            transportType = "Offline",
            isWifi = false,
            isCellular = false,
            isMetered = false,
            hasValidatedInternet = false,
            downstreamKbps = 0,
            upstreamKbps = 0,
          )
        )
      } else {
        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCell = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val isEth = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        val transport = when {
          isWifi -> "Wi-Fi"
          isCell -> "Cellular"
          isEth -> "Ethernet"
          isVpn -> "VPN"
          else -> "Connected"
        }
        val isConnected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        WidgetDataState.Available(
          NetworkSnapshot(
            isConnected = isConnected,
            transportType = transport,
            isWifi = isWifi,
            isCellular = isCell,
            isMetered = metered,
            hasValidatedInternet = validated,
            downstreamKbps = caps.linkDownstreamBandwidthKbps.coerceAtLeast(0),
            upstreamKbps = caps.linkUpstreamBandwidthKbps.coerceAtLeast(0),
          )
        )
      }
    }.getOrElse { err ->
      WidgetDataState.Error("Network state error: ${err.message}")
    }
  }
}

class MediaDataProvider : LiveWidgetDataProvider<MediaSessionSnapshot> {
  override val capability = WidgetCapability.MEDIA_SESSION

  override suspend fun fetch(context: Context): WidgetDataState<MediaSessionSnapshot> {
    return runCatching {
      val snap = WidgetMediaSessionHub.readLatestSnapshot(context)
      if (!snap.listenerPermissionGranted && !snap.hasActiveSession) {
        WidgetDataState.PermissionRequired(
          capability = WidgetCapability.MEDIA_SESSION,
          reason = "Enable Notification Access to mirror Spotify, YouTube Music, and system media sessions.",
          isSpecialSettings = true,
        )
      } else {
        WidgetDataState.Available(snap, updatedAtMs = snap.updatedAtMs)
      }
    }.getOrElse { err ->
      WidgetDiagnosticCenter.record("music", "MEDIA_SESSION", DiagnosticSeverity.ERROR, "MediaSession read failed: ${err.message}")
      WidgetDataState.Error("Unable to query MediaSession: ${err.message}")
    }
  }
}

class CalendarDataProvider(
  private val permissionCoordinator: WidgetPermissionCoordinator,
) : LiveWidgetDataProvider<CalendarSnapshot> {
  override val capability = WidgetCapability.CALENDAR

  override suspend fun fetch(context: Context): WidgetDataState<CalendarSnapshot> {
    if (!permissionCoordinator.isGranted(WidgetCapability.CALENDAR)) {
      return WidgetDataState.PermissionRequired(
        capability = WidgetCapability.CALENDAR,
        reason = "Calendar permission (READ_CALENDAR) required to display upcoming events.",
      )
    }
    return runCatching {
      val now = System.currentTimeMillis()
      val endWindow = now + 7L * 24L * 3600L * 1000L
      val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
      android.content.ContentUris.appendId(builder, now)
      android.content.ContentUris.appendId(builder, endWindow)
      val projection = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.EVENT_LOCATION,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
      )
      val events = mutableListOf<CalendarEventItem>()
      context.contentResolver.query(
        builder.build(),
        projection,
        null,
        null,
        "${CalendarContract.Instances.BEGIN} ASC",
      )?.use { cursor ->
        while (cursor.moveToNext() && events.size < 10) {
          events.add(
            CalendarEventItem(
              eventId = cursor.getLong(0),
              title = cursor.getString(1).orEmpty().ifBlank { "(Untitled Event)" },
              location = cursor.getString(2).orEmpty(),
              startMs = cursor.getLong(3),
              endMs = cursor.getLong(4),
              allDay = cursor.getInt(5) != 0,
              calendarName = cursor.getString(6).orEmpty(),
            )
          )
        }
      }
      WidgetDataState.Available(CalendarSnapshot(events = events, updatedAtMs = now))
    }.getOrElse { err ->
      WidgetDiagnosticCenter.record("calendar", "CALENDAR", DiagnosticSeverity.ERROR, "Calendar query failed: ${err.message}")
      WidgetDataState.Error("Calendar query failed: ${err.message}")
    }
  }
}

class WeatherDataProvider(
  private val permissionCoordinator: WidgetPermissionCoordinator,
  private val networkProvider: NetworkDataProvider,
) : LiveWidgetDataProvider<WeatherSnapshot> {
  override val capability = WidgetCapability.LOCATION_WEATHER

  companion object {
    private const val PREF_NAME = "nexus_widget_weather_cache"
    private const val CACHE_TTL_MS = 30L * 60L * 1000L
  }

  @SuppressLint("MissingPermission")
  override suspend fun fetch(context: Context): WidgetDataState<WeatherSnapshot> {
    if (!permissionCoordinator.isGranted(WidgetCapability.LOCATION_WEATHER)) {
      return WidgetDataState.PermissionRequired(
        capability = WidgetCapability.LOCATION_WEATHER,
        reason = "Coarse Location permission required to look up local weather.",
      )
    }
    val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    val cachedAt = prefs.getLong("observed_at_ms", 0L)
    val now = System.currentTimeMillis()
    if (cachedAt > 0L && (now - cachedAt) < CACHE_TTL_MS && prefs.contains("temp_c")) {
      return WidgetDataState.Available(readCached(prefs, isCached = true), updatedAtMs = cachedAt)
    }

    val loc = resolveLastLocation(context)
    if (loc == null) {
      return if (cachedAt > 0L && prefs.contains("temp_c")) {
        WidgetDataState.Available(readCached(prefs, isCached = true), updatedAtMs = cachedAt)
      } else {
        WidgetDataState.Unavailable("No device location fix available yet.")
      }
    }

    val netState = networkProvider.fetch(context)
    val online = (netState as? WidgetDataState.Available)?.data?.isConnected == true
    if (!online) {
      return if (cachedAt > 0L && prefs.contains("temp_c")) {
        WidgetDataState.Available(readCached(prefs, isCached = true), updatedAtMs = cachedAt)
      } else {
        WidgetDataState.Unavailable("Offline — weather requires an active internet connection.")
      }
    }

    if (!permissionCoordinator.isGranted(WidgetCapability.LOCATION_WEATHER) ||
      context.checkSelfPermission(Manifest.permission.INTERNET) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
      return WidgetDataState.PermissionRequired(WidgetCapability.LOCATION_WEATHER, "Location/Internet permission missing.")
    }

    return runCatching {
      val urlStr = String.format(
        Locale.US,
        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m",
        loc.latitude,
        loc.longitude,
      )
      val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
        connectTimeout = 4000
        readTimeout = 4000
        requestMethod = "GET"
      }
      val body = conn.inputStream.bufferedReader().use { it.readText() }
      conn.disconnect()
      val root = JSONObject(body)
      val cur = root.getJSONObject("current")
      val temp = cur.getDouble("temperature_2m")
      val wind = cur.optDouble("wind_speed_10m", 0.0)
      val hum = if (cur.has("relative_humidity_2m")) cur.optInt("relative_humidity_2m") else null
      val code = cur.optInt("weather_code", -1)
      val cond = mapWmoCode(code)
      val locLabel = String.format(Locale.US, "%.2f, %.2f", loc.latitude, loc.longitude)
      prefs.edit()
        .putFloat("temp_c", temp.toFloat())
        .putFloat("wind_kmh", wind.toFloat())
        .putInt("humidity", hum ?: -1)
        .putString("condition", cond)
        .putString("location", locLabel)
        .putLong("observed_at_ms", now)
        .apply()
      WidgetDataState.Available(
        WeatherSnapshot(temp, cond, wind, hum, locLabel, now, isCached = false),
        updatedAtMs = now,
      )
    }.getOrElse { err ->
      if (cachedAt > 0L && prefs.contains("temp_c")) {
        WidgetDataState.Available(readCached(prefs, isCached = true), updatedAtMs = cachedAt)
      } else {
        WidgetDiagnosticCenter.record("weather", "WEATHER", DiagnosticSeverity.WARN, "Weather fetch unavailable: ${err.message}")
        WidgetDataState.Unavailable("Weather service unreachable: ${err.message}")
      }
    }
  }

  @SuppressLint("MissingPermission")
  private fun resolveLastLocation(context: Context): Location? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
    return providers.mapNotNull { prov ->
      runCatching { lm.getLastKnownLocation(prov) }.getOrNull()
    }.maxByOrNull { it.time }
  }

  private fun readCached(prefs: android.content.SharedPreferences, isCached: Boolean): WeatherSnapshot {
    val hum = prefs.getInt("humidity", -1).takeIf { it >= 0 }
    return WeatherSnapshot(
      temperatureCelsius = prefs.getFloat("temp_c", 0f).toDouble(),
      conditionLabel = prefs.getString("condition", "Unknown") ?: "Unknown",
      windKmh = prefs.getFloat("wind_kmh", 0f).toDouble(),
      humidityPercent = hum,
      locationLabel = prefs.getString("location", "Current Area") ?: "Current Area",
      observedAtMs = prefs.getLong("observed_at_ms", 0L),
      isCached = isCached,
    )
  }

  private fun mapWmoCode(code: Int): String = when (code) {
    0 -> "Clear Sky"
    1, 2, 3 -> "Partly Cloudy"
    45, 48 -> "Fog"
    51, 53, 55, 61, 63, 65, 80, 81, 82 -> "Rain"
    71, 73, 75, 85, 86 -> "Snow"
    95, 96, 99 -> "Thunderstorm"
    else -> "Local Weather"
  }
}

class PhotoDataProvider : LiveWidgetDataProvider<PhotoSnapshot> {
  override val capability = WidgetCapability.PHOTOS

  override suspend fun fetch(context: Context): WidgetDataState<PhotoSnapshot> {
    return runCatching {
      val imgDir = File(context.filesDir, "widget_images")
      val assetDir = File(context.filesDir, NexusWidgetJsBridge.ASSETS_DIR)
      val dirs = listOf(imgDir, assetDir).filter { it.exists() }
      val photos = mutableListOf<SelectedPhotoItem>()
      dirs.forEach { d ->
        d.listFiles()?.sortedByDescending { it.lastModified() }?.forEach { file ->
          val lower = file.name.lowercase(Locale.US)
          if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".gif")) {
            photos.add(
              SelectedPhotoItem(
                name = file.name,
                uriOrPath = "file://${file.absolutePath}",
                isAnimatedGif = AnimatedWidgetEngine.isAnimatedGifFile(file.absolutePath),
                exists = file.exists(),
                sizeBytes = file.length(),
              )
            )
          }
        }
      }
      WidgetDataState.Available(PhotoSnapshot(photos = photos, missingCount = 0))
    }.getOrElse { err ->
      WidgetDataState.Error("Failed reading widget photos: ${err.message}")
    }
  }
}

class NotificationDataProvider : LiveWidgetDataProvider<NotificationFeedSnapshot> {
  override val capability = WidgetCapability.NOTIFICATIONS

  override suspend fun fetch(context: Context): WidgetDataState<NotificationFeedSnapshot> {
    val perm = PermissionManager(context)
    if (!perm.isNotificationAccessEnabled()) {
      return WidgetDataState.PermissionRequired(
        capability = WidgetCapability.NOTIFICATIONS,
        reason = "Notification Access required for notification feed widgets.",
        isSpecialSettings = true,
      )
    }
    return runCatching {
      val list = NexusContainer.get(context).database.notificationHistoryDao().observeAll().first().take(5)
      val latest = list.firstOrNull()
      WidgetDataState.Available(
        NotificationFeedSnapshot(
          recentCount = list.size,
          latestAppName = latest?.appName,
          latestTitle = latest?.title,
          latestText = latest?.text,
          listenerConnected = NexusNotificationListenerService.isConnected.value,
        )
      )
    }.getOrElse { err ->
      WidgetDataState.Error("Failed reading notification feed: ${err.message}")
    }
  }
}

class CustomDataProvider : LiveWidgetDataProvider<Map<String, String>> {
  override val capability = WidgetCapability.CUSTOM_DATA

  override suspend fun fetch(context: Context): WidgetDataState<Map<String, String>> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val all = prefs.all.mapValues { it.value?.toString().orEmpty() }
    return WidgetDataState.Available(all)
  }

  fun putValue(context: Context, key: String, value: String) {
    if (key.isBlank()) return
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(key.take(80), value.take(8192))
      .apply()
  }

  fun getValue(context: Context, key: String): String {
    if (key.isBlank()) return ""
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getString(key.take(80), "") ?: ""
  }

  companion object {
    private const val PREFS_NAME = "nexus_widget_custom_kv"
  }
}

class UniversalWidgetDataHub(private val context: Context) {
  private val appCtx = context.applicationContext
  val permissionCoordinator = WidgetPermissionCoordinator(appCtx, PermissionManager(appCtx))
  val timeProvider = TimeProvider()
  val batteryProvider = BatteryDataProvider()
  val chargingProvider = ChargingDataProvider()
  val memoryProvider = MemoryDataProvider()
  val storageProvider = StorageDataProvider()
  val deviceProvider = DeviceDataProvider()
  val networkProvider = NetworkDataProvider(permissionCoordinator)
  val mediaProvider = MediaDataProvider()
  val calendarProvider = CalendarDataProvider(permissionCoordinator)
  val weatherProvider = WeatherDataProvider(permissionCoordinator, networkProvider)
  val photoProvider = PhotoDataProvider()
  val notificationProvider = NotificationDataProvider()
  val customDataProvider = CustomDataProvider()
}
