package com.example.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.example.widget.engine.DiagnosticSeverity
import com.example.widget.engine.WidgetDiagnosticCenter
import com.example.widget.engine.WidgetEngineRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Deferrable, persisted JobScheduler background sync service for active NEXUS SYSTEM widgets.
 * Ensures widgets recover cleanly after Doze windows, reboots, and launcher memory pressure
 * without adding bloated third-party scheduling libraries.
 */
class NexusWidgetSyncJobService : JobService() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onStartJob(params: JobParameters?): Boolean {
    val appCtx = applicationContext
    serviceScope.launch {
      try {
        NexusWidgetLiveTicker.ensureRegistered(appCtx)
        NexusWidgetUpdater.refreshAll(appCtx)
      } catch (t: Throwable) {
        WidgetDiagnosticCenter.record(
          widgetKey = "sync_job",
          category = "SCHEDULER",
          severity = DiagnosticSeverity.WARN,
          message = "Background sync job failed: ${t.message}",
        )
      } finally {
        jobFinished(params, false)
      }
    }
    return true
  }

  override fun onStopJob(params: JobParameters?): Boolean = true

  companion object {
    const val JOB_ID = 4421

    fun schedulePeriodic(context: Context): Boolean {
      val appCtx = context.applicationContext
      val scheduler = appCtx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return false
      val existing = scheduler.getPendingJob(JOB_ID)
      if (existing != null) return true

      val comp = ComponentName(appCtx, NexusWidgetSyncJobService::class.java)
      val info = JobInfo.Builder(JOB_ID, comp)
        .setPeriodic(15L * 60L * 1000L)
        .setPersisted(true)
        .setRequiresCharging(false)
        .setRequiresDeviceIdle(false)
        .build()

      return runCatching {
        scheduler.schedule(info) == JobScheduler.RESULT_SUCCESS
      }.getOrDefault(false)
    }

    fun cancelIfNoActiveWidgets(context: Context) {
      val appCtx = context.applicationContext
      if (WidgetEngineRegistry.activeWidgetCount(appCtx) > 0) return
      val scheduler = appCtx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
      runCatching { scheduler.cancel(JOB_ID) }
    }
  }
}
