package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.example.widget.engine.WidgetMediaSessionHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NexusWidgetUpdater {
  suspend fun refreshAll(context: Context) = withContext(Dispatchers.IO) {
    val appContext = context.applicationContext
    NexusWidgetLiveTicker.ensureRegistered(appContext)
    WidgetMediaSessionHub.attachSessionObserver(appContext)
    NexusWidgetSyncJobService.schedulePeriodic(appContext)
    val mgr = runCatching { AppWidgetManager.getInstance(appContext) }.getOrNull() ?: return@withContext

    runCatching {
      val ids = mgr.getAppWidgetIds(ComponentName(appContext, NexusBatteryWidgetProvider::class.java))
      if (ids.isNotEmpty()) NexusBatteryWidgetProvider.updateWidgets(appContext, mgr, ids)
    }
    runCatching {
      val ids = mgr.getAppWidgetIds(ComponentName(appContext, NexusSystemStatusWidgetProvider::class.java))
      if (ids.isNotEmpty()) NexusSystemStatusWidgetProvider.updateWidgets(appContext, mgr, ids)
    }
    runCatching {
      val ids = mgr.getAppWidgetIds(ComponentName(appContext, NexusDeviceInfoWidgetProvider::class.java))
      if (ids.isNotEmpty()) NexusDeviceInfoWidgetProvider.updateWidgets(appContext, mgr, ids)
    }
    runCatching {
      val ids = mgr.getAppWidgetIds(ComponentName(appContext, NexusFavoritesWidgetProvider::class.java))
      if (ids.isNotEmpty()) NexusFavoritesWidgetProvider.updateWidgets(appContext, mgr, ids)
    }
    runCatching {
      val ids = mgr.getAppWidgetIds(ComponentName(appContext, NexusCustomWidgetProvider::class.java))
      if (ids.isNotEmpty()) NexusCustomWidgetProvider.updateWidgets(appContext, mgr, ids)
    }
    runCatching {
      val ids = mgr.getAppWidgetIds(ComponentName(appContext, NexusDepthClockWidgetProvider::class.java))
      if (ids.isNotEmpty()) NexusDepthClockWidgetProvider.updateWidgets(appContext, mgr, ids)
    }
    runCatching {
      val ids = mgr.getAppWidgetIds(ComponentName(appContext, NexusMusicWidgetProvider::class.java))
      if (ids.isNotEmpty()) NexusMusicWidgetProvider.updateWidgets(appContext, mgr, ids)
    }
    runCatching { NexusWidgetAlarmScheduler.rescheduleIfActive(appContext) }
  }
}
