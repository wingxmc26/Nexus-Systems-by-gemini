package com.example.data.system

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.data.model.CoreSensorType
import com.example.data.model.LiveSensorReading
import com.example.data.model.SensorAvailability
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SensorDataSource(private val context: Context) {

  private val sensorManager: SensorManager?
    get() = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

  fun getSensorAvailabilityList(): List<SensorAvailability> {
    val sm = sensorManager
    return CoreSensorType.entries.map { coreType ->
      val sensor = sm?.getDefaultSensor(coreType.androidType)
      SensorAvailability(
        coreType = coreType,
        isAvailable = sensor != null,
        sensorName = sensor?.name,
        vendor = sensor?.vendor,
        maxRange = sensor?.maximumRange,
        resolution = sensor?.resolution,
        powerMa = sensor?.power,
      )
    }
  }

  fun observeSensors(
    activeTypes: List<CoreSensorType>,
    samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_UI,
  ): Flow<Map<CoreSensorType, LiveSensorReading>> = callbackFlow {
    val sm = sensorManager
    if (sm == null || activeTypes.isEmpty()) {
      trySend(emptyMap())
      awaitClose { }
      return@callbackFlow
    }

    val stateMap = mutableMapOf<CoreSensorType, LiveSensorReading>()
    val historyMap = mutableMapOf<CoreSensorType, ArrayDeque<Float>>()

    val listener = object : SensorEventListener {
      override fun onSensorChanged(event: SensorEvent?) {
        val ev = event ?: return
        val coreType = CoreSensorType.entries.firstOrNull { it.androidType == ev.sensor.type } ?: return
        val expectedCount = coreType.axisLabels.size.coerceAtMost(ev.values.size)
        val vals = (0 until expectedCount).map { ev.values[it] }

        val deque = historyMap.getOrPut(coreType) { ArrayDeque(40) }
        val primaryVal = vals.firstOrNull() ?: 0f
        if (deque.size >= 40) deque.removeFirst()
        deque.addLast(primaryVal)

        stateMap[coreType] = LiveSensorReading(
          coreType = coreType,
          values = vals,
          accuracy = ev.accuracy,
          history = deque.toList(),
        )
        trySend(stateMap.toMap())
      }

      override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    activeTypes.forEach { coreType ->
      sm.getDefaultSensor(coreType.androidType)?.let { sensor ->
        sm.registerListener(listener, sensor, samplingPeriodUs)
      }
    }

    awaitClose {
      sm.unregisterListener(listener)
    }
  }

  fun computeAzimuthDegrees(accel: List<Float>?, mag: List<Float>?): Float? {
    if (accel == null || mag == null || accel.size < 3 || mag.size < 3) return null
    val r = FloatArray(9)
    val i = FloatArray(9)
    val ok = SensorManager.getRotationMatrix(r, i, accel.take(3).toFloatArray(), mag.take(3).toFloatArray())
    if (!ok) return null
    val orientation = FloatArray(3)
    SensorManager.getOrientation(r, orientation)
    val deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
    return (deg + 360f) % 360f
  }
}
