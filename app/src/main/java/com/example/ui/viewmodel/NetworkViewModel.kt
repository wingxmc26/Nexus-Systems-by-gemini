package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.NetworkDiagnostics
import com.example.di.NexusContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface NetworkUiState {
  data object Loading : NetworkUiState
  data class Success(val data: NetworkDiagnostics) : NetworkUiState
  data class Error(val message: String) : NetworkUiState
}

class NetworkViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)
  private val _uiState = MutableStateFlow<NetworkUiState>(NetworkUiState.Loading)
  val uiState: StateFlow<NetworkUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      while (isActive) {
        refreshOnce()
        delay(3000L)
      }
    }
  }

  fun refresh() {
    viewModelScope.launch { refreshOnce() }
  }

  private suspend fun refreshOnce() {
    runCatching { container.networkDataSource.readNetworkDiagnostics() }
      .onSuccess { _uiState.value = NetworkUiState.Success(it) }
      .onFailure { _uiState.value = NetworkUiState.Error(it.message ?: "Network inspection failed") }
  }
}
