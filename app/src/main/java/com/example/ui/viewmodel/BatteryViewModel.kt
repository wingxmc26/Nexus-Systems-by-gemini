package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BatteryHistoryEntity
import com.example.data.model.BatteryTelemetry
import com.example.di.NexusContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BatteryViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)

  val telemetry: StateFlow<BatteryTelemetry> = container.batteryDataSource.observeBattery(2000L)
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = container.batteryDataSource.readBatterySnapshot(),
    )

  val history: StateFlow<List<BatteryHistoryEntity>> = container.batteryHistoryRepository.history
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  fun logSnapshotNow() {
    viewModelScope.launch {
      val latest = container.batteryDataSource.readBatterySnapshot()
      container.batteryHistoryRepository.recordIfChanged(latest, force = true)
    }
  }

  fun clearHistory() {
    viewModelScope.launch {
      container.batteryHistoryRepository.clearHistory()
    }
  }
}
