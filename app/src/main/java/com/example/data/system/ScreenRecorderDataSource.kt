package com.example.data.system

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.RecordedMediaItem
import com.example.recorder.NexusScreenRecorderService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScreenRecorderDataSource(private val context: Context) {

  suspend fun loadRecordedLibrary(): List<RecordedMediaItem> = withContext(Dispatchers.IO) {
    val items = mutableListOf<RecordedMediaItem>()

    // 1. MediaStore videos saved in Movies/NexusScreenRecordings
    runCatching {
      val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
      val proj = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.RELATIVE_PATH,
      )
      context.contentResolver.query(uri, proj, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val dateCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
        val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val relCol = c.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)

        while (c.moveToNext()) {
          val name = c.getString(nameCol) ?: continue
          val rel = if (relCol >= 0) c.getString(relCol).orEmpty() else ""
          if (name.startsWith("NexusRec_") || rel.contains("NexusScreenRecordings")) {
            val contentUri = ContentUris.withAppendedId(uri, c.getLong(idCol))
            items.add(
              RecordedMediaItem(
                id = contentUri.toString(),
                name = name,
                pathOrUri = contentUri.toString(),
                sizeBytes = c.getLong(sizeCol).coerceAtLeast(0L),
                createdAtMs = c.getLong(dateCol) * 1000L,
                isScreenshot = false,
                durationMs = c.getLong(durCol).coerceAtLeast(0L),
              )
            )
          }
        }
      }
    }

    // 2. MediaStore screenshots saved in Pictures/NexusScreenshots
    runCatching {
      val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
      val proj = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.SIZE,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.RELATIVE_PATH,
      )
      context.contentResolver.query(uri, proj, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
        val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        val relCol = c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)

        while (c.moveToNext()) {
          val name = c.getString(nameCol) ?: continue
          val rel = if (relCol >= 0) c.getString(relCol).orEmpty() else ""
          if (name.startsWith("NexusShot_") || rel.contains("NexusScreenshots")) {
            val contentUri = ContentUris.withAppendedId(uri, c.getLong(idCol))
            items.add(
              RecordedMediaItem(
                id = contentUri.toString(),
                name = name,
                pathOrUri = contentUri.toString(),
                sizeBytes = c.getLong(sizeCol).coerceAtLeast(0L),
                createdAtMs = c.getLong(dateCol) * 1000L,
                isScreenshot = true,
              )
            )
          }
        }
      }
    }

    // 3. Fallback app-dir files
    NexusScreenRecorderService.getOutputDirectory(context).listFiles()?.forEach { f ->
      items.add(
        RecordedMediaItem(
          id = f.absolutePath,
          name = f.name,
          pathOrUri = f.absolutePath,
          sizeBytes = f.length().coerceAtLeast(0L),
          createdAtMs = f.lastModified(),
          isScreenshot = f.name.endsWith(".png"),
        )
      )
    }

    items.distinctBy { it.name }.sortedByDescending { it.createdAtMs }
  }

  suspend fun renameRecording(item: RecordedMediaItem, newName: String): Boolean = withContext(Dispatchers.IO) {
    val clean = newName.trim().replace("/", "_")
    if (clean.isBlank()) return@withContext false
    val ext = item.name.substringAfterLast('.', if (item.isScreenshot) "png" else "mp4")
    val finalName = if (clean.endsWith(".$ext")) clean else "$clean.$ext"

    if (item.pathOrUri.startsWith("content://")) {
      runCatching {
        val values = ContentValues().apply {
          put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)
        }
        context.contentResolver.update(Uri.parse(item.pathOrUri), values, null, null) > 0
      }.getOrDefault(false)
    } else {
      val f = java.io.File(item.pathOrUri)
      val dst = java.io.File(f.parentFile, finalName)
      f.exists() && !dst.exists() && f.renameTo(dst)
    }
  }

  suspend fun deleteRecording(item: RecordedMediaItem): Boolean = withContext(Dispatchers.IO) {
    if (item.pathOrUri.startsWith("content://")) {
      runCatching {
        context.contentResolver.delete(Uri.parse(item.pathOrUri), null, null) > 0
      }.getOrDefault(false)
    } else {
      val f = java.io.File(item.pathOrUri)
      f.exists() && f.delete()
    }
  }

  fun shareRecording(item: RecordedMediaItem) {
    val uri = if (item.pathOrUri.startsWith("content://")) {
      Uri.parse(item.pathOrUri)
    } else {
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", java.io.File(item.pathOrUri))
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = if (item.isScreenshot) "image/png" else "video/mp4"
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
      context.startActivity(
        Intent.createChooser(intent, "Share ${item.name}").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
      )
    }
  }
}
