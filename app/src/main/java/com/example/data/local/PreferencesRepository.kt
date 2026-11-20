package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ActiveChargingSession
import com.example.data.model.BuiltInWidgetStyleConfig
import com.example.data.model.DashboardModuleId
import com.example.data.model.DashboardPreferences
import com.example.data.model.DepthClockConfig
import com.example.data.model.LiveWallpaperConfig
import com.example.data.model.WallpaperMediaType
import com.example.ui.theme.AccentPalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.nexusDataStore: DataStore<Preferences> by preferencesDataStore(name = "nexus_prefs")

class PreferencesRepository(private val context: Context) {

  private object Keys {
    val ACCENT_ID = stringPreferencesKey("accent_id")
    val AMOLED_MODE = booleanPreferencesKey("amoled_mode")
    val REFRESH_INTERVAL_MS = longPreferencesKey("refresh_interval_ms")
    val CARD_ORDER = stringPreferencesKey("card_order")
    val HIDDEN_MODULES = stringPreferencesKey("hidden_modules")

    // Live Wallpaper configuration
    val WALLPAPER_ACTIVE_ID = longPreferencesKey("wallpaper_active_id")
    val WALLPAPER_ACTIVE_PATH = stringPreferencesKey("wallpaper_active_path")
    val WALLPAPER_VIDEO_NAME = stringPreferencesKey("wallpaper_video_name")
    val WALLPAPER_MEDIA_TYPE = stringPreferencesKey("wallpaper_media_type")
    val WALLPAPER_MUTE_AUDIO = booleanPreferencesKey("wallpaper_mute_audio")
    val WALLPAPER_SCALE_CROP = booleanPreferencesKey("wallpaper_scale_crop")
    val WALLPAPER_LOOP = booleanPreferencesKey("wallpaper_loop")
    val WALLPAPER_SPEED = floatPreferencesKey("wallpaper_speed")
    val WALLPAPER_BATTERY_PAUSE = booleanPreferencesKey("wallpaper_battery_pause")
    val WALLPAPER_OFFSET_X = floatPreferencesKey("wallpaper_offset_x")
    val WALLPAPER_OFFSET_Y = floatPreferencesKey("wallpaper_offset_y")
    val WALLPAPER_ZOOM = floatPreferencesKey("wallpaper_zoom")

    // Active Charging Session persistence across restarts
    val CHARGING_ACTIVE = booleanPreferencesKey("charging_active")
    val CHARGING_START_MS = longPreferencesKey("charging_start_ms")
    val CHARGING_LAST_SEEN_MS = longPreferencesKey("charging_last_seen_ms")
    val CHARGING_START_PCT = intPreferencesKey("charging_start_pct")
    val CHARGING_SOURCE = stringPreferencesKey("charging_source")

    // Active Depth Clock & Sound Booster & Music State
    val DEPTH_CLOCK_JSON = stringPreferencesKey("depth_clock_json")
    val RECENT_APP_PACKAGES = stringPreferencesKey("recent_app_packages")
    val LAST_MUSIC_TITLE = stringPreferencesKey("last_music_title")
    val LAST_MUSIC_ARTIST = stringPreferencesKey("last_music_artist")
    val LAST_MUSIC_PLAYING = booleanPreferencesKey("last_music_playing")
    val SOUND_BOOSTER_ENABLED = booleanPreferencesKey("sound_booster_enabled")
    val SOUND_BOOSTER_PRESET = stringPreferencesKey("sound_booster_preset")
    val SOUND_BOOSTER_BANDS = stringPreferencesKey("sound_booster_bands")
    val SOUND_BOOSTER_BASS = intPreferencesKey("sound_booster_bass")
    val SOUND_BOOSTER_TREBLE = intPreferencesKey("sound_booster_treble")
    val SOUND_BOOSTER_VIRTUALIZER = intPreferencesKey("sound_booster_virtualizer")
    val SOUND_BOOSTER_PREAMP = intPreferencesKey("sound_booster_preamp")
  }

