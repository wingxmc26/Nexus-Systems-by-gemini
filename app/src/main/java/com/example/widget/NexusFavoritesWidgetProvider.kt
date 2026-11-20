package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.model.BuiltInWidgetStyleConfig
import com.example.di.NexusContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NexusFavoritesWidgetProvider : AppWidgetProvider() {

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
    const val WIDGET_STYLE_KEY = "favorites"

    suspend fun updateWidgets(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray,
    ) {
      val container = runCatching { NexusContainer.get(context) }.getOrNull()
      val favorites = runCatching {
        container?.favoritesRepository?.getTopFavorites()
      }.getOrNull().orEmpty()
      val style = runCatching {
        container?.preferencesRepository?.getBuiltInWidgetStyle(WIDGET_STYLE_KEY)
      }.getOrNull() ?: BuiltInWidgetStyleConfig()

      val slotIds = listOf(
        R.id.widget_fav_slot_1,
        R.id.widget_fav_slot_2,
        R.id.widget_fav_slot_3,
        R.id.widget_fav_slot_4,
      )

      val openLauncherIntent = Intent(context, MainActivity::class.java).apply {
        putExtra("route", "launcher")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val openLauncherPending = PendingIntent.getActivity(
        context,
        115,
        openLauncherIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

      appWidgetIds.forEach { widgetId ->
        runCatching {
          val views = RemoteViews(context.packageName, R.layout.widget_favorites)
          BuiltInWidgetStyleHelper.applyBackgroundAndPadding(
            context = context,
            views = views,
            bgImageViewId = R.id.widget_custom_bg,
            contentRootId = R.id.widget_favorites_root,
            style = style,
          )
          BuiltInWidgetStyleHelper.applyStyledText(
            views = views,
            viewId = R.id.widget_fav_header,
            text = "FAVORITE SHORTCUTS",
            baseSp = 11f,
            colorArgb = style.accentColorArgb,
            style = style,
            isAccentHeader = true,
          )
          views.setOnClickPendingIntent(R.id.widget_favorites_root, openLauncherPending)

          if (favorites.isEmpty()) {
            views.setViewVisibility(R.id.widget_fav_empty, View.VISIBLE)
            if (style.isCustomized) {
              views.setTextColor(R.id.widget_fav_empty, style.secondaryTextColorArgb.toInt())
            }
            slotIds.forEach { id -> views.setViewVisibility(id, View.GONE) }
          } else {
            views.setViewVisibility(R.id.widget_fav_empty, View.GONE)
            val pm = context.packageManager
            slotIds.forEachIndexed { idx, slotId ->
              val fav = favorites.getOrNull(idx)
              if (fav == null) {
                views.setViewVisibility(slotId, View.GONE)
              } else {
                views.setViewVisibility(slotId, View.VISIBLE)
                BuiltInWidgetStyleHelper.applyStyledText(
                  views = views,
                  viewId = slotId,
                  text = fav.customLabel,
                  baseSp = 12f,
                  colorArgb = style.textColorArgb,
                  style = style,
                )

                val candidate = if (!fav.activityClassName.isNullOrBlank()) {
                  Intent(Intent.ACTION_MAIN).apply {
                    component = ComponentName(fav.packageName, fav.activityClassName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                  }
                } else {
                  null
                }
                val resolvedLaunch = if (candidate != null && runCatching { candidate.resolveActivity(pm) }.getOrNull() != null) {
                  candidate
                } else {
                  runCatching { pm.getLaunchIntentForPackage(fav.packageName) }.getOrNull()?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                  } ?: openLauncherIntent
                }

                val slotPending = PendingIntent.getActivity(
                  context,
                  200 + idx,
                  resolvedLaunch,
                  PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(slotId, slotPending)
              }
            }
          }
          appWidgetManager.updateAppWidget(widgetId, views)
        }
      }
    }
  }
}
