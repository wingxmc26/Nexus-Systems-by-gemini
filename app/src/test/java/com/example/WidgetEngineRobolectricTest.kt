package com.example

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.WidgetClockSerializer
import com.example.data.local.WidgetInstanceEntity
import com.example.data.local.WidgetProjectEntity
import com.example.data.model.BuiltInWidgetStyleConfig
import com.example.data.model.ClockFontChoice
import com.example.data.model.DepthClockConfig
import com.example.data.model.DepthClockMode
import com.example.data.model.VisualElementKind
import com.example.data.model.VisualWidgetElement
import com.example.data.model.VisualWidgetSpec
import com.example.di.NexusContainer
import android.content.Intent
import android.widget.FrameLayout
import android.widget.TextClock
import android.widget.ViewFlipper
import com.example.widget.AnimatedWidgetEngine
import com.example.widget.BuiltInWidgetStyleHelper
import com.example.widget.NexusBatteryWidgetProvider
import com.example.widget.NexusCustomWidgetProvider
import com.example.widget.NexusDepthClockWidgetProvider
import com.example.widget.NexusDeviceInfoWidgetProvider
import com.example.widget.NexusFavoritesWidgetProvider
import com.example.widget.NexusMusicWidgetProvider
import com.example.widget.NexusSystemStatusWidgetProvider
import com.example.widget.NexusWidgetAlarmScheduler
import com.example.widget.NexusWidgetJsBridge
import com.example.widget.NexusWidgetLiveTicker
import com.example.widget.NexusWidgetRefreshReceiver
import com.example.widget.WidgetBitmapRenderer
import com.example.widget.WidgetSizePreset
import com.example.widget.WidgetSizingEngine
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetEngineRobolectricTest {

  @Test
  fun `widget sizing engine calculates sharp native pixels for all presets and densities`() {
    val small = WidgetSizingEngine.computeRenderPixels(
      WidgetSizePreset.SMALL.widthDp,
      WidgetSizePreset.SMALL.heightDp,
      density = 2.0f,
    )
    assertEquals(320, small.widthPx)
    assertEquals(320, small.heightPx)

    val medium = WidgetSizingEngine.computeRenderPixels(
      WidgetSizePreset.MEDIUM.widthDp,
      WidgetSizePreset.MEDIUM.heightDp,
      density = 3.0f,
    )
    assertEquals(984, medium.widthPx)
    assertEquals(480, medium.heightPx)

    val iosClock = WidgetSizingEngine.computeRenderPixels(
      WidgetSizePreset.IOS_LARGE_CLOCK.widthDp,
      WidgetSizePreset.IOS_LARGE_CLOCK.heightDp,
      density = 3.5f,
    )
    assertEquals(1176, iosClock.widthPx)
    assertEquals(1176, iosClock.heightPx)

    val wide = WidgetSizingEngine.computeRenderPixels(
      WidgetSizePreset.WIDE.widthDp,
      WidgetSizePreset.WIDE.heightDp,
      density = 3.0f,
    )
    assertEquals(1080, wide.widthPx)
    assertEquals(324, wide.heightPx)

    // Extreme density caps long side safely at 1440px while preserving aspect ratio
    val extraDense = WidgetSizingEngine.computeRenderPixels(420, 210, density = 4.0f)
    assertEquals(1440, extraDense.widthPx)
    assertEquals(720, extraDense.heightPx)
  }

  @Test
  fun `dp aware visual and depth clock renderers produce sharp large 4x4 bitmaps with transparency and images`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val imgFile = File(context.cacheDir, "test_widget_photo.png")
    val srcPhoto = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply {
      eraseColor(Color.RED)
    }
    FileOutputStream(imgFile).use { fos ->
      srcPhoto.compress(Bitmap.CompressFormat.PNG, 100, fos)
    }
    srcPhoto.recycle()

    try {
      val spec = VisualWidgetSpec(
        isTransparent = true,
        sizePresetName = WidgetSizePreset.IOS_LARGE_CLOCK.name,
        targetWidthDp = 336,
        targetHeightDp = 336,
        containerPaddingDp = 16,
        refreshIntervalMinutes = 1,
        elements = listOf(
          VisualWidgetElement(
            id = "img_1",
            kind = VisualElementKind.IMAGE,
            xFraction = 0.05f,
            yFraction = 0.05f,
            widthFraction = 0.35f,
            heightFraction = 0.35f,
            cornerRadiusDp = 16,
            imageUriOrPath = imgFile.absolutePath,
            layerOrder = 0,
          ),
          VisualWidgetElement(
            id = "clk_large",
            kind = VisualElementKind.CLOCK,
            xFraction = 0.05f,
            yFraction = 0.45f,
            widthFraction = 0.90f,
            heightFraction = 0.35f,
            fontSizeSp = 48f,
            alignment = 1,
            layerOrder = 1,
          ),
        ),
      )

      val liveMap = mapOf(VisualElementKind.CLOCK to "09:41")
      val rendered = WidgetBitmapRenderer.renderVisualWidgetBitmapDp(
        spec = spec,
        liveValues = liveMap,
        widthDp = 336,
        heightDp = 336,
        density = 3.0f,
      )
      assertEquals(1008, rendered.width)
      assertEquals(1008, rendered.height)
      // Transparent background pixel in top-right outer corner
      assertEquals(Color.TRANSPARENT, rendered.getPixel(rendered.width - 2, 2))
      rendered.recycle()

      val largeDepthBmp = WidgetBitmapRenderer.renderDepthClockBitmapDp(
        config = DepthClockConfig(
          mode = DepthClockMode.DUAL,
          fontChoice = ClockFontChoice.INTER_BOLD,
          showDate = true,
        ),
        widthDp = 336,
        heightDp = 336,
        density = 3.0f,
      )
      assertEquals(1008, largeDepthBmp.width)
      assertEquals(1008, largeDepthBmp.height)
      largeDepthBmp.recycle()
    } finally {
      imgFile.delete()
    }
  }

  @Test
  fun `serializer and room dao support multiple independent widget instances without collision`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = NexusContainer.get(context).database
    val customDao = db.customWidgetDao()
    val instanceDao = db.widgetInstanceDao()

    val specA = VisualWidgetSpec(
      sizePresetName = WidgetSizePreset.SMALL.name,
      targetWidthDp = 160,
      targetHeightDp = 160,
      containerPaddingDp = 8,
      refreshIntervalMinutes = 15,
    )
    val specB = VisualWidgetSpec(
      sizePresetName = WidgetSizePreset.IOS_LARGE_CLOCK.name,
      targetWidthDp = 336,
      targetHeightDp = 336,
      containerPaddingDp = 18,
      refreshIntervalMinutes = 1,
      isTransparent = true,
    )

    val decodedB = WidgetClockSerializer.deserializeVisualWidget(WidgetClockSerializer.serializeVisualWidget(specB))
    assertEquals(336, decodedB.targetWidthDp)
    assertEquals(336, decodedB.targetHeightDp)
    assertEquals(18, decodedB.containerPaddingDp)
    assertEquals(1, decodedB.refreshIntervalMinutes)
    assertEquals(WidgetSizePreset.IOS_LARGE_CLOCK.name, decodedB.sizePresetName)

    val idA = customDao.insert(
      WidgetProjectEntity(
        name = "Compact Widget A",
        kind = "VISUAL",
        visualJson = WidgetClockSerializer.serializeVisualWidget(specA),
      )
    )
    val idB = customDao.insert(
      WidgetProjectEntity(
        name = "iOS Large Widget B",
        kind = "HTML",
        htmlCode = "<div>Large</div>",
        visualJson = WidgetClockSerializer.serializeVisualWidget(specB),
      )
    )

    instanceDao.upsert(WidgetInstanceEntity(appWidgetId = 101, projectId = idA, widthDp = 160, heightDp = 160))
    instanceDao.upsert(WidgetInstanceEntity(appWidgetId = 102, projectId = idB, widthDp = 336, heightDp = 336))

    assertEquals(idA, instanceDao.getByWidgetId(101)?.projectId)
    assertEquals(idB, instanceDao.getByWidgetId(102)?.projectId)

    instanceDao.deleteByWidgetId(101)
    assertNull(instanceDao.getByWidgetId(101))
    assertEquals(idB, instanceDao.getByWidgetId(102)?.projectId)

    customDao.deleteById(idA)
    customDao.deleteById(idB)
    instanceDao.deleteByWidgetId(102)
  }

  @Test
  fun `html widget bridge exports telemetry meta and imported local assets cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val assetsDir = File(context.filesDir, NexusWidgetJsBridge.ASSETS_DIR).apply { mkdirs() }
    val dummyAsset = File(assetsDir, "sample_badge.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

    try {
      val bridge = NexusWidgetJsBridge(context, widthDp = 336, heightDp = 336, density = 3.0f)
      val meta = JSONObject(bridge.getWidgetMetaJson())
      assertEquals(336, meta.getInt("widthDp"))
      assertEquals(336, meta.getInt("heightDp"))

      val telemetry = JSONObject(bridge.getAllTelemetryJson())
      assertTrue(telemetry.has("time24"))
      assertTrue(telemetry.has("batteryPercent"))
      assertTrue(telemetry.has("deviceModel"))

      val assetsArr = JSONArray(bridge.getAssetsJson())
      assertTrue((0 until assetsArr.length()).any { assetsArr.getJSONObject(it).getString("name") == "sample_badge.png" })

      val wrapped = NexusWidgetJsBridge.wrapHtmlDocument("<div id='ok'>Hi</div>", widthDp = 336, heightDp = 336, transparentBg = true)
      assertTrue(wrapped.contains("width=336"))
      assertTrue(wrapped.contains("background: transparent"))
    } finally {
      dummyAsset.delete()
    }
  }

  @Test
  fun `deliver widget image writes large high dpi bitmap cleanly and built in customizers persist and run safely`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val largeBmp = Bitmap.createBitmap(960, 960, Bitmap.Config.ARGB_8888)
    val rv = RemoteViews(context.packageName, R.layout.widget_custom_studio)
    val savedPath = WidgetSizingEngine.deliverWidgetImage(
      context = context,
      views = rv,
      imageViewId = R.id.widget_custom_image,
      bitmap = largeBmp,
      cacheKey = "test_large",
      preferUriDelivery = true,
    )
    largeBmp.recycle()
    assertNotNull(savedPath)
    assertTrue(File(savedPath!!).exists())
    WidgetSizingEngine.deleteCachedRendersForKey(context, "test_large")
    assertFalse(File(savedPath).exists())

    // Built-in widget style persistence & provider safety
    val prefs = NexusContainer.get(context).preferencesRepository
    val customStyle = BuiltInWidgetStyleConfig(
      backgroundColorArgb = 0xFF10131CL,
      opacity = 0.72f,
      accentColorArgb = 0xFF38BDF8L,
      textColorArgb = 0xFFFFFFFFL,
      fontSizeScale = 1.2f,
      fontWeightBold = false,
      paddingDp = 18,
      alignment = 1,
      showSecondaryDetails = false,
      isCustomized = true,
    )
    prefs.saveBuiltInWidgetStyle("battery", customStyle)
    val loadedStyle = prefs.getBuiltInWidgetStyle("battery")
    assertTrue(loadedStyle.isCustomized)
    assertEquals(0.72f, loadedStyle.opacity, 0.01f)
    assertEquals(18, loadedStyle.paddingDp)
    assertEquals(1, loadedStyle.alignment)
    assertFalse(loadedStyle.showSecondaryDetails)

    val cardBg = BuiltInWidgetStyleHelper.renderRoundedCardBackground(loadedStyle)
    assertEquals(360, cardBg.width)
    cardBg.recycle()

    val mgr = AppWidgetManager.getInstance(context)
    NexusBatteryWidgetProvider.updateWidgets(context, mgr, intArrayOf(901))
    NexusSystemStatusWidgetProvider.updateWidgets(context, mgr, intArrayOf(902))
    NexusDeviceInfoWidgetProvider.updateWidgets(context, mgr, intArrayOf(903))
    NexusMusicWidgetProvider.updateWidgets(context, mgr, intArrayOf(904))
    NexusFavoritesWidgetProvider.updateWidgets(context, mgr, intArrayOf(905))

    prefs.resetBuiltInWidgetStyle("battery")
    assertFalse(prefs.getBuiltInWidgetStyle("battery").isCustomized)
  }

  @Test
  fun `clock widget alarm aligns to exact minute boundary and providers inflate live TextClock cleanly`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val nowMs = 1_700_000_012_345L
    val nextTrigger = NexusWidgetAlarmScheduler.computeNextTriggerMs(nowMs, intervalMinutes = 1)
    assertEquals(((nowMs / 60_000L) + 1L) * 60_000L + 150L, nextTrigger)

    NexusWidgetLiveTicker.ensureRegistered(context)
    assertTrue(NexusWidgetLiveTicker.isRegistered())

    NexusWidgetRefreshReceiver().onReceive(context, Intent(NexusWidgetAlarmScheduler.ACTION_PERIODIC_WIDGET_REFRESH))
    NexusWidgetRefreshReceiver().onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))

    val mgr = AppWidgetManager.getInstance(context)
    val prefs = NexusContainer.get(context).preferencesRepository
    prefs.saveDepthClockJson(
      WidgetClockSerializer.serializeDepthClock(
        DepthClockConfig(mode = DepthClockMode.DIGITAL, showSeconds = false, showDate = true)
      )
    )
    NexusDepthClockWidgetProvider.updateWidgets(context, mgr, intArrayOf(910))
    prefs.saveDepthClockJson(
      WidgetClockSerializer.serializeDepthClock(
        DepthClockConfig(mode = DepthClockMode.DUAL, showSeconds = true, showDate = true)
      )
    )
    NexusDepthClockWidgetProvider.updateWidgets(context, mgr, intArrayOf(911))

    // Verify custom Visual Clock widget inflates live TextClock without ActionException
    val db = NexusContainer.get(context).database
    val clockSpec = VisualWidgetSpec(
      sizePresetName = WidgetSizePreset.MEDIUM.name,
      targetWidthDp = 328,
      targetHeightDp = 160,
      enableAnimation = false,
      elements = listOf(
        VisualWidgetElement(
          id = "live_clk",
          kind = VisualElementKind.CLOCK,
          xFraction = 0.05f,
          yFraction = 0.15f,
          widthFraction = 0.90f,
          heightFraction = 0.45f,
          fontSizeSp = 36f,
          alignment = 1,
        ),
        VisualWidgetElement(
          id = "live_dt",
          kind = VisualElementKind.DATE,
          xFraction = 0.05f,
          yFraction = 0.65f,
          widthFraction = 0.90f,
          heightFraction = 0.25f,
          fontSizeSp = 14f,
          alignment = 1,
        ),
      ),
    )
    val serializedClock = WidgetClockSerializer.serializeVisualWidget(clockSpec)
    val projId = db.customWidgetDao().insert(
      WidgetProjectEntity(
        name = "Live TextClock Widget",
        kind = "VISUAL",
        visualJson = serializedClock,
        isActiveHomeWidget = true,
      )
    )
    db.widgetInstanceDao().upsert(WidgetInstanceEntity(appWidgetId = 912, projectId = projId, widthDp = 328, heightDp = 160))
    NexusCustomWidgetProvider.updateWidgets(context, mgr, intArrayOf(912))

    // Verify serializer preserves animation flags cleanly
    val roundTrip = WidgetClockSerializer.deserializeVisualWidget(
      WidgetClockSerializer.serializeVisualWidget(clockSpec.copy(enableAnimation = true, animationFrameMs = 140))
    )
    assertTrue(roundTrip.enableAnimation)
    assertEquals(140, roundTrip.animationFrameMs)

    db.widgetInstanceDao().deleteByWidgetId(912)
    db.customWidgetDao().deleteById(projId)
  }

  @Test
  fun `animated widget engine detects animated html and gifs and inflates ViewFlipper cleanly`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertTrue(AnimatedWidgetEngine.detectAnimatedHtml("", "@keyframes pulse { 0% { opacity: 0; } }", ""))
    assertTrue(AnimatedWidgetEngine.detectAnimatedHtml("<canvas id='c'></canvas>", "", "requestAnimationFrame(draw);"))
    assertFalse(AnimatedWidgetEngine.detectAnimatedHtml("<div>Static</div>", ".card { color: red; }", "var x = 1;"))

    // Build minimal valid 2-frame GIF89a image
    val gifBytes = byteArrayOf(
      0x47, 0x49, 0x46, 0x38, 0x39, 0x61, // GIF89a
      0x02, 0x00, 0x02, 0x00, // 2x2
      0x80.toByte(), 0x00, 0x00, // GCT flag, 2 colors
      0x00, 0x00, 0x00, // black
      0xFF.toByte(), 0x00, 0x00, // red
      0x21, 0xFF.toByte(), 0x0B, // NETSCAPE2.0 loop extension
      0x4E, 0x45, 0x54, 0x53, 0x43, 0x41, 0x50, 0x45, 0x32, 0x2E, 0x30,
      0x03, 0x01, 0x00, 0x00, 0x00,
      0x21, 0xF9.toByte(), 0x04, 0x04, 0x0F, 0x00, 0x00, 0x00, // GCE frame 1 (150ms)
      0x2C, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x02, 0x00, 0x00,
      0x02, 0x02, 0x84.toByte(), 0x51, 0x00,
      0x21, 0xF9.toByte(), 0x04, 0x04, 0x0F, 0x00, 0x00, 0x00, // GCE frame 2 (150ms)
      0x2C, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x02, 0x00, 0x00,
      0x02, 0x02, 0x8C.toByte(), 0x51, 0x00,
      0x3B, // trailer
    )
    val gifFile = File(context.cacheDir, "anim_test.gif").apply { writeBytes(gifBytes) }
    try {
      assertTrue(AnimatedWidgetEngine.isAnimatedGifFile(gifFile.absolutePath))
      val rv = RemoteViews(context.packageName, R.layout.widget_custom_studio)
      val f0 = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
      val f1 = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
      val inlinePaths = AnimatedWidgetEngine.deliverAnimatedFrames(
        context = context,
        views = rv,
        flipperViewId = R.id.widget_custom_flipper,
        frames = listOf(f0, f1),
        frameIntervalMs = 150,
        cacheKey = "anim_unit_test",
      )
      f0.recycle()
      f1.recycle()
      assertEquals(0, inlinePaths.size)

      // Inflate RemoteViews into a host ViewGroup to verify ViewFlipper & TextClock actions are valid
      val host = FrameLayout(context)
      val inflated = rv.apply(context, host)
      val flipper = inflated.findViewById<ViewFlipper>(R.id.widget_custom_flipper)
      val textClock = inflated.findViewById<TextClock>(R.id.widget_custom_live_clock_bold)
      assertNotNull(flipper)
      assertNotNull(textClock)
      assertEquals(2, flipper.childCount)

      // Also verify large HD frames spill cleanly to disk FileProvider URIs
      val rvHd = RemoteViews(context.packageName, R.layout.widget_custom_studio)
      val hd0 = Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888)
      val hd1 = Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888)
      val uriPaths = AnimatedWidgetEngine.deliverAnimatedFrames(
        context = context,
        views = rvHd,
        flipperViewId = R.id.widget_custom_flipper,
        frames = listOf(hd0, hd1),
        frameIntervalMs = 150,
        cacheKey = "anim_hd_test",
      )
      hd0.recycle()
      hd1.recycle()
      assertEquals(2, uriPaths.size)
      assertTrue(uriPaths.all { File(it).exists() })

      WidgetSizingEngine.deleteCachedRendersForKey(context, "anim_unit_test")
      WidgetSizingEngine.deleteCachedRendersForKey(context, "anim_hd_test")
    } finally {
      gifFile.delete()
    }
  }
}
