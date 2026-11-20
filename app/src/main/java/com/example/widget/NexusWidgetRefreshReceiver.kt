package com.example.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
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
      action == Intent.ACTION_DATE_CHANGED

    if (!isPeriodicTick && !isSystemRestoreOrTime) return

    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isInteractive = runCatching { power?.isInteractive ?: true }.getOrDefault(true)
        if (!isPeriodicTick || isInteractive) {
          NexusWidgetUpdater.refreshAll(context.applicationContext)
        }
        NexusWidgetAlarmScheduler.rescheduleIfActive(context.applicationContext)
      } finally {
        pending.finish()
      }
    }
  }
}
