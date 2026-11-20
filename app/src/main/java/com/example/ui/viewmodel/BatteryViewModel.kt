package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BatterySessionEntity
import com.example.data.model.ActiveChargingSession
import com.example.data.model.BatteryTelemetry
import com.example.di.NexusContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BatteryViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)

  val telemetry: StateFlow<BatteryTelemetry> = container.batteryDataSource.observeBattery(2000L)
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = container.batteryDataSource.readBatterySnapshot(),
    )

  val sessions: StateFlow<List<BatterySessionEntity>> = container.batterySessionRepository.sessions
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  val activeSession: StateFlow<ActiveChargingSession?> =
    container.batterySessionRepository.activeSession
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null,
      )

  private val _chargingDurationMs = MutableStateFlow(0L)
  val chargingDurationMs: StateFlow<Long> = _chargingDurationMs.asStateFlow()

  init {
    viewModelScope.launch {
      telemetry.collect { snapshot ->
        runCatching { container.batterySessionRepository.processBatterySnapshot(snapshot) }
      }
    }

    viewModelScope.launch {
      combine(telemetry, activeSession) { bat, session -> bat to session }
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
  }

  fun clearSessions() {
    viewModelScope.launch {
      container.batterySessionRepository.clearSessions()
      val latest = container.batteryDataSource.readBatterySnapshot()
      if (latest.isCharging) {
        container.batterySessionRepository.processBatterySnapshot(latest)
      }
    }
  }
}
