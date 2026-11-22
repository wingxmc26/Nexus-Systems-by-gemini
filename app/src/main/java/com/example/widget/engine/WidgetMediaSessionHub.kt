package com.example.widget.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.example.data.system.PermissionManager
import com.example.di.NexusContainer
import com.example.media.NexusMusicService
import com.example.notifications.NexusNotificationListenerService
import com.example.widget.NexusWidgetUpdater
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MediaControlAction {
  PLAY_PAUSE,
  PLAY,
  PAUSE,
  NEXT,
  PREV,
  FAST_FORWARD,
  REWIND,
  SEEK_TO,
}

/**
 * Real Android MediaSessionManager + MediaController engine supporting Spotify, YouTube Music,
 * Apple Music, Chrome/media apps, and internal Nexus Audio playback without fake data.
 */
object WidgetMediaSessionHub {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val mainHandler = Handler(Looper.getMainLooper())

  private val _currentSnapshot = MutableStateFlow<MediaSessionSnapshot?>(null)
  val currentSnapshot: StateFlow<MediaSessionSnapshot?> = _currentSnapshot.asStateFlow()

  @Volatile private var listenerAttached = false
  @Volatile private var activeController: MediaController? = null
  @Volatile private var lastArtSignature: Int = 0
  private val registeredCallbacks = LinkedHashMap<String, Pair<MediaController, MediaController.Callback>>()

