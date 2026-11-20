package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.MetricValue
import com.example.ui.theme.NexusTextStyles
import com.example.ui.theme.NexusThemeExtras

@Composable
fun NexusGlassCard(
  modifier: Modifier = Modifier,
  accentHighlight: Boolean = false,
  onClick: (() -> Unit)? = null,
  testTag: String? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val colors = NexusThemeExtras.colors
  val shape = RoundedCornerShape(16.dp)
  val borderColor = if (accentHighlight) {
    colors.accent.copy(alpha = 0.42f)
  } else {
    colors.glassBorder
  }

  val baseModifier = modifier
    .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    .clip(shape)
    .background(colors.cardSurface)
    .border(BorderStroke(1.dp, borderColor), shape)
    .then(
      if (onClick != null) {
        Modifier
          .minimumInteractiveComponentSize()
          .clickable(onClick = onClick)
      } else {
        Modifier
      }
    )

  Column(
    modifier = baseModifier.padding(16.dp),
    content = content,
  )
}

@Composable
fun NexusModuleHeader(
  title: String,
  subtitle: String,
  onBack: (() -> Unit)? = null,
  trailingContent: (@Composable () -> Unit)? = null,
) {
  val colors = NexusThemeExtras.colors

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (onBack != null) {
      IconButton(
        onClick = onBack,
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(colors.cardSurface)
          .border(1.dp, colors.glassBorder, CircleShape)
          .testTag("back_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Navigate back",
          tint = colors.textPrimary,
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
    }

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = colors.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    if (trailingContent != null) {
      Spacer(modifier = Modifier.width(8.dp))
      trailingContent()
    }
  }
}

@Composable
fun SectionHeader(
  title: String,
  icon: ImageVector? = null,
  trailingText: String? = null,
) {
  val colors = NexusThemeExtras.colors
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = colors.accent,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
    }
    Text(
      text = title,
      style = MaterialTheme.typography.labelLarge,
      color = colors.textSecondary,
      modifier = Modifier.weight(1f),
    )
    if (trailingText != null) {
      Text(
        text = trailingText,
        style = MaterialTheme.typography.labelMedium,
        color = colors.textSecondary,
      )
    }
  }
}

@Composable
fun StatusPill(
  text: String,
  color: Color = NexusThemeExtras.colors.accent,
) {
  Surface(
    color = color.copy(alpha = 0.14f),
    shape = RoundedCornerShape(999.dp),
    border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelSmall,
      color = color,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
  }
}

@Composable
fun MetricKeyValueRow(
  label: String,
  value: String,
  valueColor: Color? = null,
) {
  val colors = NexusThemeExtras.colors
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 5.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = colors.textSecondary,
      modifier = Modifier.weight(1f),
    )
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = value,
      style = NexusTextStyles.MetricSmall,
      color = valueColor ?: colors.textPrimary,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
fun <T> MetricValueRow(
  label: String,
  metric: MetricValue<T>,
  formatter: (T) -> String = { it.toString() },
) {
  val colors = NexusThemeExtras.colors
  when (metric) {
    is MetricValue.Available -> MetricKeyValueRow(
      label = label,
      value = formatter(metric.value),
      valueColor = colors.textPrimary,
    )
    is MetricValue.Unavailable -> MetricKeyValueRow(
      label = label,
      value = metric.reason,
      valueColor = colors.textMuted,
    )
  }
}

@Composable
fun TelemetryArcGauge(
  progress: Float,
  centerValue: String,
  centerLabel: String,
  modifier: Modifier = Modifier,
  size: Dp = 92.dp,
  strokeWidth: Dp = 8.dp,
  gaugeColor: Color = NexusThemeExtras.colors.accent,
) {
  val colors = NexusThemeExtras.colors
  val animatedProgress by animateFloatAsState(
    targetValue = progress.coerceIn(0f, 1f),
    animationSpec = tween(durationMillis = 350),
    label = "arc_progress",
  )
  val animatedColor by animateColorAsState(targetValue = gaugeColor, label = "arc_color")

  Box(
    modifier = modifier.size(size),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.size(size)) {
      val strokePx = strokeWidth.toPx()
      val inset = strokePx / 2f
      val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
      drawArc(
        color = colors.elevatedSurface,
        startAngle = 135f,
        sweepAngle = 270f,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = arcSize,
        style = Stroke(width = strokePx, cap = StrokeCap.Round),
      )
      drawArc(
        color = animatedColor,
        startAngle = 135f,
        sweepAngle = 270f * animatedProgress,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = arcSize,
        style = Stroke(width = strokePx, cap = StrokeCap.Round),
      )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = centerValue,
        style = NexusTextStyles.MetricMedium,
        color = colors.textPrimary,
      )
      Text(
        text = centerLabel,
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun TelemetrySparkline(
  samples: List<Float>,
  modifier: Modifier = Modifier,
  lineColor: Color = NexusThemeExtras.colors.accent,
  fixedRange: Pair<Float, Float>? = null,
) {
  val colors = NexusThemeExtras.colors
  Canvas(
    modifier = modifier
      .fillMaxWidth()
      .height(56.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(colors.elevatedSurface.copy(alpha = 0.55f))
      .padding(horizontal = 8.dp, vertical = 8.dp),
  ) {
    if (samples.size < 2) return@Canvas
    val minVal = fixedRange?.first ?: samples.minOrNull() ?: 0f
    val maxVal = fixedRange?.second ?: samples.maxOrNull() ?: 1f
    val span = (maxVal - minVal).takeIf { it > 0.0001f } ?: 1f

    val stepX = size.width / (samples.size - 1).coerceAtLeast(1)
    val path = Path()

    samples.forEachIndexed { idx, v ->
      val x = idx * stepX
      val norm = ((v - minVal) / span).coerceIn(0f, 1f)
      val y = size.height - (norm * size.height)
      if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    drawPath(
      path = path,
      color = lineColor,
      style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
    )
  }
}

@Composable
fun LoadingTelemetryState(message: String = "Loading system information...") {
  val colors = NexusThemeExtras.colors
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(32.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      CircularProgressIndicator(
        color = colors.accent,
        strokeWidth = 3.dp,
        modifier = Modifier.size(32.dp),
      )
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
      )
    }
  }
}

@Composable
fun EmptyOrErrorState(
  icon: ImageVector,
  title: String,
  description: String,
) {
  val colors = NexusThemeExtras.colors
  NexusGlassCard(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = colors.accent,
        modifier = Modifier.size(32.dp),
      )
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = colors.textPrimary,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
      )
    }
  }
}
