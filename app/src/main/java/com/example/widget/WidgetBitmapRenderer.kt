package com.example.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.example.data.model.ClockFontChoice
import com.example.data.model.DepthClockConfig
import com.example.data.model.DepthClockMode
import com.example.data.model.MetricValue
import com.example.data.model.VisualElementKind
import com.example.data.model.VisualWidgetElement
import com.example.data.model.VisualWidgetSpec
import com.example.di.NexusContainer
import com.example.util.Formatters
import com.example.util.NexusBitmapUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object WidgetBitmapRenderer {

  suspend fun buildLiveValuesMap(context: Context): Map<VisualElementKind, String> {
    val hub = NexusContainer.get(context).widgetDataHub
    val time = (hub.timeProvider.fetch(context) as? com.example.widget.engine.WidgetDataState.Available)?.data
    val battery = (hub.batteryProvider.fetch(context) as? com.example.widget.engine.WidgetDataState.Available)?.data
    val mem = (hub.memoryProvider.fetch(context) as? com.example.widget.engine.WidgetDataState.Available)?.data
    val storage = (hub.storageProvider.fetch(context) as? com.example.widget.engine.WidgetDataState.Available)?.data
    val device = (hub.deviceProvider.fetch(context) as? com.example.widget.engine.WidgetDataState.Available)?.data
    val mediaState = hub.mediaProvider.fetch(context)

    val batPct = (battery?.percentage as? MetricValue.Available)?.value?.let { "$it%" } ?: "N/A"
    val temp = (battery?.temperatureCelsius as? MetricValue.Available)?.value
      ?.let { String.format(Locale.US, "%.1f°C", it) } ?: ""
    val chargeText = if (battery?.isCharging == true) {
      "Charging (${battery.chargeSource})"
    } else if (battery != null) {
      "${battery.statusLabel} $temp".trim()
    } else {
      "Battery"
    }

    val musicText = when (mediaState) {
      is com.example.widget.engine.WidgetDataState.Available ->
        if (mediaState.data.hasActiveSession) "${mediaState.data.title} — ${mediaState.data.artist}"
        else "No active playback"
      is com.example.widget.engine.WidgetDataState.PermissionRequired -> "Grant Notification Access"
      else -> "Media unavailable"
    }

    val now = Date()
    val clockDisplay = if (time != null) {
      if (time.is24HourFormat) time.time24 else time.time12
    } else {
      SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    }

    return mapOf(
      VisualElementKind.CLOCK to clockDisplay,
      VisualElementKind.DATE to (time?.dateShort ?: SimpleDateFormat("EEE, MMM dd", Locale.getDefault()).format(now)),
      VisualElementKind.BATTERY to "Battery $batPct",
      VisualElementKind.CHARGING to chargeText,
      VisualElementKind.RAM to if (mem != null) "RAM ${mem.usagePercent.toInt()}% (${Formatters.formatBytes(mem.availableRamBytes)} free)" else "RAM --",
      VisualElementKind.STORAGE to if (storage != null) "Free ${Formatters.formatBytes(storage.internalAvailableBytes)}" else "Storage --",
      VisualElementKind.DEVICE to if (device != null) "${device.manufacturer} ${device.model}" else "${Build.MANUFACTURER} ${Build.MODEL}",
      VisualElementKind.MUSIC to musicText,
      VisualElementKind.ICON to "◈",
    )
  }

  /**
   * Density- and DP-aware Visual Widget bitmap renderer supporting small, medium, large, wide,
   * tall, and iOS large clock widgets at full native crispness (no 440x260 truncation).
   */
  fun renderVisualWidgetBitmapDp(
    spec: VisualWidgetSpec,
    liveValues: Map<VisualElementKind, String>,
    widthDp: Int = spec.targetWidthDp,
    heightDp: Int = spec.targetHeightDp,
    density: Float = 2.75f,
    skipTextKinds: Set<VisualElementKind> = emptySet(),
    overrideImageBitmaps: Map<String, Bitmap> = emptyMap(),
  ): Bitmap {
    val px = WidgetSizingEngine.computeRenderPixels(widthDp, heightDp, density)
    return renderVisualWidgetBitmapInternal(
      spec = spec,
      liveValues = liveValues,
      canvasW = px.widthPx,
      canvasH = px.heightPx,
      designWidthDp = widthDp.toFloat().coerceAtLeast(72f),
      designHeightDp = heightDp.toFloat().coerceAtLeast(56f),
      pxPerDp = px.effectiveScale,
      skipTextKinds = skipTextKinds,
      overrideImageBitmaps = overrideImageBitmaps,
    )
  }

  /**
   * Preserved backwards-compatible helper used when direct pixel dimensions are requested.
   */
  fun renderVisualWidgetBitmap(
    spec: VisualWidgetSpec,
    liveValues: Map<VisualElementKind, String>,
    widthPx: Int = 440,
    heightPx: Int = 260,
  ): Bitmap {
    val safeW = widthPx.coerceIn(160, 500)
    val safeH = heightPx.coerceIn(100, 420)
    val refWidthDp = spec.targetWidthDp.coerceAtLeast(120).toFloat()
    val refHeightDp = spec.targetHeightDp.coerceAtLeast(80).toFloat()
    val pxPerDp = safeW / refWidthDp
    val raw = renderVisualWidgetBitmapInternal(
      spec = spec,
      liveValues = liveValues,
      canvasW = safeW,
      canvasH = safeH,
      designWidthDp = refWidthDp,
      designHeightDp = refHeightDp,
      pxPerDp = pxPerDp,
    )
    return NexusBitmapUtils.ensureBinderSafeBitmap(raw, maxByteBudget = 560_000, maxWidth = 520, maxHeight = 460)
  }

  private fun renderVisualWidgetBitmapInternal(
    spec: VisualWidgetSpec,
    liveValues: Map<VisualElementKind, String>,
    canvasW: Int,
    canvasH: Int,
    designWidthDp: Float,
    designHeightDp: Float,
    pxPerDp: Float,
    skipTextKinds: Set<VisualElementKind> = emptySet(),
    overrideImageBitmaps: Map<String, Bitmap> = emptyMap(),
  ): Bitmap {
    val bmp = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val margin = if (spec.isTransparent) 0f else (1.5f * pxPerDp)
    val rect = RectF(margin, margin, canvasW - margin, canvasH - margin)
    val radius = (spec.cornerRadiusDp * pxPerDp).coerceAtLeast(0f)

    if (!spec.isTransparent) {
      val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
      val gradEnd = spec.gradientEndColorArgb
      if (gradEnd != null) {
        bgPaint.shader = LinearGradient(
          0f, 0f, canvasW.toFloat(), canvasH.toFloat(),
          spec.backgroundColorArgb.toInt(),
          gradEnd.toInt(),
          Shader.TileMode.CLAMP,
        )
      } else {
        bgPaint.color = spec.backgroundColorArgb.toInt()
      }
      canvas.drawRoundRect(rect, radius, radius, bgPaint)

      if (spec.isGlassStyle) {
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.argb(20, 255, 255, 255)
          style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
          RectF(rect.left, rect.top, rect.right, rect.top + canvasH * 0.42f),
          radius,
          radius,
          sheenPaint,
        )
      }

      if (spec.borderWidthDp > 0f) {
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = spec.borderColorArgb.toInt()
          style = Paint.Style.STROKE
          strokeWidth = (spec.borderWidthDp * pxPerDp).coerceAtLeast(1f)
        }
        canvas.drawRoundRect(rect, radius, radius, borderPaint)
      }
    }

    // Apply proportional container padding matching editor preview 1:1
    val padPx = (spec.containerPaddingDp * pxPerDp)
      .coerceIn(0f, min(canvasW, canvasH) * 0.25f)
    val innerLeft = padPx
    val innerTop = padPx
    val innerW = (canvasW - padPx * 2f).coerceAtLeast(32f)
    val innerH = (canvasH - padPx * 2f).coerceAtLeast(32f)

    // Compute subtle uniform font/element adaptation factor if widget was resized on launcher
    val specW = spec.targetWidthDp.coerceAtLeast(100).toFloat()
    val specH = spec.targetHeightDp.coerceAtLeast(80).toFloat()
    val resizeRatio = min(designWidthDp / specW, designHeightDp / specH).coerceIn(0.65f, 1.85f)

    val ordered = spec.elements.filter { it.visible }.sortedBy { it.layerOrder }
    for (el in ordered) {
      drawVisualElement(
        canvas = canvas,
        el = el,
        liveValues = liveValues,
        innerLeft = innerLeft,
        innerTop = innerTop,
        innerW = innerW,
        innerH = innerH,
        pxPerDp = pxPerDp * resizeRatio,
        skipText = el.kind in skipTextKinds && el.label.isBlank(),
        overrideImage = overrideImageBitmaps[el.id],
      )
    }
    return bmp
  }

  private fun drawVisualElement(
    canvas: Canvas,
    el: VisualWidgetElement,
    liveValues: Map<VisualElementKind, String>,
    innerLeft: Float,
    innerTop: Float,
    innerW: Float,
    innerH: Float,
    pxPerDp: Float,
    skipText: Boolean = false,
    overrideImage: Bitmap? = null,
  ) {
    val left = innerLeft + el.xFraction.coerceIn(0f, 0.96f) * innerW
    val top = innerTop + el.yFraction.coerceIn(0f, 0.96f) * innerH
    val w = (el.widthFraction.coerceIn(0.05f, 1f) * innerW).coerceAtMost(innerLeft + innerW - left)
    val h = (el.heightFraction.coerceIn(0.05f, 1f) * innerH).coerceAtMost(innerTop + innerH - top)
    if (w < 4f || h < 4f) return

    val bounds = RectF(left, top, left + w, top + h)
    val elemRadius = (el.cornerRadiusDp * pxPerDp).coerceAtLeast(0f)

    if ((el.backgroundColorArgb ushr 24) > 0L) {
      val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = el.backgroundColorArgb.toInt() }
      canvas.drawRoundRect(bounds, elemRadius, elemRadius, bgPaint)
    }

    if (el.kind == VisualElementKind.IMAGE && (overrideImage != null || !el.imageUriOrPath.isNullOrBlank())) {
      if (overrideImage != null && !overrideImage.isRecycled) {
        val save = canvas.save()
        val path = Path().apply { addRoundRect(bounds, elemRadius, elemRadius, Path.Direction.CW) }
        canvas.clipPath(path)
        drawCenterCrop(canvas, overrideImage, bounds)
        canvas.restoreToCount(save)
      } else {
        val f = el.imageUriOrPath?.let { File(it) }
        if (f != null && f.exists()) {
          NexusBitmapUtils.decodeSampledBitmap(f.absolutePath, w.roundToInt(), h.roundToInt())?.let { img ->
            val save = canvas.save()
            val path = Path().apply { addRoundRect(bounds, elemRadius, elemRadius, Path.Direction.CW) }
            canvas.clipPath(path)
            drawCenterCrop(canvas, img, bounds)
            canvas.restoreToCount(save)
            img.recycle()
          }
        }
      }
      return
    }

    if (skipText) return

    val resolvedText = when (el.kind) {
      VisualElementKind.TEXT -> el.label.ifBlank { "Custom Label" }
      else -> {
        val base = liveValues[el.kind] ?: el.kind.label
        if (el.label.isNotBlank()) "${el.label}: $base" else base
      }
    }

    val pad = (el.paddingDp * pxPerDp).coerceIn(0f, min(w, h) * 0.35f)
    val availTextW = (w - pad * 2f).roundToInt().coerceAtLeast(8)
    val availTextH = (h - pad * 2f).coerceAtLeast(8f)

    // Auto-scale oversized font gently only if element height would clip a single line
    val requestedTextPx = (el.fontSizeSp * pxPerDp).coerceAtLeast(10f)
    val maxSingleLinePx = availTextH * 0.92f
    val finalTextPx = min(requestedTextPx, maxSingleLinePx).coerceAtLeast(9f)

    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = el.textColorArgb.toInt()
      textSize = finalTextPx
      typeface = if (el.fontWeightBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    val layoutAlign = when (el.alignment) {
      1 -> Layout.Alignment.ALIGN_CENTER
      2 -> Layout.Alignment.ALIGN_OPPOSITE
      else -> Layout.Alignment.ALIGN_NORMAL
    }

    val fm = textPaint.fontMetrics
    val singleLineH = (fm.descent - fm.ascent).coerceAtLeast(1f)
    val maxLines = (availTextH / singleLineH).toInt().coerceIn(1, 6)

    val staticLayout = StaticLayout.Builder
      .obtain(resolvedText, 0, resolvedText.length, textPaint, availTextW)
      .setAlignment(layoutAlign)
      .setIncludePad(false)
      .setMaxLines(maxLines)
      .setEllipsize(TextUtils.TruncateAt.END)
      .build()

    val save = canvas.save()
    canvas.clipRect(bounds)
    val yOffset = bounds.top + pad + ((availTextH - staticLayout.height).coerceAtLeast(0f) / 2f)
    canvas.translate(bounds.left + pad, yOffset)
    staticLayout.draw(canvas)
    canvas.restoreToCount(save)
  }

  /**
   * Density- and DP-aware Depth Clock renderer supporting Compact through iOS Large 4x4 slots.
   */
  fun renderDepthClockBitmapDp(
    config: DepthClockConfig,
    widthDp: Int = 336,
    heightDp: Int = 336,
    density: Float = 2.75f,
    drawTextOverlay: Boolean = true,
  ): Bitmap {
    val px = WidgetSizingEngine.computeRenderPixels(widthDp, heightDp, density)
    return renderDepthClockBitmap(config, px.widthPx, px.heightPx, px.effectiveScale, drawTextOverlay)
  }

  fun renderDepthClockBitmap(
    config: DepthClockConfig,
    widthPx: Int = 440,
    heightPx: Int = 270,
    pxPerDpHint: Float? = null,
    drawTextOverlay: Boolean = true,
  ): Bitmap {
    val maxBound = if (pxPerDpHint != null) 1440 else 480
    val safeW = widthPx.coerceIn(160, maxBound)
    val safeH = heightPx.coerceIn(100, maxBound)
    val refSide = min(safeW, safeH).toFloat()
    val scale = refSide / 320f
    val pxPerDp = pxPerDpHint ?: (safeW / 328f)
    val cornerRadius = (24f * pxPerDp).coerceAtLeast(14f)

    val bmp = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val rect = RectF(0f, 0f, safeW.toFloat(), safeH.toFloat())
    val clipPath = Path().apply { addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW) }
    val save = canvas.save()
    canvas.clipPath(clipPath)

    // Layer 1: Background base & optional custom photo
    val alphaInt = (config.backgroundOpacity.coerceIn(0f, 1f) * 255).toInt()
    val baseColor = config.backgroundColorArgb.toInt()
    canvas.drawColor(Color.argb(alphaInt, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)))

    config.backgroundImagePath?.takeIf { File(it).exists() }?.let { bgPath ->
      NexusBitmapUtils.decodeSampledBitmap(bgPath, safeW, safeH)?.let { bgBmp ->
        drawCenterCrop(canvas, bgBmp, rect)
        bgBmp.recycle()
      }
    }

    if (config.glassBlurOverlay) {
      val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
          0f, 0f, 0f, safeH.toFloat(),
          Color.argb(85, 10, 12, 16),
          Color.argb(145, 0, 0, 0),
          Shader.TileMode.CLAMP,
        )
      }
      canvas.drawRect(rect, glassPaint)
    }

    // Layer 2: Clock layer (Digital / Analog / Dual)
    val cal = Calendar.getInstance()
    val centerX = safeW / 2f
    val centerY = safeH / 2f + (config.offsetYFraction * safeH * 0.35f)
    val typeface = when (config.fontChoice) {
      ClockFontChoice.INTER_BOLD -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
      ClockFontChoice.INTER_LIGHT -> Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
      ClockFontChoice.SERIF -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
      ClockFontChoice.CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }

    if (config.mode == DepthClockMode.ANALOG || config.mode == DepthClockMode.DUAL) {
      val radius = refSide * 0.32f * config.clockScale
      drawAnalogClock(canvas, cal, centerX, centerY, radius, config, scale)
    }

    if (drawTextOverlay && (config.mode == DepthClockMode.DIGITAL || config.mode == DepthClockMode.DUAL)) {
      val pattern = when {
        config.use24Hour && config.showSeconds -> "HH:mm:ss"
        config.use24Hour -> "HH:mm"
        config.showSeconds -> "hh:mm:ss"
        else -> "hh:mm"
      }
      val timeStr = SimpleDateFormat(pattern, Locale.getDefault()).format(cal.time)
      val baseTimeFactor = if (config.mode == DepthClockMode.DUAL) 52f else 82f
      val rawTextSize = baseTimeFactor * config.clockScale * scale
      val maxWidthFit = safeW * 0.86f
      val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = config.clockColorArgb.toInt()
        this.typeface = typeface
        textSize = rawTextSize
        textAlign = Paint.Align.CENTER
        setShadowLayer(8f * scale, 0f, 3f * scale, Color.argb(110, 0, 0, 0))
      }
      val measuredW = timePaint.measureText(timeStr)
      if (measuredW > maxWidthFit && measuredW > 0f) {
        timePaint.textSize = rawTextSize * (maxWidthFit / measuredW)
      }

      val textY = if (config.mode == DepthClockMode.DUAL) {
        centerY + safeH * 0.33f
      } else {
        centerY + (timePaint.textSize * 0.25f)
      }
      canvas.drawText(timeStr, centerX, textY, timePaint)

      if (config.showDate) {
        val dateStr = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(cal.time)
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = config.accentColorArgb.toInt()
          this.typeface = Typeface.DEFAULT_BOLD
          textSize = (18f * config.clockScale * scale).coerceAtLeast(10f)
          textAlign = Paint.Align.CENTER
        }
        canvas.drawText(dateStr.uppercase(Locale.getDefault()), centerX, textY - timePaint.textSize * 0.92f, datePaint)
      }
    }

    // Layer 3: Foreground depth layer
    val fgPath = config.foregroundMaskImagePath?.takeIf { File(it).exists() }
    if (fgPath != null) {
      NexusBitmapUtils.decodeSampledBitmap(fgPath, safeW, safeH)?.let { fgBmp ->
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
          alpha = (config.depthLayerStrength.coerceIn(0.1f, 1f) * 255).toInt()
        }
        drawCenterCrop(canvas, fgBmp, rect, p)
        fgBmp.recycle()
      }
    } else if (config.depthLayerStrength > 0.05f) {
      val depthGrad = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
          0f, safeH * 0.55f, 0f, safeH.toFloat(),
          Color.TRANSPARENT,
          Color.argb((config.depthLayerStrength * 190).toInt(), 0, 0, 0),
          Shader.TileMode.CLAMP,
        )
      }
      canvas.drawRect(rect, depthGrad)
    }

    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.argb(60, 255, 255, 255)
      style = Paint.Style.STROKE
      strokeWidth = (1.5f * scale).coerceAtLeast(1f)
    }
    canvas.drawRoundRect(RectF(1f, 1f, safeW - 1f, safeH - 1f), cornerRadius, cornerRadius, border)
    canvas.restoreToCount(save)
    return bmp
  }

  private fun drawAnalogClock(
    canvas: Canvas,
    cal: Calendar,
    cx: Float,
    cy: Float,
    radius: Float,
    config: DepthClockConfig,
    scale: Float,
  ) {
    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = config.clockColorArgb.toInt()
      alpha = 180
      style = Paint.Style.STROKE
      strokeWidth = (3f * scale).coerceAtLeast(1.5f)
    }
    canvas.drawCircle(cx, cy, radius, ringPaint)

    for (i in 0 until 12) {
      val angle = Math.toRadians((i * 30 - 90).toDouble())
      val inner = radius * 0.86f
      val outer = radius * 0.96f
      canvas.drawLine(
        cx + (cos(angle) * inner).toFloat(),
        cy + (sin(angle) * inner).toFloat(),
        cx + (cos(angle) * outer).toFloat(),
        cy + (sin(angle) * outer).toFloat(),
        ringPaint,
      )
    }

    val sec = cal.get(Calendar.SECOND)
    val minVal = cal.get(Calendar.MINUTE) + sec / 60f
    val hourVal = (cal.get(Calendar.HOUR) % 12) + minVal / 60f

    val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = config.clockColorArgb.toInt()
      strokeWidth = (6f * scale).coerceAtLeast(2.5f)
      strokeCap = Paint.Cap.ROUND
    }
    val minPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = config.clockColorArgb.toInt()
      strokeWidth = (4f * scale).coerceAtLeast(2f)
      strokeCap = Paint.Cap.ROUND
    }
    val secPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = config.accentColorArgb.toInt()
      strokeWidth = (2.5f * scale).coerceAtLeast(1.5f)
      strokeCap = Paint.Cap.ROUND
    }

    val hAngle = Math.toRadians((hourVal * 30f - 90f).toDouble())
    canvas.drawLine(cx, cy, cx + (cos(hAngle) * radius * 0.52f).toFloat(), cy + (sin(hAngle) * radius * 0.52f).toFloat(), hourPaint)

    val mAngle = Math.toRadians((minVal * 6f - 90f).toDouble())
    canvas.drawLine(cx, cy, cx + (cos(mAngle) * radius * 0.76f).toFloat(), cy + (sin(mAngle) * radius * 0.76f).toFloat(), minPaint)

    if (config.showSeconds) {
      val sAngle = Math.toRadians((sec * 6f - 90f).toDouble())
      canvas.drawLine(cx, cy, cx + (cos(sAngle) * radius * 0.84f).toFloat(), cy + (sin(sAngle) * radius * 0.84f).toFloat(), secPaint)
    }
    canvas.drawCircle(cx, cy, (5f * scale).coerceAtLeast(2.5f), secPaint)
  }

  private fun drawCenterCrop(canvas: Canvas, bmp: Bitmap, dst: RectF, paint: Paint = Paint(Paint.FILTER_BITMAP_FLAG)) {
    val scale = maxOf(dst.width() / bmp.width, dst.height() / bmp.height)
    val w = bmp.width * scale
    val h = bmp.height * scale
    val left = dst.left + (dst.width() - w) / 2f
    val top = dst.top + (dst.height() - h) / 2f
    canvas.drawBitmap(bmp, null, RectF(left, top, left + w, top + h), paint)
  }
}
