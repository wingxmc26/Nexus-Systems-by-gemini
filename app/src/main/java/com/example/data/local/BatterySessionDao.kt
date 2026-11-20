package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BatterySessionDao {
  @Query("SELECT * FROM battery_sessions ORDER BY startTimeMs DESC LIMIT 50")
  fun observeSessions(): Flow<List<BatterySessionEntity>>

  @Query("SELECT * FROM battery_sessions WHERE isActive = 1 ORDER BY startTimeMs DESC LIMIT 1")
  suspend fun getActiveSession(): BatterySessionEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(session: BatterySessionEntity): Long

  @Update
  suspend fun update(session: BatterySessionEntity)

  @Query("DELETE FROM battery_sessions")
  suspend fun clearAll()
}
