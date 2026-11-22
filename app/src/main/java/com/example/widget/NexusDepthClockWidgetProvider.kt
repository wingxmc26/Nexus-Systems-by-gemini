package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.WidgetClockSerializer
import com.example.data.model.ClockFontChoice
import com.example.data.model.DepthClockMode
import com.example.di.NexusContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NexusDepthClockWidgetProvider : AppWidgetProvider() {

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
    appWidgetIds.forEach { widgetId ->
      WidgetSizingEngine.deleteCachedRendersForKey(context, "depth_clock_$widgetId")
    }
    val pending = runCatching { goAsync() }.getOrNull()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        NexusWidgetAlarmScheduler.rescheduleIfActive(context.applicationContext)
      } finally {
        runCatching { pending?.finish() }
      }
    }
  }

  companion object {
    private val TIME_VIEW_IDS = listOf(
      R.id.widget_clock_live_time_bold,
      R.id.widget_clock_live_time_light,
      R.id.widget_clock_live_time_serif,
      R.id.widget_clock_live_time_condensed,
    )

    suspend fun updateWidgets(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray,
    ) {
      NexusWidgetLiveTicker.ensureRegistered(context.applicationContext)
      val openIntent = Intent(context, MainActivity::class.java).apply {
        putExtra("route", "depth_clock")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val pendingIntent = PendingIntent.getActivity(
        context,
        106,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

      val container = runCatching { NexusContainer.get(context) }.getOrNull()
      val json = runCatching { container?.preferencesRepository?.getDepthClockJson() }.getOrNull()
      val config = WidgetClockSerializer.deserializeDepthClock(json)
      val density = context.resources.displayMetrics.density
      val useLiveTextClock = config.mode == DepthClockMode.DIGITAL &&
        config.foregroundMaskImagePath.isNullOrBlank()

      appWidgetIds.forEach { widgetId ->
        var bmp: Bitmap? = null
        runCatching {
          val slot = WidgetSizingEngine.resolveWidgetSlotDp(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetId = widgetId,
            fallbackWidthDp = 336,
            fallbackHeightDp = 336,
          )
          bmp = WidgetBitmapRenderer.renderDepthClockBitmapDp(
            config = config,
            widthDp = slot.widthDp,
            heightDp = slot.heightDp,
            density = density,
            drawTextOverlay = !useLiveTextClock,
          )
          val views = RemoteViews(context.packageName, R.layout.widget_depth_clock).apply {
            val rendered = bmp
            if (rendered != null && !rendered.isRecycled) {
              setViewVisibility(R.id.widget_clock_image, View.VISIBLE)
              setViewVisibility(R.id.widget_clock_fallback, View.GONE)
              WidgetSizingEngine.deliverWidgetImage(
                context = context,
                views = this,
                imageViewId = R.id.widget_clock_image,
                bitmap = rendered,
                cacheKey = "depth_clock_$widgetId",
              )

              if (useLiveTextClock) {
                setViewVisibility(R.id.widget_clock_live_container, View.VISIBLE)
                val activeTimeId = when (config.fontChoice) {
                  ClockFontChoice.INTER_BOLD -> R.id.widget_clock_live_time_bold
                  ClockFontChoice.INTER_LIGHT -> R.id.widget_clock_live_time_light
                  ClockFontChoice.SERIF -> R.id.widget_clock_live_time_serif
                  ClockFontChoice.CONDENSED -> R.id.widget_clock_live_time_condensed
                }
                TIME_VIEW_IDS.forEach { vid ->
                  setViewVisibility(vid, if (vid == activeTimeId) View.VISIBLE else View.GONE)
                }

                val pattern = when {
                  config.use24Hour && config.showSeconds -> "HH:mm:ss"
                  config.use24Hour -> "HH:mm"
                  config.showSeconds -> "hh:mm:ss"
                  else -> "hh:mm"
                }
                runCatching {
                  setCharSequence(activeTimeId, "setFormat12Hour", pattern)
                  setCharSequence(activeTimeId, "setFormat24Hour", pattern)
                }

                val minSideDp = min(slot.widthDp, slot.heightDp).toFloat()
                val approxChars = if (config.showSeconds) 8.2f else 5.2f
                val maxFitW = (slot.widthDp * 0.84f) / (approxChars * 0.58f)
                val maxFitH = slot.heightDp * if (config.showDate) 0.50f else 0.70f
                val timeSizeDp = min(82f * config.clockScale * (minSideDp / 320f), min(maxFitW, maxFitH))
                  .coerceIn(22f, 110f)
                val dateSizeDp = (15f * config.clockScale * (minSideDp / 320f)).coerceIn(10f, 22f)

                setTextViewTextSize(activeTimeId, TypedValue.COMPLEX_UNIT_DIP, timeSizeDp)
                setTextColor(activeTimeId, config.clockColorArgb.toInt())

                if (config.showDate) {
                  setViewVisibility(R.id.widget_clock_live_date, View.VISIBLE)
                  setTextViewTextSize(R.id.widget_clock_live_date, TypedValue.COMPLEX_UNIT_DIP, dateSizeDp)
                  setTextColor(R.id.widget_clock_live_date, config.accentColorArgb.toInt())
                } else {
                  setViewVisibility(R.id.widget_clock_live_date, View.GONE)
                }

                val yShiftPx = (config.offsetYFraction * slot.heightDp * 0.45f * density).roundToInt()
                val sidePad = (slot.widthDp * 0.05f * density).roundToInt()
                setViewPadding(
                  R.id.widget_clock_live_container,
                  sidePad,
                  yShiftPx.coerceAtLeast(0),
                  sidePad,
                  (-yShiftPx).coerceAtLeast(0),
                )
              } else {
                setViewVisibility(R.id.widget_clock_live_container, View.GONE)
              }
            } else {
              setViewVisibility(R.id.widget_clock_image, View.GONE)
              setViewVisibility(R.id.widget_clock_live_container, View.GONE)
              setViewVisibility(R.id.widget_clock_fallback, View.VISIBLE)
              val fallbackTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
              setTextViewText(R.id.widget_clock_fallback, fallbackTime)
            }
            setOnClickPendingIntent(R.id.widget_clock_root, pendingIntent)
          }
          appWidgetManager.updateAppWidget(widgetId, views)
        }
        bmp?.let { if (!it.isRecycled) it.recycle() }
      }
      runCatching { NexusWidgetAlarmScheduler.rescheduleIfActive(context) }
    }
  }
}
