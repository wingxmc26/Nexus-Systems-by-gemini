package com.example.data.local

import com.example.data.model.BatteryTelemetry
import com.example.data.model.MetricValue
import kotlinx.coroutines.flow.Flow

class BatteryHistoryRepository(private val dao: BatteryHistoryDao) {
  val history: Flow<List<BatteryHistoryEntity>> = dao.observeHistory()

  suspend fun recordIfChanged(snapshot: BatteryTelemetry, force: Boolean = false) {
    val pct = (snapshot.percentage as? MetricValue.Available)?.value ?: return
    val temp = (snapshot.temperatureCelsius as? MetricValue.Available)?.value
    val volts = (snapshot.voltageVolts as? MetricValue.Available)?.value
    val latest = dao.getLatest()
    val elapsedSinceLast = latest?.let { snapshot.timestampMs - it.timestampMs } ?: Long.MAX_VALUE

    val shouldInsert = force ||
      latest == null ||
      latest.percentage != pct ||
      latest.statusLabel != snapshot.statusLabel ||
      latest.chargeSource != snapshot.chargeSource ||
      elapsedSinceLast >= 60_000L

    if (shouldInsert) {
      dao.insert(
        BatteryHistoryEntity(
          timestampMs = snapshot.timestampMs,
          percentage = pct,
          temperatureCelsius = temp,
          voltageVolts = volts,
          statusLabel = snapshot.statusLabel,
          chargeSource = snapshot.chargeSource,
          isCharging = snapshot.isCharging,
        )
      )
    }
  }

  suspend fun clearHistory() {
    dao.clearAll()
  }
}
