package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ManagedAppSummary
import com.example.data.model.MetricValue
import com.example.di.NexusContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppFilterMode(val label: String) {
  ALL("All"),
  USER("User"),
  SYSTEM("System"),
  RECENT("Recently Used"),
}

enum class AppSortMode(val label: String) {
  NAME("Name"),
  SIZE("Size"),
  INSTALLED("Install Date"),
  UPDATED("Update Date"),
}

data class AppManagerUiState(
  val isLoading: Boolean = true,
  val hasUsageAccess: Boolean = false,
  val allApps: List<ManagedAppSummary> = emptyList(),
  val searchQuery: String = "",
  val filterMode: AppFilterMode = AppFilterMode.USER,
  val sortMode: AppSortMode = AppSortMode.NAME,
  val selectedApp: ManagedAppSummary? = null,
  val statusMessage: String? = null,
) {
  val filteredApps: List<ManagedAppSummary>
    get() {
      val q = searchQuery.trim().lowercase()
      val base = allApps.filter { app ->
        val matchesFilter = when (filterMode) {
          AppFilterMode.ALL -> true
          AppFilterMode.USER -> !app.isSystemApp
          AppFilterMode.SYSTEM -> app.isSystemApp
          AppFilterMode.RECENT -> (app.lastUsedMs ?: 0L) > 0L
        }
        val matchesQuery = q.isEmpty() ||
          app.appName.lowercase().contains(q) ||
          app.packageName.lowercase().contains(q)
        matchesFilter && matchesQuery
      }

      return when (sortMode) {
        AppSortMode.NAME -> base.sortedBy { it.appName.lowercase() }
        AppSortMode.SIZE -> base.sortedByDescending { (it.apkSizeBytes as? MetricValue.Available)?.value ?: 0L }
        AppSortMode.INSTALLED -> base.sortedByDescending { it.installedAtMs }
        AppSortMode.UPDATED -> base.sortedByDescending { it.updatedAtMs }
      }
    }
}

class AppManagerViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)
  private val ds = container.appManagerDataSource
  private val pm = container.permissionManager
  private val favoritesRepo = container.favoritesRepository

  private val _uiState = MutableStateFlow(AppManagerUiState())
  val uiState: StateFlow<AppManagerUiState> = _uiState.asStateFlow()

  init {
    refreshApps()
  }

  fun refreshApps() {
    viewModelScope.launch {
      val hasUsage = pm.isUsageStatsGranted()
      _uiState.update { it.copy(isLoading = true, hasUsageAccess = hasUsage) }
      val apps = ds.loadInstalledApps()
      _uiState.update { it.copy(isLoading = false, hasUsageAccess = hasUsage, allApps = apps) }
    }
  }

  fun buildUsageAccessIntent(): Intent = pm.buildUsageStatsIntent()
  fun setSearchQuery(q: String) = _uiState.update { it.copy(searchQuery = q) }
  fun setFilterMode(mode: AppFilterMode) = _uiState.update { it.copy(filterMode = mode) }
  fun setSortMode(mode: AppSortMode) = _uiState.update { it.copy(sortMode = mode) }
  fun selectApp(app: ManagedAppSummary?) = _uiState.update { it.copy(selectedApp = app) }

  fun launchApp(app: ManagedAppSummary) {
    if (!ds.launchApp(app.packageName)) {
      _uiState.update { it.copy(statusMessage = "${app.appName} has no launchable entry activity.") }
    }
  }

  fun openAppInfo(app: ManagedAppSummary) {
    ds.openAppInfo(app.packageName)
  }

  fun uninstallApp(app: ManagedAppSummary) {
    if (app.isSystemApp) {
      _uiState.update { it.copy(statusMessage = "System apps cannot be directly uninstalled; opening App Info instead.") }
      ds.openAppInfo(app.packageName)
    } else {
      ds.requestSystemUninstall(app.packageName)
    }
  }

  fun openDefaultAppsSettings() {
    ds.openDefaultAppsSettings()
  }

  fun shareAppDetails(app: ManagedAppSummary) {
    ds.shareAppDetails(app)
  }

  fun pinToFavorites(app: ManagedAppSummary) {
    viewModelScope.launch {
      val pkgInfo = container.packageActivityDataSource.inspectPackageByName(app.packageName).getOrNull()
      val activityClass = pkgInfo?.exportedActivities?.firstOrNull()?.className
      favoritesRepo.saveShortcut(
        packageName = app.packageName,
        activityClassName = activityClass,
        customLabel = app.appName,
        originalAppName = app.appName,
        iconKey = "apps",
        accentColorArgb = 0xFFE11D48L,
      )
      _uiState.update { it.copy(statusMessage = "Pinned ${app.appName} to Favorites.") }
    }
  }

  fun clearMessage() = _uiState.update { it.copy(statusMessage = null) }
}
