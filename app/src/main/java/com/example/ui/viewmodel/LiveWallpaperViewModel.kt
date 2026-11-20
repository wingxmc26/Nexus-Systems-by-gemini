package com.example.ui.viewmodel

import android.app.Application
import android.app.WallpaperManager
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.WallpaperItemEntity
import com.example.data.model.LiveWallpaperConfig
import com.example.data.model.WallpaperMediaType
import com.example.di.NexusContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LiveWallpaperUiState(
  val config: LiveWallpaperConfig = LiveWallpaperConfig(),
  val library: List<WallpaperItemEntity> = emptyList(),
  val activeLocalPath: String? = null,
  val isImporting: Boolean = false,
  val statusMessage: String? = null,
)

class LiveWallpaperViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)
  private val dao = container.database.wallpaperLibraryDao()

  private val _isImporting = MutableStateFlow(false)
  private val _statusMessage = MutableStateFlow<String?>(null)
  private val _isActiveWallpaper = MutableStateFlow(container.liveWallpaperDataSource.isNexusWallpaperActive())

  val uiState: StateFlow<LiveWallpaperUiState> = combine(
    container.preferencesRepository.wallpaperConfig,
    dao.observeAll(),
    _isImporting,
    _statusMessage,
    _isActiveWallpaper,
  ) { config, lib, importing, msg, isActive ->
    val activeItem = lib.firstOrNull { it.id == config.activeWallpaperId }
    val resolvedFile = container.liveWallpaperDataSource.resolveActiveFile(
      activeItem?.localFilePath ?: config.activeLocalPath
    )
    val hasLocal = resolvedFile != null && resolvedFile.exists() && resolvedFile.length() > 0L

    LiveWallpaperUiState(
      config = config.copy(
        hasLocalVideo = hasLocal,
        isCurrentlySetAsWallpaper = isActive,
      ),
      library = lib,
      activeLocalPath = resolvedFile?.absolutePath,
      isImporting = importing,
      statusMessage = msg,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = LiveWallpaperUiState(),
  )

  fun refreshWallpaperStatus() {
    _isActiveWallpaper.value = container.liveWallpaperDataSource.isNexusWallpaperActive()
  }

  fun importMedia(uri: Uri) {
    viewModelScope.launch {
      _isImporting.value = true
      _statusMessage.value = null
      container.liveWallpaperDataSource.importMediaFromUri(uri)
        .onSuccess { res ->
          val newId = dao.insert(
            WallpaperItemEntity(
              title = res.title,
              mediaType = res.mediaType.name,
              localFilePath = res.localPath,
            )
          )
          container.preferencesRepository.setWallpaperSelection(
            activeId = newId,
            fileName = res.title,
            mediaType = res.mediaType,
            localPath = res.localPath,
          )
          _statusMessage.value = "Added '${res.title}' (${res.mediaType.label}) to your Wallpaper Library."
        }
        .onFailure { err ->
          _statusMessage.value = err.message ?: "Failed to import selected wallpaper media."
        }
      _isImporting.value = false
    }
  }

  fun selectLibraryItem(item: WallpaperItemEntity) {
    viewModelScope.launch {
      val type = runCatching { WallpaperMediaType.valueOf(item.mediaType) }
        .getOrDefault(WallpaperMediaType.VIDEO)
      container.preferencesRepository.setWallpaperSelection(
        activeId = item.id,
        fileName = item.title,
        mediaType = type,
        localPath = item.localFilePath,
      )
      _statusMessage.value = "Selected '${item.title}'."
    }
  }

  fun deleteLibraryItem(item: WallpaperItemEntity) {
    viewModelScope.launch {
      val currentId = uiState.value.config.activeWallpaperId
      container.liveWallpaperDataSource.deleteLibraryFile(item)
      dao.deleteById(item.id)
      if (currentId == item.id) {
        container.preferencesRepository.setWallpaperSelection(0L, null, WallpaperMediaType.VIDEO, null)
      }
      _statusMessage.value = "Removed '${item.title}'."
    }
  }

  fun applyStaticImage(targetFlags: Int) {
    val path = uiState.value.activeLocalPath ?: return
    val targetLabel = when (targetFlags) {
      WallpaperManager.FLAG_SYSTEM -> "Home screen"
      WallpaperManager.FLAG_LOCK -> "Lock screen"
      else -> "Home & Lock screens"
    }
    viewModelScope.launch {
      container.liveWallpaperDataSource.applyStaticImageWallpaper(path, targetFlags)
        .onSuccess {
          _statusMessage.value = "Applied image wallpaper to $targetLabel."
        }
        .onFailure { err ->
          _statusMessage.value = "Failed to set static wallpaper: ${err.message}"
        }
    }
  }

  fun setMuteAudio(mute: Boolean) {
    viewModelScope.launch { container.preferencesRepository.setWallpaperMuteAudio(mute) }
  }

  fun setScaleCrop(crop: Boolean) {
    viewModelScope.launch { container.preferencesRepository.setWallpaperScaleCrop(crop) }
  }

  fun setLoopPlayback(loop: Boolean) {
    viewModelScope.launch { container.preferencesRepository.setWallpaperLoop(loop) }
  }

  fun setPlaybackSpeed(speed: Float) {
    viewModelScope.launch { container.preferencesRepository.setWallpaperSpeed(speed) }
  }

  fun setBatterySaverPause(pause: Boolean) {
    viewModelScope.launch { container.preferencesRepository.setWallpaperBatteryPause(pause) }
  }

  fun setTransform(offsetX: Float, offsetY: Float, zoom: Float) {
    viewModelScope.launch { container.preferencesRepository.setWallpaperTransform(offsetX, offsetY, zoom) }
  }

  fun buildSetWallpaperIntents(): List<Intent> {
    return container.liveWallpaperDataSource.buildSetLiveWallpaperIntents()
  }

  fun buildSetWallpaperIntent(): Intent? {
    return container.liveWallpaperDataSource.buildSetLiveWallpaperIntent()
  }

  fun isLiveWallpaperSupported(): Boolean {
    return container.liveWallpaperDataSource.isLiveWallpaperSupported()
  }

  fun setStatusMessage(message: String) {
    _statusMessage.value = message
  }

  fun clearMessage() {
    _statusMessage.value = null
  }
}
