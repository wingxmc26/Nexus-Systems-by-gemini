package com.example.di

import android.content.Context
import com.example.data.local.BatteryHistoryRepository
import com.example.data.local.NexusDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.system.AppInspectorDataSource
import com.example.data.system.AudioDataSource
import com.example.data.system.BatteryDataSource
import com.example.data.system.DisplayDataSource
import com.example.data.system.HardwareDataSource
import com.example.data.system.NetworkDataSource
import com.example.data.system.SensorDataSource
import com.example.data.system.ShortcutsDataSource
import com.example.data.system.StorageDataSource

class NexusContainer(context: Context) {
  private val appContext = context.applicationContext

  val database: NexusDatabase by lazy { NexusDatabase.getInstance(appContext) }
  val batteryHistoryRepository: BatteryHistoryRepository by lazy {
    BatteryHistoryRepository(database.batteryHistoryDao())
  }
  val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(appContext) }

  val batteryDataSource: BatteryDataSource by lazy { BatteryDataSource(appContext) }
  val hardwareDataSource: HardwareDataSource by lazy { HardwareDataSource(appContext) }
  val storageDataSource: StorageDataSource by lazy { StorageDataSource(appContext) }
  val networkDataSource: NetworkDataSource by lazy { NetworkDataSource(appContext) }
  val sensorDataSource: SensorDataSource by lazy { SensorDataSource(appContext) }
  val displayDataSource: DisplayDataSource by lazy { DisplayDataSource(appContext) }
  val audioDataSource: AudioDataSource by lazy { AudioDataSource(appContext) }
  val appInspectorDataSource: AppInspectorDataSource by lazy { AppInspectorDataSource(appContext) }
  val shortcutsDataSource: ShortcutsDataSource by lazy { ShortcutsDataSource(appContext) }

  companion object {
    @Volatile private var instance: NexusContainer? = null

    fun get(context: Context): NexusContainer {
      return instance ?: synchronized(this) {
        instance ?: NexusContainer(context).also { instance = it }
      }
    }
  }
}
