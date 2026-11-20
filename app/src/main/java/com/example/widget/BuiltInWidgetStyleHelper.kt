package com.example.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import com.example.R
import com.example.data.model.BuiltInWidgetStyleConfig
import kotlin.math.roundToInt

object BuiltInWidgetStyleHelper {

  fun applyBackgroundAndPadding(
    context: Context,
    views: RemoteViews,
    bgImageViewId: Int,
    contentRootId: Int,
    style: BuiltInWidgetStyleConfig,
  ) {
    if (!style.isCustomized) {
      views.setImageViewResource(bgImageViewId, R.drawable.widget_bg_amoled)
      return
    }
    val density = context.resources.displayMetrics.density
    val padPx = (style.paddingDp.coerceIn(4, 32) * density).roundToInt()
    views.setViewPadding(contentRootId, padPx, padPx, padPx, padPx)

    val bgBmp = renderRoundedCardBackground(style)
    views.setImageViewBitmap(bgImageViewId, bgBmp)
  }

  fun applyStyledText(
    views: RemoteViews,
    viewId: Int,
    text: String,
    baseSp: Float,
    colorArgb: Long,
    style: BuiltInWidgetStyleConfig,
    isAccentHeader: Boolean = false,
  ) {
    val styled = styledCharSequence(text, style.fontWeightBold || isAccentHeader)
    views.setTextViewText(viewId, styled)
    if (!style.isCustomized) return
    views.setTextColor(viewId, colorArgb.toInt())
    val scaledSp = (baseSp * style.fontSizeScale.coerceIn(0.75f, 1.55f))
    views.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, scaledSp)
    applyAlignment(views, viewId, style.alignment)
  }

  fun applyAlignment(views: RemoteViews, viewId: Int, alignment: Int) {
    val gravity = when (alignment) {
      1 -> Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
      2 -> Gravity.END or Gravity.CENTER_VERTICAL
      else -> Gravity.START or Gravity.CENTER_VERTICAL
    }
    runCatching { views.setInt(viewId, "setGravity", gravity) }
  }

  fun setSecondaryVisibility(views: RemoteViews, viewId: Int, style: BuiltInWidgetStyleConfig) {
    if (!style.isCustomized) return
    views.setViewVisibility(viewId, if (style.showSecondaryDetails) View.VISIBLE else View.GONE)
  }

  fun styledCharSequence(text: String, bold: Boolean): CharSequence {
    if (!bold) return text
    return SpannableString(text).apply {
      if (length > 0) {
        setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
      }
    }
  }

  fun renderRoundedCardBackground(style: BuiltInWidgetStyleConfig): Bitmap {
    val w = 360
    val h = 180
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val alphaInt = (style.opacity.coerceIn(0f, 1f) * 255f).roundToInt()
    val rawColor = style.backgroundColorArgb.toInt()
    val fillColor = Color.argb(
      alphaInt,
      Color.red(rawColor),
      Color.green(rawColor),
      Color.blue(rawColor),
    )
    val radius = (style.cornerRadiusDp.coerceIn(0, 36) * 2.2f)
    val rect = RectF(1.5f, 1.5f, w - 1.5f, h - 1.5f)
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
    canvas.drawRoundRect(rect, radius, radius, fillPaint)

    if ((style.borderColorArgb ushr 24) > 0L && style.opacity > 0.05f) {
      val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = style.borderColorArgb.toInt()
        this.style = Paint.Style.STROKE
        strokeWidth = 2.5f
      }
      canvas.drawRoundRect(rect, radius, radius, borderPaint)
    }
    return bmp
  }
}
