package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ControlCapabilityType
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.ShortcutsViewModel

@Composable
fun ShortcutsScreen(
  viewModel: ShortcutsViewModel,
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val lifecycleOwner = LocalLifecycleOwner.current

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  val timeoutOptions = listOf(
    15_000 to "15s",
    30_000 to "30s",
    60_000 to "1m",
    120_000 to "2m",
    300_000 to "5m",
    600_000 to "10m",
  )

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Quick System Controls",
      subtitle = "Direct Controls & Official Android Settings",
      onBack = onBack,
      trailingContent = {
        IconButton(
          onClick = { viewModel.refresh() },
          modifier = Modifier
            .size(48.dp)
            .testTag("controls_refresh_button"),
        ) {
          Icon(Icons.Default.Refresh, contentDescription = "Refresh System Status", tint = colors.textPrimary)
        }
      },
    )

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 4.dp,
        bottom = contentPadding.calculateBottomPadding() + 24.dp,
      ),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        NexusGlassCard(accentHighlight = true, testTag = "write_settings_card") {
          SectionHeader(
            title = "Direct System Write Controls",
            icon = Icons.Default.Security,
            trailingText = if (state.canWriteSettings) "Granted" else "Locked",
          )
          Spacer(modifier = Modifier.height(6.dp))
          if (!state.canWriteSettings) {
            Text(
              text = "Grant Modify System Settings permission to adjust Brightness, Adaptive Brightness, Screen Timeout, and Auto-Rotate directly inside Nexus System.",
              style = MaterialTheme.typography.bodyMedium,
              color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
              onClick = { runCatching { context.startActivity(viewModel.buildWriteSettingsIntent()) } },
              colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("grant_write_settings_button"),
            ) {
              Text("Grant Modify System Settings")
            }
          } else {
            Text(
              text = "Screen Brightness: ${state.brightnessPercent}% (${if (state.isAutoBrightness) "Adaptive" else "Manual"})",
              style = MaterialTheme.typography.titleMedium,
              color = colors.textPrimary,
            )
            Slider(
              value = state.brightnessPercent.toFloat(),
              onValueChange = { viewModel.setBrightnessPercent(it.toInt()) },
              valueRange = 1f..100f,
              colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Adaptive Brightness", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
              Switch(
                checked = state.isAutoBrightness,
                onCheckedChange = { viewModel.setAutoBrightness(it) },
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Auto-Rotate Screen", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
              Switch(
                checked = state.isAutoRotate,
                onCheckedChange = { viewModel.setAutoRotate(it) },
              )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text("Screen Sleep Timeout", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              timeoutOptions.forEach { (ms, label) ->
                FilterChip(
                  selected = state.screenTimeoutMs == ms,
                  onClick = { viewModel.setScreenTimeoutMs(ms) },
                  label = { Text(label) },
                )
              }
            }
          }

          state.feedback?.let { fb ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(fb, style = MaterialTheme.typography.labelMedium, color = colors.warning)
          }
        }
      }

      item {
        SectionHeader(title = "System Capabilities & Settings Shortcuts", trailingText = "${state.controls.size}")
      }

      items(state.controls, key = { it.id }) { control ->
        val icon = when (control.id) {
          "brightness" -> Icons.Default.BrightnessHigh
          "screen_timeout" -> Icons.Default.Timer
          "auto_rotate" -> Icons.Default.ScreenRotation
          "sound" -> Icons.Default.VolumeUp
          "battery_saver" -> Icons.Default.BatterySaver
          "app_self" -> Icons.Default.Info
          "all_apps" -> Icons.Default.Apps
          "wifi" -> Icons.Default.Wifi
          "storage" -> Icons.Default.Storage
          "accessibility" -> Icons.Default.AccessibilityNew
          "developer" -> Icons.Default.Code
          else -> Icons.Default.SettingsApplications
        }
        val capColor = when (control.capabilityType) {
          ControlCapabilityType.DIRECT -> colors.nominal
          ControlCapabilityType.PERMISSION_REQUIRED -> colors.warning
          ControlCapabilityType.SETTINGS_SHORTCUT -> colors.accentSecondary
          ControlCapabilityType.UNSUPPORTED -> colors.textMuted
        }

        NexusGlassCard(
          onClick = if (control.isAvailable) {
            { viewModel.openControl(control) }
          } else null,
          testTag = "shortcut_${control.id}",
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = if (control.isAvailable) colors.accent else colors.textMuted,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = control.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (control.isAvailable) colors.textPrimary else colors.textMuted,
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = control.description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
              StatusPill(
                text = if (control.isAvailable) control.currentStateText else "Unavailable",
                color = capColor,
              )
              Text(control.capabilityType.label, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            }
            if (control.isAvailable) {
              Spacer(modifier = Modifier.width(6.dp))
              Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open ${control.title}",
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }
    }
  }
}
