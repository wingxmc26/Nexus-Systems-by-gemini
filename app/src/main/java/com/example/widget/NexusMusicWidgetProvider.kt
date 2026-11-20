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
import com.example.di.NexusContainer
import com.example.media.NexusMusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NexusMusicWidgetProvider : AppWidgetProvider() {

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
    val action = intent.action ?: return
    if (action == ACTION_WIDGET_PLAY_PAUSE || action == ACTION_WIDGET_NEXT || action == ACTION_WIDGET_PREV) {
      val serviceAction = when (action) {
        ACTION_WIDGET_PLAY_PAUSE -> NexusMusicService.ACTION_WIDGET_PLAY_PAUSE
        ACTION_WIDGET_NEXT -> NexusMusicService.ACTION_WIDGET_NEXT
        else -> NexusMusicService.ACTION_WIDGET_PREV
      }
      val forwarded = runCatching {
        context.startService(Intent(context, NexusMusicService::class.java).setAction(serviceAction))
        true
      }.getOrDefault(false)

      if (!forwarded) {
        val openMusic = Intent(context, MainActivity::class.java).apply {
          putExtra("route", "music")
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        runCatching { context.startActivity(openMusic) }
      }
    }
  }

  companion object {
    const val WIDGET_STYLE_KEY = "music"
    private const val ACTION_WIDGET_PLAY_PAUSE = "com.example.widget.ACTION_MUSIC_PLAY_PAUSE"
    private const val ACTION_WIDGET_NEXT = "com.example.widget.ACTION_MUSIC_NEXT"
    private const val ACTION_WIDGET_PREV = "com.example.widget.ACTION_MUSIC_PREV"

    suspend fun updateWidgets(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray,
    ) {
      val container = runCatching { NexusContainer.get(context) }.getOrNull()
      val (title, artist, isPlaying) = runCatching {
        container?.preferencesRepository?.getLastMusicPlaybackSnapshot()
      }.getOrNull() ?: Triple("No track selected", "Open Music Player to play local audio", false)
      val style = runCatching {
        container?.preferencesRepository?.getBuiltInWidgetStyle(WIDGET_STYLE_KEY)
      }.getOrNull() ?: BuiltInWidgetStyleConfig()

      val openIntent = Intent(context, MainActivity::class.java).apply {
        putExtra("route", "music")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val openPending = PendingIntent.getActivity(
        context,
        107,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

      fun controlPending(action: String, code: Int): PendingIntent {
        val intent = Intent(context, NexusMusicWidgetProvider::class.java).apply {
          this.action = action
        }
        return PendingIntent.getBroadcast(
          context,
          code,
          intent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      }

      val prevPending = controlPending(ACTION_WIDGET_PREV, 108)
      val playPausePending = controlPending(ACTION_WIDGET_PLAY_PAUSE, 109)
      val nextPending = controlPending(ACTION_WIDGET_NEXT, 110)

      appWidgetIds.forEach { widgetId ->
        runCatching {
          val views = RemoteViews(context.packageName, R.layout.widget_music).apply {
            BuiltInWidgetStyleHelper.applyBackgroundAndPadding(
              context = context,
              views = this,
              bgImageViewId = R.id.widget_custom_bg,
              contentRootId = R.id.widget_music_root,
              style = style,
            )
            BuiltInWidgetStyleHelper.applyStyledText(
              views = this,
              viewId = R.id.widget_music_badge,
              text = "NEXUS AUDIO",
              baseSp = 10f,
              colorArgb = style.accentColorArgb,
              style = style,
              isAccentHeader = true,
            )
            BuiltInWidgetStyleHelper.applyStyledText(
              views = this,
              viewId = R.id.widget_music_title,
              text = title,
              baseSp = 14f,
              colorArgb = style.textColorArgb,
              style = style,
            )
            BuiltInWidgetStyleHelper.applyStyledText(
              views = this,
              viewId = R.id.widget_music_artist,
              text = artist,
              baseSp = 11f,
              colorArgb = style.secondaryTextColorArgb,
              style = style,
            )
            BuiltInWidgetStyleHelper.setSecondaryVisibility(this, R.id.widget_music_artist, style)
            setTextViewText(R.id.widget_music_play_pause, if (isPlaying) "PAUSE" else "PLAY")
            if (style.isCustomized) {
              setTextColor(R.id.widget_music_play_pause, style.accentColorArgb.toInt())
              setTextColor(R.id.widget_music_prev, style.textColorArgb.toInt())
              setTextColor(R.id.widget_music_next, style.textColorArgb.toInt())
            }
            setOnClickPendingIntent(R.id.widget_music_root, openPending)
            setOnClickPendingIntent(R.id.widget_music_prev, prevPending)
            setOnClickPendingIntent(R.id.widget_music_play_pause, playPausePending)
            setOnClickPendingIntent(R.id.widget_music_next, nextPending)
          }
          appWidgetManager.updateAppWidget(widgetId, views)
        }
      }
    }
  }
}
