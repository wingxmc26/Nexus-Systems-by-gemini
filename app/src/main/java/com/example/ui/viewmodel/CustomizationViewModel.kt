package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DashboardModuleId
import com.example.data.model.DashboardPreferences
import com.example.di.NexusContainer
import com.example.ui.theme.AccentPalette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CustomizationViewModel(application: Application) : AndroidViewModel(application) {
  private val repo = NexusContainer.get(application).preferencesRepository

  val preferences: StateFlow<DashboardPreferences> = repo.preferences
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = DashboardPreferences(),
    )

  fun setAccentPalette(palette: AccentPalette) {
    viewModelScope.launch { repo.setAccentPalette(palette) }
  }

  fun setAmoledMode(enabled: Boolean) {
    viewModelScope.launch { repo.setAmoledMode(enabled) }
  }

  fun setRefreshIntervalMs(intervalMs: Long) {
    viewModelScope.launch { repo.setRefreshIntervalMs(intervalMs) }
  }

  fun toggleModuleVisibility(moduleId: DashboardModuleId, visible: Boolean) {
    viewModelScope.launch { repo.setModuleVisibility(moduleId, visible) }
  }

  fun moveCardUp(moduleId: DashboardModuleId) {
    val current = preferences.value.cardOrder.toMutableList()
    val idx = current.indexOf(moduleId)
    if (idx > 0) {
      current.removeAt(idx)
      current.add(idx - 1, moduleId)
      viewModelScope.launch { repo.setCardOrder(current) }
    }
  }

  fun moveCardDown(moduleId: DashboardModuleId) {
    val current = preferences.value.cardOrder.toMutableList()
    val idx = current.indexOf(moduleId)
    if (idx >= 0 && idx < current.lastIndex) {
      current.removeAt(idx)
      current.add(idx + 1, moduleId)
      viewModelScope.launch { repo.setCardOrder(current) }
    }
  }

  fun resetDefaults() {
    viewModelScope.launch { repo.resetDefaults() }
  }
}
