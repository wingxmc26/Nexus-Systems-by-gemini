package com.example.data.local

import com.example.data.model.BuiltInWidgetStyleConfig
import com.example.data.model.ClockFontChoice
import com.example.data.model.DepthClockConfig
import com.example.data.model.DepthClockMode
import com.example.data.model.VisualElementKind
import com.example.data.model.VisualWidgetElement
import com.example.data.model.VisualWidgetSpec
import org.json.JSONArray
import org.json.JSONObject

object WidgetClockSerializer {

  fun serializeVisualWidget(spec: VisualWidgetSpec): String {
    val root = JSONObject().apply {
      put("bg", spec.backgroundColorArgb)
      put("gradEnd", spec.gradientEndColorArgb ?: JSONObject.NULL)
      put("transparent", spec.isTransparent)
      put("htmlTransparent", spec.htmlTransparent)
      put("glass", spec.isGlassStyle)
      put("borderColor", spec.borderColorArgb)
      put("borderWidth", spec.borderWidthDp.toDouble())
      put("radius", spec.cornerRadiusDp)
      put("blur", spec.blurIntensity.toDouble())
      put("sizePreset", spec.sizePresetName)
      put("widthDp", spec.targetWidthDp)
      put("heightDp", spec.targetHeightDp)
      put("padDp", spec.containerPaddingDp)
      put("refreshMin", spec.refreshIntervalMinutes)
      put("anim", spec.enableAnimation)
      put("animMs", spec.animationFrameMs)
      val arr = JSONArray()
      spec.elements.forEach { el ->
        arr.put(
          JSONObject().apply {
            put("id", el.id)
            put("kind", el.kind.name)
            put("label", el.label)
            put("x", el.xFraction.toDouble())
            put("y", el.yFraction.toDouble())
            put("w", el.widthFraction.toDouble())
            put("h", el.heightFraction.toDouble())
            put("fontSp", el.fontSizeSp.toDouble())
            put("bold", el.fontWeightBold)
            put("color", el.textColorArgb)
            put("align", el.alignment)
            put("pad", el.paddingDp)
            put("elBg", el.backgroundColorArgb)
            put("elRadius", el.cornerRadiusDp)
            put("visible", el.visible)
            put("layer", el.layerOrder)
            put("img", el.imageUriOrPath ?: "")
          }
        )
      }
      put("elements", arr)
    }
    return root.toString()
  }

  fun deserializeVisualWidget(json: String?): VisualWidgetSpec {
    if (json.isNullOrBlank()) return defaultTelemetryVisualSpec()
    return runCatching {
      val root = JSONObject(json)
      val gradVal = if (root.isNull("gradEnd")) null else root.optLong("gradEnd")
      val arr = root.optJSONArray("elements") ?: JSONArray()
      val list = buildList {
        for (i in 0 until arr.length()) {
          val o = arr.getJSONObject(i)
          val kind = runCatching { VisualElementKind.valueOf(o.getString("kind")) }
            .getOrDefault(VisualElementKind.TEXT)
          add(
            VisualWidgetElement(
              id = o.optString("id", "el_$i"),
              kind = kind,
              label = o.optString("label", ""),
              xFraction = o.optDouble("x", 0.08).toFloat(),
              yFraction = o.optDouble("y", 0.15).toFloat(),
              widthFraction = o.optDouble("w", 0.84).toFloat(),
              heightFraction = o.optDouble("h", 0.22).toFloat(),
              fontSizeSp = o.optDouble("fontSp", 16.0).toFloat(),
              fontWeightBold = o.optBoolean("bold", true),
              textColorArgb = o.optLong("color", 0xFFFFFFFFL),
              alignment = o.optInt("align", 0),
              paddingDp = o.optInt("pad", 4),
              backgroundColorArgb = o.optLong("elBg", 0x00000000L),
              cornerRadiusDp = o.optInt("elRadius", 8),
              visible = o.optBoolean("visible", true),
              layerOrder = o.optInt("layer", i),
              imageUriOrPath = o.optString("img", "").takeIf { it.isNotBlank() },
            )
          )
        }
      }
      VisualWidgetSpec(
        backgroundColorArgb = root.optLong("bg", 0xFF0A0B0EL),
        gradientEndColorArgb = gradVal,
        isTransparent = root.optBoolean("transparent", false),
        htmlTransparent = root.optBoolean("htmlTransparent", true),
        isGlassStyle = root.optBoolean("glass", true),
        borderColorArgb = root.optLong("borderColor", 0x4DE11D48L),
        borderWidthDp = root.optDouble("borderWidth", 1.5).toFloat(),
        cornerRadiusDp = root.optInt("radius", 20),
        blurIntensity = root.optDouble("blur", 0.0).toFloat(),
        sizePresetName = root.optString("sizePreset", "MEDIUM"),
        targetWidthDp = root.optInt("widthDp", 328).coerceIn(100, 520),
        targetHeightDp = root.optInt("heightDp", 160).coerceIn(60, 560),
        containerPaddingDp = root.optInt("padDp", 12).coerceIn(0, 36),
        refreshIntervalMinutes = root.optInt("refreshMin", 1).coerceIn(0, 120),
        enableAnimation = root.optBoolean("anim", false),
        animationFrameMs = root.optInt("animMs", 180).coerceIn(80, 1000),
        elements = list,
      )
    }.getOrElse { defaultTelemetryVisualSpec() }
  }

