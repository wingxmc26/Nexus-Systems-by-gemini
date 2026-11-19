package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.InstalledAppEntry
import com.example.data.model.MetricValue
import com.example.di.NexusContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppFilterMode(val label: String) {
  ALL("All"),
  USER("User Apps"),
  SYSTEM("System"),
}

enum class AppSortMode(val label: String) {
  NAME("Name"),
  SIZE("APK Size"),
  TARGET_SDK("Target SDK"),
}

data class AppInspectorUiState(
  val isLoading: Boolean = true,
  val searchQuery: String = "",
  val filterMode: AppFilterMode = AppFilterMode.ALL,
  val sortMode: AppSortMode = AppSortMode.NAME,
  val totalVisibleCount: Int = 0,
  val apps: List<InstalledAppEntry> = emptyList(),
  val error: String? = null,
)

class AppInspectorViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)

  private val _allApps = MutableStateFlow<List<InstalledAppEntry>>(emptyList())
  private val _isLoading = MutableStateFlow(true)
  private val _error = MutableStateFlow<String?>(null)
  private val _searchQuery = MutableStateFlow("")
  private val _filterMode = MutableStateFlow(AppFilterMode.ALL)
  private val _sortMode = MutableStateFlow(AppSortMode.NAME)

  val uiState: StateFlow<AppInspectorUiState> = combine(
    _allApps,
    _isLoading,
    _searchQuery,
    _filterMode,
    _sortMode,
  ) { all, loading, query, filter, sort ->
    val q = query.trim().lowercase()
    val filtered = all.filter { entry ->
      val matchesFilter = when (filter) {
        AppFilterMode.ALL -> true
        AppFilterMode.USER -> !entry.isSystemApp
        AppFilterMode.SYSTEM -> entry.isSystemApp
      }
      val matchesQuery = q.isEmpty() ||
        entry.appName.lowercase().contains(q) ||
        entry.packageName.lowercase().contains(q)
      matchesFilter && matchesQuery
    }

    val sorted = when (sort) {
      AppSortMode.NAME -> filtered.sortedBy { it.appName.lowercase() }
      AppSortMode.SIZE -> filtered.sortedByDescending {
        (it.apkSizeBytes as? MetricValue.Available)?.value ?: -1L
      }
      AppSortMode.TARGET_SDK -> filtered.sortedByDescending { it.targetSdk }
    }

    AppInspectorUiState(
      isLoading = loading,
      searchQuery = query,
      filterMode = filter,
      sortMode = sort,
      totalVisibleCount = all.size,
      apps = sorted,
      error = _error.value,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = AppInspectorUiState(),
  )

  init {
    reload()
  }

  fun reload() {
    viewModelScope.launch {
      _isLoading.value = true
      _error.value = null
      runCatching { container.appInspectorDataSource.loadLaunchableApps() }
        .onSuccess { list ->
          _allApps.value = list
          _isLoading.value = false
        }
        .onFailure { err ->
          _error.value = err.message ?: "Failed to query launchable applications"
          _isLoading.value = false
        }
    }
  }

  fun setSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun setFilterMode(mode: AppFilterMode) {
    _filterMode.value = mode
  }

  fun setSortMode(mode: AppSortMode) {
    _sortMode.value = mode
  }
}
