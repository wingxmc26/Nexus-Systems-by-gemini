package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.example.data.model.SystemControlEntry
import com.example.di.NexusContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ShortcutsUiState(
  val canWriteSettings: Boolean = false,
  val brightnessPercent: Int = 50,
  val isAutoBrightness: Boolean = false,
  val screenTimeoutMs: Int = 30_000,
  val isAutoRotate: Boolean = false,
  val controls: List<SystemControlEntry> = emptyList(),
  val feedback: String? = null,
)

class ShortcutsViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)

  private val _uiState = MutableStateFlow(ShortcutsUiState())
  val uiState: StateFlow<ShortcutsUiState> = _uiState.asStateFlow()

  init {
    refresh()
  }

  fun refresh() {
    val ds = container.shortcutsDataSource
    val (pct, auto) = ds.readBrightnessPercentAndAuto()
    _uiState.update {
      it.copy(
        canWriteSettings = ds.canWriteSettings(),
        brightnessPercent = pct,
        isAutoBrightness = auto,
        screenTimeoutMs = ds.readScreenTimeoutMs(),
        isAutoRotate = ds.readAutoRotate(),
        controls = ds.loadControls(),
      )
    }
  }

  fun buildWriteSettingsIntent(): Intent = container.shortcutsDataSource.buildWriteSettingsIntent()

  fun setBrightnessPercent(percent: Int) {
    if (container.shortcutsDataSource.setBrightnessPercent(percent)) {
      _uiState.update { it.copy(brightnessPercent = percent, isAutoBrightness = false, feedback = null) }
    } else {
      _uiState.update { it.copy(feedback = "Grant Modify System Settings permission first.") }
    }
  }

  fun setAutoBrightness(enabled: Boolean) {
    if (container.shortcutsDataSource.setAutoBrightness(enabled)) {
      refresh()
    } else {
      _uiState.update { it.copy(feedback = "Grant Modify System Settings permission first.") }
    }
  }

  fun setScreenTimeoutMs(ms: Int) {
    if (container.shortcutsDataSource.setScreenTimeoutMs(ms)) {
      refresh()
    } else {
      _uiState.update { it.copy(feedback = "Grant Modify System Settings permission first.") }
    }
  }

  fun setAutoRotate(enabled: Boolean) {
    if (container.shortcutsDataSource.setAutoRotate(enabled)) {
      refresh()
    } else {
      _uiState.update { it.copy(feedback = "Grant Modify System Settings permission first.") }
    }
  }

  fun openControl(entry: SystemControlEntry): Boolean {
    return container.shortcutsDataSource.openControl(entry)
  }

  fun clearFeedback() {
    _uiState.update { it.copy(feedback = null) }
  }
}