  fun defaultTelemetryVisualSpec(): VisualWidgetSpec {
    return VisualWidgetSpec(
      backgroundColorArgb = 0xFF0A0B0EL,
      gradientEndColorArgb = 0xFF170B12L,
      isTransparent = false,
      isGlassStyle = true,
      borderColorArgb = 0x55E11D48L,
      borderWidthDp = 1.5f,
      cornerRadiusDp = 22,
      elements = listOf(
        VisualWidgetElement(
          id = "el_clock",
          kind = VisualElementKind.CLOCK,
          xFraction = 0.07f,
          yFraction = 0.08f,
          widthFraction = 0.55f,
          heightFraction = 0.28f,
          fontSizeSp = 28f,
          fontWeightBold = true,
          textColorArgb = 0xFFFFFFFFL,
          layerOrder = 0,
        ),
        VisualWidgetElement(
          id = "el_bat",
          kind = VisualElementKind.BATTERY,
          xFraction = 0.64f,
          yFraction = 0.12f,
          widthFraction = 0.30f,
          heightFraction = 0.20f,
          fontSizeSp = 15f,
          fontWeightBold = true,
          textColorArgb = 0xFFE11D48L,
          alignment = 2,
          layerOrder = 1,
        ),
        VisualWidgetElement(
          id = "el_ram",
          kind = VisualElementKind.RAM,
          xFraction = 0.07f,
          yFraction = 0.44f,
          widthFraction = 0.86f,
          heightFraction = 0.18f,
          fontSizeSp = 13f,
          fontWeightBold = false,
          textColorArgb = 0xFFD1D5DBL,
          layerOrder = 2,
        ),
        VisualWidgetElement(
          id = "el_storage",
          kind = VisualElementKind.STORAGE,
          xFraction = 0.07f,
          yFraction = 0.66f,
          widthFraction = 0.86f,
          heightFraction = 0.18f,
          fontSizeSp = 13f,
          fontWeightBold = false,
          textColorArgb = 0xFF9CA3AFL,
          layerOrder = 3,
        ),
      ),
    )
  }

  fun serializeDepthClock(cfg: DepthClockConfig): String {
    return JSONObject().apply {
      put("mode", cfg.mode.name)
      put("font", cfg.fontChoice.id)
      put("seconds", cfg.showSeconds)
      put("date", cfg.showDate)
      put("h24", cfg.use24Hour)
      put("clockColor", cfg.clockColorArgb)
      put("accentColor", cfg.accentColorArgb)
      put("bgColor", cfg.backgroundColorArgb)
      put("bgOpacity", cfg.backgroundOpacity.toDouble())
      put("glass", cfg.glassBlurOverlay)
      put("bgImg", cfg.backgroundImagePath ?: "")
      put("fgImg", cfg.foregroundMaskImagePath ?: "")
      put("depth", cfg.depthLayerStrength.toDouble())
      put("scale", cfg.clockScale.toDouble())
      put("offsetY", cfg.offsetYFraction.toDouble())
    }.toString()
  }

