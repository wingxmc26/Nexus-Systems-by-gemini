package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BatteryHistoryEntity::class], version = 1, exportSchema = false)
abstract class NexusDatabase : RoomDatabase() {
  abstract fun batteryHistoryDao(): BatteryHistoryDao

  companion object {
    @Volatile private var instance: NexusDatabase? = null

    fun getInstance(context: Context): NexusDatabase {
      return instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
          context.applicationContext,
          NexusDatabase::class.java,
          "nexus_system_db",
        ).build().also { instance = it }
      }
    }
  }
}
