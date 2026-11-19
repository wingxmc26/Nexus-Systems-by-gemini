package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.HardwareTelemetry
import com.example.di.NexusContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface HardwareUiState {
  data object Loading : HardwareUiState
  data class Success(val data: HardwareTelemetry) : HardwareUiState
  data class Error(val message: String) : HardwareUiState
}

class HardwareViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)
  private val _uiState = MutableStateFlow<HardwareUiState>(HardwareUiState.Loading)
  val uiState: StateFlow<HardwareUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      while (isActive) {
        loadOnce()
        delay(2000L)
      }
    }
  }

  fun refresh() {
    viewModelScope.launch { loadOnce() }
  }

  private suspend fun loadOnce() {
    runCatching { container.hardwareDataSource.readHardwareTelemetry() }
      .onSuccess { _uiState.value = HardwareUiState.Success(it) }
      .onFailure { _uiState.value = HardwareUiState.Error(it.message ?: "Hardware probe failed") }
  }
}
