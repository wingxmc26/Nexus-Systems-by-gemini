package com.example.data.system

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.data.model.LocalAudioTrack
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicLibraryDataSource(private val context: Context) {

  suspend fun scanLocalAudio(): List<LocalAudioTrack> = withContext(Dispatchers.IO) {
    val result = mutableListOf<LocalAudioTrack>()
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
      MediaStore.Audio.Media._ID,
      MediaStore.Audio.Media.TITLE,
      MediaStore.Audio.Media.ARTIST,
      MediaStore.Audio.Media.ALBUM,
      MediaStore.Audio.Media.ALBUM_ID,
      MediaStore.Audio.Media.DURATION,
      MediaStore.Audio.Media.DATA,
      MediaStore.Audio.Media.MIME_TYPE,
      MediaStore.Audio.Media.DATE_ADDED,
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DURATION} > 5000"

    runCatching {
      context.contentResolver.query(
        collection,
        projection,
        selection,
        null,
        "${MediaStore.Audio.Media.TITLE} ASC",
      )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

        while (cursor.moveToNext()) {
          val id = cursor.getLong(idCol)
          val uri = ContentUris.withAppendedId(collection, id)
          val filePath = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
          val folder = if (filePath.isNotBlank()) File(filePath).parentFile?.name ?: "Audio" else "Music"

          result.add(
            LocalAudioTrack(
              id = id,
              uriString = uri.toString(),
              title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Track #$id",
              artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" && it.isNotBlank() } ?: "Unknown Artist",
              album = cursor.getString(albumCol)?.takeIf { it != "<unknown>" && it.isNotBlank() } ?: "Unknown Album",
              albumId = cursor.getLong(albumIdCol),
              durationMs = cursor.getLong(durCol).coerceAtLeast(0L),
              folderName = folder,
              filePath = filePath,
              mimeType = cursor.getString(mimeCol) ?: "audio/*",
              dateAddedSec = cursor.getLong(dateCol),
            )
          )
        }
      }
    }
    result
  }

  suspend fun trackFromPickedUri(uri: Uri): LocalAudioTrack = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val displayName = runCatching {
      resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
      }
    }.getOrNull() ?: uri.lastPathSegment ?: "Picked Audio"

    var title = displayName.substringBeforeLast('.')
    var artist = "Local File"
    var album = "Picked Audio"
    var durationMs = 0L

    val mmr = MediaMetadataRetriever()
    runCatching {
      mmr.setDataSource(context, uri)
      mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
      mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it }
      mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }?.let { album = it }
      mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { durationMs = it }
    }
    runCatching { mmr.release() }

    LocalAudioTrack(
      id = uri.toString().hashCode().toLong(),
      uriString = uri.toString(),
      title = title,
      artist = artist,
      album = album,
      albumId = 0L,
      durationMs = durationMs,
      folderName = "Picked",
      filePath = "",
      mimeType = resolver.getType(uri) ?: "audio/*",
      dateAddedSec = System.currentTimeMillis() / 1000L,
    )
  }

  suspend fun loadLyricsForTrack(track: LocalAudioTrack): String? = withContext(Dispatchers.IO) {
    if (track.filePath.isBlank()) return@withContext null
    runCatching {
      val audioFile = File(track.filePath)
      val base = audioFile.nameWithoutExtension
      val lrcFile = File(audioFile.parentFile, "$base.lrc")
      if (lrcFile.exists() && lrcFile.canRead()) {
        return@runCatching lrcFile.readText().take(24_000)
      }
      val txtFile = File(audioFile.parentFile, "$base.txt")
      if (txtFile.exists() && txtFile.canRead()) {
        return@runCatching txtFile.readText().take(24_000)
      }
      null
    }.getOrNull()
  }

  suspend fun loadAlbumArtBytes(uriString: String): ByteArray? = withContext(Dispatchers.IO) {
    val mmr = MediaMetadataRetriever()
    return@withContext runCatching {
      mmr.setDataSource(context, Uri.parse(uriString))
      mmr.embeddedPicture
    }.getOrNull().also {
      runCatching { mmr.release() }
    }
  }
}
