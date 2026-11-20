package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.model.BuiltInWidgetStyleConfig
import com.example.data.model.MetricValue
import com.example.di.NexusContainer
import com.example.util.Formatters
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NexusBatteryWidgetProvider : AppWidgetProvider() {

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

  companion object {
    const val WIDGET_STYLE_KEY = "battery"

    suspend fun updateWidgets(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray,
    ) {
      val openIntent = Intent(context, MainActivity::class.java).apply {
        putExtra("route", "battery")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val pendingIntent = PendingIntent.getActivity(
        context,
        101,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

      val container = runCatching { NexusContainer.get(context) }.getOrNull()
      val battery = runCatching { container?.batteryDataSource?.readBatterySnapshot() }.getOrNull()
      if (battery != null && container != null) {
        runCatching { container.batterySessionRepository.processBatterySnapshot(battery) }
      }
      val activeSession = runCatching { container?.preferencesRepository?.getActiveChargingSessionSnapshot() }.getOrNull()
      val style = runCatching {
        container?.preferencesRepository?.getBuiltInWidgetStyle(WIDGET_STYLE_KEY)
      }.getOrNull() ?: BuiltInWidgetStyleConfig()

      val pctText = (battery?.percentage as? MetricValue.Available)?.value?.let { "$it%" } ?: "--%"
      val statusText = battery?.let { "${it.statusLabel} • ${it.chargeSource}" } ?: "Battery Monitor"
      val durationText = if (battery != null && battery.isCharging && activeSession != null) {
        val elapsed = (System.currentTimeMillis() - activeSession.startTimeMs).coerceAtLeast(0L)
        "Charging: ${Formatters.formatDurationWords(elapsed)}"
      } else {
        val temp = (battery?.temperatureCelsius as? MetricValue.Available)?.value
        if (temp != null && battery != null) {
          String.format(Locale.US, "%.1f °C • %s", temp, battery.healthLabel)
        } else {
          "Health: ${battery?.healthLabel ?: "Tap to open"}"
        }
      }

      appWidgetIds.forEach { widgetId ->
        runCatching {
          val views = RemoteViews(context.packageName, R.layout.widget_battery).apply {
            BuiltInWidgetStyleHelper.applyBackgroundAndPadding(
              context = context,
              views = this,
              bgImageViewId = R.id.widget_custom_bg,
              contentRootId = R.id.widget_battery_root,
              style = style,
            )
            BuiltInWidgetStyleHelper.applyStyledText(
              views = this,
              viewId = R.id.widget_battery_pct,
              text = pctText,
              baseSp = 22f,
              colorArgb = style.textColorArgb,
              style = style,
            )
            BuiltInWidgetStyleHelper.applyStyledText(
              views = this,
              viewId = R.id.widget_battery_status,
              text = statusText,
              baseSp = 12f,
              colorArgb = style.accentColorArgb,
              style = style,
              isAccentHeader = true,
            )
            BuiltInWidgetStyleHelper.applyStyledText(
              views = this,
              viewId = R.id.widget_battery_duration,
              text = durationText,
              baseSp = 12f,
              colorArgb = style.secondaryTextColorArgb,
              style = style,
            )
            BuiltInWidgetStyleHelper.setSecondaryVisibility(this, R.id.widget_battery_duration, style)
            setOnClickPendingIntent(R.id.widget_battery_root, pendingIntent)
          }
          appWidgetManager.updateAppWidget(widgetId, views)
        }
      }
    }
  }
}
