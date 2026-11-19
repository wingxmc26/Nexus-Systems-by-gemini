package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class AccentPalette(
  val id: String,
  val displayName: String,
  val primary: Color,
  val secondary: Color,
  val glow: Color,
) {
  CRIMSON_RED(
    id = "crimson_red",
    displayName = "Crimson Pulse",
    primary = Color(0xFFE11D48),
    secondary = Color(0xFFFF4D6D),
    glow = Color(0x33E11D48),
  ),
  CYBER_CYAN(
    id = "cyber_cyan",
    displayName = "Cyber Cyan",
    primary = Color(0xFF06B6D4),
    secondary = Color(0xFF22D3EE),
    glow = Color(0x3306B6D4),
  ),
  AMBER_CORE(
    id = "amber_core",
    displayName = "Amber Core",
    primary = Color(0xFFF59E0B),
    secondary = Color(0xFFFBBF24),
    glow = Color(0x33F59E0B),
  ),
  EMERALD_MATRIX(
    id = "emerald_matrix",
    displayName = "Emerald Matrix",
    primary = Color(0xFF10B981),
    secondary = Color(0xFF34D399),
    glow = Color(0x3310B981),
  ),
  ELECTRIC_VIOLET(
    id = "electric_violet",
    displayName = "Electric Violet",
    primary = Color(0xFF8B5CF6),
    secondary = Color(0xFFA78BFA),
    glow = Color(0x338B5CF6),
  );

  companion object {
    fun fromId(id: String?): AccentPalette =
      entries.firstOrNull { it.id == id } ?: CRIMSON_RED
  }
}

object NexusPalette {
  val AmoledBlack = Color(0xFF000000)
  val MatteObsidian = Color(0xFF090B10)
  val CardSurface = Color(0xFF0F1219)
  val CardSurfaceAmoled = Color(0xFF080A0F)
  val ElevatedSurface = Color(0xFF161B26)
  val GlassHighlight = Color(0x14FFFFFF)
  val GlassBorder = Color(0x1FFFFFFF)
  val TextPrimary = Color(0xFFF3F4F6)
  val TextSecondary = Color(0xFF9CA3AF)
  val TextMuted = Color(0xFF6B7280)

  val StatusNominal = Color(0xFF10B981)
  val StatusWarning = Color(0xFFF59E0B)
  val StatusCritical = Color(0xFFEF4444)
  val StatusInfo = Color(0xFF38BDF8)
}
