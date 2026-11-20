package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [
    BatteryHistoryEntity::class,
    BatterySessionEntity::class,
    FavoriteActivityEntity::class,
    WallpaperItemEntity::class,
    WidgetProjectEntity::class,
    WidgetInstanceEntity::class,
    ClockPresetEntity::class,
    MusicPlaylistEntity::class,
    MusicPlaylistTrackEntity::class,
    MusicFavoriteEntity::class,
    MusicHistoryEntity::class,
    VideoProgressEntity::class,
    NotificationHistoryEntity::class,
    EqProfileEntity::class,
  ],
  version = 4,
  exportSchema = false,
)
abstract class NexusDatabase : RoomDatabase() {
  abstract fun batteryHistoryDao(): BatteryHistoryDao
  abstract fun batterySessionDao(): BatterySessionDao
  abstract fun favoriteActivityDao(): FavoriteActivityDao
  abstract fun wallpaperLibraryDao(): WallpaperLibraryDao
  abstract fun customWidgetDao(): CustomWidgetDao
  abstract fun widgetInstanceDao(): WidgetInstanceDao
  abstract fun clockPresetDao(): ClockPresetDao
  abstract fun musicLibraryDao(): MusicLibraryDao
  abstract fun videoProgressDao(): VideoProgressDao
  abstract fun notificationHistoryDao(): NotificationHistoryDao
  abstract fun eqProfileDao(): EqProfileDao

  companion object {
    @Volatile private var instance: NexusDatabase? = null

    val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS widget_instances (
            appWidgetId INTEGER NOT NULL PRIMARY KEY,
            projectId INTEGER NOT NULL,
            widthDp INTEGER NOT NULL DEFAULT 328,
            heightDp INTEGER NOT NULL DEFAULT 160,
            cachedBitmapPath TEXT,
            updatedAtMs INTEGER NOT NULL DEFAULT 0
          )
          """.trimIndent()
        )
      }
    }

    fun getInstance(context: Context): NexusDatabase {
      return instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
          context.applicationContext,
          NexusDatabase::class.java,
          "nexus_system_db",
        )
          .addMigrations(MIGRATION_3_4)
          .fallbackToDestructiveMigration(dropAllTables = true)
          .build()
          .also { instance = it }
      }
    }
  }
}
