package com.example.widget.engine

import android.graphics.Bitmap
import android.os.Parcelable
import com.example.widget.WidgetSizePreset

enum class WidgetCapability(
  val id: String,
  val displayName: String,
  val explanation: String,
) {
  TIME("time", "Clock & Timezone", "Provides authoritative device clock, 12h/24h format, timezone, and date."),
  BATTERY("battery", "Battery Telemetry", "Reads real battery level, health, temperature, and voltage."),
  CHARGING("charging", "Charging Status", "Tracks charging source, current, and charging session duration."),
  MEMORY("memory", "Memory (RAM)", "Reads live system available and total RAM via ActivityManager."),
  STORAGE("storage", "Storage Space", "Reads internal and external storage capacity and free space."),
  DEVICE("device", "Device Information", "Provides device model, OS version, SDK level, and uptime."),
  NETWORK("network", "Network State", "Detects Wi-Fi, Cellular, VPN, or offline status via ConnectivityManager."),
  MEDIA_SESSION("media_session", "Media Playback & Controls", "Reads active Spotify, YouTube Music, or other media sessions and sends Play/Pause/Next/Previous/Seek controls via Notification Listener access."),
  NOTIFICATIONS("notifications", "Notification Feed", "Reads incoming device notifications locally when Notification Access is enabled."),
  CALENDAR("calendar", "Calendar Events", "Reads upcoming device calendar events when Calendar permission is granted."),
  LOCATION_WEATHER("weather", "Location & Weather", "Uses coarse device location to look up local weather conditions without storing tracking history."),
  PHOTOS("photos", "Selected Photos & Assets", "Reads user-picked photos or assets from Photo Picker / SAF."),
  CUSTOM_DATA("custom_data", "Widget Custom Storage", "Stores persistent widget-local JSON settings and state."),
}

enum class CapabilityStatus {
  GRANTED,
  MISSING_RUNTIME,
  MISSING_SPECIAL_SETTINGS,
  UNAVAILABLE,
}

sealed class WidgetDataState<out T> {
  data object Loading : WidgetDataState<Nothing>()
  data class Available<T>(
    val data: T,
    val updatedAtMs: Long = System.currentTimeMillis(),
  ) : WidgetDataState<T>()

  data class PermissionRequired(
    val capability: WidgetCapability,
    val reason: String,
    val isSpecialSettings: Boolean = false,
  ) : WidgetDataState<Nothing>()

  data class Unavailable(val reason: String) : WidgetDataState<Nothing>()
  data class Error(
    val message: String,
    val recoverable: Boolean = true,
  ) : WidgetDataState<Nothing>()
}

enum class WidgetRenderMode {
  REMOTE_VIEWS_NATIVE,
  VISUAL_CANVAS_WITH_LIVE_CLOCK,
  HTML_HEADLESS,
  ANIMATED_FLIPPER,
}

data class WidgetDescriptor(
  val id: String,
  val title: String,
  val category: String,
  val providerClass: Class<*>,
  val requiredCapabilities: Set<WidgetCapability>,
  val renderMode: WidgetRenderMode,
  val recommendedSizePreset: WidgetSizePreset,
  val supportsStyleCustomization: Boolean = false,
)

enum class DiagnosticSeverity {
  INFO,
  WARN,
  ERROR,
}

data class WidgetDiagnosticEntry(
  val id: Long = System.nanoTime(),
  val timestampMs: Long = System.currentTimeMillis(),
  val widgetKey: String,
  val widgetId: Int? = null,
  val category: String,
  val severity: DiagnosticSeverity,
  val message: String,
  val suggestion: String? = null,
)

data class TimeSnapshot(
  val epochMs: Long,
  val time24: String,
  val time12: String,
  val time12NoAmPm: String,
  val amPm: String,
  val seconds: String,
  val dateShort: String,
  val dateFull: String,
  val dayOfWeek: String,
  val timezoneId: String,
  val timezoneDisplay: String,
  val is24HourFormat: Boolean,
)

data class NetworkSnapshot(
  val isConnected: Boolean,
  val transportType: String,
  val isWifi: Boolean,
  val isCellular: Boolean,
  val isMetered: Boolean,
  val hasValidatedInternet: Boolean,
  val downstreamKbps: Int,
  val upstreamKbps: Int,
)

data class MediaSessionSnapshot(
  val hasActiveSession: Boolean,
  val listenerPermissionGranted: Boolean,
  val title: String,
  val artist: String,
  val album: String,
  val sourceAppName: String,
  val sourcePackageName: String,
  val isPlaying: Boolean,
  val playbackStateCode: Int,
  val positionMs: Long,
  val durationMs: Long,
  val playbackSpeed: Float,
  val supportedActionsMask: Long,
  val artworkFilePath: String? = null,
  val appIconFilePath: String? = null,
  val updatedAtMs: Long = System.currentTimeMillis(),
) {
  val canPlayPause: Boolean
    get() = (supportedActionsMask and (android.media.session.PlaybackState.ACTION_PLAY or
      android.media.session.PlaybackState.ACTION_PAUSE or
      android.media.session.PlaybackState.ACTION_PLAY_PAUSE)) != 0L
  val canSkipNext: Boolean
    get() = (supportedActionsMask and android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT) != 0L
  val canSkipPrev: Boolean
    get() = (supportedActionsMask and android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0L
  val canSeek: Boolean
    get() = (supportedActionsMask and android.media.session.PlaybackState.ACTION_SEEK_TO) != 0L
}

data class WeatherSnapshot(
  val temperatureCelsius: Double,
  val conditionLabel: String,
  val windKmh: Double,
  val humidityPercent: Int?,
  val locationLabel: String,
  val observedAtMs: Long,
  val isCached: Boolean = false,
)

data class CalendarEventItem(
  val eventId: Long,
  val title: String,
  val location: String,
  val startMs: Long,
  val endMs: Long,
  val allDay: Boolean,
  val calendarName: String,
)

data class CalendarSnapshot(
  val events: List<CalendarEventItem>,
  val updatedAtMs: Long = System.currentTimeMillis(),
)

data class SelectedPhotoItem(
  val name: String,
  val uriOrPath: String,
  val isAnimatedGif: Boolean,
  val exists: Boolean,
  val sizeBytes: Long,
)

data class PhotoSnapshot(
  val photos: List<SelectedPhotoItem>,
  val missingCount: Int,
)

data class NotificationFeedSnapshot(
  val recentCount: Int,
  val latestAppName: String?,
  val latestTitle: String?,
  val latestText: String?,
  val listenerConnected: Boolean,
)
