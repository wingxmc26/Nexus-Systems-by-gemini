package com.example.data.system

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.WallpaperItemEntity
import com.example.data.model.WallpaperMediaType
import com.example.util.NexusBitmapUtils
import com.example.wallpaper.NexusVideoWallpaperService
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportedWallpaperResult(
  val title: String,
  val mediaType: WallpaperMediaType,
  val localPath: String,
)

class LiveWallpaperDataSource(private val context: Context) {

  fun getWallpaperDir(): File {
    val dir = File(context.filesDir, "wallpapers")
    if (!dir.exists()) dir.mkdirs()
    return dir
  }

  fun getWallpaperFile(): File = File(getWallpaperDir(), "active_wallpaper.mp4")

  fun resolveActiveFile(overridePath: String?): File? {
    if (!overridePath.isNullOrBlank()) {
      val f = File(overridePath)
      if (f.exists() && f.length() > 0L) return f
    }
    val legacy = getWallpaperFile()
    return if (legacy.exists() && legacy.length() > 0L) legacy else null
  }

  fun isLiveWallpaperSupported(): Boolean {
    return runCatching {
      val pm = context.packageManager
      pm.hasSystemFeature(PackageManager.FEATURE_LIVE_WALLPAPER) ||
        buildSetLiveWallpaperIntents().isNotEmpty()
    }.getOrDefault(true)
  }

  fun isNexusWallpaperActive(): Boolean {
    return runCatching {
      val wm = WallpaperManager.getInstance(context)
      val info = wm.wallpaperInfo
      info?.component == ComponentName(context, NexusVideoWallpaperService::class.java)
    }.getOrDefault(false)
  }

  suspend fun importMediaFromUri(uri: Uri): Result<ImportedWallpaperResult> = withContext(Dispatchers.IO) {
    runCatching {
      val resolver = context.contentResolver
      val displayName = queryFileName(uri) ?: "nexus_wallpaper_${System.currentTimeMillis()}"
      val mime = resolver.getType(uri)?.lowercase().orEmpty()
      val lower = displayName.lowercase()

      // Copy into local app storage first so we can reliably inspect headers if needed
      val rawExt = lower.substringAfterLast('.', "").filter { it.isLetterOrDigit() }.take(5)
      val tempFile = File(getWallpaperDir(), "wp_tmp_${System.currentTimeMillis()}.${rawExt.ifEmpty { "bin" }}")
      resolver.openInputStream(uri)?.use { input ->
        FileOutputStream(tempFile).use { output ->
          val buf = ByteArray(16 * 1024)
          var read: Int
          while (input.read(buf).also { read = it } != -1) {
            output.write(buf, 0, read)
          }
          output.flush()
        }
      } ?: error("Unable to open selected media stream")

      if (!tempFile.exists() || tempFile.length() <= 0L) {
        tempFile.delete()
        error("Selected wallpaper file is empty")
      }

      val headerBytes = ByteArray(12)
      val headerLen = tempFile.inputStream().use { it.read(headerBytes) }
      val isGifMagic = headerLen >= 6 &&
        headerBytes[0] == 'G'.code.toByte() &&
        headerBytes[1] == 'I'.code.toByte() &&
        headerBytes[2] == 'F'.code.toByte()

      val isImageDecodable = if (!isGifMagic) {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(tempFile.absolutePath, opts)
        opts.outWidth > 0 && opts.outHeight > 0
      } else false

      val mediaType = when {
        mime == "image/gif" || lower.endsWith(".gif") || isGifMagic -> WallpaperMediaType.GIF
        mime.startsWith("image/") ||
          lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
          lower.endsWith(".png") || lower.endsWith(".webp") ||
          lower.endsWith(".bmp") || lower.endsWith(".heic") ||
          isImageDecodable -> WallpaperMediaType.IMAGE
        else -> WallpaperMediaType.VIDEO
      }

      val finalExt = when (mediaType) {
        WallpaperMediaType.GIF -> "gif"
        WallpaperMediaType.IMAGE -> rawExt.takeIf { it in setOf("jpg", "jpeg", "png", "webp", "bmp", "heic") } ?: "png"
        WallpaperMediaType.VIDEO -> rawExt.takeIf { it in setOf("mp4", "webm", "mkv", "mov", "3gp", "m4v") } ?: "mp4"
      }

      val destFile = File(getWallpaperDir(), "wp_${System.currentTimeMillis()}.$finalExt")
      if (!tempFile.renameTo(destFile)) {
        tempFile.copyTo(destFile, overwrite = true)
        tempFile.delete()
      }

      ImportedWallpaperResult(
        title = displayName,
        mediaType = mediaType,
        localPath = destFile.absolutePath,
      )
    }
  }

  suspend fun applyStaticImageWallpaper(
    filePath: String,
    targetFlags: Int, // WallpaperManager.FLAG_SYSTEM, FLAG_LOCK, or both
  ): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val file = File(filePath)
      require(file.exists()) { "Image file no longer exists" }
      val bitmap = NexusBitmapUtils.decodeSampledBitmap(file.absolutePath, 1440, 2560)
        ?: error("Unable to decode selected image")
      try {
        val wm = WallpaperManager.getInstance(context)
        wm.setBitmap(bitmap, null, true, targetFlags)
      } finally {
        if (!bitmap.isRecycled) bitmap.recycle()
      }
      Unit
    }
  }

  fun deleteLibraryFile(item: WallpaperItemEntity) {
    runCatching {
      val f = File(item.localFilePath)
      if (f.exists()) f.delete()
    }
  }

  fun buildSetLiveWallpaperIntents(): List<Intent> {
    val pm = context.packageManager
    val component = ComponentName(context, NexusVideoWallpaperService::class.java)
    val candidates = listOf(
      Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
      },
      Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER),
      Intent(Intent.ACTION_SET_WALLPAPER),
    )
    return candidates.filter { runCatching { it.resolveActivity(pm) != null }.getOrDefault(false) }
  }

  fun buildSetLiveWallpaperIntent(): Intent? {
    return buildSetLiveWallpaperIntents().firstOrNull()
      ?: Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        putExtra(
          WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
          ComponentName(context, NexusVideoWallpaperService::class.java),
        )
      }
  }

  private fun queryFileName(uri: Uri): String? {
    return runCatching {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
      }
    }.getOrNull()
  }
}
