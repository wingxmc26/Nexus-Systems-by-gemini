package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_activities")
data class FavoriteActivityEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val packageName: String,
  val activityClassName: String?,
  val customLabel: String,
  val originalAppName: String,
  val iconKey: String,
  val accentColorArgb: Long,
  val iconShape: String = "rounded",
  val customIconPath: String? = null,
  val iconZoom: Float = 1.0f,
  val createdAtMs: Long = System.currentTimeMillis(),
)
