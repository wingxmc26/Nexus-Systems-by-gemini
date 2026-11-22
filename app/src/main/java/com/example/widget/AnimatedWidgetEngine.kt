package com.example.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.RemoteViews
import com.example.R
import com.example.data.model.VisualElementKind
import com.example.data.model.VisualWidgetSpec
import java.io.File
import java.io.FileInputStream
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Extracts and delivers multi-frame animations for custom Visual GIF widgets and animated
 * HTML/CSS/JS widgets using native AppWidget ViewFlipper playback + FileProvider URIs.
 */
object AnimatedWidgetEngine {

  data class GifFrameSequence(
    val frames: List<Bitmap>,
    val frameIntervalMs: Int,
  )

  fun isAnimatedGifFile(path: String?): Boolean {
    if (path.isNullOrBlank()) return false
    val f = File(path)
    if (!f.exists() || f.length() < 6L) return false
    if (path.endsWith(".gif", ignoreCase = true)) return true
    return runCatching {
      FileInputStream(f).use { fis ->
        val header = ByteArray(6)
        val n = fis.read(header)
        n >= 6 &&
          header[0] == 'G'.code.toByte() &&
          header[1] == 'I'.code.toByte() &&
          header[2] == 'F'.code.toByte()
      }
    }.getOrDefault(false)
  }

  fun hasAnyAnimatedImage(spec: VisualWidgetSpec): Boolean {
    return spec.elements.any { el ->
      el.visible && el.kind == VisualElementKind.IMAGE && isAnimatedGifFile(el.imageUriOrPath)
    }
  }

  fun detectAnimatedHtml(html: String, css: String, js: String): Boolean {
    val combinedCss = css.lowercase()
    val combinedHtml = html.lowercase()
    val combinedJs = js.lowercase()
    return combinedCss.contains("@keyframes") ||
      combinedCss.contains("animation:") ||
      combinedCss.contains("animation-name") ||
      combinedHtml.contains(".gif") ||
      combinedHtml.contains("<canvas") ||
      combinedJs.contains("requestanimationframe") ||
      combinedJs.contains("setinterval")
  }

  @Suppress("DEPRECATION")
  fun extractGifFrames(
    path: String,
    maxFrames: Int = 10,
    maxSidePx: Int = 440,
  ): GifFrameSequence? {
    val file = File(path)
    if (!file.exists()) return null
    val movie = runCatching { android.graphics.Movie.decodeFile(file.absolutePath) }.getOrNull() ?: return null
    val mw = movie.width()
    val mh = movie.height()
    if (mw <= 0 || mh <= 0) return null

    val duration = movie.duration().let { if (it <= 0) 1200 else it }
    val count = minOf(maxFrames.coerceIn(2, 16), maxOf(4, duration / 120))
    val scale = min(maxSidePx.toFloat() / mw.toFloat(), maxSidePx.toFloat() / mh.toFloat()).coerceAtMost(2.5f)
    val outW = (mw * scale).roundToInt().coerceIn(32, maxSidePx)
    val outH = (mh * scale).roundToInt().coerceIn(32, maxSidePx)

    val frames = ArrayList<Bitmap>(count)
    for (i in 0 until count) {
      val timeMs = ((i.toFloat() / count.toFloat()) * duration.toFloat()).roundToInt().coerceIn(0, duration)
      movie.setTime(timeMs)
      val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(bmp)
      canvas.scale(outW.toFloat() / mw.toFloat(), outH.toFloat() / mh.toFloat())
      movie.draw(canvas, 0f, 0f)
      frames.add(bmp)
    }
    if (frames.isEmpty()) return null
    val frameMs = (duration / count).coerceIn(85, 420)
    return GifFrameSequence(frames = frames, frameIntervalMs = frameMs)
  }

  fun deliverAnimatedFrames(
    context: Context,
    views: RemoteViews,
    flipperViewId: Int,
    frames: List<Bitmap>,
    frameIntervalMs: Int,
    cacheKey: String,
  ): List<String> {
    if (frames.isEmpty()) return emptyList()
    WidgetSizingEngine.deleteCachedRendersForKey(context, "${cacheKey}_f")
    views.removeAllViews(flipperViewId)
    runCatching {
      views.setInt(flipperViewId, "setFlipInterval", frameIntervalMs.coerceIn(80, 1000))
    }

    val perFrameInlineLimit = 520_000 / frames.size.coerceAtLeast(1)
    val savedPaths = mutableListOf<String>()
    frames.forEachIndexed { idx, frameBmp ->
      if (!frameBmp.isRecycled) {
        val child = RemoteViews(context.packageName, R.layout.widget_custom_frame_item)
        val useUri = frameBmp.allocationByteCount > perFrameInlineLimit
        val path = WidgetSizingEngine.deliverWidgetImage(
          context = context,
          views = child,
          imageViewId = R.id.widget_frame_image,
          bitmap = frameBmp,
          cacheKey = "${cacheKey}_f${idx}",
          preferUriDelivery = useUri,
        )
        if (path != null) savedPaths.add(path)
        views.addView(flipperViewId, child)
      }
    }
    return savedPaths
  }
}
