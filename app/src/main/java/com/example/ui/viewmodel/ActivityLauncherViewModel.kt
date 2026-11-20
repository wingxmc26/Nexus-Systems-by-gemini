package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.FavoriteActivityEntity
import com.example.data.model.LaunchablePackageInfo
import com.example.data.model.ShortcutShape
import com.example.di.NexusContainer
import com.example.widget.NexusWidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LauncherFilterMode(val label: String) {
  ALL("All Visible"),
  USER("User Apps"),
  SYSTEM("System"),
  FAVORITES("Saved Shortcuts"),
}

data class ActivityLauncherUiState(
  val isLoading: Boolean = true,
  val searchQuery: String = "",
  val filterMode: LauncherFilterMode = LauncherFilterMode.ALL,
  val packages: List<LaunchablePackageInfo> = emptyList(),
  val manuallyInspectedPackage: LaunchablePackageInfo? = null,
  val favorites: List<FavoriteActivityEntity> = emptyList(),
  val isPinSupported: Boolean = false,
  val statusMessage: String? = null,
)

class ActivityLauncherViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)

  private val _allPackages = MutableStateFlow<List<LaunchablePackageInfo>>(emptyList())
  private val _inspectedPackage = MutableStateFlow<LaunchablePackageInfo?>(null)
  private val _isLoading = MutableStateFlow(true)
  private val _searchQuery = MutableStateFlow("")
  private val _filterMode = MutableStateFlow(LauncherFilterMode.ALL)
  private val _statusMessage = MutableStateFlow<String?>(null)

  val uiState: StateFlow<ActivityLauncherUiState> = combine(
    _allPackages,
    _isLoading,
    _searchQuery,
    _filterMode,
    container.favoritesRepository.favorites,
    _inspectedPackage,
    _statusMessage,
  ) { flows ->
    @Suppress("UNCHECKED_CAST")
    val all = flows[0] as List<LaunchablePackageInfo>
    val loading = flows[1] as Boolean
    val query = flows[2] as String
    val filter = flows[3] as LauncherFilterMode
    @Suppress("UNCHECKED_CAST")
    val favs = flows[4] as List<FavoriteActivityEntity>
    val inspected = flows[5] as LaunchablePackageInfo?
    val msg = flows[6] as String?

    val favPackages = favs.map { it.packageName }.toSet()
    val q = query.trim().lowercase()
    val filtered = all.filter { pkg ->
      val matchFilter = when (filter) {
        LauncherFilterMode.ALL -> true
        LauncherFilterMode.USER -> !pkg.isSystemApp
        LauncherFilterMode.SYSTEM -> pkg.isSystemApp
        LauncherFilterMode.FAVORITES -> pkg.packageName in favPackages
      }
      val matchQuery = q.isEmpty() ||
        pkg.appName.lowercase().contains(q) ||
        pkg.packageName.lowercase().contains(q) ||
        pkg.exportedActivities.any { it.simpleName.lowercase().contains(q) }
      matchFilter && matchQuery
    }

    ActivityLauncherUiState(
      isLoading = loading,
      searchQuery = query,
      filterMode = filter,
      packages = filtered,
      manuallyInspectedPackage = inspected,
      favorites = favs,
      isPinSupported = container.packageActivityDataSource.isPinShortcutSupported(),
      statusMessage = msg,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = ActivityLauncherUiState(),
  )

  init {
    reload()
  }

  fun reload() {
    viewModelScope.launch {
      _isLoading.value = true
      _allPackages.value = runCatching {
        container.packageActivityDataSource.loadPackagesWithActivities()
      }.getOrDefault(emptyList())
      _isLoading.value = false
    }
  }

  fun setSearchQuery(q: String) {
    _searchQuery.value = q
  }

  fun setFilterMode(mode: LauncherFilterMode) {
    _filterMode.value = mode
  }

  fun inspectPackage(packageName: String) {
    viewModelScope.launch {
      container.packageActivityDataSource.inspectPackageByName(packageName)
        .onSuccess { pkg ->
          _inspectedPackage.value = pkg
          _statusMessage.value = "Loaded '${pkg.appName}' (${pkg.exportedActivities.size} exported activities)."
        }
        .onFailure { err ->
          _inspectedPackage.value = null
          _statusMessage.value = err.message ?: "Unable to inspect package."
        }
    }
  }

  fun clearInspectedPackage() {
    _inspectedPackage.value = null
  }

  fun launchTarget(packageName: String, activityClassName: String?) {
    container.packageActivityDataSource.launchTarget(packageName, activityClassName)
      .onFailure { err ->
        _statusMessage.value = "Cannot launch component: ${err.localizedMessage ?: "Permission or exported restriction"}"
      }
  }

  suspend fun saveUploadedIcon(uri: Uri): String? {
    return container.packageActivityDataSource.saveUploadedIconLocally(uri).getOrNull()
  }

  fun saveShortcut(
    editId: Long? = null,
    packageName: String,
    activityClassName: String?,
    customLabel: String,
    originalAppName: String,
    iconKey: String,
    accentColorArgb: Long,
    shape: ShortcutShape,
    customIconPath: String?,
    iconZoom: Float,
  ) {
    viewModelScope.launch {
      container.favoritesRepository.saveShortcut(
        editId = editId,
        packageName = packageName,
        activityClassName = activityClassName,
        customLabel = customLabel.trim().ifBlank { originalAppName },
        originalAppName = originalAppName,
        iconKey = iconKey,
        accentColorArgb = accentColorArgb,
        iconShape = shape.id,
        customIconPath = customIconPath,
        iconZoom = iconZoom,
        allowDuplicateForApp = true,
      )
      runCatching { NexusWidgetUpdater.refreshAll(getApplication()) }
      _statusMessage.value = if (editId != null) {
        "Updated shortcut '$customLabel'."
      } else {
        "Saved shortcut '$customLabel'."
      }
    }
  }

  fun removeFavorite(id: Long) {
    viewModelScope.launch {
      container.favoritesRepository.removeFavorite(id)
      runCatching { NexusWidgetUpdater.refreshAll(getApplication()) }
      _statusMessage.value = "Removed shortcut."
    }
  }

  fun pinHomeScreenShortcut(
    packageName: String,
    activityClassName: String?,
    customLabel: String,
    accentColorArgb: Long,
    iconKey: String,
    shape: ShortcutShape,
    customIconPath: String?,
    iconZoom: Float,
  ) {
    container.packageActivityDataSource.pinHomeScreenShortcut(
      packageName = packageName,
      activityClassName = activityClassName,
      customLabel = customLabel,
      accentColorArgb = accentColorArgb,
      iconKey = iconKey,
      shape = shape,
      customIconPath = customIconPath,
      iconZoom = iconZoom,
    ).onSuccess { requested ->
      _statusMessage.value = if (requested) {
        "Shortcut pin request sent to your launcher."
      } else {
        "Your current home launcher does not support pinned shortcuts."
      }
    }.onFailure { err ->
      _statusMessage.value = err.message ?: "Failed to create home-screen shortcut."
    }
  }

  fun clearMessage() {
    _statusMessage.value = null
  }
}
