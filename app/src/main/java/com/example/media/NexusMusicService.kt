package com.example.media

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.di.NexusContainer
import com.example.ui.navigation.NexusRoutes
import com.example.widget.NexusWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NexusMusicService : MediaSessionService() {
  companion object {
    const val ACTION_WIDGET_PLAY_PAUSE = "com.example.action.MUSIC_WIDGET_PLAY_PAUSE"
    const val ACTION_WIDGET_NEXT = "com.example.action.MUSIC_WIDGET_NEXT"
    const val ACTION_WIDGET_PREV = "com.example.action.MUSIC_WIDGET_PREV"
  }

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var player: ExoPlayer? = null
  private var mediaSession: MediaSession? = null

  override fun onCreate() {
    super.onCreate()
    val container = NexusContainer.get(applicationContext)

    val audioAttrs = AudioAttributes.Builder()
      .setUsage(C.USAGE_MEDIA)
      .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
      .build()

    val exo = ExoPlayer.Builder(this)
      .setAudioAttributes(audioAttrs, true)
      .setHandleAudioBecomingNoisy(true)
      .build()

    exo.addListener(object : Player.Listener {
      override fun onAudioSessionIdChanged(audioSessionId: Int) {
        container.soundBoosterEngine.attachToSession(audioSessionId)
      }

      override fun onIsPlayingChanged(isPlaying: Boolean) {
        syncWidgetState(exo)
      }

      override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        syncWidgetState(exo)
      }
    })

    if (exo.audioSessionId > 0) {
      container.soundBoosterEngine.attachToSession(exo.audioSessionId)
    }

    val openIntent = Intent(this, MainActivity::class.java).apply {
      putExtra("route", NexusRoutes.MUSIC)
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val sessionActivity = PendingIntent.getActivity(
      this,
      1100,
      openIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    player = exo
    mediaSession = MediaSession.Builder(this, exo)
      .setSessionActivity(sessionActivity)
      .build()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val exo = player
    when (intent?.action) {
      ACTION_WIDGET_PLAY_PAUSE -> if (exo != null) {
        if (exo.isPlaying) exo.pause() else if (exo.mediaItemCount > 0) exo.play()
        syncWidgetState(exo)
      }
      ACTION_WIDGET_NEXT -> if (exo != null && exo.hasNextMediaItem()) {
        exo.seekToNextMediaItem()
        exo.play()
        syncWidgetState(exo)
      }
      ACTION_WIDGET_PREV -> if (exo != null && exo.hasPreviousMediaItem()) {
        exo.seekToPreviousMediaItem()
        exo.play()
        syncWidgetState(exo)
      }
    }
    return super.onStartCommand(intent, flags, startId)
  }

  private fun syncWidgetState(exo: ExoPlayer) {
    val meta = exo.currentMediaItem?.mediaMetadata
    val title = meta?.title?.toString()?.takeIf { it.isNotBlank() } ?: "No track selected"
    val artist = meta?.artist?.toString()?.takeIf { it.isNotBlank() } ?: "Nexus Audio"
    val playing = exo.isPlaying
    serviceScope.launch {
      NexusContainer.get(applicationContext).preferencesRepository
        .updateLastMusicPlayback(title, artist, playing)
      NexusWidgetUpdater.refreshAll(applicationContext)
    }
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
    return mediaSession
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    val exo = player
    if (exo == null || !exo.playWhenReady || exo.mediaItemCount == 0) {
      stopSelf()
    }
  }

  override fun onDestroy() {
    serviceScope.cancel()
    mediaSession?.run {
      player.release()
      release()
      mediaSession = null
    }
    player = null
    super.onDestroy()
  }
}
