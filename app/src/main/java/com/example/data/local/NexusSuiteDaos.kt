package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WallpaperLibraryDao {
  @Query("SELECT * FROM wallpaper_library ORDER BY createdAtMs DESC")
  fun observeAll(): Flow<List<WallpaperItemEntity>>

  @Query("SELECT * FROM wallpaper_library WHERE id = :id LIMIT 1")
  suspend fun getById(id: Long): WallpaperItemEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(item: WallpaperItemEntity): Long

  @Query("DELETE FROM wallpaper_library WHERE id = :id")
  suspend fun deleteById(id: Long)
}

@Dao
interface CustomWidgetDao {
  @Query("SELECT * FROM custom_widgets ORDER BY updatedAtMs DESC")
  fun observeAll(): Flow<List<WidgetProjectEntity>>

  @Query("SELECT * FROM custom_widgets WHERE isActiveHomeWidget = 1 LIMIT 1")
  suspend fun getActiveHomeWidget(): WidgetProjectEntity?

  @Query("SELECT * FROM custom_widgets WHERE id = :id LIMIT 1")
  suspend fun getById(id: Long): WidgetProjectEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(project: WidgetProjectEntity): Long

  @Update
  suspend fun update(project: WidgetProjectEntity)

  @Query("UPDATE custom_widgets SET isActiveHomeWidget = 0")
  suspend fun clearActiveFlag()

  @Query("UPDATE custom_widgets SET isActiveHomeWidget = 1, renderedBitmapPath = :bitmapPath, updatedAtMs = :ts WHERE id = :id")
  suspend fun markAsActive(id: Long, bitmapPath: String?, ts: Long = System.currentTimeMillis())

  @Query("DELETE FROM custom_widgets WHERE id = :id")
  suspend fun deleteById(id: Long)
}

@Dao
interface WidgetInstanceDao {
  @Query("SELECT * FROM widget_instances ORDER BY updatedAtMs DESC")
  fun observeAll(): Flow<List<WidgetInstanceEntity>>

  @Query("SELECT * FROM widget_instances ORDER BY updatedAtMs DESC")
  suspend fun getAll(): List<WidgetInstanceEntity>

  @Query("SELECT * FROM widget_instances WHERE appWidgetId = :widgetId LIMIT 1")
  suspend fun getByWidgetId(widgetId: Int): WidgetInstanceEntity?

  @Query("SELECT * FROM widget_instances WHERE projectId = :projectId")
  suspend fun getByProjectId(projectId: Long): List<WidgetInstanceEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(instance: WidgetInstanceEntity)

  @Query("DELETE FROM widget_instances WHERE appWidgetId = :widgetId")
  suspend fun deleteByWidgetId(widgetId: Int)

  @Query("DELETE FROM widget_instances WHERE projectId = :projectId")
  suspend fun deleteByProjectId(projectId: Long)
}

@Dao
interface ClockPresetDao {
  @Query("SELECT * FROM clock_presets ORDER BY updatedAtMs DESC")
  fun observeAll(): Flow<List<ClockPresetEntity>>

  @Query("SELECT * FROM clock_presets WHERE isActiveWidget = 1 LIMIT 1")
  suspend fun getActivePreset(): ClockPresetEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(preset: ClockPresetEntity): Long

  @Query("UPDATE clock_presets SET isActiveWidget = 0")
  suspend fun clearActiveFlag()

  @Query("UPDATE clock_presets SET isActiveWidget = 1 WHERE id = :id")
  suspend fun markActive(id: Long)

  @Query("DELETE FROM clock_presets WHERE id = :id")
  suspend fun deleteById(id: Long)
}

@Dao
interface MusicLibraryDao {
  @Query("SELECT * FROM music_playlists ORDER BY createdAtMs DESC")
  fun observePlaylists(): Flow<List<MusicPlaylistEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPlaylist(playlist: MusicPlaylistEntity): Long

  @Query("DELETE FROM music_playlists WHERE id = :id")
  suspend fun deletePlaylist(id: Long)

  @Query("SELECT * FROM music_playlist_tracks WHERE playlistId = :playlistId ORDER BY addedAtMs ASC")
  fun observePlaylistTracks(playlistId: Long): Flow<List<MusicPlaylistTrackEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun addTrackToPlaylist(track: MusicPlaylistTrackEntity): Long

  @Query("DELETE FROM music_playlist_tracks WHERE playlistId = :playlistId AND id = :entryId")
  suspend fun removeTrackFromPlaylist(playlistId: Long, entryId: Long)

  @Query("SELECT * FROM music_favorites ORDER BY addedAtMs DESC")
  fun observeFavorites(): Flow<List<MusicFavoriteEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertFavorite(fav: MusicFavoriteEntity)

  @Query("DELETE FROM music_favorites WHERE trackUri = :uri")
  suspend fun removeFavorite(uri: String)

  @Query("SELECT * FROM music_history ORDER BY playedAtMs DESC LIMIT 40")
  fun observeHistory(): Flow<List<MusicHistoryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertHistory(entry: MusicHistoryEntity)
}

@Dao
interface VideoProgressDao {
  @Query("SELECT * FROM video_progress ORDER BY lastPlayedMs DESC LIMIT 30")
  fun observeRecentVideos(): Flow<List<VideoProgressEntity>>

  @Query("SELECT * FROM video_progress WHERE uriString = :uri LIMIT 1")
  suspend fun getProgress(uri: String): VideoProgressEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertProgress(progress: VideoProgressEntity)
}

@Dao
interface NotificationHistoryDao {
  @Query("SELECT * FROM notification_history ORDER BY postedAtMs DESC LIMIT 500")
  fun observeAll(): Flow<List<NotificationHistoryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entry: NotificationHistoryEntity): Long

  @Query("UPDATE notification_history SET isPinned = :pinned WHERE id = :id")
  suspend fun setPinned(id: Long, pinned: Boolean)

  @Query("UPDATE notification_history SET isImportant = :important WHERE id = :id")
  suspend fun setImportant(id: Long, important: Boolean)

  @Query("DELETE FROM notification_history WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM notification_history")
  suspend fun clearAll()
}

@Dao
interface EqProfileDao {
  @Query("SELECT * FROM eq_profiles ORDER BY updatedAtMs DESC")
  fun observeProfiles(): Flow<List<EqProfileEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(profile: EqProfileEntity): Long

  @Query("DELETE FROM eq_profiles WHERE id = :id")
  suspend fun deleteById(id: Long)
}
