package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import androidx.core.content.FileProvider
import com.example.util.NexusBitmapUtils
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

enum class WidgetSizePreset(
  val label: String,
  val widthDp: Int,
  val heightDp: Int,
  val gridDescription: String,
) {
  SMALL("Small 2×2", 160, 160, "2×2 Compact"),
  MEDIUM("Medium 4×2", 328, 160, "4×2 Standard"),
  LARGE("Large 4×4", 328, 328, "4×4 iOS Square"),
  WIDE("Extra-Large Wide", 360, 108, "4×1 Full-Width"),
  TALL("Tall 2×4", 164, 328, "2×4 Vertical"),
  IOS_LARGE_CLOCK("iOS Large Clock", 336, 336, "4×4 Depth Clock"),
  AUTO("Auto-Fit Slot", 328, 160, "Matches Launcher Bounds"),
  CUSTOM("Custom DP", 328, 220, "Manual W × H dp");

  companion object {
    fun fromName(name: String?): WidgetSizePreset =
      entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MEDIUM
  }
}

data class ResolvedWidgetSlot(
  val widthDp: Int,
  val heightDp: Int,
  val isLandscape: Boolean,
)

data class RenderPixelSize(
  val widthPx: Int,
  val heightPx: Int,
  val effectiveScale: Float,
)

object WidgetSizingEngine {

  private const val RENDERS_DIR = "widget_renders"
  const val BINDER_SAFE_THRESHOLD_BYTES = 680_000

  fun resolveWidgetSlotDp(
    context: Context,
    appWidgetManager: AppWidgetManager?,
    appWidgetId: Int,
    fallbackWidthDp: Int = 328,
    fallbackHeightDp: Int = 160,
  ): ResolvedWidgetSlot {
    val isLandscape =
      context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val opts: Bundle? = if (appWidgetManager != null && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
      runCatching { appWidgetManager.getAppWidgetOptions(appWidgetId) }.getOrNull()
    } else null

    if (opts != null) {
      // Android 12+ exact launcher sizes list if present
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        @Suppress("DEPRECATION")
        val sizes: ArrayList<SizeF>? = runCatching {
          opts.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
        }.getOrNull()
        if (!sizes.isNullOrEmpty()) {
          val chosen = if (isLandscape) {
            sizes.maxByOrNull { it.width } ?: sizes.first()
          } else {
            sizes.maxByOrNull { it.height } ?: sizes.first()
          }
          val w = chosen.width.roundToInt().coerceIn(72, 900)
          val h = chosen.height.roundToInt().coerceIn(56, 900)
          if (w >= 72 && h >= 56) {
            return ResolvedWidgetSlot(w, h, isLandscape)
          }
        }
      }

      val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
      val maxW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
      val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
      val maxH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)

      val rawW = if (isLandscape) (if (maxW > 0) maxW else minW) else (if (minW > 0) minW else maxW)
      val rawH = if (isLandscape) (if (minH > 0) minH else maxH) else (if (maxH > 0) maxH else minH)

