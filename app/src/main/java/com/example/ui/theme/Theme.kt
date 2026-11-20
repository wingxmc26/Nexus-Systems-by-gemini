package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class NexusExtendedColors(
  val background: Color,
  val cardSurface: Color,
  val elevatedSurface: Color,
  val glassBorder: Color,
  val accent: Color,
  val accentSecondary: Color,
  val textPrimary: Color,
  val textSecondary: Color,
  val textMuted: Color,
  val nominal: Color,
  val warning: Color,
  val critical: Color,
  val info: Color,
  val isAmoled: Boolean,
)

val LocalNexusColors = staticCompositionLocalOf {
  NexusExtendedColors(
    background = NexusPalette.AmoledBlack,
    cardSurface = NexusPalette.CardSurfaceAmoled,
    elevatedSurface = NexusPalette.ElevatedSurface,
    glassBorder = NexusPalette.SubtleBorder,
    accent = AccentPalette.CRIMSON_RED.primary,
    accentSecondary = AccentPalette.CRIMSON_RED.secondary,
    textPrimary = NexusPalette.TextPrimary,
    textSecondary = NexusPalette.TextSecondary,
    textMuted = NexusPalette.TextMuted,
    nominal = NexusPalette.StatusNominal,
    warning = NexusPalette.StatusWarning,
    critical = NexusPalette.StatusCritical,
    info = NexusPalette.StatusInfo,
    isAmoled = true,
  )
}

private val NexusShapes = Shapes(
  extraSmall = RoundedCornerShape(8.dp),
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(20.dp),
  extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun NexusTheme(
  accentPalette: AccentPalette = AccentPalette.CRIMSON_RED,
  amoledMode: Boolean = true,
  content: @Composable () -> Unit,
) {
  val bg = if (amoledMode) NexusPalette.AmoledBlack else NexusPalette.MatteSurface
  val surface = if (amoledMode) NexusPalette.CardSurfaceAmoled else NexusPalette.CardSurface

  val extendedColors = NexusExtendedColors(
    background = bg,
    cardSurface = surface,
    elevatedSurface = NexusPalette.ElevatedSurface,
    glassBorder = NexusPalette.SubtleBorder,
    accent = accentPalette.primary,
    accentSecondary = accentPalette.secondary,
    textPrimary = NexusPalette.TextPrimary,
    textSecondary = NexusPalette.TextSecondary,
    textMuted = NexusPalette.TextMuted,
    nominal = NexusPalette.StatusNominal,
    warning = NexusPalette.StatusWarning,
    critical = NexusPalette.StatusCritical,
    info = NexusPalette.StatusInfo,
    isAmoled = amoledMode,
  )

  val colorScheme = darkColorScheme(
    primary = accentPalette.primary,
    onPrimary = Color.White,
    primaryContainer = accentPalette.primary.copy(alpha = 0.16f),
    onPrimaryContainer = accentPalette.secondary,
    secondary = accentPalette.secondary,
    onSecondary = Color.White,
    background = bg,
    onBackground = NexusPalette.TextPrimary,
    surface = surface,
    onSurface = NexusPalette.TextPrimary,
    surfaceVariant = NexusPalette.ElevatedSurface,
    onSurfaceVariant = NexusPalette.TextSecondary,
    outline = NexusPalette.SubtleBorder,
    error = NexusPalette.StatusCritical,
    onError = Color.White,
  )

  CompositionLocalProvider(LocalNexusColors provides extendedColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = NexusTypography,
      shapes = NexusShapes,
      content = content,
    )
  }
}

object NexusThemeExtras {
  val colors: NexusExtendedColors
    @Composable get() = LocalNexusColors.current
}
