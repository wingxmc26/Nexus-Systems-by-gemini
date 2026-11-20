package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.NotificationHistoryEntity
import com.example.di.NexusContainer
import com.example.notifications.NexusNotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NotificationFilter(val label: String) {
  ALL("All"),
  PINNED("Pinned"),
  IMPORTANT("Important"),
  ONGOING("Ongoing"),
}

data class NotificationStudioUiState(
  val hasNotificationAccess: Boolean = false,
  val isServiceConnected: Boolean = false,
  val items: List<NotificationHistoryEntity> = emptyList(),
  val appCounts: List<Pair<String, Int>> = emptyList(),
  val searchQuery: String = "",
  val filter: NotificationFilter = NotificationFilter.ALL,
  val selectedAppFilter: String? = null,
  val statusMessage: String? = null,
)

class NotificationStudioViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)
  private val dao = container.database.notificationHistoryDao()
  private val pm = container.permissionManager

  private val _hasAccess = MutableStateFlow(pm.isNotificationAccessEnabled())
  private val _search = MutableStateFlow("")
  private val _filter = MutableStateFlow(NotificationFilter.ALL)
  private val _appFilter = MutableStateFlow<String?>(null)
  private val _status = MutableStateFlow<String?>(null)

  val uiState: StateFlow<NotificationStudioUiState> = combine(
    _hasAccess,
    NexusNotificationListenerService.isConnected,
    dao.observeAll(),
    _search,
    _filter,
    _appFilter,
    _status,
  ) { arr ->
    val access = arr[0] as Boolean
    val connected = arr[1] as Boolean
    @Suppress("UNCHECKED_CAST")
    val all = arr[2] as List<NotificationHistoryEntity>
    val q = (arr[3] as String).trim().lowercase()
    val filter = arr[4] as NotificationFilter
    val appFilter = arr[5] as String?
    val status = arr[6] as String?

    val counts = all.groupBy { it.appName }
      .map { it.key to it.value.size }
      .sortedByDescending { it.second }
      .take(12)

    val filtered = all.filter { n ->
      val fOk = when (filter) {
        NotificationFilter.ALL -> true
        NotificationFilter.PINNED -> n.isPinned
        NotificationFilter.IMPORTANT -> n.isImportant
        NotificationFilter.ONGOING -> n.isOngoing
      }
      val appOk = appFilter == null || n.appName == appFilter
      val qOk = q.isEmpty() ||
        n.appName.lowercase().contains(q) ||
        n.title.lowercase().contains(q) ||
        n.text.lowercase().contains(q)
      fOk && appOk && qOk
    }

    NotificationStudioUiState(
      hasNotificationAccess = access,
      isServiceConnected = connected,
      items = filtered,
      appCounts = counts,
      searchQuery = arr[3] as String,
      filter = filter,
      selectedAppFilter = appFilter,
      statusMessage = status,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = NotificationStudioUiState(),
  )

  fun refreshAccess() {
    _hasAccess.value = pm.isNotificationAccessEnabled()
  }

  fun buildNotificationAccessIntent(): Intent = pm.buildNotificationAccessIntent()

  fun setSearchQuery(q: String) { _search.value = q }
  fun setFilter(filter: NotificationFilter) { _filter.value = filter }
  fun selectAppFilter(appName: String?) {
    _appFilter.value = if (_appFilter.value == appName) null else appName
  }

  fun togglePinned(entry: NotificationHistoryEntity) {
    viewModelScope.launch { dao.setPinned(entry.id, !entry.isPinned) }
  }

  fun toggleImportant(entry: NotificationHistoryEntity) {
    viewModelScope.launch { dao.setImportant(entry.id, !entry.isImportant) }
  }

  fun dismissActive(entry: NotificationHistoryEntity) {
    val ok = NexusNotificationListenerService.dismissActiveNotification(entry.notificationKey)
    _status.value = if (ok) {
      "Dismissed active notification from ${entry.appName}."
    } else {
      "Notification already cleared or listener service is disconnected."
    }
  }

  fun deleteEntry(entry: NotificationHistoryEntity) {
    viewModelScope.launch { dao.deleteById(entry.id) }
  }

  fun clearAllHistory() {
    viewModelScope.launch {
      dao.clearAll()
      _status.value = "Notification history cleared."
    }
  }

  fun clearStatus() { _status.value = null }
}
