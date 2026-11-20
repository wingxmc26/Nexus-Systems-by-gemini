package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.model.BuiltInWidgetStyleConfig
import com.example.di.NexusContainer
import com.example.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NexusDeviceInfoWidgetProvider : AppWidgetProvider() {

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        updateWidgets(context, appWidgetManager, appWidgetIds)
      } finally {
        pending.finish()
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

  companion object {
    const val WIDGET_STYLE_KEY = "device_info"

    suspend fun updateWidgets(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray,
    ) {
      val title = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
      val uptime = Formatters.formatDuration(SystemClock.elapsedRealtime())
      val subtitle = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • Uptime $uptime"

      val container = runCatching { NexusContainer.get(context) }.getOrNull()
      val style = runCatching {
        container?.preferencesRepository?.getBuiltInWidgetStyle(WIDGET_STYLE_KEY)
      }.getOrNull() ?: BuiltInWidgetStyleConfig()

      val openIntent = Intent(context, MainActivity::class.java).apply {
        putExtra("route", "hardware")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val pendingIntent = PendingIntent.getActivity(
        context,
        104,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

      appWidgetIds.forEach { widgetId ->
        runCatching {
          val views = RemoteViews(context.packageName, R.layout.widget_device_info).apply {
            BuiltInWidgetStyleHelper.applyBackgroundAndPadding(
              context = context,
              views = this,
              bgImageViewId = R.id.widget_custom_bg,
              contentRootId = R.id.widget_device_root,
              style = style,
            )
            BuiltInWidgetStyleHelper.applyStyledText(
              views = this,
              viewId = R.id.widget_device_title,
              text = title,
              baseSp = 15f,
              colorArgb = style.textColorArgb,
              style = style,
            )
            BuiltInWidgetStyleHelper.applyStyledText(
              views = this,
              viewId = R.id.widget_device_subtitle,
              text = subtitle,
              baseSp = 12f,
              colorArgb = style.secondaryTextColorArgb,
              style = style,
            )
            BuiltInWidgetStyleHelper.setSecondaryVisibility(this, R.id.widget_device_subtitle, style)
            setOnClickPendingIntent(R.id.widget_device_root, pendingIntent)
          }
          appWidgetManager.updateAppWidget(widgetId, views)
        }
      }
    }
  }
}
