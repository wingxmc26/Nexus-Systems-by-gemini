package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.WidgetClockSerializer
import com.example.data.model.BatteryTelemetry
import com.example.data.model.ClockFontChoice
import com.example.data.model.DepthClockConfig
import com.example.data.model.DepthClockMode
import com.example.data.model.MetricValue
import com.example.data.model.VisualElementKind
import com.example.data.model.VisualWidgetElement
import com.example.data.model.VisualWidgetSpec
import com.example.data.system.BatteryDataSource
import com.example.data.system.LiveWallpaperDataSource
import com.example.data.system.PermissionManager
import com.example.data.system.RamManagerDataSource
import com.example.data.system.ShortcutsDataSource
import com.example.data.system.StorageDataSource
import com.example.di.NexusContainer
import com.example.util.Formatters
import com.example.widget.WidgetBitmapRenderer
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `app name matches NEXUS SYSTEM`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("NEXUS SYSTEM", appName)
  }

  @Test
  fun `formatters produce accurate units`() {
    assertEquals("1.00 KB", Formatters.formatBytes(1024L))
    assertEquals("1h 1m 1s", Formatters.formatDuration(3661_000L))
    assertEquals("01:01:01", Formatters.formatTimer(3661_000L))
    assertEquals("2.40 GHz", Formatters.formatFrequencyKhz(2_400_000L))
    assertEquals("+1250 mA", Formatters.formatCurrentMa(1_250_000, isCharging = true))
    assertEquals("-450 mA", Formatters.formatCurrentMa(-450_000, isCharging = false))
    assertEquals("23 minutes", Formatters.formatDurationWords(23 * 60_000L))
    assertEquals("1 hour 5 minutes", Formatters.formatDurationWords(65 * 60_000L))
    val cal = java.util.Calendar.getInstance(java.util.Locale.US).apply {
      set(java.util.Calendar.HOUR_OF_DAY, 20)
      set(java.util.Calendar.MINUTE, 30)
      set(java.util.Calendar.SECOND, 0)
    }
    assertEquals(
      "Charging 67-89% in 23 minutes at 8:30 PM",
      Formatters.formatChargingSessionLog(67, 89, 23 * 60_000L, cal.timeInMillis, isActive = false),
    )
  }

  @Test
  fun `data sources read safely without crashing`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val battery = BatteryDataSource(context).readBatterySnapshot()
    assertTrue(battery.statusLabel.isNotBlank())

    val controls = ShortcutsDataSource(context).loadControls()
    assertTrue(controls.isNotEmpty())

    val ram = RamManagerDataSource(context).readSystemMemoryDetails()
    assertTrue(ram.totalRamBytes >= 0L)

    val storage = StorageDataSource(context).readStorageOverview()
    assertTrue(storage.internalTotalBytes >= 0L)

    val wp = LiveWallpaperDataSource(context)
    assertNotNull(wp.getWallpaperFile())
    assertNotNull(wp.buildSetLiveWallpaperIntents())

    val perms = PermissionManager(context).loadAuditSnapshot()
    assertEquals(12, perms.size)
  }

  @Test
  fun `battery session repository tracks charging start and stop`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NexusContainer.get(context).batterySessionRepository
    repo.clearSessions()

    val startTs = 1_700_000_000_000L
    repo.processBatterySnapshot(
      BatteryTelemetry(
        percentage = MetricValue.Available(42),
        statusLabel = "Charging",
        isCharging = true,
        chargeSource = "USB",
        timestampMs = startTs,
      )
    )
    val active = repo.activeSession.first()
    assertNotNull(active)
    assertEquals(42, active?.startPercentage)
    assertEquals("USB", active?.chargeSource)

    repo.processBatterySnapshot(
      BatteryTelemetry(
        percentage = MetricValue.Available(68),
        statusLabel = "Discharging",
        isCharging = false,
        chargeSource = "Unplugged",
        timestampMs = startTs + 30_000L,
      )
    )
    val sessions = repo.sessions.first()
    assertTrue(sessions.isNotEmpty())
    assertFalse(sessions.first().isActive)
    assertEquals(68, sessions.first().endPercentage)
  }

  @Test
  fun `wallpaper preferences persist local path directly for wallpaper service`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = NexusContainer.get(context).preferencesRepository
    repo.setWallpaperSelection(
      activeId = 7L,
      fileName = "demo.mp4",
      mediaType = com.example.data.model.WallpaperMediaType.VIDEO,
      localPath = "/data/user/0/demo/files/wallpapers/wp_demo.mp4",
    )
    val snap = repo.wallpaperConfig.first()
    assertEquals(7L, snap.activeWallpaperId)
    assertEquals("/data/user/0/demo/files/wallpapers/wp_demo.mp4", snap.activeLocalPath)
    assertTrue(snap.hasLocalVideo)
    repo.setWallpaperSelection(0L, null, com.example.data.model.WallpaperMediaType.VIDEO, null)
  }

  @Test
  fun `widget and depth clock serializers and bitmap renderers stay Binder-safe`() {
    val spec = VisualWidgetSpec(
      backgroundColorArgb = 0xFF101218L,
      isTransparent = true,
      htmlTransparent = true,
      elements = listOf(
        VisualWidgetElement(
          id = "clk_1",
          kind = VisualElementKind.CLOCK,
          label = "Clock",
          xFraction = 0.08f,
          yFraction = 0.12f,
        ),
        VisualWidgetElement(
          id = "bat_1",
          kind = VisualElementKind.BATTERY,
          label = "Battery",
          xFraction = 0.08f,
          yFraction = 0.55f,
        ),
      ),
    )
    val rawSpec = WidgetClockSerializer.serializeVisualWidget(spec)
    val decodedSpec = WidgetClockSerializer.deserializeVisualWidget(rawSpec)
    assertEquals(2, decodedSpec.elements.size)
    assertTrue(decodedSpec.isTransparent)
    assertTrue(decodedSpec.htmlTransparent)
    assertEquals(VisualElementKind.CLOCK, decodedSpec.elements[0].kind)

    val clockCfg = DepthClockConfig(
      mode = DepthClockMode.DUAL,
      fontChoice = ClockFontChoice.INTER_BOLD,
      showSeconds = true,
    )
    val rawClock = WidgetClockSerializer.serializeDepthClock(clockCfg)
    val decodedClock = WidgetClockSerializer.deserializeDepthClock(rawClock)
    assertEquals(DepthClockMode.DUAL, decodedClock.mode)
    assertTrue(decodedClock.showSeconds)

    val liveMap = mapOf(
      VisualElementKind.CLOCK to "12:30",
      VisualElementKind.BATTERY to "Battery 88%",
    )
    val widgetBmp = WidgetBitmapRenderer.renderVisualWidgetBitmap(decodedSpec, liveMap, widthPx = 440, heightPx = 380)
    assertTrue(widgetBmp.allocationByteCount < 650_000)
    assertTrue(widgetBmp.height <= 460)
    widgetBmp.recycle()

    val clockBmp = WidgetBitmapRenderer.renderDepthClockBitmap(decodedClock)
    assertTrue(clockBmp.allocationByteCount < 650_000)
    clockBmp.recycle()
  }

  @Test
  fun `storage zip compression and zip-slip protection behave safely`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val ds = StorageDataSource(context)
    val tempRoot = File(context.cacheDir, "zip_test_${System.currentTimeMillis()}").apply { mkdirs() }
    try {
      val sample = File(tempRoot, "hello.txt").apply { writeText("nexus-system-offline") }
      val outZip = File(tempRoot, "bundle.zip")
      val zipRes = ds.compressFilesToZip(listOf(sample.absolutePath), outZip.absolutePath)
      assertTrue(zipRes.isSuccess)
      assertTrue(outZip.exists())

      val extractRes = ds.extractZipArchive(outZip.absolutePath)
      assertTrue(extractRes.isSuccess)
      val extractedFile = File(extractRes.getOrThrow(), "hello.txt")
      assertTrue(extractedFile.exists())
      assertEquals("nexus-system-offline", extractedFile.readText())

      // Verify malicious traversal path is rejected
      val maliciousZip = File(tempRoot, "bad.zip")
      ZipOutputStream(FileOutputStream(maliciousZip)).use { zos ->
        zos.putNextEntry(ZipEntry("../../outside.txt"))
        zos.write("blocked".toByteArray())
        zos.closeEntry()
      }
      val badRes = ds.extractZipArchive(maliciousZip.absolutePath)
      assertTrue(badRes.isFailure)
    } finally {
      tempRoot.deleteRecursively()
    }
  }

  @Test
  fun `main navigation tabs and updated battery log behave without crashes`() {
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("nav_dashboard").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_battery").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("battery_hero_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("log_battery_snapshot_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("battery_history_controls").assertDoesNotExist()

    composeTestRule.onNodeWithTag("nav_applock").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("app_lock_list").assertIsDisplayed()

    composeTestRule.onNodeWithTag("nav_utilities").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("hub_battery").assertIsDisplayed()
    composeTestRule.onNodeWithTag("utilities_hub_list").performScrollToNode(hasTestTag("hub_wallpaper"))
    composeTestRule.onNodeWithTag("hub_wallpaper").assertIsDisplayed()
    composeTestRule.onNodeWithTag("utilities_hub_list").performScrollToNode(hasTestTag("hub_widget_studio"))
    composeTestRule.onNodeWithTag("hub_widget_studio").assertIsDisplayed()
  }
}
