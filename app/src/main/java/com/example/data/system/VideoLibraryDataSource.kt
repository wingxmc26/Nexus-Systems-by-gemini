package com.example.data.system

import android.content.ContentUris
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.Display
import com.example.data.model.LocalVideoItem
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HdrHardwareSummary(
  val displayHdrTypes: List<String>,
  val decoderCodecsHdr: List<String>,
  val isHdrDisplaySupported: Boolean,
)

class VideoLibraryDataSource(private val context: Context) {

  suspend fun scanLocalVideos(): List<LocalVideoItem> = withContext(Dispatchers.IO) {
    val list = mutableListOf<LocalVideoItem>()
    val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
      MediaStore.Video.Media._ID,
      MediaStore.Video.Media.DISPLAY_NAME,
      MediaStore.Video.Media.DURATION,
      MediaStore.Video.Media.WIDTH,
      MediaStore.Video.Media.HEIGHT,
      MediaStore.Video.Media.SIZE,
      MediaStore.Video.Media.MIME_TYPE,
      MediaStore.Video.Media.DATA,
      MediaStore.Video.Media.DATE_ADDED,
    )

    runCatching {
      context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        "${MediaStore.Video.Media.DATE_ADDED} DESC",
      )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val wCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
        val hCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
        val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
        val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

        while (cursor.moveToNext()) {
          val id = cursor.getLong(idCol)
          val name = cursor.getString(nameCol) ?: "Video #$id"
          val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
          val folder = if (path.isNotBlank()) File(path).parentFile?.name ?: "Videos" else "Videos"
          val uri = ContentUris.withAppendedId(collection, id)
          val lower = name.lowercase()
          val hdrLikely = lower.contains("hdr") || lower.contains("10bit") || lower.contains("dolby") || lower.contains("hlg")

          list.add(
            LocalVideoItem(
              id = id,
              uriString = uri.toString(),
              title = name,
              folderName = folder,
              durationMs = cursor.getLong(durCol).coerceAtLeast(0L),
              width = cursor.getInt(wCol).coerceAtLeast(0),
              height = cursor.getInt(hCol).coerceAtLeast(0),
              sizeBytes = cursor.getLong(sizeCol).coerceAtLeast(0L),
              mimeType = cursor.getString(mimeCol) ?: "video/mp4",
              dateAddedSec = cursor.getLong(dateCol),
              isHdrLikely = hdrLikely,
            )
          )
        }
      }
    }
    list
  }

  fun itemFromPickedUri(uri: Uri): LocalVideoItem {
    val resolver = context.contentResolver
    var name = uri.lastPathSegment ?: "Picked Video"
    var size = 0L
    runCatching {
      resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
          name = c.getString(0) ?: name
          if (!c.isNull(1)) size = c.getLong(1)
        }
      }
    }
    return LocalVideoItem(
      id = uri.toString().hashCode().toLong(),
      uriString = uri.toString(),
      title = name,
      folderName = "Picked",
      durationMs = 0L,
      width = 0,
      height = 0,
      sizeBytes = size,
      mimeType = resolver.getType(uri) ?: "video/mp4",
      dateAddedSec = System.currentTimeMillis() / 1000L,
    )
  }

  @Suppress("DEPRECATION")
  fun readHdrCapabilities(): HdrHardwareSummary {
    val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
    val types = if (display != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      val ints = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        display.mode?.supportedHdrTypes?.toList() ?: display.hdrCapabilities?.supportedHdrTypes?.toList().orEmpty()
      } else {
        display.hdrCapabilities?.supportedHdrTypes?.toList().orEmpty()
      }
      ints.map {
        when (it) {
          Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
          Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
          Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
          Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
          else -> "HDR Type $it"
        }
      }.distinct()
    } else emptyList()

    val codecs = runCatching {
      MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        .filter { !it.isEncoder && it.supportedTypes.any { t -> t.startsWith("video/") } }
        .filter {
          val n = it.name.lowercase()
          n.contains("hevc") || n.contains("av1") || n.contains("dolby") || n.contains("vp9")
        }
        .map { it.name }
        .take(6)
    }.getOrDefault(emptyList())

    return HdrHardwareSummary(
      displayHdrTypes = types,
      decoderCodecsHdr = codecs,
      isHdrDisplaySupported = types.isNotEmpty(),
    )
  }
}
