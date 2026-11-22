package com.example.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.FavoriteActivityEntity
import com.example.data.model.ActiveChargingSession
import com.example.data.model.BatteryTelemetry
import com.example.data.model.DashboardPreferences
import com.example.data.model.HardwareTelemetry
import com.example.di.NexusContainer
import com.example.widget.NexusWidgetUpdater
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DashboardOverviewState(
  val isLoading: Boolean = true,
  val hardware: HardwareTelemetry? = null,
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
      initialValue = container.batteryDataSource.readBatterySnapshot(),
    )

  val activeChargingSession: StateFlow<ActiveChargingSession?> =
    container.batterySessionRepository.activeSession
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null,
      )

  val favorites: StateFlow<List<FavoriteActivityEntity>> =
    container.favoritesRepository.favorites
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  private val _chargingDurationMs = MutableStateFlow(0L)
  val chargingDurationMs: StateFlow<Long> = _chargingDurationMs.asStateFlow()

  private val _dashboardState = MutableStateFlow(DashboardOverviewState())
  val dashboardState: StateFlow<DashboardOverviewState> = _dashboardState.asStateFlow()

  init {
    viewModelScope.launch {
      preferences.flatMapLatest { prefs ->
        flow {
          while (true) {
            val hw = runCatching { container.hardwareDataSource.readHardwareTelemetry() }.getOrNull()
            emit(
              DashboardOverviewState(
                isLoading = false,
                hardware = hw,
                error = if (hw == null) "Unable to read system information" else null,
              )
            )
            delay(prefs.refreshIntervalMs)
          }
        }
      }.collect { state ->
        _dashboardState.value = state
      }
    }

    // Track charging time sessions on battery state changes
    viewModelScope.launch {
      batteryTelemetry.collect { snapshot ->
        runCatching { container.batterySessionRepository.processBatterySnapshot(snapshot) }
      }
    }

    // Update live 1-second charging duration ticker while charging is active
    viewModelScope.launch {
      combine(batteryTelemetry, activeChargingSession) { bat, session -> bat to session }
        .collect { (bat, session) ->
          if (bat.isCharging && session != null) {
            while (isActive) {
              _chargingDurationMs.value = (System.currentTimeMillis() - session.startTimeMs).coerceAtLeast(0L)
              delay(1000L)
            }
          } else {
            _chargingDurationMs.value = 0L
          }
        }
    }

    // Refresh widgets when favorites change
    viewModelScope.launch {
      favorites.collect {
        runCatching { NexusWidgetUpdater.refreshAll(getApplication()) }
      }
    }
  }

  fun refreshNow() {
    viewModelScope.launch {
      val hw = runCatching { container.hardwareDataSource.readHardwareTelemetry() }.getOrNull()
      _dashboardState.value = DashboardOverviewState(
        isLoading = false,
        hardware = hw,
      )
      runCatching { NexusWidgetUpdater.refreshAll(getApplication()) }
    }
  }

  fun launchFavorite(fav: FavoriteActivityEntity): Boolean {
    return runCatching {
      val app = getApplication<Application>()
      val intent = if (!fav.activityClassName.isNullOrBlank()) {
        Intent().apply {
          component = ComponentName(fav.packageName, fav.activityClassName)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      } else {
        app.packageManager.getLaunchIntentForPackage(fav.packageName)?.apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return false
      }
      app.startActivity(intent)
      true
    }.getOrDefault(false)
  }
}
