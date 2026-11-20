package com.example.widget

import android.app.ActivityManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.model.BuiltInWidgetStyleConfig
import com.example.data.model.MetricValue
import com.example.di.NexusContainer
import com.example.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NexusSystemStatusWidgetProvider : AppWidgetProvider() {

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

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    if (intent.action == ACTION_REFRESH) {
      val mgr = runCatching { AppWidgetManager.getInstance(context) }.getOrNull() ?: return
      val ids = mgr.getAppWidgetIds(ComponentName(context, NexusSystemStatusWidgetProvider::class.java))
      if (ids.isNotEmpty()) onUpdate(context, mgr, ids)
    }
  }

  companion object {
    const val ACTION_REFRESH = "com.example.widget.ACTION_REFRESH_SYSTEM_WIDGET"
    const val WIDGET_STYLE_KEY = "system_status"

    suspend fun updateWidgets(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray,
    ) {
      val openIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val openPending = PendingIntent.getActivity(
        context,
        102,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

      val refreshIntent = Intent(context, NexusSystemStatusWidgetProvider::class.java).apply {
        action = ACTION_REFRESH
      }
      val refreshPending = PendingIntent.getBroadcast(
        context,
        103,
        refreshIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

      val container = runCatching { NexusContainer.get(context) }.getOrNull()
      val style = runCatching {
        container?.preferencesRepository?.getBuiltInWidgetStyle(WIDGET_STYLE_KEY)
      }.getOrNull() ?: BuiltInWidgetStyleConfig()

      val (ramLine, ramPct) = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalRam = memInfo.totalMem.coerceAtLeast(1L)
        val usedRam = (totalRam - memInfo.availMem).coerceAtLeast(0L)
        val pct = ((usedRam.toFloat() / totalRam.toFloat()) * 100f).toInt().coerceIn(0, 100)
        "RAM: ${Formatters.formatBytes(usedRam)} / ${Formatters.formatBytes(totalRam)} ($pct%)" to pct
      }.getOrDefault("RAM: Tap to inspect" to 0)

      val storageLine = runCatching {
        val stat = StatFs(Environment.getDataDirectory().path)
        "Free Storage: ${Formatters.formatBytes(stat.availableBytes.coerceAtLeast(0L))} of ${Formatters.formatBytes(stat.totalBytes.coerceAtLeast(0L))}"
      }.getOrDefault("Storage: Tap to inspect")

      val batteryLine = runCatching {
        val battery = container?.batteryDataSource?.readBatterySnapshot()
        val batPct = (battery?.percentage as? MetricValue.Available)?.value?.let { "$it%" } ?: "N/A"
        "Battery: $batPct • ${battery?.statusLabel ?: "Unknown"} (${battery?.chargeSource ?: "Unknown"})"
      }.getOrDefault("Battery: Tap to inspect")

      appWidgetIds.forEach { widgetId ->
        runCatching {
          val views = RemoteViews(context.packageName, R.layout.widget_system_status).apply {
            BuiltInWidgetStyleHelper.applyBackgroundAndPadding(
              context = context,
              views = this,
              bgImageViewId = R.id.widget_custom_bg,
              contentRootId = R.id.widget_system_root,
              style = style,
            )
            BuiltInWidgetStyleHelper.applyStyledText(
              views = this,
              viewId = R.id.widget_sys_header,
              text = "NEXUS SYSTEM",
              baseSp = 11f,
              colorArgb = style.accentColorArgb,
              style = style,
              isAccentHeader = true,
            )
            BuiltInWidgetStyleHelper.applyStyledText(
              views = this,
              viewId = R.id.widget_sys_ram,
              text = ramLine,
              baseSp = 14f,
              colorArgb = style.textColorArgb,
              style = style,
            )
            setProgressBar(R.id.widget_sys_ram_progress, 100, ramPct, false)
            BuiltInWidgetStyleHelper.applyStyledText(
              views = this,
              viewId = R.id.widget_sys_battery_line,
              text = batteryLine,
              baseSp = 12f,
              colorArgb = style.secondaryTextColorArgb,
              style = style,
            )
            BuiltInWidgetStyleHelper.applyStyledText(
              views = this,
              viewId = R.id.widget_sys_storage_line,
              text = storageLine,
              baseSp = 12f,
              colorArgb = style.secondaryTextColorArgb,
              style = style,
            )
            BuiltInWidgetStyleHelper.setSecondaryVisibility(this, R.id.widget_sys_storage_line, style)
            setOnClickPendingIntent(R.id.widget_system_root, openPending)
            setOnClickPendingIntent(R.id.widget_sys_refresh, refreshPending)
          }
          appWidgetManager.updateAppWidget(widgetId, views)
        }
      }
    }
  }
}