  private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
    val ctx = appContextRef ?: return@OnActiveSessionsChangedListener
    scope.launch {
      syncControllers(ctx, controllers.orEmpty(), notifyWidgets = true)
    }
  }

  @Volatile private var appContextRef: Context? = null

  fun attachSessionObserver(context: Context) {
    val appCtx = context.applicationContext
    appContextRef = appCtx
    if (listenerAttached) return

    val perm = PermissionManager(appCtx)
    if (!perm.isNotificationAccessEnabled()) return

    val msm = appCtx.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
    val comp = ComponentName(appCtx, NexusNotificationListenerService::class.java)
    runCatching {
      msm.addOnActiveSessionsChangedListener(sessionsChangedListener, comp, mainHandler)
      listenerAttached = true
      val existing = msm.getActiveSessions(comp)
      scope.launch {
        syncControllers(appCtx, existing, notifyWidgets = true)
      }
    }.onFailure { err ->
      listenerAttached = false
      WidgetDiagnosticCenter.record(
        widgetKey = "music",
        category = "MEDIA_SESSION",
        severity = DiagnosticSeverity.WARN,
        message = "MediaSession observer requires Notification Access: ${err.message ?: "SecurityException"}",
        suggestion = "Enable Notification Listener access in Settings -> Notification Access.",
      )
    }
  }

  fun detachSessionObserver(context: Context) {
    val appCtx = context.applicationContext
    val msm = appCtx.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    runCatching { msm?.removeOnActiveSessionsChangedListener(sessionsChangedListener) }
    clearCallbacks()
    listenerAttached = false
    activeController = null
  }

  suspend fun readLatestSnapshot(context: Context): MediaSessionSnapshot {
    val appCtx = context.applicationContext
    appContextRef = appCtx
    val perm = PermissionManager(appCtx)
    val listenerGranted = perm.isNotificationAccessEnabled()

    if (listenerGranted) {
      if (!listenerAttached) {
        attachSessionObserver(appCtx)
      }
      val msm = appCtx.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
      val comp = ComponentName(appCtx, NexusNotificationListenerService::class.java)
      val controllers = runCatching { msm?.getActiveSessions(comp) }.getOrNull().orEmpty()
      if (controllers.isNotEmpty()) {
        return syncControllers(appCtx, controllers, notifyWidgets = false)
      }
    }

    // Fallback to internal NexusMusicService playback snapshot only if genuinely played inside app
    val internal = runCatching {
      NexusContainer.get(appCtx).preferencesRepository.getLastMusicPlaybackSnapshot()
    }.getOrNull()

    val hasInternal = internal != null &&
      internal.first.isNotBlank() &&
      internal.first != "No track selected" &&
      internal.first != "No Audio Track"

    return if (hasInternal) {
      MediaSessionSnapshot(
        hasActiveSession = true,
        listenerPermissionGranted = listenerGranted,
        title = internal!!.first,
        artist = internal.second,
        album = "",
        sourceAppName = "NEXUS AUDIO",
        sourcePackageName = appCtx.packageName,
        isPlaying = internal.third,
        playbackStateCode = if (internal.third) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
        positionMs = 0L,
        durationMs = 0L,
        playbackSpeed = 1.0f,
        supportedActionsMask = PlaybackState.ACTION_PLAY_PAUSE or
          PlaybackState.ACTION_SKIP_TO_NEXT or
          PlaybackState.ACTION_SKIP_TO_PREVIOUS,
        artworkFilePath = null,
        appIconFilePath = null,
      ).also { _currentSnapshot.value = it }
    } else {
      MediaSessionSnapshot(
        hasActiveSession = false,
        listenerPermissionGranted = listenerGranted,
        title = if (listenerGranted) "No active media session" else "Notification Access needed",
        artist = if (listenerGranted) {
          "Play music in Spotify, YT Music, or Nexus Audio"
        } else {
          "Grant Notification Access to mirror Spotify & external players"
        },
        album = "",
        sourceAppName = "NEXUS AUDIO",
        sourcePackageName = "",
        isPlaying = false,
        playbackStateCode = PlaybackState.STATE_NONE,
        positionMs = 0L,
        durationMs = 0L,
        playbackSpeed = 1.0f,
        supportedActionsMask = 0L,
      ).also { _currentSnapshot.value = it }
    }
  }

  private fun clearCallbacks() {
    synchronized(registeredCallbacks) {
      registeredCallbacks.values.forEach { (ctrl, cb) ->
        runCatching { ctrl.unregisterCallback(cb) }
      }
      registeredCallbacks.clear()
    }
  }

  private suspend fun syncControllers(
    context: Context,
    controllers: List<MediaController>,
    notifyWidgets: Boolean,
  ): MediaSessionSnapshot {
    clearCallbacks()

    val chosen = selectPrimaryController(context, controllers)
    activeController = chosen

    if (chosen != null) {
      val cb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
          scope.launch {
            extractSnapshot(context, chosen, listenerGranted = true).also { _currentSnapshot.value = it }
            NexusWidgetUpdater.refreshAll(context)
          }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
          scope.launch {
            extractSnapshot(context, chosen, listenerGranted = true).also { _currentSnapshot.value = it }
            NexusWidgetUpdater.refreshAll(context)
          }
        }

        override fun onSessionDestroyed() {
          scope.launch {
            readLatestSnapshot(context)
            NexusWidgetUpdater.refreshAll(context)
          }
        }
      }
      synchronized(registeredCallbacks) {
        runCatching {
          chosen.registerCallback(cb, mainHandler)
          registeredCallbacks[chosen.packageName] = chosen to cb
        }
      }
    }

    val snap = if (chosen != null) {
      extractSnapshot(context, chosen, listenerGranted = true)
    } else {
      readLatestSnapshotFallbackNoMsm(context, listenerGranted = true)
    }
    _currentSnapshot.value = snap

    if (notifyWidgets) {
      NexusWidgetUpdater.refreshAll(context)
    }
    return snap
  }

  data class MediaControllerInput(
    val packageName: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val stateCode: Int,
    val positionMs: Long,
    val playbackSpeed: Float,
    val lastPositionUpdateTime: Long,
    val supportedActionsMask: Long,
    val albumArtBitmap: Bitmap? = null,
    val albumArtUri: String? = null,
    val hasMetadata: Boolean = true,
  )

  fun toInput(controller: MediaController): MediaControllerInput {
    val meta = controller.metadata
    val ps = controller.playbackState
    val pkg = controller.packageName.orEmpty()
    val directBmp = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
      ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
      ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    val uriStr = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
      ?: meta?.getString(MediaMetadata.METADATA_KEY_ART_URI)
      ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)

    return MediaControllerInput(
      packageName = pkg,
      title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
        ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
      artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
      album = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM),
      durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L,
      stateCode = ps?.state ?: PlaybackState.STATE_NONE,
      positionMs = ps?.position?.coerceAtLeast(0L) ?: 0L,
      playbackSpeed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1.0f,
      lastPositionUpdateTime = ps?.lastPositionUpdateTime ?: 0L,
      supportedActionsMask = ps?.actions
        ?: (PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS),
      albumArtBitmap = directBmp,
      albumArtUri = uriStr,
      hasMetadata = meta != null,
    )
  }

  fun selectPrimaryController(
    context: Context,
    controllers: List<MediaController>,
  ): MediaController? {
    if (controllers.isEmpty()) return null
    val inputs = controllers.map { it to toInput(it) }
    val selected = selectPrimaryInput(context, inputs.map { it.second }) ?: return controllers.firstOrNull()
    return inputs.firstOrNull { it.second === selected }?.first ?: controllers.firstOrNull()
  }

  fun selectPrimaryInput(
    context: Context,
    inputs: List<MediaControllerInput>,
  ): MediaControllerInput? {
    if (inputs.isEmpty()) return null
    val playing = inputs.firstOrNull {
      it.stateCode == PlaybackState.STATE_PLAYING || it.stateCode == PlaybackState.STATE_BUFFERING
    }
    if (playing != null) return playing

    val pausedExternal = inputs.firstOrNull {
      it.packageName != context.packageName && it.hasMetadata
    }
    if (pausedExternal != null) return pausedExternal

    return inputs.firstOrNull { it.hasMetadata } ?: inputs.firstOrNull()
  }

  fun extractSnapshot(
    context: Context,
    controller: MediaController,
    listenerGranted: Boolean,
  ): MediaSessionSnapshot {
    return buildSnapshot(context, toInput(controller), listenerGranted)
  }

  fun buildSnapshot(
    context: Context,
    input: MediaControllerInput,
    listenerGranted: Boolean,
  ): MediaSessionSnapshot {
    val pkg = input.packageName
    val pm = context.packageManager

    val appName = runCatching {
      val info = pm.getApplicationInfo(pkg, 0)
      pm.getApplicationLabel(info).toString()
    }.getOrDefault(
      pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }.ifBlank { pkg }
    )

    val resolvedTitle = input.title?.takeIf { it.isNotBlank() } ?: "Playing on $appName"
    val resolvedArtist = input.artist?.takeIf { it.isNotBlank() } ?: appName
    val resolvedAlbum = input.album.orEmpty()
    val duration = input.durationMs.coerceAtLeast(0L)

    val isPlaying = input.stateCode == PlaybackState.STATE_PLAYING || input.stateCode == PlaybackState.STATE_BUFFERING
    val speed = input.playbackSpeed.takeIf { it > 0f } ?: 1.0f
    val rawPos = input.positionMs.coerceAtLeast(0L)
    val computedPos = if (isPlaying && input.lastPositionUpdateTime > 0L) {
      val elapsed = (SystemClock.elapsedRealtime() - input.lastPositionUpdateTime).coerceAtLeast(0L)
      val advanced = rawPos + (elapsed * speed).toLong()
      if (duration > 0L) advanced.coerceIn(0L, duration) else advanced
    } else {
      if (duration > 0L) rawPos.coerceIn(0L, duration) else rawPos
    }

    val artPath = persistArtworkFromInput(context, input, "$pkg|$resolvedTitle|$resolvedAlbum")
    val appIconPath = persistAppIconIfPresent(context, pkg)

    return MediaSessionSnapshot(
      hasActiveSession = true,
      listenerPermissionGranted = listenerGranted,
      title = resolvedTitle,
      artist = resolvedArtist,
      album = resolvedAlbum,
      sourceAppName = appName,
      sourcePackageName = pkg,
      isPlaying = isPlaying,
      playbackStateCode = input.stateCode,
      positionMs = computedPos,
      durationMs = duration,
      playbackSpeed = speed,
      supportedActionsMask = input.supportedActionsMask,
      artworkFilePath = artPath,
      appIconFilePath = appIconPath,
    )
  }

  private fun persistArtworkFromInput(context: Context, input: MediaControllerInput, key: String): String? {
    val dir = File(context.filesDir, "widget_renders").apply { if (!exists()) mkdirs() }
    val outFile = File(dir, "media_art_current.png")
    val directBmp = input.albumArtBitmap

    if (directBmp != null && !directBmp.isRecycled) {
      val sig = "$key|${directBmp.width}x${directBmp.height}".hashCode()
      if (sig == lastArtSignature && outFile.exists()) return outFile.absolutePath
      return runCatching {
        val scaled = scaleSquareMax(directBmp, 240)
        FileOutputStream(outFile).use { fos -> scaled.compress(Bitmap.CompressFormat.PNG, 90, fos) }
        if (scaled !== directBmp && !scaled.isRecycled) scaled.recycle()
        lastArtSignature = sig
        outFile.absolutePath
      }.getOrNull()
    }

    val uriStr = input.albumArtUri
    if (!uriStr.isNullOrBlank()) {
      val sig = "$key|$uriStr".hashCode()
      if (sig == lastArtSignature && outFile.exists()) return outFile.absolutePath
      return runCatching {
        val uri = Uri.parse(uriStr)
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        if (decoded != null) {
          val scaled = scaleSquareMax(decoded, 240)
          FileOutputStream(outFile).use { fos -> scaled.compress(Bitmap.CompressFormat.PNG, 90, fos) }
          if (scaled !== decoded && !scaled.isRecycled) scaled.recycle()
          decoded.recycle()
          lastArtSignature = sig
          outFile.absolutePath
        } else null
      }.getOrNull()
    }

    return null
  }

  fun dispatchTransportCommand(
    context: Context,
    action: MediaControlAction,
    seekPositionMs: Long = 0L,
  ): Boolean {
    val appCtx = context.applicationContext
    val perm = PermissionManager(appCtx)
    var ctrl = activeController
    if (ctrl == null && perm.isNotificationAccessEnabled()) {
      val msm = appCtx.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
      val comp = ComponentName(appCtx, NexusNotificationListenerService::class.java)
      val list = runCatching { msm?.getActiveSessions(comp) }.getOrNull().orEmpty()
      ctrl = selectPrimaryController(appCtx, list)
      activeController = ctrl
    }

    if (ctrl != null) {
      val tc = ctrl.transportControls
      val ok = runCatching {
        val stateCode = ctrl.playbackState?.state
        val playing = stateCode == PlaybackState.STATE_PLAYING || stateCode == PlaybackState.STATE_BUFFERING
        when (action) {
          MediaControlAction.PLAY_PAUSE -> if (playing) tc.pause() else tc.play()
          MediaControlAction.PLAY -> tc.play()
          MediaControlAction.PAUSE -> tc.pause()
          MediaControlAction.NEXT -> tc.skipToNext()
          MediaControlAction.PREV -> tc.skipToPrevious()
          MediaControlAction.FAST_FORWARD -> tc.fastForward()
          MediaControlAction.REWIND -> tc.rewind()
          MediaControlAction.SEEK_TO -> tc.seekTo(seekPositionMs.coerceAtLeast(0L))
        }
        true
      }.getOrDefault(false)
      if (ok) {
        scope.launch { readLatestSnapshot(appCtx) }
        return true
      }
    }

    // Fall back cleanly to internal NexusMusicService for standard actions
    val serviceAction = when (action) {
      MediaControlAction.PLAY_PAUSE,
      MediaControlAction.PLAY,
      MediaControlAction.PAUSE -> NexusMusicService.ACTION_WIDGET_PLAY_PAUSE
      MediaControlAction.NEXT -> NexusMusicService.ACTION_WIDGET_NEXT
      MediaControlAction.PREV -> NexusMusicService.ACTION_WIDGET_PREV
      else -> null
    } ?: return false

    return runCatching {
      appCtx.startService(Intent(appCtx, NexusMusicService::class.java).setAction(serviceAction))
      true
    }.getOrDefault(false)
  }

  private suspend fun readLatestSnapshotFallbackNoMsm(context: Context, listenerGranted: Boolean): MediaSessionSnapshot {
    val internal = runCatching {
      NexusContainer.get(context).preferencesRepository.getLastMusicPlaybackSnapshot()
    }.getOrNull()
    val hasInternal = internal != null &&
      internal.first.isNotBlank() &&
      internal.first != "No track selected" &&
      internal.first != "No Audio Track"
    return if (hasInternal) {
      MediaSessionSnapshot(
        hasActiveSession = true,
        listenerPermissionGranted = listenerGranted,
        title = internal!!.first,
        artist = internal.second,
        album = "",
        sourceAppName = "NEXUS AUDIO",
        sourcePackageName = context.packageName,
        isPlaying = internal.third,
        playbackStateCode = if (internal.third) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
        positionMs = 0L,
        durationMs = 0L,
        playbackSpeed = 1.0f,
        supportedActionsMask = PlaybackState.ACTION_PLAY_PAUSE or
          PlaybackState.ACTION_SKIP_TO_NEXT or
          PlaybackState.ACTION_SKIP_TO_PREVIOUS,
      )
    } else {
      MediaSessionSnapshot(
        hasActiveSession = false,
        listenerPermissionGranted = listenerGranted,
        title = "No active media session",
        artist = "Play music in Spotify, YT Music, or Nexus Audio",
        album = "",
        sourceAppName = "NEXUS AUDIO",
        sourcePackageName = "",
        isPlaying = false,
        playbackStateCode = PlaybackState.STATE_NONE,
        positionMs = 0L,
        durationMs = 0L,
        playbackSpeed = 1.0f,
        supportedActionsMask = 0L,
      )
    }
  }

  private fun persistArtworkIfPresent(context: Context, meta: MediaMetadata?, key: String): String? {
    if (meta == null) return null
    val dir = File(context.filesDir, "widget_renders").apply { if (!exists()) mkdirs() }
    val outFile = File(dir, "media_art_current.png")
    val directBmp = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
      ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
      ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

    if (directBmp != null && !directBmp.isRecycled) {
      val sig = "$key|${directBmp.width}x${directBmp.height}".hashCode()
      if (sig == lastArtSignature && outFile.exists()) return outFile.absolutePath
      return runCatching {
        val scaled = scaleSquareMax(directBmp, 240)
        FileOutputStream(outFile).use { fos -> scaled.compress(Bitmap.CompressFormat.PNG, 90, fos) }
        if (scaled !== directBmp && !scaled.isRecycled) scaled.recycle()
        lastArtSignature = sig
        outFile.absolutePath
      }.getOrNull()
    }

    val uriStr = meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
      ?: meta.getString(MediaMetadata.METADATA_KEY_ART_URI)
      ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)

    if (!uriStr.isNullOrBlank()) {
      val sig = "$key|$uriStr".hashCode()
      if (sig == lastArtSignature && outFile.exists()) return outFile.absolutePath
      return runCatching {
        val uri = Uri.parse(uriStr)
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        if (decoded != null) {
          val scaled = scaleSquareMax(decoded, 240)
          FileOutputStream(outFile).use { fos -> scaled.compress(Bitmap.CompressFormat.PNG, 90, fos) }
          if (scaled !== decoded && !scaled.isRecycled) scaled.recycle()
          decoded.recycle()
          lastArtSignature = sig
          outFile.absolutePath
        } else null
      }.getOrNull()
    }

    return null
  }

  private fun persistAppIconIfPresent(context: Context, pkg: String): String? {
    if (pkg.isBlank()) return null
    val dir = File(context.filesDir, "widget_renders").apply { if (!exists()) mkdirs() }
    val outFile = File(dir, "media_app_${pkg.hashCode()}.png")
    if (outFile.exists() && outFile.length() > 0L) return outFile.absolutePath
    return runCatching {
      val drawable: Drawable = context.packageManager.getApplicationIcon(pkg)
      val bmp = drawableToBitmap(drawable, 72)
      FileOutputStream(outFile).use { fos -> bmp.compress(Bitmap.CompressFormat.PNG, 90, fos) }
      bmp.recycle()
      outFile.absolutePath
    }.getOrNull()
  }

  private fun scaleSquareMax(src: Bitmap, maxSide: Int): Bitmap {
    if (src.width <= maxSide && src.height <= maxSide) return src
    val ratio = minOf(maxSide.toFloat() / src.width, maxSide.toFloat() / src.height)
    val w = (src.width * ratio).toInt().coerceAtLeast(16)
    val h = (src.height * ratio).toInt().coerceAtLeast(16)
    return Bitmap.createScaledBitmap(src, w, h, true)
  }

  private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
      return scaleSquareMax(drawable.bitmap, sizePx)
    }
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(canvas)
    return bmp
  }
}
