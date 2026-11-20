package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.MetricKeyValueRow
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.theme.NexusTextStyles
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.DisplayTestMode
import com.example.ui.viewmodel.DisplayTesterViewModel
import java.util.Locale

@Composable
fun DisplayTesterScreen(
  viewModel: DisplayTesterViewModel,
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val activeTest by viewModel.activeTest.collectAsStateWithLifecycle()
  val colorIndex by viewModel.colorIndex.collectAsStateWithLifecycle()
  val specs = viewModel.specs

  BackHandler(enabled = activeTest != DisplayTestMode.NONE) {
    viewModel.exitTest()
  }

  when (activeTest) {
    DisplayTestMode.DEAD_PIXEL -> {
      val currentColor = viewModel.deadPixelColors[colorIndex]
      val overlayTextColor = if (currentColor.isDark) Color.White else Color.Black
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(currentColor.color)
          .clickable { viewModel.nextDeadPixelColor() }
          .testTag("dead_pixel_fullscreen"),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(999.dp),
          ) {
            Text(
              text = "${currentColor.name} • Tap screen to cycle",
              style = MaterialTheme.typography.labelMedium,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
          }
          ExitTestPill(onExit = { viewModel.exitTest() })
        }
        Text(
          text = "${colorIndex + 1} / ${viewModel.deadPixelColors.size}",
          style = NexusTextStyles.MetricSmall,
          color = overlayTextColor.copy(alpha = 0.5f),
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 32.dp),
        )
      }
      return
    }
    DisplayTestMode.BANDING -> {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black)
          .testTag("banding_fullscreen"),
      ) {
        Column(modifier = Modifier.fillMaxSize()) {
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .background(Brush.horizontalGradient(listOf(Color.Black, Color.Red))),
          )
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .background(Brush.horizontalGradient(listOf(Color.Black, Color.Green))),
          )
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .background(Brush.horizontalGradient(listOf(Color.Black, Color.Blue))),
          )
          Row(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth(),
          ) {
            for (step in 0..15) {
              val v = (step / 15f)
              Box(
                modifier = Modifier
                  .weight(1f)
                  .fillMaxSize()
                  .background(Color(v, v, v)),
              )
            }
          }
        }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(999.dp),
          ) {
            Text(
              text = "RGB & 16-Step Grayscale Uniformity",
              style = MaterialTheme.typography.labelMedium,
              color = Color.White,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
          }
          ExitTestPill(onExit = { viewModel.exitTest() })
        }
      }
      return
    }
    DisplayTestMode.MULTITOUCH -> {
      MultiTouchTestOverlay(onExit = { viewModel.exitTest() })
      return
    }
    DisplayTestMode.NONE -> Unit
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Display Tester",
      subtitle = "PANEL SPECS, DEAD PIXELS & MULTI-TOUCH",
      onBack = onBack,
    )

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 4.dp,
        bottom = contentPadding.calculateBottomPadding() + 24.dp,
      ),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        NexusGlassCard(accentHighlight = true, testTag = "display_specs_card") {
          SectionHeader(title = "Panel & Refresh Capabilities", icon = Icons.Default.Monitor)
          Spacer(modifier = Modifier.height(6.dp))
          MetricKeyValueRow("Physical Resolution", "${specs.physicalWidthPx} × ${specs.physicalHeightPx} px")
          MetricKeyValueRow("Usable Viewport", "${specs.usableWidthPx} × ${specs.usableHeightPx} px")
          MetricKeyValueRow("Density", "${specs.densityDpi} dpi (${specs.densityBucket}, ${specs.densityScale}x)")
          MetricKeyValueRow("Current Refresh Rate", String.format(Locale.US, "%.1f Hz", specs.currentRefreshRateHz))
          MetricKeyValueRow(
            label = "Supported Refresh Rates",
            value = specs.supportedRefreshRatesHz.joinToString(" / ") { "${it.toInt()} Hz" },
          )
          MetricKeyValueRow(
            label = "HDR Support",
            value = if (specs.hdrTypes.isEmpty()) "Standard Dynamic Range" else specs.hdrTypes.joinToString(", "),
          )
          MetricKeyValueRow("Wide Color Gamut", if (specs.isWideColorGamut) "Supported" else "Standard sRGB")
        }
      }

      item {
        NexusGlassCard(testTag = "display_dead_pixel_card") {
          SectionHeader(title = "Dead Pixel & Uniformity Inspection", icon = Icons.Default.Palette)
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Inspect the screen using full-screen solid colors (Red, Green, Blue, White, Black). Tap anywhere to cycle colors or use Exit.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
          )
          Spacer(modifier = Modifier.height(12.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            viewModel.deadPixelColors.forEachIndexed { idx, swatch ->
              Box(
                modifier = Modifier
                  .size(44.dp)
                  .clip(CircleShape)
                  .background(swatch.color)
                  .clickable { viewModel.startDeadPixelTest(idx) }
                  .testTag("swatch_$idx"),
              )
            }
          }
          Spacer(modifier = Modifier.height(12.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Button(
              onClick = { viewModel.startDeadPixelTest(0) },
              colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
              modifier = Modifier
                .weight(1f)
                .testTag("start_dead_pixel_button"),
            ) {
              Text("Start Pixel Test")
            }
            Button(
              onClick = { viewModel.startBandingTest() },
              colors = ButtonDefaults.buttonColors(containerColor = colors.elevatedSurface),
              modifier = Modifier
                .weight(1f)
                .testTag("start_banding_button"),
            ) {
              Icon(Icons.Default.Gradient, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Gradients")
            }
          }
        }
      }

      item {
        NexusGlassCard(testTag = "display_multitouch_card") {
          SectionHeader(title = "Multi-Touch & Digitizer Coordinates", icon = Icons.Default.TouchApp)
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Tracks simultaneous touch points, live X/Y coordinates, pointer IDs, and touch pressure across the entire screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
          )
          Spacer(modifier = Modifier.height(12.dp))
          Button(
            onClick = { viewModel.startMultiTouchTest() },
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("start_multitouch_button"),
          ) {
            Icon(Icons.Default.TouchApp, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Launch Multi-Touch Test")
          }
        }
      }
    }
  }
}

