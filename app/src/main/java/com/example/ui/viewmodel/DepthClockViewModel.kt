package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ClockPresetEntity
import com.example.data.local.WidgetClockSerializer
import com.example.data.model.DepthClockConfig
import com.example.di.NexusContainer
import com.example.widget.NexusWidgetUpdater
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DepthClockUiState(
  val config: DepthClockConfig = DepthClockConfig(),
  val presetName: String = "AMOLED Crimson Depth",
  val presets: List<ClockPresetEntity> = emptyList(),
  val statusMessage: String? = null,
)

class DepthClockViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)
  private val dao = container.database.clockPresetDao()

  private val _config = MutableStateFlow(DepthClockConfig())
  private val _presetName = MutableStateFlow("AMOLED Crimson Depth")
  private val _statusMessage = MutableStateFlow<String?>(null)

  val uiState: StateFlow<DepthClockUiState> = combine(
    _config,
    _presetName,
    dao.observeAll(),
    _statusMessage,
  ) { cfg, name, list, msg ->
    DepthClockUiState(
      config = cfg,
      presetName = name,
      presets = list,
      statusMessage = msg,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = DepthClockUiState(),
  )

  init {
    viewModelScope.launch {
      val json = container.preferencesRepository.getDepthClockJson()
      if (!json.isNullOrBlank()) {
        _config.value = WidgetClockSerializer.deserializeDepthClock(json)
      }
    }
  }

  fun setPresetName(name: String) {
    _presetName.value = name
  }

  fun updateConfig(transform: (DepthClockConfig) -> DepthClockConfig) {
    val updated = transform(_config.value)
    _config.value = updated
    viewModelScope.launch {
      container.preferencesRepository.saveDepthClockJson(WidgetClockSerializer.serializeDepthClock(updated))
      NexusWidgetUpdater.refreshAll(getApplication())
    }
  }

  fun importLayerImage(uri: Uri, isForegroundMask: Boolean) {
    viewModelScope.launch(Dispatchers.IO) {
      runCatching {
        val dir = File(getApplication<Application>().filesDir, "depth_clock").apply { if (!exists()) mkdirs() }
        val prefix = if (isForegroundMask) "fg_mask_" else "bg_photo_"
        val outFile = File(dir, "$prefix${System.currentTimeMillis()}.png")
        getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
          FileOutputStream(outFile).use { output -> input.copyTo(output) }
        } ?: error("Unable to read selected image")
        outFile.absolutePath
      }.onSuccess { path ->
        updateConfig { cfg ->
          if (isForegroundMask) cfg.copy(foregroundMaskImagePath = path)
          else cfg.copy(backgroundImagePath = path)
        }
        _statusMessage.value = if (isForegroundMask) "Loaded foreground cutout overlay." else "Loaded background photo."
      }.onFailure {
        _statusMessage.value = "Failed to import image: ${it.message}"
      }
    }
  }

  fun savePreset(activate: Boolean) {
    viewModelScope.launch {
      val clean = _presetName.value.trim().ifBlank { "Depth Clock" }
      val json = WidgetClockSerializer.serializeDepthClock(_config.value)
      container.preferencesRepository.saveDepthClockJson(json)
      val id = dao.insert(
        ClockPresetEntity(
          name = clean,
          configJson = json,
          isActiveWidget = activate,
        )
      )
      if (activate) {
        dao.clearActiveFlag()
        dao.markActive(id)
      }
      NexusWidgetUpdater.refreshAll(getApplication())
      _statusMessage.value = if (activate) "Saved & applied '$clean' to Depth Clock widget." else "Saved clock preset '$clean'."
    }
  }

  fun loadPreset(preset: ClockPresetEntity) {
    val parsed = WidgetClockSerializer.deserializeDepthClock(preset.configJson)
    _config.value = parsed
    _presetName.value = preset.name
    viewModelScope.launch {
      container.preferencesRepository.saveDepthClockJson(preset.configJson)
      dao.clearActiveFlag()
      dao.markActive(preset.id)
      NexusWidgetUpdater.refreshAll(getApplication())
    }
    _statusMessage.value = "Applied '${preset.name}'."
  }

  fun deletePreset(id: Long) {
    viewModelScope.launch {
      dao.deleteById(id)
    }
  }
}
