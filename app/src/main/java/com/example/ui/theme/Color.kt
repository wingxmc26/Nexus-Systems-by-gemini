package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class AccentPalette(
  val id: String,
  val displayName: String,
  val primary: Color,
  val secondary: Color,
) {
  CRIMSON_RED(
    id = "crimson_red",
    displayName = "Nexus Red",
    primary = Color(0xFFE11D48),
    secondary = Color(0xFFF43F5E),
  ),
  CORAL_RED(
    id = "coral_red",
    displayName = "Crimson",
    primary = Color(0xFFDC2626),
    secondary = Color(0xFFEF4444),
  ),
  SLATE_BLUE(
    id = "slate_blue",
    displayName = "Steel Blue",
    primary = Color(0xFF3B82F6),
    secondary = Color(0xFF60A5FA),
  ),
  EMERALD(
    id = "emerald",
    displayName = "Emerald",
    primary = Color(0xFF10B981),
    secondary = Color(0xFF34D399),
  ),
  AMBER(
    id = "amber",
    displayName = "Amber",
    primary = Color(0xFFF59E0B),
    secondary = Color(0xFFFBBF24),
  );

  companion object {
    fun fromId(id: String?): AccentPalette =
      entries.firstOrNull { it.id == id } ?: CRIMSON_RED
  }
}

object NexusPalette {
  val AmoledBlack = Color(0xFF000000)
  val MatteSurface = Color(0xFF0B0D12)
  val CardSurface = Color(0xFF101218)
  val CardSurfaceAmoled = Color(0xFF0A0B0E)
  val ElevatedSurface = Color(0xFF161922)
  val SubtleBorder = Color(0x18FFFFFF)
  val AccentBorder = Color(0x4DE11D48)
  val TextPrimary = Color(0xFFFFFFFF)
  val TextSecondary = Color(0xFF9CA3AF)
  val TextMuted = Color(0xFF6B7280)

  val StatusNominal = Color(0xFF10B981)
  val StatusWarning = Color(0xFFF59E0B)
  val StatusCritical = Color(0xFFEF4444)
  val StatusInfo = Color(0xFF38BDF8)
}
