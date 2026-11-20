package com.example.recorder

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.ui.navigation.NexusRoutes
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecorderServiceState(
  val isRecording: Boolean = false,
  val isPaused: Boolean = false,
  val elapsedMs: Long = 0L,
  val lastSavedMessage: String? = null,
  val errorMessage: String? = null,
)

class NexusScreenRecorderService : Service() {

  companion object {
    const val ACTION_START_RECORD = "com.example.recorder.START_RECORD"
    const val ACTION_TAKE_SCREENSHOT = "com.example.recorder.TAKE_SCREENSHOT"
    const val ACTION_PAUSE_RESUME = "com.example.recorder.PAUSE_RESUME"
    const val ACTION_STOP = "com.example.recorder.STOP"

    const val EXTRA_RESULT_CODE = "result_code"
    const val EXTRA_DATA_INTENT = "data_intent"
    const val EXTRA_WIDTH = "width"
    const val EXTRA_HEIGHT = "height"
    const val EXTRA_FPS = "fps"
    const val EXTRA_BITRATE = "bitrate"
    const val EXTRA_MIC = "mic"

    private const val CHANNEL_ID = "nexus_screen_recorder_channel"
    private const val NOTIF_ID = 4401

    private val _state = MutableStateFlow(RecorderServiceState())
    val state: StateFlow<RecorderServiceState> = _state.asStateFlow()

    fun getOutputDirectory(context: Context): File {
      val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        ?: File(context.filesDir, "recordings")
      val dir = File(moviesDir, "NexusRecordings")
      if (!dir.exists()) dir.mkdirs()
      return dir
    }

    fun getPublicMoviesDirIfPermitted(): File? {
      val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "NexusScreenRecordings")
      return if (runCatching { dir.exists() || dir.mkdirs() }.getOrDefault(false)) dir else null
    }
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var timerJob: Job? = null
  private var mediaProjection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var mediaRecorder: MediaRecorder? = null
  private var outputPfd: ParcelFileDescriptor? = null
  private var activeFilePath: String? = null
  private var isPaused = false
  private var elapsedBeforePauseMs = 0L
  private var resumeStartMs = 0L