  fun deserializeDepthClock(json: String?): DepthClockConfig {
    if (json.isNullOrBlank()) return DepthClockConfig()
    return runCatching {
      val o = JSONObject(json)
      DepthClockConfig(
        mode = runCatching { DepthClockMode.valueOf(o.optString("mode")) }.getOrDefault(DepthClockMode.DIGITAL),
        fontChoice = ClockFontChoice.entries.firstOrNull { it.id == o.optString("font") } ?: ClockFontChoice.INTER_BOLD,
        showSeconds = o.optBoolean("seconds", false),
        showDate = o.optBoolean("date", true),
        use24Hour = o.optBoolean("h24", true),
        clockColorArgb = o.optLong("clockColor", 0xFFFFFFFFL),
        accentColorArgb = o.optLong("accentColor", 0xFFE11D48L),
        backgroundColorArgb = o.optLong("bgColor", 0xFF08090CL),
        backgroundOpacity = o.optDouble("bgOpacity", 0.88).toFloat(),
        glassBlurOverlay = o.optBoolean("glass", true),
        backgroundImagePath = o.optString("bgImg", "").takeIf { it.isNotBlank() },
        foregroundMaskImagePath = o.optString("fgImg", "").takeIf { it.isNotBlank() },
        depthLayerStrength = o.optDouble("depth", 0.45).toFloat(),
        clockScale = o.optDouble("scale", 1.0).toFloat(),
        offsetYFraction = o.optDouble("offsetY", 0.0).toFloat(),
      )
    }.getOrDefault(DepthClockConfig())
  }

  fun serializeBuiltInStyle(cfg: BuiltInWidgetStyleConfig): String {
    return JSONObject().apply {
      put("bg", cfg.backgroundColorArgb)
      put("opacity", cfg.opacity.toDouble())
      put("accent", cfg.accentColorArgb)
      put("text", cfg.textColorArgb)
      put("subText", cfg.secondaryTextColorArgb)
      put("border", cfg.borderColorArgb)
      put("fontScale", cfg.fontSizeScale.toDouble())
      put("bold", cfg.fontWeightBold)
      put("padDp", cfg.paddingDp)
      put("align", cfg.alignment)
      put("radius", cfg.cornerRadiusDp)
      put("details", cfg.showSecondaryDetails)
      put("custom", cfg.isCustomized)
    }.toString()
  }

  fun deserializeBuiltInStyle(json: String?): BuiltInWidgetStyleConfig {
    if (json.isNullOrBlank()) return BuiltInWidgetStyleConfig()
    return runCatching {
      val o = JSONObject(json)
      BuiltInWidgetStyleConfig(
        backgroundColorArgb = o.optLong("bg", 0xFF050608L),
        opacity = o.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f),
        accentColorArgb = o.optLong("accent", 0xFFE11D48L),
        textColorArgb = o.optLong("text", 0xFFFFFFFFL),
        secondaryTextColorArgb = o.optLong("subText", 0xFF9CA3AFL),
        borderColorArgb = o.optLong("border", 0x26FFFFFFL),
        fontSizeScale = o.optDouble("fontScale", 1.0).toFloat().coerceIn(0.75f, 1.45f),
        fontWeightBold = o.optBoolean("bold", true),
        paddingDp = o.optInt("padDp", 14).coerceIn(4, 28),
        alignment = o.optInt("align", 0).coerceIn(0, 2),
        cornerRadiusDp = o.optInt("radius", 18).coerceIn(0, 32),
        showSecondaryDetails = o.optBoolean("details", true),
        isCustomized = o.optBoolean("custom", false),
      )
    }.getOrDefault(BuiltInWidgetStyleConfig())
  }
}
