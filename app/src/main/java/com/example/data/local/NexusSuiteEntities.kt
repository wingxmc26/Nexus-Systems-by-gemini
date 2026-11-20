package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallpaper_library")
data class WallpaperItemEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val mediaType: String, // VIDEO, IMAGE, GIF
  val localFilePath: String,
  val createdAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "custom_widgets")
data class WidgetProjectEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val kind: String, // VISUAL or HTML
  val visualJson: String = "",
  val htmlCode: String = "",
  val cssCode: String = "",
  val jsCode: String = "",
  val isActiveHomeWidget: Boolean = false,
  val renderedBitmapPath: String? = null,
  val updatedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "widget_instances")
data class WidgetInstanceEntity(
  @PrimaryKey val appWidgetId: Int,
  val projectId: Long,
  val widthDp: Int = 328,
  val heightDp: Int = 160,
  val cachedBitmapPath: String? = null,
  val updatedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "clock_presets")
data class ClockPresetEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val configJson: String,
  val isActiveWidget: Boolean = false,
  val updatedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "music_playlists")
data class MusicPlaylistEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val createdAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "music_playlist_tracks")
data class MusicPlaylistTrackEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val playlistId: Long,
  val trackUri: String,
  val title: String,
  val artist: String,
  val album: String,
  val durationMs: Long,
  val addedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "music_favorites")
data class MusicFavoriteEntity(
  @PrimaryKey val trackUri: String,
  val title: String,
  val artist: String,
  val addedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "music_history")
data class MusicHistoryEntity(
  @PrimaryKey val trackUri: String,
  val title: String,
  val artist: String,
  val album: String,
  val durationMs: Long,
  val playedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "video_progress")
data class VideoProgressEntity(
  @PrimaryKey val uriString: String,
  val title: String,
  val positionMs: Long,
  val durationMs: Long,
  val playbackSpeed: Float = 1.0f,
  val subtitleOffsetMs: Long = 0L,
  val lastPlayedMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "notification_history")
data class NotificationHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val notificationKey: String,
  val packageName: String,
  val appName: String,
  val title: String,
  val text: String,
  val subText: String = "",
  val channelId: String = "",
  val category: String = "",
  val postedAtMs: Long = System.currentTimeMillis(),
  val isOngoing: Boolean = false,
  val isClearable: Boolean = true,
  val isPinned: Boolean = false,
  val isImportant: Boolean = false,
)

@Entity(tableName = "eq_profiles")
data class EqProfileEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val bandsCsv: String,
  val bassStrength: Int,
  val trebleBoostMb: Int,
  val virtualizerStrength: Int,
  val preampGainMb: Int,
  val updatedAtMs: Long = System.currentTimeMillis(),
)
