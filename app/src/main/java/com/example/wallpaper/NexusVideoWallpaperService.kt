package com.example.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.example.data.model.LiveWallpaperConfig
import com.example.data.model.WallpaperMediaType
import com.example.di.NexusContainer
import com.example.util.NexusBitmapUtils
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NexusVideoWallpaperService : WallpaperService() {

  override fun onCreateEngine(): Engine = VideoWallpaperEngine()

  inner class VideoWallpaperEngine : Engine() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var configJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null
    private var imageBitmap: Bitmap? = null
    private var gifDrawable: Drawable? = null

    private var isSurfaceReady = false
    private var configLoaded = false
    private var isVideoPrepared = false
    private var hasPreparedForSurface = false
    private var activeConfig = LiveWallpaperConfig()
    private var activePath: String? = null

    private val gifTick = object : Runnable {
      override fun run() {
        val holder = surfaceHolder ?: return
        if (!isSurfaceReady || !isVisible) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val pauseForBattery = activeConfig.batterySaverPause && (pm?.isPowerSaveMode == true)
        drawImageOrGifFrame(holder)
        if (!pauseForBattery && activeConfig.mediaType == WallpaperMediaType.GIF) {
          val delayMs = (33f / activeConfig.playbackSpeed.coerceIn(0.25f, 2f)).toLong().coerceIn(16L, 120L)
          mainHandler.postDelayed(this, delayMs)
        }
      }
    }

    override fun onCreate(surfaceHolder: SurfaceHolder?) {
      super.onCreate(surfaceHolder)
      val container = NexusContainer.get(applicationContext)
      configJob = serviceScope.launch {
        container.preferencesRepository.wallpaperConfig.collectLatest { cfg ->
          val directPath = cfg.activeLocalPath?.takeIf { File(it).exists() }
          val libItem = if (directPath == null && cfg.activeWallpaperId > 0L) {
            container.database.wallpaperLibraryDao().getById(cfg.activeWallpaperId)
          } else null
          val resolvedFile = container.liveWallpaperDataSource.resolveActiveFile(
            directPath ?: libItem?.localFilePath
          )
          val resolvedPath = resolvedFile?.absolutePath

          val needsReload = !hasPreparedForSurface ||
            resolvedPath != activePath ||
            cfg.mediaType != activeConfig.mediaType ||
            cfg.scaleCrop != activeConfig.scaleCrop ||
            cfg.loopPlayback != activeConfig.loopPlayback

          activeConfig = cfg
          activePath = resolvedPath
          configLoaded = true

          val currentHolder = this@VideoWallpaperEngine.surfaceHolder
          if (needsReload) {
            if (isSurfaceReady && currentHolder != null && currentHolder.surface?.isValid == true) {
              prepareOrFallback(currentHolder)
            }
          } else {
            mediaPlayer?.let { mp ->
              val vol = if (cfg.muteAudio) 0f else 1f
              runCatching { mp.setVolume(vol, vol) }
              if (isVideoPrepared) {
                runCatching {
                  mp.playbackParams = PlaybackParams().setSpeed(cfg.playbackSpeed.coerceIn(0.25f, 2f))
                }
              }
            }
            if (cfg.mediaType != WallpaperMediaType.VIDEO && isSurfaceReady && currentHolder != null) {
              drawImageOrGifFrame(currentHolder)
            }
          }
        }
      }
    }

    override fun onSurfaceCreated(holder: SurfaceHolder) {
      super.onSurfaceCreated(holder)
      isSurfaceReady = true
      hasPreparedForSurface = false
      if (configLoaded && holder.surface?.isValid == true) {
        prepareOrFallback(holder)
      }
    }

    override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
      super.onSurfaceChanged(holder, format, width, height)
      if (!configLoaded || holder.surface?.isValid != true) return
      if (activePath == null) {
        drawFallbackFrame(holder)
      } else if (activeConfig.mediaType != WallpaperMediaType.VIDEO) {
        drawImageOrGifFrame(holder)
      }
    }

    override fun onVisibilityChanged(visible: Boolean) {
      super.onVisibilityChanged(visible)
      val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
      val pauseForBattery = activeConfig.batterySaverPause && (pm?.isPowerSaveMode == true)

      mediaPlayer?.let { player ->
        if (isVideoPrepared) {
          runCatching {
            if (visible && !pauseForBattery) {
              if (!player.isPlaying) player.start()
            } else {
              if (player.isPlaying) player.pause()
            }
          }
        }
      }

      if (activeConfig.mediaType == WallpaperMediaType.GIF) {
        mainHandler.removeCallbacks(gifTick)
        if (visible && !pauseForBattery) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (gifDrawable as? AnimatedImageDrawable)?.start()
          }
          mainHandler.post(gifTick)
        } else {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (gifDrawable as? AnimatedImageDrawable)?.stop()
          }
        }
      } else if (activeConfig.mediaType == WallpaperMediaType.IMAGE && visible && isSurfaceReady) {
        surfaceHolder?.let { drawImageOrGifFrame(it) }
      }
    }

    override fun onSurfaceDestroyed(holder: SurfaceHolder) {
      isSurfaceReady = false
      hasPreparedForSurface = false
      releaseAllMedia()
      super.onSurfaceDestroyed(holder)
    }

    override fun onDestroy() {
      configJob?.cancel()
      serviceScope.cancel()
      releaseAllMedia()
      super.onDestroy()
    }

    private fun prepareOrFallback(holder: SurfaceHolder?) {
      val surfaceHolder = holder ?: return
      if (surfaceHolder.surface?.isValid != true) return
      releaseAllMedia()
      hasPreparedForSurface = true

      val path = activePath
      if (path.isNullOrBlank() || !File(path).exists()) {
        drawFallbackFrame(surfaceHolder)
        return
      }

      when (activeConfig.mediaType) {
        WallpaperMediaType.VIDEO -> prepareVideo(surfaceHolder, path)
        WallpaperMediaType.IMAGE -> prepareImage(surfaceHolder, path)
        WallpaperMediaType.GIF -> prepareGif(surfaceHolder, path)
      }
    }

    private fun prepareVideo(holder: SurfaceHolder, path: String) {
      if (holder.surface?.isValid != true) return
      isVideoPrepared = false
      runCatching {
        val player = MediaPlayer().apply {
          setDataSource(path)
          setSurface(holder.surface)
          isLooping = activeConfig.loopPlayback
          setVideoScalingMode(
            if (activeConfig.scaleCrop) MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            else MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT
          )
          val vol = if (activeConfig.muteAudio) 0f else 1f
          setVolume(vol, vol)
          setOnPreparedListener { mp ->
            isVideoPrepared = true
            runCatching {
              if (activeConfig.playbackSpeed != 1.0f) {
                mp.playbackParams = PlaybackParams().setSpeed(activeConfig.playbackSpeed.coerceIn(0.25f, 2f))
              }
            }
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val pauseForBattery = activeConfig.batterySaverPause && (pm?.isPowerSaveMode == true)
            if (isSurfaceReady && isVisible && !pauseForBattery) {
              runCatching { mp.start() }
            }
          }
          setOnErrorListener { _, _, _ ->
            isVideoPrepared = false
            releaseAllMedia()
            drawFallbackFrame(holder)
            true
          }
          prepareAsync()
        }
        mediaPlayer = player
      }.onFailure {
        isVideoPrepared = false
        releaseAllMedia()
        drawFallbackFrame(holder)
      }
    }

    private fun prepareImage(holder: SurfaceHolder, path: String) {
      runCatching {
        imageBitmap = NexusBitmapUtils.decodeSampledBitmap(path, 1440, 2560)
        if (imageBitmap != null) {
          drawImageOrGifFrame(holder)
        } else {
          drawFallbackFrame(holder)
        }
      }.onFailure {
        drawFallbackFrame(holder)
      }
    }

    private fun prepareGif(holder: SurfaceHolder, path: String) {
      runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          val source = ImageDecoder.createSource(File(path))
          val drawable = ImageDecoder.decodeDrawable(source)
          gifDrawable = drawable
          if (drawable is AnimatedImageDrawable) {
            drawable.repeatCount = if (activeConfig.loopPlayback) AnimatedImageDrawable.REPEAT_INFINITE else 1
            drawable.start()
          }
          mainHandler.post(gifTick)
        } else {
          imageBitmap = NexusBitmapUtils.decodeSampledBitmap(path, 1080, 1920)
          drawImageOrGifFrame(holder)
        }
      }.onFailure {
        drawFallbackFrame(holder)
      }
    }

    private fun drawImageOrGifFrame(holder: SurfaceHolder) {
      if (holder.surface?.isValid != true) return
      var canvas: Canvas? = null
      try {
        canvas = holder.lockCanvas() ?: return
        canvas.drawColor(Color.BLACK)
        val cw = canvas.width.toFloat()
        val ch = canvas.height.toFloat()

        val gif = gifDrawable
        val bmp = imageBitmap
        if (gif != null) {
          val iw = gif.intrinsicWidth.takeIf { it > 0 }?.toFloat() ?: cw
          val ih = gif.intrinsicHeight.takeIf { it > 0 }?.toFloat() ?: ch
          val baseScale = if (activeConfig.scaleCrop) {
            maxOf(cw / iw, ch / ih)
          } else {
            minOf(cw / iw, ch / ih)
          } * activeConfig.zoomScale.coerceIn(0.5f, 2.5f)
          val dw = (iw * baseScale).toInt()
          val dh = (ih * baseScale).toInt()
          val left = ((cw - dw) / 2f + activeConfig.offsetXPercent * cw).toInt()
          val top = ((ch - dh) / 2f + activeConfig.offsetYPercent * ch).toInt()
          gif.setBounds(left, top, left + dw, top + dh)
          gif.draw(canvas)
        } else if (bmp != null && !bmp.isRecycled) {
          val iw = bmp.width.toFloat().coerceAtLeast(1f)
          val ih = bmp.height.toFloat().coerceAtLeast(1f)
          val baseScale = if (activeConfig.scaleCrop) {
            maxOf(cw / iw, ch / ih)
          } else {
            minOf(cw / iw, ch / ih)
          } * activeConfig.zoomScale.coerceIn(0.5f, 2.5f)
          val matrix = Matrix().apply {
            postScale(baseScale, baseScale)
            postTranslate(
              (cw - iw * baseScale) / 2f + activeConfig.offsetXPercent * cw,
              (ch - ih * baseScale) / 2f + activeConfig.offsetYPercent * ch,
            )
          }
          canvas.drawBitmap(bmp, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        }
      } catch (_: Throwable) {
      } finally {
        if (canvas != null) {
          runCatching { holder.unlockCanvasAndPost(canvas) }
        }
      }
    }

    private fun drawFallbackFrame(holder: SurfaceHolder) {
      if (holder.surface?.isValid != true) return
      var canvas: Canvas? = null
      try {
        canvas = holder.lockCanvas() ?: return
        canvas.drawColor(Color.BLACK)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.parseColor("#E11D48")
          textSize = 42f
          textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.parseColor("#9CA3AF")
          textSize = 28f
          textAlign = Paint.Align.CENTER
        }
        val cx = canvas.width / 2f
        val cy = canvas.height / 2f
        canvas.drawText("NEXUS SYSTEM", cx, cy - 16f, textPaint)
        canvas.drawText("Select wallpaper media in Wallpaper Studio", cx, cy + 32f, subPaint)
      } catch (_: Throwable) {
      } finally {
        if (canvas != null) {
          runCatching { holder.unlockCanvasAndPost(canvas) }
        }
      }
    }

    private fun releaseAllMedia() {
      mainHandler.removeCallbacks(gifTick)
      val wasPrepared = isVideoPrepared
      isVideoPrepared = false
      val player = mediaPlayer
      mediaPlayer = null
      if (player != null) {
        if (wasPrepared) runCatching { if (player.isPlaying) player.stop() }
        runCatching { player.reset() }
        runCatching { player.release() }
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        (gifDrawable as? AnimatedImageDrawable)?.stop()
      }
      gifDrawable = null
      imageBitmap?.let { if (!it.isRecycled) it.recycle() }
      imageBitmap = null
    }
  }
}
