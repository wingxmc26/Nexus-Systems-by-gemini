package com.example.data.local

import com.example.data.model.ActiveChargingSession
import com.example.data.model.BatteryTelemetry
import com.example.data.model.MetricValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BatterySessionRepository(
  private val sessionDao: BatterySessionDao,
  private val preferencesRepository: PreferencesRepository,
) {
  private val mutex = Mutex()

  val sessions: Flow<List<BatterySessionEntity>> = sessionDao.observeSessions()
  val activeSession: Flow<ActiveChargingSession?> = preferencesRepository.activeChargingSession

  suspend fun processBatterySnapshot(snapshot: BatteryTelemetry) = mutex.withLock {
    val pct = (snapshot.percentage as? MetricValue.Available)?.value ?: return
    val now = snapshot.timestampMs
    val currentActive = sessionDao.getActiveSession()

    if (snapshot.isCharging) {
      if (currentActive == null) {
        sessionDao.insert(
          BatterySessionEntity(
            startTimeMs = now,
            endTimeMs = null,
            startPercentage = pct,
            endPercentage = pct,
            chargeSource = snapshot.chargeSource,
            isActive = true,
          )
        )
        preferencesRepository.setActiveChargingSession(
          startTimeMs = now,
          lastSeenMs = now,
          startPct = pct,
          source = snapshot.chargeSource,
        )
      } else {
        if (currentActive.endPercentage != pct || currentActive.chargeSource != snapshot.chargeSource) {
          sessionDao.update(
            currentActive.copy(
              endPercentage = pct,
              chargeSource = snapshot.chargeSource,
            )
          )
        }
        preferencesRepository.touchActiveChargingSession(
          lastSeenMs = now,
          source = snapshot.chargeSource,
        )
      }
    } else {
      if (currentActive != null) {
        val lastSeen = preferencesRepository.getLastSeenChargingTimeMs() ?: now
        val endMs = if ((now - lastSeen) in 0L..60_000L) now else lastSeen.coerceAtLeast(currentActive.startTimeMs)
        sessionDao.update(
          currentActive.copy(
            endTimeMs = endMs,
            endPercentage = pct,
            isActive = false,
          )
        )
      }
      preferencesRepository.clearActiveChargingSession()
    }
  }

  suspend fun clearSessions() = mutex.withLock {
    sessionDao.clearAll()
    preferencesRepository.clearActiveChargingSession()
  }
}
