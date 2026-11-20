package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CategoryBreakdown
import com.example.data.model.DirectFileEntry
import com.example.data.model.SafAnalysisState
import com.example.data.model.SafFileEntry
import com.example.data.model.StorageOverview
import com.example.di.NexusContainer
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DirectSortMode(val label: String) {
  NAME("Name"),
  SIZE("Size"),
  DATE("Date"),
  TYPE("Type"),
}

data class StorageUiState(
  val isLoadingOverview: Boolean = true,
  val overview: StorageOverview? = null,
  val hasAllFilesAccess: Boolean = false,
  val safAnalysis: SafAnalysisState = SafAnalysisState(),
  val currentBrowsePath: String = "",
  val statusMessage: String? = null,
  // Direct file browser state
  val directCurrentPath: String = "",
  val directEntries: List<DirectFileEntry> = emptyList(),
  val directError: String? = null,
  val directSearchQuery: String = "",
  val directSortMode: DirectSortMode = DirectSortMode.NAME,
  val selectedDirectPaths: Set<String> = emptySet(),
  val clipboardPaths: List<String> = emptyList(),
  val clipboardIsMove: Boolean = false,
  val calculatedFolderSizes: Map<String, Long> = emptyMap(),
  val isDirectScanning: Boolean = false,
  val directCategories: List<CategoryBreakdown> = emptyList(),
  val directRecentFiles: List<DirectFileEntry> = emptyList(),
  val directLargeFiles: List<DirectFileEntry> = emptyList(),
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

  val filteredDirectEntries: List<DirectFileEntry>
    get() {
      val q = directSearchQuery.trim().lowercase()
      val base = if (q.isEmpty()) directEntries else directEntries.filter { it.name.lowercase().contains(q) }
      val comparator = when (directSortMode) {
        DirectSortMode.NAME -> compareByDescending<DirectFileEntry> { it.isDirectory }.thenBy { it.name.lowercase() }
        DirectSortMode.SIZE -> compareByDescending<DirectFileEntry> { it.isDirectory }.thenByDescending { it.sizeBytes }
        DirectSortMode.DATE -> compareByDescending<DirectFileEntry> { it.isDirectory }.thenByDescending { it.lastModifiedMs }
        DirectSortMode.TYPE -> compareByDescending<DirectFileEntry> { it.isDirectory }.thenBy { it.category.label }.thenBy { it.name.lowercase() }
      }
      return base.sortedWith(comparator)
    }

  val isProtectedDirectLocation: Boolean
    get() {
      val norm = directCurrentPath.replace('\\', '/')
      return norm.contains("/Android/data") || norm.contains("/Android/obb")
    }
}

class StorageViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)
  private val ds = container.storageDataSource
  private val pm = container.permissionManager
  private val _uiState = MutableStateFlow(StorageUiState(directCurrentPath = ds.getRootStoragePath()))
  val uiState: StateFlow<StorageUiState> = _uiState.asStateFlow()

  private var scanJob: Job? = null
  private var directScanJob: Job? = null
  private var lastTreeUri: Uri? = null

  init {
    refreshOverview()
    refreshAllFilesPermission()
  }

  fun refreshOverview() {
    viewModelScope.launch {
      val overview = runCatching { ds.readStorageOverview() }.getOrNull()
      _uiState.update { it.copy(isLoadingOverview = false, overview = overview) }
    }
  }

  fun refreshAllFilesPermission() {
    val granted = pm.isAllFilesAccessGranted()
    _uiState.update { it.copy(hasAllFilesAccess = granted) }
    if (granted) {
      openDirectDirectory(_uiState.value.directCurrentPath.ifBlank { ds.getRootStoragePath() })
    }
  }

  fun buildAllFilesAccessIntent(): Intent = pm.buildAllFilesAccessIntent()

  fun buildDownloadsInitialUri(): Uri? = ds.buildDownloadsInitialUri()

  fun getRootStoragePath(): String = ds.getRootStoragePath()

  fun openDirectDirectory(path: String) {
    viewModelScope.launch {
      val res = ds.listDirectDirectory(path)
      res.onSuccess { entries ->
        _uiState.update {
          it.copy(
            directCurrentPath = path,
            directEntries = entries,
            directError = null,
            selectedDirectPaths = emptySet(),
          )
        }
      }.onFailure { err ->
        _uiState.update {
          it.copy(
            directCurrentPath = path,
            directEntries = emptyList(),
            directError = err.message ?: "Unable to read directory",
            selectedDirectPaths = emptySet(),
          )
        }
      }
    }
  }

  fun navigateDirectUp(): Boolean {
    val cur = File(_uiState.value.directCurrentPath)
    val root = File(ds.getRootStoragePath())
    if (cur.absolutePath == root.absolutePath) return false
    val parent = cur.parentFile ?: return false
    if (parent.absolutePath.length < root.absolutePath.length) {
      openDirectDirectory(root.absolutePath)
    } else {
      openDirectDirectory(parent.absolutePath)
    }
    return true
  }

  fun openDownloadsDirect() = openDirectDirectory(ds.getDownloadsPath())

  fun openPicturesDirect() {
    val pics = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath
    openDirectDirectory(pics)
  }

  fun setDirectSearch(query: String) {
    _uiState.update { it.copy(directSearchQuery = query) }
  }

  fun setDirectSort(mode: DirectSortMode) {
    _uiState.update { it.copy(directSortMode = mode) }
  }

  fun toggleDirectSelection(path: String) {
    _uiState.update { s ->
      val next = s.selectedDirectPaths.toMutableSet()
      if (!next.add(path)) next.remove(path)
      s.copy(selectedDirectPaths = next)
    }
  }

  fun clearDirectSelection() {
    _uiState.update { it.copy(selectedDirectPaths = emptySet()) }
  }

  fun createFolder(name: String) {
    viewModelScope.launch {
      ds.createDirectFolder(_uiState.value.directCurrentPath, name).onSuccess { created ->
        _uiState.update { it.copy(statusMessage = "Created folder '${created.name}'.") }
        openDirectDirectory(_uiState.value.directCurrentPath)
      }.onFailure { err ->
        _uiState.update { it.copy(statusMessage = err.message ?: "Unable to create folder.") }
      }
    }
  }

  fun renameDirectFile(path: String, newName: String) {
    viewModelScope.launch {
      ds.renameDirectFile(path, newName).onSuccess { dst ->
        _uiState.update { it.copy(statusMessage = "Renamed to '${dst.name}'.") }
        openDirectDirectory(_uiState.value.directCurrentPath)
      }.onFailure { err ->
        _uiState.update { it.copy(statusMessage = err.message ?: "Rename failed.") }
      }
    }
  }

  fun copyPathsToClipboard(paths: List<String>) {
    _uiState.update {
      it.copy(
        clipboardPaths = paths,
        clipboardIsMove = false,
        selectedDirectPaths = emptySet(),
        statusMessage = "${paths.size} item(s) ready to copy. Navigate to destination and tap Paste.",
      )
    }
  }

  fun movePathsToClipboard(paths: List<String>) {
    _uiState.update {
      it.copy(
        clipboardPaths = paths,
        clipboardIsMove = true,
        selectedDirectPaths = emptySet(),
        statusMessage = "${paths.size} item(s) marked to move. Navigate to destination and tap Paste.",
      )
    }
  }

  fun clearClipboard() {
    _uiState.update { it.copy(clipboardPaths = emptyList()) }
  }

  fun pasteClipboardHere() {
    val paths = _uiState.value.clipboardPaths
    if (paths.isEmpty()) return
    val isMove = _uiState.value.clipboardIsMove
    val dest = _uiState.value.directCurrentPath
    viewModelScope.launch {
      var okCount = 0
      var lastErr: String? = null
      for (src in paths) {
        val res = if (isMove) ds.moveFileOrDir(src, dest) else ds.copyFileOrDir(src, dest)
        res.onSuccess { okCount++ }.onFailure { lastErr = it.message }
      }
      _uiState.update {
        it.copy(
          clipboardPaths = emptyList(),
          statusMessage = if (okCount == paths.size) {
            "${if (isMove) "Moved" else "Copied"} $okCount item(s) to ${File(dest).name}."
          } else {
            "${if (isMove) "Moved" else "Copied"} $okCount/${paths.size}. ${lastErr.orEmpty()}"
          },
        )
      }
      openDirectDirectory(dest)
      refreshOverview()
    }
  }

  fun deleteDirectPaths(paths: List<String>) {
    viewModelScope.launch {
      ds.deleteDirectPaths(paths).onSuccess { count ->
        _uiState.update {
          it.copy(
            selectedDirectPaths = emptySet(),
            statusMessage = "Deleted $count item(s).",
          )
        }
        openDirectDirectory(_uiState.value.directCurrentPath)
        refreshOverview()
      }.onFailure { err ->
        _uiState.update { it.copy(statusMessage = err.message ?: "Delete failed.") }
      }
    }
  }

  fun extractZip(zipPath: String) {
    viewModelScope.launch {
      ds.extractZipArchive(zipPath).onSuccess { outDir ->
        _uiState.update { it.copy(statusMessage = "Extracted archive to '${outDir.name}/'.") }
        openDirectDirectory(_uiState.value.directCurrentPath)
      }.onFailure { err ->
        _uiState.update { it.copy(statusMessage = "Extract failed: ${err.message}") }
      }
    }
  }

  fun compressSelectedToZip(archiveName: String) {
    val selected = _uiState.value.selectedDirectPaths.toList()
    if (selected.isEmpty()) return
    val cleanName = archiveName.trim().ifBlank { "nexus_archive" }.let { if (it.endsWith(".zip", true)) it else "$it.zip" }
    val outPath = File(_uiState.value.directCurrentPath, cleanName).absolutePath
    viewModelScope.launch {
      ds.compressFilesToZip(selected, outPath).onSuccess { outFile ->
        _uiState.update {
          it.copy(
            selectedDirectPaths = emptySet(),
            statusMessage = "Created ZIP archive '${outFile.name}'.",
          )
        }
        openDirectDirectory(_uiState.value.directCurrentPath)
      }.onFailure { err ->
        _uiState.update { it.copy(statusMessage = "Compression failed: ${err.message}") }
      }
    }
  }

  fun shareDirectFile(path: String) {
    ds.buildShareIntentForFile(path).onSuccess { intent ->
      getApplication<Application>().startActivity(intent)
    }.onFailure { err ->
      _uiState.update { it.copy(statusMessage = err.message ?: "Unable to share file.") }
    }
  }

  fun calculateFolderSize(path: String) {
    viewModelScope.launch {
      val sz = ds.calculateFolderSize(path)
      _uiState.update {
        it.copy(calculatedFolderSizes = it.calculatedFolderSizes + (path to sz))
      }
    }
  }

  fun scanDirectOverview() {
    directScanJob?.cancel()
    directScanJob = viewModelScope.launch {
      _uiState.update { it.copy(isDirectScanning = true) }
      runCatching {
        ds.scanDirectOverviewStats()
      }.onSuccess { (cats, recent, large) ->
        _uiState.update {
          it.copy(
            isDirectScanning = false,
            directCategories = cats,
            directRecentFiles = recent,
            directLargeFiles = large,
          )
        }
      }.onFailure { err ->
        _uiState.update {
          it.copy(
            isDirectScanning = false,
            statusMessage = "Direct scan failed: ${err.message}",
          )
        }
      }
    }
  }

  fun analyzeSafTree(uri: Uri) {
    lastTreeUri = uri
    runCatching {
      getApplication<Application>().contentResolver.takePersistableUriPermission(
        uri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
      )
    }

    scanJob?.cancel()
    scanJob = viewModelScope.launch {
      _uiState.update {
        it.copy(
          currentBrowsePath = "",
          statusMessage = null,
          safAnalysis = SafAnalysisState(
            treeUriString = uri.toString(),
            isScanning = true,
          ),
        )
      }

      runCatching {
        ds.analyzeSafTree(uri) { count, bytes ->
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

  fun deleteEntry(entry: SafFileEntry) {
    viewModelScope.launch {
      val deleted = ds.deleteSafDocument(entry.uriString)
      if (deleted) {
        _uiState.update { it.copy(statusMessage = "Deleted '${entry.name}'.") }
        refreshOverview()
        lastTreeUri?.let { analyzeSafTree(it) }
      } else {
        _uiState.update {
          it.copy(statusMessage = "Unable to delete '${entry.name}'. Ensure write permission was granted in the folder picker.")
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

  fun clearMessage() {
    _uiState.update { it.copy(statusMessage = null) }
  }
}