      if (rawW >= 56 && rawH >= 48) {
        return ResolvedWidgetSlot(
          widthDp = rawW.coerceIn(72, 900),
          heightDp = rawH.coerceIn(56, 900),
          isLandscape = isLandscape,
        )
      }
    }

    return ResolvedWidgetSlot(
      widthDp = fallbackWidthDp.coerceIn(72, 900),
      heightDp = fallbackHeightDp.coerceIn(56, 900),
      isLandscape = isLandscape,
    )
  }

  /**
   * Computes crisp pixel dimensions for [widthDp] x [heightDp] at [density] while preserving
   * exact aspect ratio and capping the longest side at [maxLongSidePx] (default 1440px).
   */
  fun computeRenderPixels(
    widthDp: Int,
    heightDp: Int,
    density: Float,
    maxLongSidePx: Int = 1440,
  ): RenderPixelSize {
    val safeWdp = widthDp.coerceIn(72, 900)
    val safeHdp = heightDp.coerceIn(56, 900)
    val safeDensity = density.coerceIn(1.0f, 4.5f)
    val rawW = safeWdp * safeDensity
    val rawH = safeHdp * safeDensity
    val longSide = maxOf(rawW, rawH)
    val capRatio = if (longSide > maxLongSidePx) maxLongSidePx / longSide else 1f
    val pxW = (rawW * capRatio).roundToInt().coerceAtLeast(120)
    val pxH = (rawH * capRatio).roundToInt().coerceAtLeast(90)
    return RenderPixelSize(
      widthPx = pxW,
      heightPx = pxH,
      effectiveScale = pxW.toFloat() / safeWdp.toFloat(),
    )
  }

  /**
   * Delivers rendered [bitmap] cleanly into [views]:
   * - Small/medium bitmaps (<= 680KB) use direct IPC `setImageViewBitmap`.
   * - Large/XL/High-DPI bitmaps write a timestamped PNG into `filesDir/widget_renders` and bind
   *   via `FileProvider` `setImageViewUri` with read grants to active home launcher packages,
   *   avoiding Android's 1 MB Binder limit while keeping ultra-sharp iOS-style clarity.
   *   Falls back cleanly to Binder-safe scaled Bitmap if FileProvider grant cannot resolve.
   */
  fun deliverWidgetImage(
    context: Context,
    views: RemoteViews,
    imageViewId: Int,
    bitmap: Bitmap,
    cacheKey: String,
    preferUriDelivery: Boolean = false,
  ): String? {
    val byteCount = bitmap.allocationByteCount
    if (!preferUriDelivery && byteCount <= BINDER_SAFE_THRESHOLD_BYTES) {
      views.setImageViewBitmap(imageViewId, bitmap)
      return null
    }

    val writtenFile = runCatching {
      val dir = File(context.filesDir, RENDERS_DIR).apply { if (!exists()) mkdirs() }
      dir.listFiles()?.forEach { existing ->
        if (existing.name.startsWith("${cacheKey}_") && existing.name.endsWith(".png")) {
          existing.delete()
        }
      }
      val outFile = File(dir, "${cacheKey}_${System.currentTimeMillis()}.png")
      FileOutputStream(outFile).use { fos ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
      }
      outFile
    }.getOrNull()

    if (writtenFile != null && writtenFile.exists()) {
      val uriBound = runCatching {
        val uri = FileProvider.getUriForFile(
          context,
          "${context.packageName}.fileprovider",
          writtenFile,
        )
        val grantedCount = grantUriToHomeLaunchers(context, uri)
        if (grantedCount > 0) {
          views.setImageViewUri(imageViewId, uri)
          true
        } else {
          false
        }
      }.getOrDefault(false)

      if (uriBound) {
        return writtenFile.absolutePath
      }
    }

    val safeBmp = NexusBitmapUtils.ensureBinderSafeBitmap(
      bitmap = bitmap,
      maxByteBudget = BINDER_SAFE_THRESHOLD_BYTES,
      maxWidth = 880,
      maxHeight = 880,
    )
    views.setImageViewBitmap(imageViewId, safeBmp)
    return writtenFile?.absolutePath
  }

  fun deleteCachedRendersForKey(context: Context, cacheKey: String) {
    runCatching {
      val dir = File(context.filesDir, RENDERS_DIR)
      if (!dir.exists()) return
      dir.listFiles()?.forEach { f ->
        if (f.name.startsWith("${cacheKey}_")) {
          f.delete()
        }
      }
    }
  }

  private fun grantUriToHomeLaunchers(context: Context, uri: android.net.Uri): Int {
    val packages = linkedSetOf<String>()
    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val pm = context.packageManager
    val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      pm.queryIntentActivities(homeIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
    } else {
      @Suppress("DEPRECATION")
      pm.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
    }
    resolved.forEach { info ->
      info.activityInfo?.packageName?.let { packages.add(it) }
    }
    packages.add("com.android.systemui")

    var count = 0
    packages.forEach { pkg ->
      val ok = runCatching {
        context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
      }.getOrDefault(false)
      if (ok) count++
    }
    return count
  }
}
