package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.WidgetClockSerializer
import com.example.data.local.WidgetInstanceEntity
import com.example.data.local.WidgetProjectEntity
import com.example.data.model.VisualElementKind
import com.example.data.model.VisualWidgetElement
import com.example.data.model.VisualWidgetSpec
import com.example.di.NexusContainer
import com.example.util.NexusBitmapUtils
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NexusCustomWidgetProvider : AppWidgetProvider() {

  override fun onEnabled(context: Context) {
    super.onEnabled(context)
    NexusWidgetLiveTicker.ensureRegistered(context.applicationContext)
  }

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    NexusWidgetLiveTicker.ensureRegistered(context.applicationContext)
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        updateWidgets(context, appWidgetManager, appWidgetIds)
      } finally {
        pendingResult.finish()
      }
    }
  }

  override fun onAppWidgetOptionsChanged(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
    newOptions: Bundle,
  ) {
    onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
  }

  override fun onDeleted(context: Context, appWidgetIds: IntArray) {
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val container = runCatching { NexusContainer.get(context) }.getOrNull()
        val instanceDao = container?.database?.widgetInstanceDao()
        appWidgetIds.forEach { widgetId ->
          runCatching { instanceDao?.deleteByWidgetId(widgetId) }
          WidgetSizingEngine.deleteCachedRendersForKey(context, "custom_$widgetId")
        }
        NexusWidgetAlarmScheduler.rescheduleIfActive(context)
        NexusWidgetSyncJobService.cancelIfNoActiveWidgets(context)
      } finally {
        pendingResult.finish()
      }
    }
  }

  companion object {
    private val LIVE_OVERLAY_IDS = listOf(
      R.id.widget_custom_live_clock_bold,
      R.id.widget_custom_live_clock_reg,
      R.id.widget_custom_live_date_bold,
      R.id.widget_custom_live_date_reg,
    )

    suspend fun updateWidgets(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray,
    ) {
      NexusWidgetLiveTicker.ensureRegistered(context.applicationContext)
      val container = runCatching { NexusContainer.get(context) }.getOrNull()
      val customDao = container?.database?.customWidgetDao()
      val instanceDao = container?.database?.widgetInstanceDao()
      val fallbackProject = runCatching { customDao?.getActiveHomeWidget() }.getOrNull()
      val liveMap = runCatching { WidgetBitmapRenderer.buildLiveValuesMap(context) }.getOrDefault(emptyMap())
      val density = context.resources.displayMetrics.density

      appWidgetIds.forEach { widgetId ->
        var renderedBmp: Bitmap? = null
        val animBitmaps = mutableListOf<Bitmap>()
        runCatching {
          val boundInstance = runCatching { instanceDao?.getByWidgetId(widgetId) }.getOrNull()
          val project: WidgetProjectEntity? = if (boundInstance != null) {
            runCatching { customDao?.getById(boundInstance.projectId) }.getOrNull() ?: fallbackProject
          } else {
            fallbackProject
          }

          val spec = if (project != null) {
            runCatching { WidgetClockSerializer.deserializeVisualWidget(project.visualJson) }.getOrNull()
          } else null

          val slot = WidgetSizingEngine.resolveWidgetSlotDp(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetId = widgetId,
            fallbackWidthDp = spec?.targetWidthDp ?: 328,
            fallbackHeightDp = spec?.targetHeightDp ?: 160,
          )

          if (project != null) {
            val openIntent = Intent(context, MainActivity::class.java).apply {
              putExtra("route", "widget_studio")
              putExtra("widget_id", widgetId)
              putExtra("project_id", project.id)
              flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
              context,
              10_000 + widgetId,
              openIntent,
              PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val nativeClockEl = if (project.kind == "VISUAL" && spec != null) {
              findLiveTextClockCandidate(spec, VisualElementKind.CLOCK)
            } else null
            val nativeDateEl = if (project.kind == "VISUAL" && spec != null) {
              findLiveTextClockCandidate(spec, VisualElementKind.DATE)
            } else null
            val skipTextKinds = buildSet {
              if (nativeClockEl != null) add(VisualElementKind.CLOCK)
              if (nativeDateEl != null) add(VisualElementKind.DATE)
            }

            var flipIntervalMs = spec?.animationFrameMs?.coerceIn(80, 1000) ?: 180
            if (project.kind == "VISUAL" && spec != null) {
              val hasGif = AnimatedWidgetEngine.hasAnyAnimatedImage(spec)
              if (hasGif) {
                val gifSeqMap = spec.elements
                  .filter { it.visible && it.kind == VisualElementKind.IMAGE && AnimatedWidgetEngine.isAnimatedGifFile(it.imageUriOrPath) }
                  .mapNotNull { el ->
                    AnimatedWidgetEngine.extractGifFrames(el.imageUriOrPath!!, maxFrames = 10, maxSidePx = 420)
                      ?.let { seq -> el.id to seq }
                  }
                  .toMap()
                val maxFramesCount = gifSeqMap.values.maxOfOrNull { it.frames.size } ?: 0
                if (maxFramesCount >= 2) {
                  flipIntervalMs = gifSeqMap.values.firstOrNull()?.frameIntervalMs ?: flipIntervalMs
                  val animDensity = density.coerceAtMost(2.0f)
                  for (fIdx in 0 until maxFramesCount) {
                    val overrides = gifSeqMap.mapValues { (_, seq) ->
                      seq.frames[fIdx % seq.frames.size]
                    }
                    animBitmaps.add(
                      WidgetBitmapRenderer.renderVisualWidgetBitmapDp(
                        spec = spec,
                        liveValues = liveMap,
                        widthDp = slot.widthDp,
                        heightDp = slot.heightDp,
                        density = animDensity,
                        skipTextKinds = skipTextKinds,
                        overrideImageBitmaps = overrides,
                      )
                    )
                  }
                }
                gifSeqMap.values.forEach { seq ->
                  seq.frames.forEach { if (!it.isRecycled) it.recycle() }
                }
              }

              if (animBitmaps.size < 2) {
                renderedBmp = WidgetBitmapRenderer.renderVisualWidgetBitmapDp(
                  spec = spec,
                  liveValues = liveMap,
                  widthDp = slot.widthDp,
                  heightDp = slot.heightDp,
                  density = density,
                  skipTextKinds = skipTextKinds,
                )
              }
            } else {
              val combinedHtml = buildCombinedHtml(
                context = context,
                project = project,
                widthDp = slot.widthDp,
                heightDp = slot.heightDp,
                spec = spec,
              )
              val isAnimatedHtml = spec?.enableAnimation == true ||
                AnimatedWidgetEngine.detectAnimatedHtml(project.htmlCode, project.cssCode, project.jsCode)

              if (isAnimatedHtml) {
                val captured = HeadlessHtmlWidgetRenderer.renderHtmlAnimationFrames(
                  context = context,
                  rawHtml = combinedHtml,
                  widthDp = slot.widthDp,
                  heightDp = slot.heightDp,
                  density = density,
                  transparentBg = spec?.htmlTransparent ?: true,
                  frameCount = 8,
                  intervalMs = flipIntervalMs,
                  widgetId = widgetId,
                )
                if (captured.size >= 2) {
                  animBitmaps.addAll(captured)
                } else {
                  captured.firstOrNull()?.let { renderedBmp = it }
                }
              }

              if (animBitmaps.size < 2 && renderedBmp == null) {
                val headless = HeadlessHtmlWidgetRenderer.renderHtmlToBitmap(
                  context = context,
                  rawHtml = combinedHtml,
                  widthDp = slot.widthDp,
                  heightDp = slot.heightDp,
                  density = density,
                  transparentBg = spec?.htmlTransparent ?: true,
                  widgetId = widgetId,
                )
                if (headless != null) {
                  renderedBmp = headless
                } else {
                  val path = project.renderedBitmapPath
                  if (!path.isNullOrBlank() && File(path).exists()) {
                    val reqPx = WidgetSizingEngine.computeRenderPixels(slot.widthDp, slot.heightDp, density)
                    NexusBitmapUtils.decodeSampledBitmap(
                      path,
                      reqPx.widthPx,
                      reqPx.heightPx,
                      applyExifOrientation = false,
                    )?.let { renderedBmp = it }
                  }
                }
              }
            }

            var cachedFilePath: String? = null
            val views = RemoteViews(context.packageName, R.layout.widget_custom_studio).apply {
              LIVE_OVERLAY_IDS.forEach { setViewVisibility(it, View.GONE) }

              if (animBitmaps.size >= 2) {
                setViewVisibility(R.id.widget_custom_image, View.GONE)
                setViewVisibility(R.id.widget_custom_placeholder, View.GONE)
                setViewVisibility(R.id.widget_custom_flipper, View.VISIBLE)
                val paths = AnimatedWidgetEngine.deliverAnimatedFrames(
                  context = context,
                  views = this,
                  flipperViewId = R.id.widget_custom_flipper,
                  frames = animBitmaps,
                  frameIntervalMs = flipIntervalMs,
                  cacheKey = "custom_$widgetId",
                )
                cachedFilePath = paths.firstOrNull()
              } else {
                setViewVisibility(R.id.widget_custom_flipper, View.GONE)
                val singleBmp = renderedBmp
                if (singleBmp != null && !singleBmp.isRecycled) {
                  setViewVisibility(R.id.widget_custom_image, View.VISIBLE)
                  setViewVisibility(R.id.widget_custom_placeholder, View.GONE)
                  cachedFilePath = WidgetSizingEngine.deliverWidgetImage(
                    context = context,
                    views = this,
                    imageViewId = R.id.widget_custom_image,
                    bitmap = singleBmp,
                    cacheKey = "custom_$widgetId",
                  )
                } else {
                  setViewVisibility(R.id.widget_custom_image, View.GONE)
                  setViewVisibility(R.id.widget_custom_placeholder, View.VISIBLE)
                  setTextViewText(R.id.widget_custom_title, project.name)
                  setTextViewText(R.id.widget_custom_subtitle, "Tap to preview & activate widget")
                }
              }

              if (spec != null && (animBitmaps.size >= 2 || renderedBmp != null)) {
                nativeClockEl?.let { el ->
                  val viewId = if (el.fontWeightBold) {
                    R.id.widget_custom_live_clock_bold
                  } else {
                    R.id.widget_custom_live_clock_reg
                  }
                  applyLiveTextClockOverlay(this, viewId, spec, el, slot.widthDp, slot.heightDp, density, "HH:mm", 5.2f)
                }
                nativeDateEl?.let { el ->
                  val viewId = if (el.fontWeightBold) {
                    R.id.widget_custom_live_date_bold
                  } else {
                    R.id.widget_custom_live_date_reg
                  }
                  applyLiveTextClockOverlay(this, viewId, spec, el, slot.widthDp, slot.heightDp, density, "EEE, MMM dd", 11.2f)
                }
              }

              setOnClickPendingIntent(R.id.widget_custom_root, pendingIntent)
            }
            appWidgetManager.updateAppWidget(widgetId, views)

            runCatching {
              instanceDao?.upsert(
                WidgetInstanceEntity(
                  appWidgetId = widgetId,
                  projectId = project.id,
                  widthDp = slot.widthDp,
                  heightDp = slot.heightDp,
                  cachedBitmapPath = cachedFilePath ?: boundInstance?.cachedBitmapPath,
                  updatedAtMs = System.currentTimeMillis(),
                ),
              )
            }
          } else {
            val openIntent = Intent(context, MainActivity::class.java).apply {
              putExtra("route", "widget_studio")
              putExtra("widget_id", widgetId)
              flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
              context,
              10_000 + widgetId,
              openIntent,
              PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_custom_studio).apply {
              LIVE_OVERLAY_IDS.forEach { setViewVisibility(it, View.GONE) }
              setViewVisibility(R.id.widget_custom_flipper, View.GONE)
              setViewVisibility(R.id.widget_custom_image, View.GONE)
              setViewVisibility(R.id.widget_custom_placeholder, View.VISIBLE)
              setTextViewText(R.id.widget_custom_title, "NEXUS WIDGET STUDIO")
              setTextViewText(
                R.id.widget_custom_subtitle,
                "Design & activate a custom widget in Nexus System",
              )
              setOnClickPendingIntent(R.id.widget_custom_root, pendingIntent)
            }
            appWidgetManager.updateAppWidget(widgetId, views)
          }
        }
        renderedBmp?.let { if (!it.isRecycled) it.recycle() }
        animBitmaps.forEach { if (!it.isRecycled) it.recycle() }
      }
      runCatching { NexusWidgetAlarmScheduler.rescheduleIfActive(context) }
    }

    private fun findLiveTextClockCandidate(
      spec: VisualWidgetSpec,
      kind: VisualElementKind,
    ): VisualWidgetElement? {
      val matches = spec.elements.filter { it.visible && it.kind == kind }
      if (matches.size != 1) return null
      val target = matches.first()
      if (target.label.isNotBlank()) return null

      // Avoid native TextClock overlay if an opaque higher-layer element visually covers it.
      val tRight = target.xFraction + target.widthFraction
      val tBottom = target.yFraction + target.heightFraction
      val occluded = spec.elements.any { other ->
        other.visible &&
          other.id != target.id &&
          other.layerOrder > target.layerOrder &&
          (other.kind == VisualElementKind.IMAGE || (other.backgroundColorArgb ushr 24) >= 160L) &&
          other.xFraction < tRight &&
          (other.xFraction + other.widthFraction) > target.xFraction &&
          other.yFraction < tBottom &&
          (other.yFraction + other.heightFraction) > target.yFraction
      }
      return if (occluded) null else target
    }

    private fun applyLiveTextClockOverlay(
      views: RemoteViews,
      viewId: Int,
      spec: VisualWidgetSpec,
      el: VisualWidgetElement,
      slotWidthDp: Int,
      slotHeightDp: Int,
      density: Float,
      formatPattern: String,
      estChars: Float,
    ) {
      views.setViewVisibility(viewId, View.VISIBLE)
      runCatching {
        views.setCharSequence(viewId, "setFormat12Hour", formatPattern)
        views.setCharSequence(viewId, "setFormat24Hour", formatPattern)
      }
      views.setTextColor(viewId, el.textColorArgb.toInt())

      val gravity = when (el.alignment) {
        1 -> Gravity.CENTER
        2 -> Gravity.END or Gravity.CENTER_VERTICAL
        else -> Gravity.START or Gravity.CENTER_VERTICAL
      }
      runCatching { views.setInt(viewId, "setGravity", gravity) }

      val containerPadDp = spec.containerPaddingDp.toFloat().coerceIn(0f, min(slotWidthDp, slotHeightDp) * 0.25f)
      val innerWDp = (slotWidthDp - containerPadDp * 2f).coerceAtLeast(16f)
      val innerHDp = (slotHeightDp - containerPadDp * 2f).coerceAtLeast(16f)

      val specW = spec.targetWidthDp.coerceAtLeast(100).toFloat()
      val specH = spec.targetHeightDp.coerceAtLeast(80).toFloat()
      val resizeRatio = min(slotWidthDp.toFloat() / specW, slotHeightDp.toFloat() / specH).coerceIn(0.65f, 1.85f)

      val leftDp = containerPadDp + el.xFraction.coerceIn(0f, 0.96f) * innerWDp
      val topDp = containerPadDp + el.yFraction.coerceIn(0f, 0.96f) * innerHDp
      val elWDp = (el.widthFraction.coerceIn(0.05f, 1f) * innerWDp).coerceAtMost(containerPadDp + innerWDp - leftDp)
      val elHDp = (el.heightFraction.coerceIn(0.05f, 1f) * innerHDp).coerceAtMost(containerPadDp + innerHDp - topDp)
      val elPadDp = (el.paddingDp * resizeRatio).coerceIn(0f, min(elWDp, elHDp) * 0.35f)

      val leftPx = ((leftDp + elPadDp) * density).roundToInt().coerceAtLeast(0)
      val topPx = ((topDp + elPadDp) * density).roundToInt().coerceAtLeast(0)
      val rightPx = (((slotWidthDp - (leftDp + elWDp)) + elPadDp) * density).roundToInt().coerceAtLeast(0)
      val bottomPx = (((slotHeightDp - (topDp + elHDp)) + elPadDp) * density).roundToInt().coerceAtLeast(0)
      views.setViewPadding(viewId, leftPx, topPx, rightPx, bottomPx)

      val availWDp = (elWDp - elPadDp * 2f).coerceAtLeast(8f)
      val availHDp = (elHDp - elPadDp * 2f).coerceAtLeast(8f)
      val maxFitW = availWDp / (estChars * 0.56f)
      val maxFitH = availHDp * 0.88f
      val sizeDp = min(el.fontSizeSp * resizeRatio, min(maxFitW, maxFitH)).coerceAtLeast(9f)
      views.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_DIP, sizeDp)
    }

    private fun buildCombinedHtml(
      context: Context,
      project: WidgetProjectEntity,
      widthDp: Int,
      heightDp: Int,
      spec: VisualWidgetSpec?,
    ): String {
      val assetsDir = File(context.filesDir, NexusWidgetJsBridge.ASSETS_DIR)
      return NexusWidgetJsBridge.composeHtmlDocument(
        rawHtml = project.htmlCode,
        rawCss = project.cssCode,
        rawJs = project.jsCode,
        widthDp = widthDp,
        heightDp = heightDp,
        transparentBg = spec?.htmlTransparent ?: true,
        assetsDir = assetsDir,
        isAutoSize = spec?.sizePresetName == WidgetSizePreset.AUTO.name,
      )
    }
  }
}
