package com.example

import android.Manifest
import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.di.NexusContainer
import com.example.widget.NexusBatteryWidgetProvider
import com.example.widget.NexusDeviceInfoWidgetProvider
import com.example.widget.NexusFavoritesWidgetProvider
import com.example.widget.NexusMusicWidgetProvider
import com.example.widget.NexusSystemStatusWidgetProvider
import com.example.widget.NexusWidgetJsBridge
import com.example.widget.NexusWidgetLiveTicker
import com.example.widget.NexusWidgetRefreshReceiver
import com.example.widget.NexusWidgetSyncJobService
import com.example.widget.WidgetSizePreset
import com.example.widget.engine.CapabilityStatus
import com.example.widget.engine.DiagnosticSeverity
import com.example.widget.engine.WidgetCapability
import com.example.widget.engine.WidgetDataState
import com.example.widget.engine.WidgetDescriptor
import com.example.widget.engine.WidgetDiagnosticCenter
import com.example.widget.engine.WidgetEngineRegistry
import com.example.widget.engine.WidgetMediaSessionHub
import com.example.widget.engine.WidgetRenderMode
import java.io.File
import java.time.Duration
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.robolectric.shadows.ShadowSystemClock
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetEngineAuditRobolectricTest {

  private val createdSessions = mutableListOf<MediaSession>()

  @After
  fun tearDown() {
    createdSessions.forEach { runCatching { it.release() } }
    createdSessions.clear()
    WidgetDiagnosticCenter.clear()
  }

  @Test
  fun `time provider respects timezone and date format while broadcasts and job scheduler activate cleanly`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val hub = NexusContainer.get(context).widgetDataHub
    val origTz = TimeZone.getDefault()
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dhaka"))
      val state = hub.timeProvider.fetch(context)
      assertTrue(state is WidgetDataState.Available)
      val snap = (state as WidgetDataState.Available).data
      assertEquals("Asia/Dhaka", snap.timezoneId)
      assertTrue(snap.time24.matches(Regex("\\d{2}:\\d{2}")))
      assertTrue(snap.time12.contains("AM", true) || snap.time12.contains("PM", true))
      assertTrue(snap.dateFull.isNotBlank())

      NexusWidgetLiveTicker.ensureRegistered(context)
      assertTrue(NexusWidgetLiveTicker.isRegistered())
      NexusWidgetSyncJobService.schedulePeriodic(context)

      val receiver = NexusWidgetRefreshReceiver()
      receiver.onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
      receiver.onReceive(context, Intent(Intent.ACTION_DATE_CHANGED))
      receiver.onReceive(context, Intent(Intent.ACTION_LOCALE_CHANGED))
      receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
    } finally {
      TimeZone.setDefault(origTz)
    }
  }

  @Test
  fun `media session hub extracts real metadata position and artwork with zero fake data`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val art = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }

    // Ensure real Android MediaController instance extracts safely even with empty framework state
    val realSession = MediaSession(context, "nexus_test_real").also { createdSessions.add(it) }
    val safeFallbackSnap = WidgetMediaSessionHub.extractSnapshot(context, realSession.controller, listenerGranted = true)
    assertTrue(safeFallbackSnap.hasActiveSession)

    ShadowSystemClock.advanceBy(Duration.ofSeconds(10))
    val spotifyInput = WidgetMediaSessionHub.MediaControllerInput(
      packageName = "com.spotify.music",
      title = "Midnight City",
      artist = "M83",
      album = "Hurry Up",
      durationMs = 240_000L,
      stateCode = PlaybackState.STATE_PLAYING,
      positionMs = 30_000L,
      playbackSpeed = 1.0f,
      lastPositionUpdateTime = SystemClock.elapsedRealtime() - 5_000L,
      supportedActionsMask = PlaybackState.ACTION_PLAY_PAUSE or
        PlaybackState.ACTION_SKIP_TO_NEXT or
        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
        PlaybackState.ACTION_SEEK_TO,
      albumArtBitmap = art,
      hasMetadata = true,
    )
    val pausedInput = WidgetMediaSessionHub.MediaControllerInput(
      packageName = "com.google.android.apps.youtube.music",
      title = "Other Track",
      artist = "Artist",
      album = "",
      durationMs = 120_000L,
      stateCode = PlaybackState.STATE_PAUSED,
      positionMs = 10_000L,
      playbackSpeed = 1.0f,
      lastPositionUpdateTime = SystemClock.elapsedRealtime(),
      supportedActionsMask = PlaybackState.ACTION_PLAY_PAUSE,
      hasMetadata = true,
    )

    val selected = WidgetMediaSessionHub.selectPrimaryInput(context, listOf(pausedInput, spotifyInput))
    assertEquals("com.spotify.music", selected?.packageName)
    assertEquals(PlaybackState.STATE_PLAYING, selected?.stateCode)

    val snap = WidgetMediaSessionHub.buildSnapshot(context, selected!!, listenerGranted = true)
    assertTrue(snap.hasActiveSession)
    assertTrue(snap.isPlaying)
    assertEquals("Midnight City", snap.title)
    assertEquals("M83", snap.artist)
    assertEquals("Hurry Up", snap.album)
    assertEquals(240_000L, snap.durationMs)
    assertTrue("Expected elapsed playback interpolation >= 34s", snap.positionMs >= 34_000L)
    assertTrue(snap.canPlayPause)
    assertTrue(snap.canSkipNext)
    assertTrue(snap.canSkipPrev)
    assertTrue(snap.canSeek)
    val artPath = snap.artworkFilePath
    assertNotNull(artPath)
    assertTrue(File(artPath!!).exists())

    // Without artwork in metadata, artworkFilePath must remain null (no fake fallback)
    val noArtSnap = WidgetMediaSessionHub.buildSnapshot(context, pausedInput, listenerGranted = true)
    assertNull(noArtSnap.artworkFilePath)

    val artFile = File(artPath)
    art.recycle()
    artFile.delete()
  }

  @Test
  fun `media data provider distinguishes missing permission from idle granted state`(): Unit = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val hub = NexusContainer.get(context).widgetDataHub

    // 1. Permission revoked & no active internal session -> PermissionRequired
    Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", "")
    val deniedState = hub.mediaProvider.fetch(context)
    assertTrue(deniedState is WidgetDataState.PermissionRequired)
    assertEquals(WidgetCapability.MEDIA_SESSION, (deniedState as WidgetDataState.PermissionRequired).capability)

    // 2. Permission granted & 0 controllers -> honest Available(hasActiveSession = false), zero fake track
    Settings.Secure.putString(
      context.contentResolver,
      "enabled_notification_listeners",
      "${context.packageName}/com.example.notifications.NexusNotificationListenerService",
    )
    val idleState = hub.mediaProvider.fetch(context)
    assertTrue(idleState is WidgetDataState.Available)
    val idleSnap = (idleState as WidgetDataState.Available).data
    assertFalse(idleSnap.hasActiveSession)
    assertTrue(idleSnap.listenerPermissionGranted)

    // Clean up listener setting
    Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", "")
  }

  @Test
  fun `universal js bridge serves all 13 apis injects fonts enforces security and records diagnostics`(): Unit = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Application>()
    Shadows.shadowOf(context).grantPermissions(Manifest.permission.ACCESS_NETWORK_STATE)
    val assetsDir = File(context.filesDir, NexusWidgetJsBridge.ASSETS_DIR).apply { mkdirs() }
    val dummyFont = File(assetsDir, "NexusCustomMono.ttf").apply { writeBytes(byteArrayOf(0, 1, 0, 0)) }
    val dummyPhoto = File(assetsDir, "bg_card.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

    try {
      val bridge = NexusWidgetJsBridge(context, widthDp = 328, heightDp = 160, widgetId = 77)
      assertEquals(77, bridge.getWidgetId())

      // Verify all 13 documented JS endpoints return parseable JSON
      assertEquals("available", JSONObject(bridge.getDeviceInfo()).getString("status"))
      assertEquals("available", JSONObject(bridge.getBatteryInfo()).getString("status"))
      assertEquals("available", JSONObject(bridge.getStorageInfo()).getString("status"))
      assertEquals("available", JSONObject(bridge.getMemoryInfo()).getString("status"))
      assertEquals("available", JSONObject(bridge.getNetworkInfo()).getString("status"))
      assertEquals("available", JSONObject(bridge.getCurrentTime()).getString("status"))
      assertTrue(JSONObject(bridge.getWeather()).has("status"))
      assertTrue(JSONObject(bridge.getMediaSession()).has("status"))
      assertTrue(JSONObject(bridge.getCalendarEvents()).has("status"))

      val photosObj = JSONObject(bridge.getSelectedPhotos())
      assertEquals("available", photosObj.getString("status"))
      assertTrue(photosObj.getInt("count") >= 1)

      // Custom key-value storage via bridge + blocked unsafe action check
      val blockedRes = JSONObject(bridge.executeWidgetAction("""{"action":"exec_shell","cmd":"rm"}"""))
      assertFalse(blockedRes.getBoolean("ok"))
      assertTrue(blockedRes.getString("error").contains("Unsupported", ignoreCase = true))

      val saveRes = JSONObject(bridge.executeWidgetAction("""{"action":"save_custom_data","key":"city","value":"Dhaka"}"""))
      assertTrue(saveRes.getBoolean("ok"))
      assertEquals("Dhaka", bridge.getCustomData("city"))

      // Invalid package launch is safely rejected
      val badAppRes = JSONObject(bridge.openApp("invalid package!"))
      assertFalse(badAppRes.getBoolean("ok"))

      // JS runtime errors flow cleanly into WidgetDiagnosticCenter
      bridge.reportJsError("ReferenceError: foo is not defined", "widget.js", 12)
      val diag = WidgetDiagnosticCenter.entries.value
      assertTrue(diag.any { it.category == "HTML_JS" && it.severity == DiagnosticSeverity.ERROR && it.widgetId == 77 })

      // Font-face CSS injection & WebView URL allowlisting
      val doc = NexusWidgetJsBridge.wrapHtmlDocument("<div>Hello</div>", 328, 160, transparentBg = true, assetsDir = assetsDir)
      assertTrue(doc.contains("@font-face"))
      assertTrue(doc.contains("font-family: 'NexusCustomMono'"))
      assertTrue(doc.contains("NexusRuntime"))
      assertTrue(NexusWidgetJsBridge.isSafeWebViewUrl("file://${assetsDir.absolutePath}/a.png"))
      assertTrue(NexusWidgetJsBridge.isSafeWebViewUrl("data:text/html,hi"))
      assertFalse(NexusWidgetJsBridge.isSafeWebViewUrl("https://untrusted.example.com/phish"))
    } finally {
      dummyFont.delete()
      dummyPhoto.delete()
    }
  }

  @Test
  fun `permission coordinator and widget engine registry handle runtime grants and built in providers cleanly`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val container = NexusContainer.get(app)
    val coordinator = container.widgetDataHub.permissionCoordinator

    assertEquals(CapabilityStatus.GRANTED, coordinator.statusFor(WidgetCapability.TIME))
    assertEquals(CapabilityStatus.GRANTED, coordinator.statusFor(WidgetCapability.BATTERY))
    assertEquals(CapabilityStatus.GRANTED, coordinator.statusFor(WidgetCapability.MEMORY))

    // Grant CALENDAR runtime permission via Robolectric ShadowApplication and verify CalendarDataProvider
    Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_CALENDAR)
    assertEquals(CapabilityStatus.GRANTED, coordinator.statusFor(WidgetCapability.CALENDAR))
    val calState = container.widgetDataHub.calendarProvider.fetch(app)
    assertTrue(calState is WidgetDataState.Available)

    // Register an extensible future widget descriptor without touching engine internals
    WidgetEngineRegistry.registerDescriptor(
      WidgetDescriptor(
        id = "calendar_agenda_future",
        title = "Upcoming Agenda",
        category = "Productivity",
        providerClass = NexusFavoritesWidgetProvider::class.java,
        requiredCapabilities = setOf(WidgetCapability.CALENDAR, WidgetCapability.TIME),
        renderMode = WidgetRenderMode.HTML_HEADLESS,
        recommendedSizePreset = WidgetSizePreset.MEDIUM,
      )
    )
    val found = WidgetEngineRegistry.findById("calendar_agenda_future")
    assertNotNull(found)
    assertTrue(WidgetEngineRegistry.missingCapabilitiesFor(found!!, coordinator).isEmpty())

    // Built-in widget regression safety sweep
    val mgr = AppWidgetManager.getInstance(app)
    NexusBatteryWidgetProvider.updateWidgets(app, mgr, intArrayOf(921))
    NexusSystemStatusWidgetProvider.updateWidgets(app, mgr, intArrayOf(922))
    NexusDeviceInfoWidgetProvider.updateWidgets(app, mgr, intArrayOf(923))
    NexusMusicWidgetProvider.updateWidgets(app, mgr, intArrayOf(924))
    NexusFavoritesWidgetProvider.updateWidgets(app, mgr, intArrayOf(925))
  }
}
