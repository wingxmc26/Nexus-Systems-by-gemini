package com.example.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.local.WidgetClockSerializer
import com.example.data.model.VisualElementKind
import com.example.di.NexusContainer

object NexusWidgetAlarmScheduler {
  const val ACTION_PERIODIC_WIDGET_REFRESH = "com.example.action.PERIODIC_WIDGET_REFRESH"
  private const val REQUEST_CODE = 44091

  fun canScheduleExact(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val mgr = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
    return runCatching { mgr.canScheduleExactAlarms() }.getOrDefault(false)
  }

  fun computeNextTriggerMs(nowMs: Long, intervalMinutes: Int): Long {
    val safeMin = intervalMinutes.coerceIn(1, 60)
    return if (safeMin == 1) {
      ((nowMs / 60_000L) + 1L) * 60_000L + 150L
    } else {
      nowMs + safeMin * 60_000L
    }
  }

  suspend fun determineRefreshIntervalMinutes(context: Context): Int? {
    val appCtx = context.applicationContext
    val mgr = runCatching { AppWidgetManager.getInstance(appCtx) }.getOrNull()

    val depthIds = mgr?.getAppWidgetIds(ComponentName(appCtx, NexusDepthClockWidgetProvider::class.java)) ?: intArrayOf()
    val customIds = mgr?.getAppWidgetIds(ComponentName(appCtx, NexusCustomWidgetProvider::class.java)) ?: intArrayOf()
    val batteryIds = mgr?.getAppWidgetIds(ComponentName(appCtx, NexusBatteryWidgetProvider::class.java)) ?: intArrayOf()
    val sysIds = mgr?.getAppWidgetIds(ComponentName(appCtx, NexusSystemStatusWidgetProvider::class.java)) ?: intArrayOf()
    val devIds = mgr?.getAppWidgetIds(ComponentName(appCtx, NexusDeviceInfoWidgetProvider::class.java)) ?: intArrayOf()
    val favIds = mgr?.getAppWidgetIds(ComponentName(appCtx, NexusFavoritesWidgetProvider::class.java)) ?: intArrayOf()
    val musicIds = mgr?.getAppWidgetIds(ComponentName(appCtx, NexusMusicWidgetProvider::class.java)) ?: intArrayOf()

    val container = runCatching { NexusContainer.get(appCtx) }.getOrNull()
    val instances = runCatching { container?.database?.widgetInstanceDao()?.getAll() }.getOrNull().orEmpty()
    val activeProj = runCatching { container?.database?.customWidgetDao()?.getActiveHomeWidget() }.getOrNull()

    val anyPlaced = depthIds.isNotEmpty() ||
      customIds.isNotEmpty() ||
      batteryIds.isNotEmpty() ||
      sysIds.isNotEmpty() ||
      devIds.isNotEmpty() ||
      favIds.isNotEmpty() ||
      musicIds.isNotEmpty() ||
      instances.isNotEmpty() ||
      activeProj != null

    if (!anyPlaced) return null

    // Depth clock (especially Analog/Dual) always ticks cleanly at 1-minute resolution.
    if (depthIds.isNotEmpty()) return 1

    val customDao = container?.database?.customWidgetDao()
    val relevantProjectIds = buildSet {
      instances.forEach { add(it.projectId) }
      if (isEmpty() && activeProj != null) add(activeProj.id)
    }

    var minCustomInterval: Int? = null
    for (projId in relevantProjectIds) {
      val proj = runCatching { customDao?.getById(projId) }.getOrNull() ?: continue
      val spec = WidgetClockSerializer.deserializeVisualWidget(proj.visualJson)
      val hasClockOrDate = proj.kind == "HTML" ||
        spec.elements.any { it.visible && (it.kind == VisualElementKind.CLOCK || it.kind == VisualElementKind.DATE) }
      if (hasClockOrDate && spec.refreshIntervalMinutes > 0) {
        return 1
      }
      if (spec.refreshIntervalMinutes > 0) {
        minCustomInterval = minOf(minCustomInterval ?: spec.refreshIntervalMinutes, spec.refreshIntervalMinutes)
      }
    }

    return (minCustomInterval ?: 15).coerceIn(1, 60)
  }

  suspend fun rescheduleIfActive(context: Context) {
    val appCtx = context.applicationContext
    val intervalMin = determineRefreshIntervalMinutes(appCtx)
    if (intervalMin == null) {
      cancel(appCtx)
      return
    }
    val triggerAt = computeNextTriggerMs(System.currentTimeMillis(), intervalMin)
    scheduleAt(appCtx, triggerAt)
  }

  fun scheduleIn(context: Context, delayMs: Long) {
    val triggerAt = System.currentTimeMillis() + delayMs.coerceAtLeast(10_000L)
    scheduleAt(context, triggerAt)
  }

  fun scheduleAt(context: Context, triggerAtMs: Long) {
    val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    val pi = buildPendingIntent(context)
    val safeTrigger = triggerAtMs.coerceAtLeast(System.currentTimeMillis() + 5_000L)
    if (canScheduleExact(context)) {
      val ok = runCatching {
        alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC, safeTrigger, pi)
        true
      }.getOrDefault(false)
      if (!ok) {
        runCatching { alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC, safeTrigger, pi) }
      }
    } else {
      runCatching { alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC, safeTrigger, pi) }
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
