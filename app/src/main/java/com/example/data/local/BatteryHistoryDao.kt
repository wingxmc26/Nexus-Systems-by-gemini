package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryHistoryDao {
  @Query("SELECT * FROM battery_history ORDER BY timestampMs DESC LIMIT 120")
  fun observeHistory(): Flow<List<BatteryHistoryEntity>>

  @Query("SELECT * FROM battery_history ORDER BY timestampMs DESC LIMIT 1")
  suspend fun getLatest(): BatteryHistoryEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entry: BatteryHistoryEntity)

  @Query("DELETE FROM battery_history")
  suspend fun clearAll()
}
