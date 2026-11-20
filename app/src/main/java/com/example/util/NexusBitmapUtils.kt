package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import kotlin.math.sqrt

object NexusBitmapUtils {

  fun decodeSampledBitmap(
    filePath: String,
    reqWidth: Int = 1080,
    reqHeight: Int = 1920,
    applyExifOrientation: Boolean = true,
  ): Bitmap? {
    val file = File(filePath)
    if (!file.exists() || file.length() <= 0L) return null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    val halfW = bounds.outWidth / 2
    val halfH = bounds.outHeight / 2
    while ((halfW / sampleSize) >= reqWidth && (halfH / sampleSize) >= reqHeight) {
      sampleSize *= 2
    }

    val decodeOpts = BitmapFactory.Options().apply {
      inSampleSize = sampleSize.coerceAtLeast(1)
      inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val raw = runCatching { BitmapFactory.decodeFile(file.absolutePath, decodeOpts) }.getOrNull()
      ?: return null

    if (!applyExifOrientation) return raw

    val rotationDegrees = runCatching {
      val exif = ExifInterface(file.absolutePath)
      when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
      }
    }.getOrDefault(0f)

    if (rotationDegrees == 0f) return raw

    return runCatching {
      val m = Matrix().apply { postRotate(rotationDegrees) }
      val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
      if (rotated !== raw) raw.recycle()
      rotated
    }.getOrDefault(raw)
  }

  /**
   * Ensures bitmaps attached directly to RemoteViews remain safely under Android's Binder IPC ceiling.
   */
  fun ensureBinderSafeBitmap(
    bitmap: Bitmap,
    maxByteBudget: Int = 680_000,
    maxWidth: Int = 880,
    maxHeight: Int = 880,
  ): Bitmap {
    val bytes = bitmap.allocationByteCount
    if (bytes <= maxByteBudget && bitmap.width <= maxWidth && bitmap.height <= maxHeight) {
      return bitmap
    }
    val ratio = sqrt(maxByteBudget.toDouble() / bytes.toDouble()).toFloat().coerceIn(0.1f, 0.95f)
    val maxDimRatio = minOf(
      maxWidth.toFloat() / bitmap.width.coerceAtLeast(1),
      maxHeight.toFloat() / bitmap.height.coerceAtLeast(1),
      1f,
    )
    val scale = minOf(ratio, maxDimRatio)
    val targetW = (bitmap.width * scale).toInt().coerceAtLeast(120)
    val targetH = (bitmap.height * scale).toInt().coerceAtLeast(72)
    return runCatching {
      val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
      if (scaled !== bitmap) bitmap.recycle()
      scaled
    }.getOrDefault(bitmap)
  }
}
