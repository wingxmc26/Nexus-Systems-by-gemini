package com.example.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.data.local.WidgetClockSerializer
import com.example.di.NexusContainer

object NexusWidgetAlarmScheduler {
  const val ACTION_PERIODIC_WIDGET_REFRESH = "com.example.action.PERIODIC_WIDGET_REFRESH"
  private const val REQUEST_CODE = 44091

  suspend fun rescheduleIfActive(context: Context) {
    val appCtx = context.applicationContext
    val container = runCatching { NexusContainer.get(appCtx) }.getOrNull() ?: return
    val allProjects = runCatching { container.database.customWidgetDao().observeAll() }
    val instances = runCatching { container.database.widgetInstanceDao().getAll() }.getOrDefault(emptyList())

    val minMinutes = runCatching {
      val boundIds = instances.map { it.projectId }.toSet()
      val activeProj = container.database.customWidgetDao().getActiveHomeWidget()
      val relevantIds = if (boundIds.isEmpty() && activeProj != null) setOf(activeProj.id) else boundIds
      relevantIds.mapNotNull { id ->
        container.database.customWidgetDao().getById(id)?.let { proj ->
          WidgetClockSerializer.deserializeVisualWidget(proj.visualJson).refreshIntervalMinutes
        }
      }.filter { it > 0 }.minOrNull()
    }.getOrNull()

    val intervalMin = (minMinutes ?: 15).coerceIn(1, 60)
    if (instances.isEmpty() && runCatching { container.database.customWidgetDao().getActiveHomeWidget() }.getOrNull() == null) {
      cancel(appCtx)
      return
    }
    scheduleIn(appCtx, intervalMin * 60_000L)
  }

  fun scheduleIn(context: Context, delayMs: Long) {
    val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val pi = buildPendingIntent(context)
    val triggerAt = System.currentTimeMillis() + delayMs.coerceAtLeast(60_000L)
    runCatching {
      alarmMgr.set(AlarmManager.RTC, triggerAt, pi)
    }
  }

  fun cancel(context: Context) {
    val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    runCatching { alarmMgr.cancel(buildPendingIntent(context)) }
  }

  private fun buildPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, NexusWidgetRefreshReceiver::class.java).apply {
      action = ACTION_PERIODIC_WIDGET_REFRESH
    }
    return PendingIntent.getBroadcast(
      context,
      REQUEST_CODE,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
  }
}
