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
    AutomationRuleEntity::class,
    SmartRuleEntity::class,
    ExecutionLogEntity::class,
    GestureProfileEntity::class,
    GestureMappingEntity::class,
    SharedShortcutEntity::class,
    QuickNoteEntity::class,
    VaultFolderEntity::class,
    VaultItemEntity::class,
    AppLockProfileEntity::class,
    AuthHistoryEntity::class,
    SavedBookmarkEntity::class,
  ],
  version = 6,
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
  abstract fun automationRuleDao(): AutomationRuleDao
  abstract fun smartRuleDao(): SmartRuleDao
  abstract fun executionLogDao(): ExecutionLogDao
  abstract fun gestureStudioDao(): GestureStudioDao
  abstract fun sharedShortcutDao(): SharedShortcutDao
  abstract fun quickNoteDao(): QuickNoteDao
  abstract fun vaultDao(): VaultDao
  abstract fun appLockDao(): AppLockDao
  abstract fun savedBookmarkDao(): SavedBookmarkDao

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

    val MIGRATION_4_5 = object : Migration(4, 5) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS automation_rules (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            name TEXT NOT NULL,
            description TEXT NOT NULL DEFAULT '',
            enabled INTEGER NOT NULL DEFAULT 1,
            triggerType TEXT NOT NULL,
            triggerConfigJson TEXT NOT NULL,
            actionsJson TEXT NOT NULL,
            isTemplate INTEGER NOT NULL DEFAULT 0,
            runCount INTEGER NOT NULL DEFAULT 0,
            lastTriggeredMs INTEGER NOT NULL DEFAULT 0,
            createdAtMs INTEGER NOT NULL DEFAULT 0,
            updatedAtMs INTEGER NOT NULL DEFAULT 0
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS smart_rules (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            name TEXT NOT NULL,
            description TEXT NOT NULL DEFAULT '',
            enabled INTEGER NOT NULL DEFAULT 1,
            priority INTEGER NOT NULL DEFAULT 50,
            cooldownMs INTEGER NOT NULL DEFAULT 60000,
            conditionLogicJson TEXT NOT NULL,
            thenActionsJson TEXT NOT NULL,
            elseActionsJson TEXT NOT NULL DEFAULT '[]',
            lastEvaluatedMs INTEGER NOT NULL DEFAULT 0,
            lastTriggeredMs INTEGER NOT NULL DEFAULT 0,
            lastOutcome TEXT NOT NULL DEFAULT '',
            lastConditionSignature TEXT NOT NULL DEFAULT '',
            updatedAtMs INTEGER NOT NULL DEFAULT 0
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS execution_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            sourceType TEXT NOT NULL,
            sourceId INTEGER NOT NULL DEFAULT 0,
            sourceName TEXT NOT NULL,
            success INTEGER NOT NULL,
            branch TEXT NOT NULL DEFAULT 'RUN',
            details TEXT NOT NULL DEFAULT '',
            errorMessage TEXT,
            executedAtMs INTEGER NOT NULL DEFAULT 0
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS gesture_profiles (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            name TEXT NOT NULL,
            isActive INTEGER NOT NULL DEFAULT 0,
            sensitivity REAL NOT NULL DEFAULT 1.0,
            inAppEnabled INTEGER NOT NULL DEFAULT 1,
            systemOverlayEnabled INTEGER NOT NULL DEFAULT 0,
            zoneWidthDp INTEGER NOT NULL DEFAULT 28,
            updatedAtMs INTEGER NOT NULL DEFAULT 0
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS gesture_mappings (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            profileId INTEGER NOT NULL,
            gestureType TEXT NOT NULL,
            activationZone TEXT NOT NULL DEFAULT 'FULL_SCREEN',
            actionsJson TEXT NOT NULL,
            label TEXT NOT NULL,
            enabled INTEGER NOT NULL DEFAULT 1,
            updatedAtMs INTEGER NOT NULL DEFAULT 0
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS shared_shortcuts (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            label TEXT NOT NULL,
            category TEXT NOT NULL DEFAULT 'Quick Actions',
            isFavorite INTEGER NOT NULL DEFAULT 0,
            sortOrder INTEGER NOT NULL DEFAULT 0,
            actionsJson TEXT NOT NULL,
            targetSurface TEXT NOT NULL DEFAULT 'BOTH',
            accentColorArgb INTEGER NOT NULL DEFAULT 4293212981,
            iconKey TEXT NOT NULL DEFAULT 'bolt'
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS quick_notes (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            title TEXT NOT NULL,
            content TEXT NOT NULL,
            pinned INTEGER NOT NULL DEFAULT 0,
            updatedAtMs INTEGER NOT NULL DEFAULT 0
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS vault_folders (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            name TEXT NOT NULL,
            createdAtMs INTEGER NOT NULL DEFAULT 0
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS vault_items (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            folderId INTEGER NOT NULL DEFAULT 0,
            displayName TEXT NOT NULL,
            mimeType TEXT NOT NULL,
            category TEXT NOT NULL,
            encryptedFilePath TEXT NOT NULL,
            sha256Hex TEXT NOT NULL,
            originalSizeBytes INTEGER NOT NULL,
            chunkSizeBytes INTEGER NOT NULL DEFAULT 262144,
            createdAtMs INTEGER NOT NULL DEFAULT 0,
            updatedAtMs INTEGER NOT NULL DEFAULT 0
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS app_lock_profiles (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            name TEXT NOT NULL,
            isActive INTEGER NOT NULL DEFAULT 0,
            protectedPackagesJson TEXT NOT NULL DEFAULT '[]',
            protectedNexusRoutesJson TEXT NOT NULL DEFAULT '[]',
            autoLockDelayMs INTEGER NOT NULL DEFAULT 30000,
            lockOnScreenOff INTEGER NOT NULL DEFAULT 1,
            updatedAtMs INTEGER NOT NULL DEFAULT 0
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS auth_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            targetType TEXT NOT NULL,
            targetName TEXT NOT NULL,
            method TEXT NOT NULL,
            success INTEGER NOT NULL,
            failureReason TEXT NOT NULL DEFAULT '',
            timestampMs INTEGER NOT NULL DEFAULT 0
          )
          """.trimIndent()
        )
      }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS saved_bookmarks (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            title TEXT NOT NULL,
            url TEXT NOT NULL,
            createdAtMs INTEGER NOT NULL DEFAULT 0
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
          .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
          .fallbackToDestructiveMigration(dropAllTables = true)
          .build()
          .also { instance = it }
      }
    }
  }
}
