package com.example.di

import android.content.Context
import com.example.core.NexusActionDispatcher
import com.example.data.local.AdvancedPreferencesRepository
import com.example.data.local.BatteryHistoryRepository
import com.example.data.local.BatterySessionRepository
import com.example.data.local.FavoritesRepository
import com.example.data.local.NexusDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.system.AppManagerDataSource
import com.example.data.system.BatteryDataSource
import com.example.data.system.HardwareDataSource
import com.example.data.system.LiveWallpaperDataSource
import com.example.data.system.MusicLibraryDataSource
import com.example.data.system.PackageActivityDataSource
import com.example.data.system.PermissionManager
import com.example.data.system.RamManagerDataSource
import com.example.data.system.ScreenRecorderDataSource
import com.example.data.system.ShortcutsDataSource
import com.example.data.system.StorageDataSource
import com.example.data.system.VideoLibraryDataSource
import com.example.gesture.GestureRepository
import com.example.media.SoundBoosterEngine
import com.example.overlay.SharedShortcutRepository
import com.example.security.VaultRepository
import com.example.widget.engine.UniversalWidgetDataHub

class NexusContainer(context: Context) {
  private val appContext = context.applicationContext

  val database: NexusDatabase by lazy { NexusDatabase.getInstance(appContext) }
  val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(appContext) }
  val advancedPreferences: AdvancedPreferencesRepository by lazy { AdvancedPreferencesRepository(appContext) }
  val permissionManager: PermissionManager by lazy { PermissionManager(appContext) }

  val batteryHistoryRepository: BatteryHistoryRepository by lazy {
    BatteryHistoryRepository(database.batteryHistoryDao())
  }
  val batterySessionRepository: BatterySessionRepository by lazy {
    BatterySessionRepository(database.batterySessionDao(), preferencesRepository)
  }
  val favoritesRepository: FavoritesRepository by lazy {
    FavoritesRepository(database.favoriteActivityDao())
  }

  val batteryDataSource: BatteryDataSource by lazy { BatteryDataSource(appContext) }
  val hardwareDataSource: HardwareDataSource by lazy { HardwareDataSource(appContext) }
  val ramManagerDataSource: RamManagerDataSource by lazy { RamManagerDataSource(appContext) }
  val liveWallpaperDataSource: LiveWallpaperDataSource by lazy { LiveWallpaperDataSource(appContext) }
  val packageActivityDataSource: PackageActivityDataSource by lazy { PackageActivityDataSource(appContext) }
  val storageDataSource: StorageDataSource by lazy { StorageDataSource(appContext) }
  val shortcutsDataSource: ShortcutsDataSource by lazy { ShortcutsDataSource(appContext) }

  val soundBoosterEngine: SoundBoosterEngine by lazy { SoundBoosterEngine() }
  val musicLibraryDataSource: MusicLibraryDataSource by lazy { MusicLibraryDataSource(appContext) }
  val videoLibraryDataSource: VideoLibraryDataSource by lazy { VideoLibraryDataSource(appContext) }
  val appManagerDataSource: AppManagerDataSource by lazy { AppManagerDataSource(appContext, permissionManager) }
  val screenRecorderDataSource: ScreenRecorderDataSource by lazy { ScreenRecorderDataSource(appContext) }
  val widgetDataHub: UniversalWidgetDataHub by lazy { UniversalWidgetDataHub(appContext) }

  val actionDispatcher: NexusActionDispatcher by lazy {
    NexusActionDispatcher(appContext, permissionManager, shortcutsDataSource, database)
  }

  val gestureRepository: GestureRepository by lazy {
    GestureRepository(database.gestureStudioDao(), actionDispatcher)
  }

  val sharedShortcutRepository: SharedShortcutRepository by lazy {
    SharedShortcutRepository(database.sharedShortcutDao(), database.quickNoteDao(), actionDispatcher)
  }

  val vaultRepository: VaultRepository by lazy {
    VaultRepository(appContext, database.vaultDao())
  }

  companion object {
    @Volatile private var instance: NexusContainer? = null

    fun get(context: Context): NexusContainer {
      return instance ?: synchronized(this) {
        instance ?: NexusContainer(context).also { instance = it }
      }
    }
  }
}
