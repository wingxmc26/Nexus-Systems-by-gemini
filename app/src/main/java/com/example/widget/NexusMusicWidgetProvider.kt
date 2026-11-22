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
import com.example.data.system.PermissionManager
import com.example.di.NexusContainer
import com.example.util.NexusBitmapUtils
import com.example.widget.engine.MediaControlAction
import com.example.widget.engine.WidgetDataState
import com.example.widget.engine.WidgetMediaSessionHub
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NexusMusicWidgetProvider : AppWidgetProvider() {

  override fun onEnabled(context: Context) {
    super.onEnabled(context)
    NexusWidgetLiveTicker.ensureRegistered(context.applicationContext)
  }

  override fun onUpdate(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
  ) {
    NexusWidgetLiveTicker.ensureRegistered(context.applicationContext)
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
    val mediaAction = when (action) {
      ACTION_WIDGET_PLAY_PAUSE -> MediaControlAction.PLAY_PAUSE
      ACTION_WIDGET_NEXT -> MediaControlAction.NEXT
      ACTION_WIDGET_PREV -> MediaControlAction.PREV
      ACTION_WIDGET_SEEK_FORWARD -> MediaControlAction.FAST_FORWARD
      ACTION_WIDGET_SEEK_BACKWARD -> MediaControlAction.REWIND
      else -> null
    } ?: return

    val dispatched = WidgetMediaSessionHub.dispatchTransportCommand(context, mediaAction)
    if (!dispatched) {
      val openMusic = Intent(context, MainActivity::class.java).apply {
        putExtra("route", "music")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      runCatching { context.startActivity(openMusic) }
      return
    }

    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val mgr = runCatching { AppWidgetManager.getInstance(context) }.getOrNull() ?: return@launch
        val ids = mgr.getAppWidgetIds(ComponentName(context, NexusMusicWidgetProvider::class.java))
        if (ids.isNotEmpty()) updateWidgets(context, mgr, ids)
      } finally {
        pending.finish()
      }
    }
  }

  companion object {
    const val WIDGET_STYLE_KEY = "music"
    const val ACTION_WIDGET_PLAY_PAUSE = "com.example.widget.ACTION_MUSIC_PLAY_PAUSE"
    const val ACTION_WIDGET_NEXT = "com.example.widget.ACTION_MUSIC_NEXT"
    const val ACTION_WIDGET_PREV = "com.example.widget.ACTION_MUSIC_PREV"
    const val ACTION_WIDGET_SEEK_FORWARD = "com.example.widget.ACTION_MUSIC_SEEK_FORWARD"
    const val ACTION_WIDGET_SEEK_BACKWARD = "com.example.widget.ACTION_MUSIC_SEEK_BACKWARD"

    suspend fun updateWidgets(
      context: Context,
      appWidgetManager: AppWidgetManager,
      appWidgetIds: IntArray,
    ) {
      val container = runCatching { NexusContainer.get(context) }.getOrNull()
      val mediaState = runCatching {
        container?.widgetDataHub?.mediaProvider?.fetch(context)
      }.getOrNull()

      val style = runCatching {
        container?.preferencesRepository?.getBuiltInWidgetStyle(WIDGET_STYLE_KEY)
      }.getOrNull() ?: BuiltInWidgetStyleConfig()

      var badgeText = "NEXUS AUDIO"
      var title = "No active media session"
      var artist = "Play Spotify, YT Music, or Nexus Audio"
      var isPlaying = false
      var artworkPath: String? = null
      var timeLine: String? = null
      var progressPct: Int? = null
      var sourcePkg: String? = null
      var needsListenerAccess = false

      when (mediaState) {
        is WidgetDataState.Available -> {
          val snap = mediaState.data
          val statusLabel = when {
            snap.isPlaying -> "PLAYING"
            snap.hasActiveSession -> "PAUSED"
            else -> "READY"
          }
          badgeText = "${snap.sourceAppName.uppercase(Locale.getDefault())} • $statusLabel"
          title = snap.title
          artist = if (snap.album.isNotBlank() && snap.artist.isNotBlank()) {
            "${snap.artist} • ${snap.album}"
          } else {
            snap.artist
          }
          isPlaying = snap.isPlaying
          artworkPath = snap.artworkFilePath
          sourcePkg = snap.sourcePackageName.takeIf { it.isNotBlank() }
          if (snap.durationMs > 0L) {
            val pos = snap.positionMs.coerceIn(0L, snap.durationMs)
            timeLine = "${formatMediaTime(pos)} / ${formatMediaTime(snap.durationMs)}"
            progressPct = ((pos * 100L) / snap.durationMs).toInt().coerceIn(0, 100)
          }
        }
        is WidgetDataState.PermissionRequired -> {
          needsListenerAccess = true
          badgeText = "MEDIA PERMISSION"
          title = "Enable Notification Access"
          artist = "Tap widget to mirror Spotify, YT Music & active players"
        }
        is WidgetDataState.Error -> {
          badgeText = "MEDIA ENGINE"
          title = "Media session unavailable"
          artist = mediaState.message
        }
        else -> Unit
      }

      val openIntent = when {
        needsListenerAccess -> PermissionManager(context).buildNotificationAccessIntent()
        sourcePkg != null && sourcePkg != context.packageName -> {
          context.packageManager.getLaunchIntentForPackage(sourcePkg)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
          } ?: Intent(context, MainActivity::class.java).apply {
            putExtra("route", "music")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
          }
        }
        else -> Intent(context, MainActivity::class.java).apply {
          putExtra("route", "music")
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
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

      val artBitmap = artworkPath?.let { path ->
        runCatching { NexusBitmapUtils.decodeSampledBitmap(path, 160, 160) }.getOrNull()
      }

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
            if (artBitmap != null && !artBitmap.isRecycled) {
              setViewVisibility(R.id.widget_music_artwork, View.VISIBLE)
              setImageViewBitmap(R.id.widget_music_artwork, artBitmap)
            } else {
              setViewVisibility(R.id.widget_music_artwork, View.GONE)
            }
            BuiltInWidgetStyleHelper.applyStyledText(
              views = this,
              viewId = R.id.widget_music_badge,
              text = badgeText,
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

            if (timeLine != null && style.showSecondaryDetails) {
              setViewVisibility(R.id.widget_music_time, View.VISIBLE)
              setTextViewText(R.id.widget_music_time, timeLine)
              setTextColor(R.id.widget_music_time, style.secondaryTextColorArgb.toInt())
            } else {
              setViewVisibility(R.id.widget_music_time, View.GONE)
            }

            if (progressPct != null) {
              setViewVisibility(R.id.widget_music_progress, View.VISIBLE)
              setProgressBar(R.id.widget_music_progress, 100, progressPct, false)
            } else {
              setViewVisibility(R.id.widget_music_progress, View.GONE)
            }

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
      if (artBitmap != null && !artBitmap.isRecycled) {
        artBitmap.recycle()
      }
    }

    private fun formatMediaTime(ms: Long): String {
      val totalSec = (ms / 1000L).coerceAtLeast(0L)
      val mins = totalSec / 60L
      val secs = totalSec % 60L
      return String.format(Locale.US, "%d:%02d", mins, secs)
    }
  }
}
