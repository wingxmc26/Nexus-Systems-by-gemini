package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_history")
data class BatteryHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val timestampMs: Long,
  val percentage: Int,
  val temperatureCelsius: Float?,
  val voltageVolts: Float?,
  val statusLabel: String,
  val chargeSource: String,
  val isCharging: Boolean,
)
