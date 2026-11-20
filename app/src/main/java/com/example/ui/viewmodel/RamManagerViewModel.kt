package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ManagedAppSummary
import com.example.data.model.SystemMemoryDetails
import com.example.di.NexusContainer
import com.example.util.Formatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RamAppFilter(val label: String) {
  USER("User Apps"),
  ALL("All Visible"),
  SYSTEM("System"),
}

data class RamManagerUiState(
  val isLoadingMemory: Boolean = true,
  val memory: SystemMemoryDetails? = null,
  val isLoadingApps: Boolean = true,
  val apps: List<ManagedAppSummary> = emptyList(),
  val searchQuery: String = "",
  val filter: RamAppFilter = RamAppFilter.USER,
  val actionMessage: String? = null,
)

class RamManagerViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)

  private val _memory = MutableStateFlow<SystemMemoryDetails?>(null)
  private val _allApps = MutableStateFlow<List<ManagedAppSummary>>(emptyList())
  private val _isLoadingApps = MutableStateFlow(true)
  private val _searchQuery = MutableStateFlow("")
  private val _filter = MutableStateFlow(RamAppFilter.USER)
  private val _actionMessage = MutableStateFlow<String?>(null)

  val uiState: StateFlow<RamManagerUiState> = combine(
    _memory,
    _allApps,
    _isLoadingApps,
    _searchQuery,
    _filter,
    _actionMessage,
  ) { flows ->
    val mem = flows[0] as SystemMemoryDetails?
    @Suppress("UNCHECKED_CAST")
    val allApps = flows[1] as List<ManagedAppSummary>
    val loadingApps = flows[2] as Boolean
    val query = flows[3] as String
    val filter = flows[4] as RamAppFilter
    val msg = flows[5] as String?

    val q = query.trim().lowercase()
    val filtered = allApps.filter { app ->
      val matchFilter = when (filter) {
        RamAppFilter.ALL -> true
        RamAppFilter.USER -> !app.isSystemApp
        RamAppFilter.SYSTEM -> app.isSystemApp
      }
      val matchQuery = q.isEmpty() ||
        app.appName.lowercase().contains(q) ||
        app.packageName.lowercase().contains(q)
      matchFilter && matchQuery
    }

    RamManagerUiState(
      isLoadingMemory = mem == null,
      memory = mem,
      isLoadingApps = loadingApps,
      apps = filtered,
      searchQuery = query,
      filter = filter,
      actionMessage = msg,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = RamManagerUiState(),
  )

  init {
    viewModelScope.launch {
      while (isActive) {
        _memory.value = runCatching { container.ramManagerDataSource.readSystemMemoryDetails() }.getOrNull()
        delay(2000L)
      }
    }
    reloadApps()
  }

  fun reloadApps() {
    viewModelScope.launch {
      _isLoadingApps.value = true
      _allApps.value = runCatching { container.ramManagerDataSource.loadManageableApps() }.getOrDefault(emptyList())
      _isLoadingApps.value = false
    }
  }

  fun refreshMemoryNow() {
    viewModelScope.launch {
      _memory.value = runCatching { container.ramManagerDataSource.readSystemMemoryDetails() }.getOrNull()
    }
  }

  fun setSearchQuery(q: String) {
    _searchQuery.value = q
  }

  fun setFilter(filter: RamAppFilter) {
    _filter.value = filter
  }

  fun clearOwnCache() {
    viewModelScope.launch {
      val freed = container.ramManagerDataSource.clearOwnAppCache()
      _memory.value = runCatching { container.ramManagerDataSource.readSystemMemoryDetails() }.getOrNull()
      _actionMessage.value = "Cleared Nexus System cache (${Formatters.formatBytes(freed)}) and requested GC."
    }
  }

  fun clearMessage() {
    _actionMessage.value = null
  }
}
