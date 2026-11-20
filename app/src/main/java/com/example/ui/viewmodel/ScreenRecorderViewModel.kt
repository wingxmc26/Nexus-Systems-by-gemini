package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.RecordedMediaItem
import com.example.data.model.ScreenRecorderConfig
import com.example.di.NexusContainer
import com.example.recorder.NexusScreenRecorderService
import com.example.recorder.RecorderServiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScreenRecorderUiState(
  val config: ScreenRecorderConfig = ScreenRecorderConfig(),
  val hasMicPermission: Boolean = false,
  val hasPostNotifPermission: Boolean = true,
  val serviceState: RecorderServiceState = RecorderServiceState(),
  val recordings: List<RecordedMediaItem> = emptyList(),
  val isLoadingLibrary: Boolean = false,
  val statusMessage: String? = null,
)

class ScreenRecorderViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)
  private val ds = container.screenRecorderDataSource
  private val pm = container.permissionManager

  private val _config = MutableStateFlow(ScreenRecorderConfig())
  private val _hasMic = MutableStateFlow(pm.isRecordAudioGranted())
  private val _hasPostNotif = MutableStateFlow(pm.isPostNotificationsGranted())
  private val _library = MutableStateFlow<List<RecordedMediaItem>>(emptyList())
  private val _loading = MutableStateFlow(false)
  private val _status = MutableStateFlow<String?>(null)

  val uiState: StateFlow<ScreenRecorderUiState> = combine(
    _config,
    _hasMic,
    _hasPostNotif,
    NexusScreenRecorderService.state,
    _library,
    _loading,
    _status,
  ) { arr ->
    @Suppress("UNCHECKED_CAST")
    ScreenRecorderUiState(
      config = arr[0] as ScreenRecorderConfig,
      hasMicPermission = arr[1] as Boolean,
      hasPostNotifPermission = arr[2] as Boolean,
      serviceState = arr[3] as RecorderServiceState,
      recordings = arr[4] as List<RecordedMediaItem>,
      isLoadingLibrary = arr[5] as Boolean,
      statusMessage = arr[6] as String?,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = ScreenRecorderUiState(),
  )

  init {
    refreshLibrary()
  }

  fun refreshPermissions() {
    _hasMic.value = pm.isRecordAudioGranted()
    _hasPostNotif.value = pm.isPostNotificationsGranted()
  }

  fun refreshLibrary() {
    viewModelScope.launch {
      _loading.value = true
      _library.value = ds.loadRecordedLibrary()
      _loading.value = false
    }
  }

  fun setResolution(label: String) { _config.value = _config.value.copy(resolutionLabel = label) }
  fun setFps(fps: Int) { _config.value = _config.value.copy(fps = fps) }
  fun setBitrateMbps(mbps: Int) { _config.value = _config.value.copy(bitrateMbps = mbps) }
  fun setRecordMic(enabled: Boolean) { _config.value = _config.value.copy(recordMicrophone = enabled) }

  fun createScreenCaptureIntent(): Intent {
    val mpm = getApplication<Application>().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    return mpm.createScreenCaptureIntent()
  }

  @Suppress("DEPRECATION")
  fun startRecordingWithConsent(resultCode: Int, dataIntent: Intent) {
    val cfg = _config.value
    val ctx = getApplication<Application>()
    val dm = DisplayMetrics()
    val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    wm.defaultDisplay.getRealMetrics(dm)
    val nativeW = dm.widthPixels.coerceAtLeast(720)
    val nativeH = dm.heightPixels.coerceAtLeast(1280)

    val (targetW, targetH) = when (cfg.resolutionLabel) {
      "720p" -> {
        val h = (nativeH * (720f / nativeW.toFloat())).toInt().coerceAtLeast(720)
        even(720) to even(h)
      }
      "1080p" -> {
        val w = nativeW.coerceAtMost(1080)
        val h = (nativeH * (w.toFloat() / nativeW.toFloat())).toInt()
        even(w) to even(h)
      }
      else -> even(nativeW) to even(nativeH)
    }

    val intent = Intent(ctx, NexusScreenRecorderService::class.java).apply {
      action = NexusScreenRecorderService.ACTION_START_RECORD
      putExtra(NexusScreenRecorderService.EXTRA_RESULT_CODE, resultCode)
      putExtra(NexusScreenRecorderService.EXTRA_DATA_INTENT, dataIntent)
      putExtra(NexusScreenRecorderService.EXTRA_WIDTH, targetW)
      putExtra(NexusScreenRecorderService.EXTRA_HEIGHT, targetH)
      putExtra(NexusScreenRecorderService.EXTRA_FPS, cfg.fps)
      putExtra(NexusScreenRecorderService.EXTRA_BITRATE, cfg.bitrateMbps * 1_000_000)
      putExtra(NexusScreenRecorderService.EXTRA_MIC, cfg.recordMicrophone && pm.isRecordAudioGranted())
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      ctx.startForegroundService(intent)
    } else {
      ctx.startService(intent)
    }
  }

  fun captureScreenshotWithConsent(resultCode: Int, dataIntent: Intent) {
    val ctx = getApplication<Application>()
    val intent = Intent(ctx, NexusScreenRecorderService::class.java).apply {
      action = NexusScreenRecorderService.ACTION_TAKE_SCREENSHOT
      putExtra(NexusScreenRecorderService.EXTRA_RESULT_CODE, resultCode)
      putExtra(NexusScreenRecorderService.EXTRA_DATA_INTENT, dataIntent)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      ctx.startForegroundService(intent)
    } else {
      ctx.startService(intent)
    }
  }

  fun togglePauseResume() {
    val ctx = getApplication<Application>()
    ctx.startService(Intent(ctx, NexusScreenRecorderService::class.java).apply {
      action = NexusScreenRecorderService.ACTION_PAUSE_RESUME
    })
  }

  fun stopRecording() {
    val ctx = getApplication<Application>()
    ctx.startService(Intent(ctx, NexusScreenRecorderService::class.java).apply {
      action = NexusScreenRecorderService.ACTION_STOP
    })
  }

  fun renameItem(item: RecordedMediaItem, newName: String) {
    viewModelScope.launch {
      val ok = ds.renameRecording(item, newName)
      _status.value = if (ok) "Renamed successfully." else "Unable to rename '${item.name}'."
      refreshLibrary()
    }
  }

  fun deleteItem(item: RecordedMediaItem) {
    viewModelScope.launch {
      val ok = ds.deleteRecording(item)
      _status.value = if (ok) "Deleted '${item.name}'." else "Unable to delete '${item.name}'."
      refreshLibrary()
    }
  }

  fun shareItem(item: RecordedMediaItem) = ds.shareRecording(item)
  fun clearStatus() { _status.value = null }

  private fun even(v: Int): Int = (v / 2) * 2
}
