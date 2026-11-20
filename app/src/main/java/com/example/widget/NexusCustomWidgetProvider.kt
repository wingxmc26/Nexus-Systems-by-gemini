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
import com.example.data.local.WidgetInstanceEntity
import com.example.data.local.WidgetProjectEntity
import com.example.di.NexusContainer
import com.example.util.NexusBitmapUtils
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NexusCustomWidgetProvider : AppWidgetProvider() {

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
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val container = runCatching { NexusContainer.get(context) }.getOrNull()
        val instanceDao = container?.database?.widgetInstanceDao()
        appWidgetIds.forEach { widgetId ->
          runCatching { instanceDao?.deleteByWidgetId(widgetId) }
          WidgetSizingEngine.deleteCachedRendersForKey(context, "custom_$widgetId")
        }
        NexusWidgetAlarmScheduler.rescheduleIfActive(context)
      } finally {
        pendingResult.finish()
      }
    }
  }

  companion object {
    suspend fun updateWidgets(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray,
    ) {
      val container = runCatching { NexusContainer.get(context) }.getOrNull()
      val customDao = container?.database?.customWidgetDao()
      val instanceDao = container?.database?.widgetInstanceDao()
      val fallbackProject = runCatching { customDao?.getActiveHomeWidget() }.getOrNull()
      val liveMap = runCatching { WidgetBitmapRenderer.buildLiveValuesMap(context) }.getOrDefault(emptyMap())
      val density = context.resources.displayMetrics.density

      appWidgetIds.forEach { widgetId ->
        var renderedBmp: Bitmap? = null
        runCatching {
          val boundInstance = runCatching { instanceDao?.getByWidgetId(widgetId) }.getOrNull()
          val project: WidgetProjectEntity? = if (boundInstance != null) {
            runCatching { customDao?.getById(boundInstance.projectId) }.getOrNull() ?: fallbackProject
          } else {
            fallbackProject
          }

          val spec = if (project != null) {
            runCatching { WidgetClockSerializer.deserializeVisualWidget(project.visualJson) }.getOrNull()
          } else null

          val slot = WidgetSizingEngine.resolveWidgetSlotDp(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetId = widgetId,
            fallbackWidthDp = spec?.targetWidthDp ?: 328,
            fallbackHeightDp = spec?.targetHeightDp ?: 160,
          )

          if (project != null) {
            val bitmap: Bitmap? = if (project.kind == "VISUAL" && spec != null) {
              WidgetBitmapRenderer.renderVisualWidgetBitmapDp(
                spec = spec,
                liveValues = liveMap,
                widthDp = slot.widthDp,
                heightDp = slot.heightDp,
                density = density,
              ).also { renderedBmp = it }
            } else {
              val combinedHtml = buildCombinedHtml(project)
              val headless = HeadlessHtmlWidgetRenderer.renderHtmlToBitmap(
                context = context,
                rawHtml = combinedHtml,
                widthDp = slot.widthDp,
                heightDp = slot.heightDp,
                density = density,
                transparentBg = spec?.htmlTransparent ?: true,
              )
              if (headless != null) {
                headless.also { renderedBmp = it }
              } else {
                val path = project.renderedBitmapPath
                if (!path.isNullOrBlank() && File(path).exists()) {
                  val reqPx = WidgetSizingEngine.computeRenderPixels(slot.widthDp, slot.heightDp, density)
                  NexusBitmapUtils.decodeSampledBitmap(
                    path,
                    reqPx.widthPx,
                    reqPx.heightPx,
                    applyExifOrientation = false,
                  )?.also { renderedBmp = it }
                } else null
              }
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
              putExtra("route", "widget_studio")
              putExtra("widget_id", widgetId)
              putExtra("project_id", project.id)
              flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
              context,
              10_000 + widgetId,
              openIntent,
              PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            var cachedFilePath: String? = null
            val views = RemoteViews(context.packageName, R.layout.widget_custom_studio).apply {
              if (bitmap != null && !bitmap.isRecycled) {
                setViewVisibility(R.id.widget_custom_image, View.VISIBLE)
                setViewVisibility(R.id.widget_custom_placeholder, View.GONE)
                cachedFilePath = WidgetSizingEngine.deliverWidgetImage(
                  context = context,
                  views = this,
                  imageViewId = R.id.widget_custom_image,
                  bitmap = bitmap,
                  cacheKey = "custom_$widgetId",
                )
              } else {
                setViewVisibility(R.id.widget_custom_image, View.GONE)
                setViewVisibility(R.id.widget_custom_placeholder, View.VISIBLE)
                setTextViewText(R.id.widget_custom_title, project.name)
                setTextViewText(R.id.widget_custom_subtitle, "Tap to preview & activate widget")
              }
              setOnClickPendingIntent(R.id.widget_custom_root, pendingIntent)
            }
            appWidgetManager.updateAppWidget(widgetId, views)

            runCatching {
              instanceDao?.upsert(
                WidgetInstanceEntity(
                  appWidgetId = widgetId,
                  projectId = project.id,
                  widthDp = slot.widthDp,
                  heightDp = slot.heightDp,
                  cachedBitmapPath = cachedFilePath ?: boundInstance?.cachedBitmapPath,
                  updatedAtMs = System.currentTimeMillis(),
                ),
              )
            }
          } else {
            val openIntent = Intent(context, MainActivity::class.java).apply {
              putExtra("route", "widget_studio")
              putExtra("widget_id", widgetId)
              flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
              context,
              10_000 + widgetId,
              openIntent,
              PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_custom_studio).apply {
              setViewVisibility(R.id.widget_custom_image, View.GONE)
              setViewVisibility(R.id.widget_custom_placeholder, View.VISIBLE)
              setTextViewText(R.id.widget_custom_title, "NEXUS WIDGET STUDIO")
              setTextViewText(
                R.id.widget_custom_subtitle,
                "Design & activate a custom widget in Nexus System",
              )
              setOnClickPendingIntent(R.id.widget_custom_root, pendingIntent)
            }
            appWidgetManager.updateAppWidget(widgetId, views)
          }
        }
        renderedBmp?.let { if (!it.isRecycled) it.recycle() }
      }
      runCatching { NexusWidgetAlarmScheduler.rescheduleIfActive(context) }
    }

    private fun buildCombinedHtml(project: WidgetProjectEntity): String {
      return """
        <!DOCTYPE html><html><head><meta charset="utf-8">
        <style>${project.cssCode}</style></head>
        <body>${project.htmlCode}
        <script>${project.jsCode}</script></body></html>
      """.trimIndent()
    }
  }
}
