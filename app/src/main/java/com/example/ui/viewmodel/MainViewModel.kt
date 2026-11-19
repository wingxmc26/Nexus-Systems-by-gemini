package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BatteryTelemetry
import com.example.data.model.DashboardPreferences
import com.example.data.model.HardwareTelemetry
import com.example.data.model.StorageOverview
import com.example.di.NexusContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardOverviewState(
  val isLoading: Boolean = true,
  val hardware: HardwareTelemetry? = null,
  val storage: StorageOverview? = null,
  val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)

  val preferences: StateFlow<DashboardPreferences> = container.preferencesRepository.preferences
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = DashboardPreferences(),
    )

  val batteryTelemetry: StateFlow<BatteryTelemetry> = preferences
    .flatMapLatest { prefs -> container.batteryDataSource.observeBattery(prefs.refreshIntervalMs) }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = BatteryTelemetry(),
    )

  private val _dashboardState = MutableStateFlow(DashboardOverviewState())
  val dashboardState: StateFlow<DashboardOverviewState> = _dashboardState.asStateFlow()

  init {
    viewModelScope.launch {
      preferences.flatMapLatest { prefs ->
        flow {
          while (true) {
            val hw = runCatching { container.hardwareDataSource.readHardwareTelemetry() }.getOrNull()
            val st = runCatching { container.storageDataSource.readStorageOverview() }.getOrNull()
            emit(
              DashboardOverviewState(
                isLoading = false,
                hardware = hw,
                storage = st,
                error = if (hw == null && st == null) "Unable to read system telemetry" else null,
              )
            )
            delay(prefs.refreshIntervalMs)
          }
        }
      }.collect { state ->
        _dashboardState.value = state
      }
    }

    // Automatically log battery changes to local Room charging history
    viewModelScope.launch {
      batteryTelemetry.collect { snapshot ->
        runCatching { container.batteryHistoryRepository.recordIfChanged(snapshot) }
      }
    }
  }

  fun refreshNow() {
    viewModelScope.launch {
      val hw = runCatching { container.hardwareDataSource.readHardwareTelemetry() }.getOrNull()
      val st = runCatching { container.storageDataSource.readStorageOverview() }.getOrNull()
      _dashboardState.value = DashboardOverviewState(
        isLoading = false,
        hardware = hw,
        storage = st,
      )
    }
  }
}
