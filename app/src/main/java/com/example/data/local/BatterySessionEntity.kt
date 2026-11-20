package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_sessions")
data class BatterySessionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val startTimeMs: Long,
  val endTimeMs: Long?,
  val startPercentage: Int,
  val endPercentage: Int,
  val chargeSource: String,
  val isActive: Boolean,
)
