package com.example.ui.viewmodel

import android.app.Application
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CoreSensorType
import com.example.data.model.LiveSensorReading
import com.example.data.model.SensorAvailability
import com.example.di.NexusContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class SensorSamplingRate(val label: String, val delayUs: Int) {
  NORMAL("Normal", SensorManager.SENSOR_DELAY_NORMAL),
  UI("Balanced", SensorManager.SENSOR_DELAY_UI),
  FAST("Fast", SensorManager.SENSOR_DELAY_GAME),
}

@OptIn(ExperimentalCoroutinesApi::class)
class SensorLabViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)

  val availability: List<SensorAvailability> = container.sensorDataSource.getSensorAvailabilityList()
  private val availableTypes: List<CoreSensorType> =
    availability.filter { it.isAvailable }.map { it.coreType }

  private val _isMonitoring = MutableStateFlow(true)
  val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

  private val _samplingRate = MutableStateFlow(SensorSamplingRate.UI)
  val samplingRate: StateFlow<SensorSamplingRate> = _samplingRate.asStateFlow()

  private val _selectedTestSensor = MutableStateFlow<CoreSensorType?>(availableTypes.firstOrNull())
  val selectedTestSensor: StateFlow<CoreSensorType?> = _selectedTestSensor.asStateFlow()

  val liveReadings: StateFlow<Map<CoreSensorType, LiveSensorReading>> = combine(
    _isMonitoring,
    _samplingRate,
  ) { active, rate -> active to rate }
    .flatMapLatest { (active, rate) ->
      if (!active || availableTypes.isEmpty()) flowOf(emptyMap())
      else container.sensorDataSource.observeSensors(availableTypes, rate.delayUs)
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyMap(),
    )

  val azimuthDegrees: StateFlow<Float?> = liveReadings
    .map { map ->
      val accel = map[CoreSensorType.ACCELEROMETER]?.values
      val mag = map[CoreSensorType.MAGNETOMETER]?.values
      container.sensorDataSource.computeAzimuthDegrees(accel, mag)
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = null,
    )

  fun toggleMonitoring() {
    _isMonitoring.value = !_isMonitoring.value
  }

  fun setSamplingRate(rate: SensorSamplingRate) {
    _samplingRate.value = rate
  }

  fun selectSensorForTest(type: CoreSensorType) {
    _selectedTestSensor.value = type
  }
}
