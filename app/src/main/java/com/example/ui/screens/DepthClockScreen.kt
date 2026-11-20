package com.example.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ClockFontChoice
import com.example.data.model.DepthClockMode
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.DepthClockViewModel
import com.example.widget.NexusDepthClockWidgetProvider
import com.example.widget.WidgetBitmapRenderer
import kotlinx.coroutines.delay

@Composable
fun DepthClockScreen(
  viewModel: DepthClockViewModel,
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var tick by remember { mutableIntStateOf(0) }

  LaunchedEffect(state.config.showSeconds) {
    while (true) {
      delay(if (state.config.showSeconds) 1000L else 10_000L)
      tick++
    }
  }

  val bgPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri -> if (uri != null) viewModel.importLayerImage(uri, isForegroundMask = false) }

  val fgPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri -> if (uri != null) viewModel.importLayerImage(uri, isForegroundMask = true) }

  val previewBitmap = remember(state.config, tick) {
    runCatching { WidgetBitmapRenderer.renderDepthClockBitmap(state.config).asImageBitmap() }.getOrNull()
  }
  val appWidgetMgr = remember(context) { runCatching { AppWidgetManager.getInstance(context) }.getOrNull() }
  val canPin = remember(appWidgetMgr) {
    runCatching { appWidgetMgr?.isRequestPinAppWidgetSupported == true }.getOrDefault(false)
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Depth Clock Studio",
      subtitle = "Layered Digital, Analog & Photo Clock",
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
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        NexusGlassCard(accentHighlight = true, testTag = "depth_clock_preview_card") {
          SectionHeader(title = "Live Layered Clock Canvas", icon = Icons.Default.AccessTime)
          Spacer(modifier = Modifier.height(8.dp))
          if (previewBitmap != null) {
            Image(
              bitmap = previewBitmap,
              contentDescription = "Depth Clock Preview",
              contentScale = ContentScale.FillWidth,
              modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(720f / 440f)
                .clip(RoundedCornerShape(18.dp)),
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Home-screen widget updates dynamically as a layered depth clock. Note: Android does not permit third-party apps to replace the system lockscreen clock UI directly.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
          )
        }
      }

      item {
        NexusGlassCard {
          SectionHeader(title = "Clock Mode & Typography", icon = Icons.Default.Layers)
          Spacer(modifier = Modifier.height(6.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DepthClockMode.entries.forEach { mode ->
              FilterChip(
                selected = state.config.mode == mode,
                onClick = { viewModel.updateConfig { it.copy(mode = mode) } },
                label = { Text(mode.label) },
              )
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
          Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ClockFontChoice.entries.forEach { font ->
              FilterChip(
                selected = state.config.fontChoice == font,
                onClick = { viewModel.updateConfig { it.copy(fontChoice = font) } },
                label = { Text(font.label) },
              )
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("24-Hour Time", color = colors.textPrimary)
            Switch(checked = state.config.use24Hour, onCheckedChange = { v -> viewModel.updateConfig { it.copy(use24Hour = v) } })
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Show Seconds", color = colors.textPrimary)
            Switch(checked = state.config.showSeconds, onCheckedChange = { v -> viewModel.updateConfig { it.copy(showSeconds = v) } })
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Show Date Header", color = colors.textPrimary)
            Switch(checked = state.config.showDate, onCheckedChange = { v -> viewModel.updateConfig { it.copy(showDate = v) } })
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Glass Overlay Gradient", color = colors.textPrimary)
            Switch(checked = state.config.glassBlurOverlay, onCheckedChange = { v -> viewModel.updateConfig { it.copy(glassBlurOverlay = v) } })
          }
        }
      }

      item {
        NexusGlassCard {
          SectionHeader(title = "Depth Layers & Colors", icon = Icons.Default.Image)
          Spacer(modifier = Modifier.height(8.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              onClick = { bgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
              modifier = Modifier.weight(1f),
            ) {
              Text("Bg Photo")
            }
            OutlinedButton(
              onClick = { fgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
              modifier = Modifier.weight(1f),
            ) {
              Text("Fg Overlay PNG")
            }
          }
          if (state.config.backgroundImagePath != null || state.config.foregroundMaskImagePath != null) {
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
              onClick = { viewModel.updateConfig { it.copy(backgroundImagePath = null, foregroundMaskImagePath = null) } },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Clear Custom Photos")
            }
          }

          Spacer(modifier = Modifier.height(8.dp))
          Text("Clock Color", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
          Spacer(modifier = Modifier.height(6.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(0xFFFFFFFFL, 0xFFE11D48L, 0xFF10B981L, 0xFF3B82F6L, 0xFFF59E0BL, 0xFFD1D5DBL).forEach { argb ->
              Box(
                modifier = Modifier
                  .size(30.dp)
                  .clip(CircleShape)
                  .background(Color(argb))
                  .border(BorderStroke(if (state.config.clockColorArgb == argb) 2.5.dp else 1.dp, Color.White), CircleShape)
                  .clickable { viewModel.updateConfig { it.copy(clockColorArgb = argb) } },
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))
          Text("Clock Scale (${String.format(java.util.Locale.US, "%.2fx", state.config.clockScale)})", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
          Slider(
            value = state.config.clockScale,
            onValueChange = { v -> viewModel.updateConfig { it.copy(clockScale = v) } },
            valueRange = 0.65f..1.45f,
            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
          )
          Text("Depth Layer Scrim (${(state.config.depthLayerStrength * 100).toInt()}%)", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
          Slider(
            value = state.config.depthLayerStrength,
            onValueChange = { v -> viewModel.updateConfig { it.copy(depthLayerStrength = v) } },
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
          )
          Text("Vertical Position", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
          Slider(
            value = state.config.offsetYFraction,
            onValueChange = { v -> viewModel.updateConfig { it.copy(offsetYFraction = v) } },
            valueRange = -0.35f..0.35f,
            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
          )
        }
      }

      item {
        NexusGlassCard {
          OutlinedTextField(
            value = state.presetName,
            onValueChange = { viewModel.setPresetName(it) },
            label = { Text("Clock Preset Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(modifier = Modifier.height(8.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              onClick = { viewModel.savePreset(activate = false) },
              modifier = Modifier.weight(1f),
            ) { Text("Save Preset") }
            Button(
              onClick = { viewModel.savePreset(activate = true) },
              colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
              modifier = Modifier
                .weight(1f)
                .testTag("activate_depth_clock_button"),
            ) { Text("Apply to Widget") }
          }
          if (canPin) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
              onClick = {
                runCatching {
                  appWidgetMgr?.requestPinAppWidget(
                    ComponentName(context, NexusDepthClockWidgetProvider::class.java),
                    null,
                    null,
                  )
                }
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(Icons.Default.AddToHomeScreen, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Pin Depth Clock Widget to Home")
            }
          }
          state.statusMessage?.let { msg ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = colors.nominal)
          }
        }
      }

      if (state.presets.isNotEmpty()) {
        item {
          SectionHeader(title = "Saved Clock Presets", trailingText = "${state.presets.size}")
        }
        items(state.presets, key = { it.id }) { preset ->
          NexusGlassCard(
            accentHighlight = preset.isActiveWidget,
            onClick = { viewModel.loadPreset(preset) },
          ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Column(modifier = Modifier.weight(1f)) {
                Text(preset.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
              }
              if (preset.isActiveWidget) {
                StatusPill(text = "Active", color = colors.nominal)
                Spacer(modifier = Modifier.width(6.dp))
              }
              IconButton(onClick = { viewModel.deletePreset(preset.id) }) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete preset", tint = colors.critical)
              }
            }
          }
        }
      }
    }
  }
}
