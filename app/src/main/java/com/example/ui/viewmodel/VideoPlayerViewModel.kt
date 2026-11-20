package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.VideoProgressEntity
import com.example.data.model.LocalVideoItem
import com.example.data.model.VideoAspectMode
import com.example.data.model.VideoDecoderMode
import com.example.data.system.HdrHardwareSummary
import com.example.di.NexusContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VideoPlayerUiState(
  val hasVideoPermission: Boolean = false,
  val isLoading: Boolean = false,
  val videos: List<LocalVideoItem> = emptyList(),
  val recentProgress: List<VideoProgressEntity> = emptyList(),
  val searchQuery: String = "",
  val activeVideo: LocalVideoItem? = null,
  val resumePositionMs: Long = 0L,
  val aspectMode: VideoAspectMode = VideoAspectMode.FIT,
  val decoderMode: VideoDecoderMode = VideoDecoderMode.AUTO_HW,
  val playbackSpeed: Float = 1.0f,
  val externalSubtitleUri: Uri? = null,
  val subtitleOffsetMs: Long = 0L,
  val hdrSummary: HdrHardwareSummary? = null,
)

class VideoPlayerViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)
  private val dao = container.database.videoProgressDao()

  private val _hasPerm = MutableStateFlow(container.permissionManager.isVideoMediaGranted())
  private val _isLoading = MutableStateFlow(false)
  private val _videos = MutableStateFlow<List<LocalVideoItem>>(emptyList())
  private val _searchQuery = MutableStateFlow("")
  private val _activeVideo = MutableStateFlow<LocalVideoItem?>(null)
  private val _resumePos = MutableStateFlow(0L)
  private val _aspectMode = MutableStateFlow(VideoAspectMode.FIT)
  private val _decoderMode = MutableStateFlow(VideoDecoderMode.AUTO_HW)
  private val _speed = MutableStateFlow(1.0f)
  private val _subUri = MutableStateFlow<Uri?>(null)
  private val _subOffset = MutableStateFlow(0L)
  private val _hdrSummary = MutableStateFlow(container.videoLibraryDataSource.readHdrCapabilities())

  val uiState: StateFlow<VideoPlayerUiState> = combine(
    _hasPerm,
    _isLoading,
    _videos,
    dao.observeRecentVideos(),
    _searchQuery,
    _activeVideo,
    _resumePos,
    _aspectMode,
    _decoderMode,
    _speed,
    _subUri,
    _subOffset,
    _hdrSummary,
  ) { arr ->
    @Suppress("UNCHECKED_CAST")
    val all = arr[2] as List<LocalVideoItem>
    val q = (arr[4] as String).trim().lowercase()
    val filtered = if (q.isEmpty()) all else all.filter {
      it.title.lowercase().contains(q) || it.folderName.lowercase().contains(q)
    }

    @Suppress("UNCHECKED_CAST")
    val recentProgress = arr[3] as List<VideoProgressEntity>

    VideoPlayerUiState(
      hasVideoPermission = arr[0] as Boolean,
      isLoading = arr[1] as Boolean,
      videos = filtered,
      recentProgress = recentProgress,
      searchQuery = arr[4] as String,
      activeVideo = arr[5] as LocalVideoItem?,
      resumePositionMs = arr[6] as Long,
      aspectMode = arr[7] as VideoAspectMode,
      decoderMode = arr[8] as VideoDecoderMode,
      playbackSpeed = arr[9] as Float,
      externalSubtitleUri = arr[10] as Uri?,
      subtitleOffsetMs = arr[11] as Long,
      hdrSummary = arr[12] as HdrHardwareSummary?,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = VideoPlayerUiState(),
  )

  init {
    if (_hasPerm.value) scanVideos()
  }

  fun videoPermissionName(): String = container.permissionManager.videoMediaPermissionName()

  fun onPermissionUpdated() {
    val g = container.permissionManager.isVideoMediaGranted()
    _hasPerm.value = g
    if (g) scanVideos()
  }

  fun scanVideos() {
    viewModelScope.launch {
      _isLoading.value = true
      _videos.value = container.videoLibraryDataSource.scanLocalVideos()
      _isLoading.value = false
    }
  }

  fun setSearchQuery(q: String) { _searchQuery.value = q }

  fun openVideo(item: LocalVideoItem) {
    viewModelScope.launch {
      val prev = dao.getProgress(item.uriString)
      _resumePos.value = prev?.positionMs ?: 0L
      _speed.value = prev?.playbackSpeed ?: 1.0f
      _subOffset.value = prev?.subtitleOffsetMs ?: 0L
      _subUri.value = null
      _activeVideo.value = item
    }
  }

  fun openVideoUri(uri: Uri, titleHint: String? = null) {
    val item = container.videoLibraryDataSource.itemFromPickedUri(uri)
    val named = if (!titleHint.isNullOrBlank()) item.copy(title = titleHint) else item
    openVideo(named)
  }

  fun closePlayer() {
    _activeVideo.value = null
  }

  fun saveProgress(positionMs: Long, durationMs: Long) {
    val v = _activeVideo.value ?: return
    viewModelScope.launch {
      dao.upsertProgress(
        VideoProgressEntity(
          uriString = v.uriString,
          title = v.title,
          positionMs = positionMs.coerceAtLeast(0L),
          durationMs = durationMs.coerceAtLeast(0L),
          playbackSpeed = _speed.value,
          subtitleOffsetMs = _subOffset.value,
        )
      )
    }
  }

  fun setAspectMode(mode: VideoAspectMode) { _aspectMode.value = mode }
  fun setDecoderMode(mode: VideoDecoderMode) { _decoderMode.value = mode }
  fun setSpeed(spd: Float) { _speed.value = spd.coerceIn(0.25f, 2.5f) }
  fun attachExternalSubtitle(uri: Uri) { _subUri.value = uri }
  fun shiftSubtitleOffset(deltaMs: Long) { _subOffset.value += deltaMs }
}
