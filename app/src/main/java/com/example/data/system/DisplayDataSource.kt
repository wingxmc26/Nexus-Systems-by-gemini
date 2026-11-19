package com.example.data.system

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import com.example.data.model.DisplaySpecs

class DisplayDataSource(private val context: Context) {

  @Suppress("DEPRECATION")
  fun readDisplaySpecs(): DisplaySpecs {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    val display: Display? = dm?.getDisplay(Display.DEFAULT_DISPLAY) ?: wm?.defaultDisplay
    val resMetrics = context.resources.displayMetrics

    val (physW, physH, usableW, usableH) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
      val maxBounds = wm.maximumWindowMetrics.bounds
      val curBounds = wm.currentWindowMetrics.bounds
      listOf(maxBounds.width(), maxBounds.height(), curBounds.width(), curBounds.height())
    } else if (display != null) {
      val real = DisplayMetrics()
      display.getRealMetrics(real)
      listOf(real.widthPixels, real.heightPixels, resMetrics.widthPixels, resMetrics.heightPixels)
    } else {
      listOf(resMetrics.widthPixels, resMetrics.heightPixels, resMetrics.widthPixels, resMetrics.heightPixels)
    }

    val dpi = resMetrics.densityDpi
    val bucket = when {
      dpi <= DisplayMetrics.DENSITY_LOW -> "ldpi"
      dpi <= DisplayMetrics.DENSITY_MEDIUM -> "mdpi"
      dpi <= DisplayMetrics.DENSITY_HIGH -> "hdpi"
      dpi <= DisplayMetrics.DENSITY_XHIGH -> "xhdpi"
      dpi <= DisplayMetrics.DENSITY_XXHIGH -> "xxhdpi"
      dpi <= DisplayMetrics.DENSITY_XXXHIGH -> "xxxhdpi"
      else -> "${dpi}dpi"
    }

    val currentRefresh = display?.refreshRate ?: 60f
    val supportedRates = runCatching {
      display?.supportedModes?.map { it.refreshRate }?.distinct()?.sortedDescending() ?: listOf(currentRefresh)
    }.getOrDefault(listOf(currentRefresh))

    val hdrTypes = runCatching {
      val caps = display?.hdrCapabilities ?: return@runCatching emptyList()
      val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        display.mode?.supportedHdrTypes ?: caps.supportedHdrTypes
      } else {
        caps.supportedHdrTypes
      }
      types.map { type ->
        when (type) {
          Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
          Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
          Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
          Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
          else -> "HDR ($type)"
        }
      }.distinct()
    }.getOrDefault(emptyList())

    val wideGamut = runCatching { display?.isWideColorGamut ?: false }.getOrDefault(false)

    return DisplaySpecs(
      physicalWidthPx = physW,
      physicalHeightPx = physH,
      usableWidthPx = usableW,
      usableHeightPx = usableH,
      densityDpi = dpi,
      densityScale = resMetrics.density,
      densityBucket = bucket,
      currentRefreshRateHz = currentRefresh,
      supportedRefreshRatesHz = supportedRates,
      hdrTypes = hdrTypes,
      isWideColorGamut = wideGamut,
    )
  }
}