  val preferences: Flow<DashboardPreferences> = context.nexusDataStore.data.map { prefs ->
    val accent = AccentPalette.fromId(prefs[Keys.ACCENT_ID])
    val amoled = prefs[Keys.AMOLED_MODE] ?: true
    val refresh = prefs[Keys.REFRESH_INTERVAL_MS] ?: 2000L

    val storedOrder = prefs[Keys.CARD_ORDER]
      ?.split(",")
      ?.mapNotNull { DashboardModuleId.fromId(it.trim()) }
      .orEmpty()
    val completeOrder = (storedOrder + DashboardModuleId.defaultOrder).distinct()

    val hidden = prefs[Keys.HIDDEN_MODULES]
      ?.split(",")
      ?.mapNotNull { DashboardModuleId.fromId(it.trim()) }
      ?.toSet()
      .orEmpty()

    DashboardPreferences(
      accentPalette = accent,
      amoledMode = amoled,
      refreshIntervalMs = refresh.coerceIn(1000L, 10_000L),
      cardOrder = completeOrder,
      hiddenModules = hidden,
    )
  }

  val wallpaperConfig: Flow<LiveWallpaperConfig> = context.nexusDataStore.data.map { prefs ->
    val name = prefs[Keys.WALLPAPER_VIDEO_NAME]
    val localPath = prefs[Keys.WALLPAPER_ACTIVE_PATH]
    val typeName = prefs[Keys.WALLPAPER_MEDIA_TYPE] ?: WallpaperMediaType.VIDEO.name
    val type = runCatching { WallpaperMediaType.valueOf(typeName) }.getOrDefault(WallpaperMediaType.VIDEO)
    LiveWallpaperConfig(
      activeWallpaperId = prefs[Keys.WALLPAPER_ACTIVE_ID] ?: 0L,
      activeLocalPath = localPath,
      videoFileName = name,
      mediaType = type,
      muteAudio = prefs[Keys.WALLPAPER_MUTE_AUDIO] ?: true,
      scaleCrop = prefs[Keys.WALLPAPER_SCALE_CROP] ?: true,
      loopPlayback = prefs[Keys.WALLPAPER_LOOP] ?: true,
      playbackSpeed = prefs[Keys.WALLPAPER_SPEED] ?: 1.0f,
      batterySaverPause = prefs[Keys.WALLPAPER_BATTERY_PAUSE] ?: true,
      offsetXPercent = prefs[Keys.WALLPAPER_OFFSET_X] ?: 0f,
      offsetYPercent = prefs[Keys.WALLPAPER_OFFSET_Y] ?: 0f,
      zoomScale = prefs[Keys.WALLPAPER_ZOOM] ?: 1.0f,
      hasLocalVideo = !localPath.isNullOrBlank() || !name.isNullOrBlank(),
      isCurrentlySetAsWallpaper = false,
    )
  }

  val activeChargingSession: Flow<ActiveChargingSession?> = context.nexusDataStore.data.map { prefs ->
    val isActive = prefs[Keys.CHARGING_ACTIVE] ?: false
    val startMs = prefs[Keys.CHARGING_START_MS] ?: 0L
    if (isActive && startMs > 0L) {
      ActiveChargingSession(
        startTimeMs = startMs,
        startPercentage = prefs[Keys.CHARGING_START_PCT] ?: 0,
        chargeSource = prefs[Keys.CHARGING_SOURCE] ?: "Unknown",
      )
    } else {
      null
    }
  }

  val recentAppPackages: Flow<List<String>> = context.nexusDataStore.data.map { prefs ->
    prefs[Keys.RECENT_APP_PACKAGES]
      ?.split(",")
      ?.filter { it.isNotBlank() }
      .orEmpty()
  }

  val depthClockJson: Flow<String?> = context.nexusDataStore.data.map { prefs ->
    prefs[Keys.DEPTH_CLOCK_JSON]
  }

  suspend fun getActiveChargingSessionSnapshot(): ActiveChargingSession? =
    activeChargingSession.first()

  suspend fun getLastSeenChargingTimeMs(): Long? {
    val prefs = context.nexusDataStore.data.first()
    return prefs[Keys.CHARGING_LAST_SEEN_MS]?.takeIf { it > 0L }
  }

  suspend fun setActiveChargingSession(
    startTimeMs: Long,
    lastSeenMs: Long,
    startPct: Int,
    source: String,
  ) {
    context.nexusDataStore.edit { prefs ->
      prefs[Keys.CHARGING_ACTIVE] = true
      prefs[Keys.CHARGING_START_MS] = startTimeMs
      prefs[Keys.CHARGING_LAST_SEEN_MS] = lastSeenMs
      prefs[Keys.CHARGING_START_PCT] = startPct
      prefs[Keys.CHARGING_SOURCE] = source
    }
  }

