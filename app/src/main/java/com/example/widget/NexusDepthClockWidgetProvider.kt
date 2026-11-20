package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.local.WidgetClockSerializer
import com.example.di.NexusContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NexusDepthClockWidgetProvider : AppWidgetProvider() {

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
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
  }

  companion object {
    suspend fun updateWidgets(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray,
    ) {
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
            } else {
              setViewVisibility(R.id.widget_clock_image, View.GONE)
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
    }
  }
}