  private val projectionCallback = object : MediaProjection.Callback() {
    override fun onStop() {
      stopRecordingAndCleanup("Screen capture stopped by system or lock screen.")
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
  }

  @Suppress("DEPRECATION")
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_START_RECORD -> {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val dataIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(EXTRA_DATA_INTENT, Intent::class.java)
        } else {
          intent.getParcelableExtra(EXTRA_DATA_INTENT)
        }
        if (resultCode != Activity.RESULT_OK || dataIntent == null) {
          _state.update { it.copy(errorMessage = "Screen capture permission was not granted.") }
          stopSelf()
          return START_NOT_STICKY
        }
        val width = intent.getIntExtra(EXTRA_WIDTH, 1280)
        val height = intent.getIntExtra(EXTRA_HEIGHT, 720)
        val fps = intent.getIntExtra(EXTRA_FPS, 30)
        val bitrate = intent.getIntExtra(EXTRA_BITRATE, 8_000_000)
        val useMic = intent.getBooleanExtra(EXTRA_MIC, false)
        beginRecording(resultCode, dataIntent, width, height, fps, bitrate, useMic)
      }
      ACTION_TAKE_SCREENSHOT -> {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val dataIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(EXTRA_DATA_INTENT, Intent::class.java)
        } else {
          intent.getParcelableExtra(EXTRA_DATA_INTENT)
        }
        if (resultCode == Activity.RESULT_OK && dataIntent != null) {
          captureScreenshot(resultCode, dataIntent)
        } else {
          stopSelf()
        }
      }
      ACTION_PAUSE_RESUME -> togglePauseResume()
      ACTION_STOP -> stopRecordingAndCleanup("Recording saved.")
    }
    return START_NOT_STICKY
  }

  private fun startForegroundCompat(includeMic: Boolean, statusText: String) {
    val notification = buildNotification(statusText)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val fgsType = if (includeMic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
      } else {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
      }
      startForeground(NOTIF_ID, notification, fgsType)
    } else {
      startForeground(NOTIF_ID, notification)
    }
  }

  @Suppress("DEPRECATION")
  private fun beginRecording(
    resultCode: Int,
    dataIntent: Intent,
    targetWidth: Int,
    targetHeight: Int,
    fps: Int,
    bitrate: Int,
    useMic: Boolean,
  ) {
    startForegroundCompat(useMic, "Preparing screen capture...")

    runCatching {
      val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
      val projection = mpm.getMediaProjection(resultCode, dataIntent)
        ?: error("Unable to acquire MediaProjection session")
      mediaProjection = projection
      projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

      val dm = DisplayMetrics()
      val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
      wm.defaultDisplay.getRealMetrics(dm)
      val densityDpi = dm.densityDpi.coerceAtLeast(160)

      val fileName = "NexusRec_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
      val (recorder, pathLabel) = createPreparedRecorder(fileName, targetWidth, targetHeight, fps, bitrate, useMic)
      activeFilePath = pathLabel
      mediaRecorder = recorder

      virtualDisplay = projection.createVirtualDisplay(
        "NexusScreenCapture",
        targetWidth,
        targetHeight,
        densityDpi,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        recorder.surface,
        null,
        null,
      )
      recorder.start()

      isPaused = false
      elapsedBeforePauseMs = 0L
      resumeStartMs = System.currentTimeMillis()
      _state.value = RecorderServiceState(
        isRecording = true,
        isPaused = false,
        elapsedMs = 0L,
        lastSavedMessage = null,
        errorMessage = null,
      )
      startTimerLoop()
      updateNotification("Recording screen ($targetWidth×$targetHeight @ ${fps}fps)")
    }.onFailure { err ->
      stopRecordingAndCleanup(null, error = "Failed to start recording: ${err.message}")
    }
  }

  @Suppress("DEPRECATION")
  private fun createPreparedRecorder(
    fileName: String,
    width: Int,
    height: Int,
    fps: Int,
    bitrate: Int,
    useMic: Boolean,
  ): Pair<MediaRecorder, String> {
    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      MediaRecorder(this)
    } else {
      MediaRecorder()
    }

    if (useMic) {
      recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
    }
    recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
    recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
    if (useMic) {
      recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
      recorder.setAudioEncodingBitRate(128_000)
      recorder.setAudioSamplingRate(44_100)
    }
    recorder.setVideoSize(width, height)
    recorder.setVideoFrameRate(fps)
    recorder.setVideoEncodingBitRate(bitrate)

    var label = fileName
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/NexusScreenRecordings")
      }
      val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
      val pfd = uri?.let { contentResolver.openFileDescriptor(it, "rw") }
      if (pfd != null) {
        outputPfd = pfd
        recorder.setOutputFile(pfd.fileDescriptor)
        label = uri.toString()
      } else {
        val fallback = File(getOutputDirectory(this), fileName)
        recorder.setOutputFile(fallback.absolutePath)
        label = fallback.absolutePath
      }
    } else {
      val fallback = File(getOutputDirectory(this), fileName)
      recorder.setOutputFile(fallback.absolutePath)
      label = fallback.absolutePath
    }

    recorder.prepare()
    return recorder to label
  }

  @Suppress("DEPRECATION")
  private fun captureScreenshot(resultCode: Int, dataIntent: Intent) {
    startForegroundCompat(false, "Capturing screenshot...")
    runCatching {
      val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
      val projection = mpm.getMediaProjection(resultCode, dataIntent)
        ?: error("Unable to acquire MediaProjection for screenshot")
      mediaProjection = projection
      projection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))

      val dm = DisplayMetrics()
      val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
      wm.defaultDisplay.getRealMetrics(dm)
      val w = dm.widthPixels.coerceAtLeast(720)
      val h = dm.heightPixels.coerceAtLeast(1280)

      val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
      val vd = projection.createVirtualDisplay(
        "NexusScreenshot",
        w,
        h,
        dm.densityDpi,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        reader.surface,
        null,
        null,
      )
      virtualDisplay = vd

      var captured = false
      reader.setOnImageAvailableListener({ r ->
        if (captured) return@setOnImageAvailableListener
        val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
        captured = true
        scope.launch(Dispatchers.IO) {
          val msg = runCatching {
            val plane = image.planes[0]
            val buf = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * w
            val bmp = Bitmap.createBitmap(w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(buf)
            val cropped = Bitmap.createBitmap(bmp, 0, 0, w, h)
            if (cropped !== bmp) bmp.recycle()

            val fileName = "NexusShot_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png"
            saveScreenshotBitmap(cropped, fileName)
            cropped.recycle()
            "Screenshot saved: $fileName"
          }.getOrElse { "Screenshot failed: ${it.message}" }

          image.close()
          runCatching { r.close() }
          scope.launch(Dispatchers.Main) {
            stopRecordingAndCleanup(msg)
          }
        }
      }, Handler(Looper.getMainLooper()))
    }.onFailure { err ->
      stopRecordingAndCleanup(null, error = "Screenshot failed: ${err.message}")
    }
  }

  private fun saveScreenshotBitmap(bmp: Bitmap, fileName: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/NexusScreenshots")
      }
      val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
      if (uri != null) {
        contentResolver.openOutputStream(uri)?.use { out ->
          bmp.compress(Bitmap.CompressFormat.PNG, 95, out)
          return
        }
      }
    }
    val file = File(getOutputDirectory(this), fileName)
    FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
  }

  private fun togglePauseResume() {
    val rec = mediaRecorder ?: return
    if (!_state.value.isRecording) return
    runCatching {
      if (!isPaused) {
        rec.pause()
        elapsedBeforePauseMs += (System.currentTimeMillis() - resumeStartMs)
        isPaused = true
        _state.update { it.copy(isPaused = true, elapsedMs = elapsedBeforePauseMs) }
        updateNotification("Recording paused")
      } else {
        rec.resume()
        resumeStartMs = System.currentTimeMillis()
        isPaused = false
        _state.update { it.copy(isPaused = false) }
        updateNotification("Recording screen...")
      }
    }
  }

  private fun startTimerLoop() {
    timerJob?.cancel()
    timerJob = scope.launch {
      while (_state.value.isRecording) {
        if (!isPaused) {
          val total = elapsedBeforePauseMs + (System.currentTimeMillis() - resumeStartMs)
          _state.update { it.copy(elapsedMs = total) }
        }
        delay(500L)
      }
    }
  }

  private fun stopRecordingAndCleanup(savedMessage: String?, error: String? = null) {
    timerJob?.cancel()
    runCatching {
      mediaRecorder?.apply {
        if (_state.value.isRecording) {
          runCatching { stop() }
        }
        reset()
        release()
      }
    }
    mediaRecorder = null
    runCatching { virtualDisplay?.release() }
    virtualDisplay = null
    runCatching { outputPfd?.close() }
    outputPfd = null
    runCatching {
      mediaProjection?.unregisterCallback(projectionCallback)
      mediaProjection?.stop()
    }
    mediaProjection = null

    _state.value = RecorderServiceState(
      isRecording = false,
      isPaused = false,
      elapsedMs = 0L,
      lastSavedMessage = savedMessage,
      errorMessage = error,
    )
    runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
    stopSelf()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Nexus Screen Recorder",
        NotificationManager.IMPORTANCE_LOW,
      )
      mgr.createNotificationChannel(channel)
    }
  }

  private fun updateNotification(text: String) {
    val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    mgr.notify(NOTIF_ID, buildNotification(text))
  }

  private fun buildNotification(text: String): Notification {
    val openIntent = PendingIntent.getActivity(
      this,
      4402,
      Intent(this, MainActivity::class.java).apply {
        putExtra("route", NexusRoutes.RECORDER)
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
      },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val stopIntent = PendingIntent.getService(
      this,
      4403,
      Intent(this, NexusScreenRecorderService::class.java).apply { action = ACTION_STOP },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val pauseIntent = PendingIntent.getService(
      this,
      4404,
      Intent(this, NexusScreenRecorderService::class.java).apply { action = ACTION_PAUSE_RESUME },
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle("NEXUS Screen Recorder")
      .setContentText(text)
      .setOngoing(true)
      .setContentIntent(openIntent)
      .addAction(0, if (isPaused) "Resume" else "Pause", pauseIntent)
      .addAction(0, "Stop", stopIntent)
      .build()
  }

  override fun onDestroy() {
    timerJob?.cancel()
    scope.cancel()
    super.onDestroy()
  }
}