  suspend fun touchActiveChargingSession(lastSeenMs: Long, source: String) {
    context.nexusDataStore.edit { prefs ->
      prefs[Keys.CHARGING_LAST_SEEN_MS] = lastSeenMs
      prefs[Keys.CHARGING_SOURCE] = source
    }
  }

  suspend fun clearActiveChargingSession() {
    context.nexusDataStore.edit { prefs ->
      prefs[Keys.CHARGING_ACTIVE] = false
      prefs.remove(Keys.CHARGING_START_MS)
      prefs.remove(Keys.CHARGING_LAST_SEEN_MS)
      prefs.remove(Keys.CHARGING_START_PCT)
      prefs.remove(Keys.CHARGING_SOURCE)
    }
  }

  suspend fun setWallpaperSelection(
    activeId: Long,
    fileName: String?,
    mediaType: WallpaperMediaType,
    localPath: String? = null,
  ) {
    context.nexusDataStore.edit { prefs ->
      if (fileName.isNullOrBlank() && localPath.isNullOrBlank()) {
        prefs.remove(Keys.WALLPAPER_VIDEO_NAME)
        prefs.remove(Keys.WALLPAPER_ACTIVE_ID)
        prefs.remove(Keys.WALLPAPER_ACTIVE_PATH)
      } else {
        prefs[Keys.WALLPAPER_ACTIVE_ID] = activeId
        if (!fileName.isNullOrBlank()) prefs[Keys.WALLPAPER_VIDEO_NAME] = fileName
        if (!localPath.isNullOrBlank()) prefs[Keys.WALLPAPER_ACTIVE_PATH] = localPath
        else prefs.remove(Keys.WALLPAPER_ACTIVE_PATH)
        prefs[Keys.WALLPAPER_MEDIA_TYPE] = mediaType.name
      }
    }
  }

  suspend fun setWallpaperVideo(fileName: String?) =
    setWallpaperSelection(0L, fileName, WallpaperMediaType.VIDEO, null)

  suspend fun setWallpaperMuteAudio(mute: Boolean) {
    context.nexusDataStore.edit { it[Keys.WALLPAPER_MUTE_AUDIO] = mute }
  }

  suspend fun setWallpaperScaleCrop(crop: Boolean) {
    context.nexusDataStore.edit { it[Keys.WALLPAPER_SCALE_CROP] = crop }
  }

  suspend fun setWallpaperLoop(loop: Boolean) {
    context.nexusDataStore.edit { it[Keys.WALLPAPER_LOOP] = loop }
  }

  suspend fun setWallpaperSpeed(speed: Float) {
    context.nexusDataStore.edit { it[Keys.WALLPAPER_SPEED] = speed.coerceIn(0.25f, 2.0f) }
  }

  suspend fun setWallpaperBatteryPause(pause: Boolean) {
    context.nexusDataStore.edit { it[Keys.WALLPAPER_BATTERY_PAUSE] = pause }
  }

  suspend fun setWallpaperTransform(offsetX: Float, offsetY: Float, zoom: Float) {
    context.nexusDataStore.edit {
      it[Keys.WALLPAPER_OFFSET_X] = offsetX.coerceIn(-0.5f, 0.5f)
      it[Keys.WALLPAPER_OFFSET_Y] = offsetY.coerceIn(-0.5f, 0.5f)
      it[Keys.WALLPAPER_ZOOM] = zoom.coerceIn(0.5f, 2.5f)
    }
  }

  suspend fun recordRecentAppPackage(packageName: String) {
    context.nexusDataStore.edit { prefs ->
      val current = prefs[Keys.RECENT_APP_PACKAGES]
        ?.split(",")
        ?.filter { it.isNotBlank() && it != packageName }
        .orEmpty()
      val updated = (listOf(packageName) + current).take(12)
      prefs[Keys.RECENT_APP_PACKAGES] = updated.joinToString(",")
    }
  }

  suspend fun saveDepthClockJson(json: String) {
    context.nexusDataStore.edit { it[Keys.DEPTH_CLOCK_JSON] = json }
  }

  suspend fun getDepthClockJson(): String? = context.nexusDataStore.data.first()[Keys.DEPTH_CLOCK_JSON]

  fun observeBuiltInWidgetStyle(widgetType: String): Flow<BuiltInWidgetStyleConfig> {
    val key = stringPreferencesKey("builtin_widget_style_$widgetType")
    return context.nexusDataStore.data.map { prefs ->
      WidgetClockSerializer.deserializeBuiltInStyle(prefs[key])
    }
  }