private data class PointerPoint(
  val id: Long,
  val position: Offset,
  val pressure: Float,
)

@Composable
private fun MultiTouchTestOverlay(onExit: () -> Unit) {
  val colors = NexusThemeExtras.colors
  val activePointers = remember { mutableStateMapOf<Long, PointerPoint>() }
  val pointerColors = remember {
    listOf(
      Color(0xFFE11D48),
      Color(0xFF06B6D4),
      Color(0xFF10B981),
      Color(0xFFF59E0B),
      Color(0xFF8B5CF6),
      Color(0xFFEC4899),
    )
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
      .testTag("multitouch_fullscreen")
      .pointerInput(Unit) {
        awaitPointerEventScope {
          while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { change ->
              if (change.pressed) {
                activePointers[change.id.value] = PointerPoint(
                  id = change.id.value,
                  position = change.position,
                  pressure = change.pressure,
                )
              } else {
                activePointers.remove(change.id.value)
              }
            }
            if (event.type == PointerEventType.Release && event.changes.none { it.pressed }) {
              activePointers.clear()
            }
          }
        }
      },
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      // Subtle grid
      val step = 64.dp.toPx()
      var x = 0f
      while (x < size.width) {
        drawLine(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
      }
      var y = 0f
      while (y < size.height) {
        drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
      }

      activePointers.values.forEachIndexed { idx, pt ->
        val c = pointerColors[idx % pointerColors.size]
        drawLine(c.copy(alpha = 0.45f), Offset(0f, pt.position.y), Offset(size.width, pt.position.y), strokeWidth = 1.5.dp.toPx())
        drawLine(c.copy(alpha = 0.45f), Offset(pt.position.x, 0f), Offset(pt.position.x, size.height), strokeWidth = 1.5.dp.toPx())
        drawCircle(color = c.copy(alpha = 0.25f), radius = 44.dp.toPx(), center = pt.position)
        drawCircle(color = c, radius = 44.dp.toPx(), center = pt.position, style = Stroke(width = 2.5.dp.toPx()))
        drawCircle(color = Color.White, radius = 8.dp.toPx(), center = pt.position)
      }
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(16.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          color = colors.cardSurface.copy(alpha = 0.85f),
          shape = RoundedCornerShape(999.dp),
        ) {
          Text(
            text = "Active Touches: ${activePointers.size}",
            style = NexusTextStyles.MetricSmall,
            color = colors.accentSecondary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
          )
        }
        ExitTestPill(onExit = onExit)
      }
      Spacer(modifier = Modifier.height(8.dp))
      activePointers.values.forEach { pt ->
        Text(
          text = "ID ${pt.id}: (${pt.position.x.toInt()}, ${pt.position.y.toInt()}) • P=${String.format(Locale.US, "%.2f", pt.pressure)}",
          style = NexusTextStyles.MetricSmall,
          color = Color.White.copy(alpha = 0.85f),
        )
      }
      if (activePointers.isEmpty()) {
        Text(
          text = "Place one or more fingers anywhere on the screen",
          style = MaterialTheme.typography.bodyMedium,
          color = Color.White.copy(alpha = 0.6f),
        )
      }
    }
  }
}

@Composable
private fun ExitTestPill(onExit: () -> Unit) {
  Surface(
    onClick = onExit,
    color = Color.Black.copy(alpha = 0.78f),
    shape = RoundedCornerShape(999.dp),
    modifier = Modifier.testTag("exit_display_test_button"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Default.Close, contentDescription = "Exit Test", tint = Color.White, modifier = Modifier.size(16.dp))
      Spacer(modifier = Modifier.width(6.dp))
      Text("Exit", style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
  }
}
