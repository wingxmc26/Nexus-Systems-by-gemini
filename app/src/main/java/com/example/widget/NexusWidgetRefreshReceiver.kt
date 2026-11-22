package com.example.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.widget.engine.WidgetMediaSessionHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NexusWidgetRefreshReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return
    val isPeriodicTick = action == NexusWidgetAlarmScheduler.ACTION_PERIODIC_WIDGET_REFRESH
    val isSystemRestoreOrTime = action == Intent.ACTION_BOOT_COMPLETED ||
      action == Intent.ACTION_MY_PACKAGE_REPLACED ||
      action == Intent.ACTION_TIME_CHANGED ||
      action == Intent.ACTION_TIMEZONE_CHANGED ||
      action == Intent.ACTION_DATE_CHANGED ||
      action == Intent.ACTION_LOCALE_CHANGED

    if (!isPeriodicTick && !isSystemRestoreOrTime) return

    val appCtx = context.applicationContext
    NexusWidgetLiveTicker.ensureRegistered(appCtx)
    NexusWidgetSyncJobService.schedulePeriodic(appCtx)
    WidgetMediaSessionHub.attachSessionObserver(appCtx)

    val pending = runCatching { goAsync() }.getOrNull()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        NexusWidgetUpdater.refreshAll(appCtx)
      } finally {
        runCatching { pending?.finish() }
      }
    }
  }
}