  suspend fun getBuiltInWidgetStyle(widgetType: String): BuiltInWidgetStyleConfig {
    val key = stringPreferencesKey("builtin_widget_style_$widgetType")
    val json = context.nexusDataStore.data.first()[key]
    return WidgetClockSerializer.deserializeBuiltInStyle(json)
  }

  suspend fun saveBuiltInWidgetStyle(widgetType: String, config: BuiltInWidgetStyleConfig) {
    val key = stringPreferencesKey("builtin_widget_style_$widgetType")
    context.nexusDataStore.edit {
      it[key] = WidgetClockSerializer.serializeBuiltInStyle(config.copy(isCustomized = true))
    }
  }

  suspend fun resetBuiltInWidgetStyle(widgetType: String) {
    val key = stringPreferencesKey("builtin_widget_style_$widgetType")
    context.nexusDataStore.edit { it.remove(key) }
  }

  suspend fun updateLastMusicPlayback(title: String, artist: String, isPlaying: Boolean) {
    context.nexusDataStore.edit {
      it[Keys.LAST_MUSIC_TITLE] = title
      it[Keys.LAST_MUSIC_ARTIST] = artist
      it[Keys.LAST_MUSIC_PLAYING] = isPlaying
    }
  }

  suspend fun getLastMusicPlaybackSnapshot(): Triple<String, String, Boolean> {
    val prefs = context.nexusDataStore.data.first()
    return Triple(
      prefs[Keys.LAST_MUSIC_TITLE] ?: "No track selected",
      prefs[Keys.LAST_MUSIC_ARTIST] ?: "Open Music Player to play local audio",
      prefs[Keys.LAST_MUSIC_PLAYING] ?: false,
    )
  }

  suspend fun saveSoundBoosterState(
    enabled: Boolean,
    preset: String,
    bands: List<Int>,
    bass: Int,
    treble: Int,
    virtualizer: Int,
    preamp: Int,
  ) {
    context.nexusDataStore.edit {
      it[Keys.SOUND_BOOSTER_ENABLED] = enabled
      it[Keys.SOUND_BOOSTER_PRESET] = preset
      it[Keys.SOUND_BOOSTER_BANDS] = bands.joinToString(",")
      it[Keys.SOUND_BOOSTER_BASS] = bass
      it[Keys.SOUND_BOOSTER_TREBLE] = treble
      it[Keys.SOUND_BOOSTER_VIRTUALIZER] = virtualizer
      it[Keys.SOUND_BOOSTER_PREAMP] = preamp
    }
  }

  suspend fun getSoundBoosterSnapshot(): Preferences = context.nexusDataStore.data.first()

  suspend fun setAccentPalette(palette: AccentPalette) {
    context.nexusDataStore.edit { it[Keys.ACCENT_ID] = palette.id }
  }

  suspend fun setAmoledMode(enabled: Boolean) {
    context.nexusDataStore.edit { it[Keys.AMOLED_MODE] = enabled }
  }

  suspend fun setRefreshIntervalMs(intervalMs: Long) {
    context.nexusDataStore.edit { it[Keys.REFRESH_INTERVAL_MS] = intervalMs }
  }

  suspend fun setCardOrder(order: List<DashboardModuleId>) {
    context.nexusDataStore.edit { it[Keys.CARD_ORDER] = order.joinToString(",") { m -> m.id } }
  }

  suspend fun setModuleVisibility(moduleId: DashboardModuleId, visible: Boolean) {
    context.nexusDataStore.edit { prefs ->
      val current = prefs[Keys.HIDDEN_MODULES]
        ?.split(",")
        ?.mapNotNull { DashboardModuleId.fromId(it.trim()) }
        ?.toMutableSet() ?: mutableSetOf()
      if (visible) current.remove(moduleId) else current.add(moduleId)
      prefs[Keys.HIDDEN_MODULES] = current.joinToString(",") { it.id }
    }
  }

  suspend fun resetDefaults() {
    context.nexusDataStore.edit { prefs ->
      prefs.remove(Keys.ACCENT_ID)
      prefs.remove(Keys.AMOLED_MODE)
      prefs.remove(Keys.REFRESH_INTERVAL_MS)
      prefs.remove(Keys.CARD_ORDER)
      prefs.remove(Keys.HIDDEN_MODULES)
    }
  }
}
