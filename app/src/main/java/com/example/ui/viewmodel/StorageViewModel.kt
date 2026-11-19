package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.SafAnalysisState
import com.example.data.model.SafFileEntry
import com.example.data.model.StorageOverview
import com.example.di.NexusContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StorageUiState(
  val isLoadingOverview: Boolean = true,
  val overview: StorageOverview? = null,
  val safAnalysis: SafAnalysisState = SafAnalysisState(),
  val currentBrowsePath: String = "",
) {
  val currentFolderChildren: List<SafFileEntry>
    get() {
      val all = safAnalysis.allEntries
      if (all.isEmpty()) return emptyList()
      return all.filter { entry ->
        val parent = entry.relativePath.substringBeforeLast('/', "")
        parent == currentBrowsePath
      }.sortedWith(compareByDescending<SafFileEntry> { it.isDirectory }.thenByDescending { it.sizeBytes })
    }
}

class StorageViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)
  private val _uiState = MutableStateFlow(StorageUiState())
  val uiState: StateFlow<StorageUiState> = _uiState.asStateFlow()

  private var scanJob: Job? = null

  init {
    refreshOverview()
  }

  fun refreshOverview() {
    viewModelScope.launch {
      val overview = runCatching { container.storageDataSource.readStorageOverview() }.getOrNull()
      _uiState.update { it.copy(isLoadingOverview = false, overview = overview) }
    }
  }

  fun analyzeSafTree(uri: Uri) {
    runCatching {
      getApplication<Application>().contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION,
      )
    }

    scanJob?.cancel()
    scanJob = viewModelScope.launch {
      _uiState.update {
        it.copy(
          currentBrowsePath = "",
          safAnalysis = SafAnalysisState(
            treeUriString = uri.toString(),
            isScanning = true,
          ),
        )
      }

      runCatching {
        container.storageDataSource.analyzeSafTree(uri) { count, bytes ->
          _uiState.update { state ->
            state.copy(
              safAnalysis = state.safAnalysis.copy(
                isScanning = true,
                scannedCount = count,
                totalScannedBytes = bytes,
              ),
            )
          }
        }
      }.onSuccess { result ->
        _uiState.update { it.copy(safAnalysis = result, currentBrowsePath = "") }
      }.onFailure { err ->
        _uiState.update {
          it.copy(
            safAnalysis = SafAnalysisState(
              treeUriString = uri.toString(),
              isScanning = false,
              errorMessage = err.message ?: "Unable to scan selected folder",
            ),
          )
        }
      }
    }
  }

  fun cancelScan() {
    scanJob?.cancel()
    _uiState.update {
      it.copy(safAnalysis = it.safAnalysis.copy(isScanning = false))
    }
  }

  fun openFolder(relativePath: String) {
    _uiState.update { it.copy(currentBrowsePath = relativePath) }
  }

  fun navigateUp(): Boolean {
    val cur = _uiState.value.currentBrowsePath
    if (cur.isEmpty()) return false
    val parent = cur.substringBeforeLast('/', "")
    _uiState.update { it.copy(currentBrowsePath = parent) }
    return true
  }
}
