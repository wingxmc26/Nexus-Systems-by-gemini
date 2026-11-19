package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
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
  val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "System Shortcuts",
      subtitle = "OFFICIAL ANDROID SETTINGS INTENTS",
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
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        NexusGlassCard {
          Text(
            text = "Opens official Android Settings pages directly. Nexus System does not alter restricted system settings without user interaction.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
          )
        }
      }

      items(shortcuts, key = { it.id }) { shortcut ->
        val icon = when (shortcut.id) {
          "display" -> Icons.Default.BrightnessHigh
          "sound" -> Icons.Default.VolumeUp
          "battery" -> Icons.Default.BatteryStd
          "wifi" -> Icons.Default.Wifi
          "apps" -> Icons.Default.Apps
          "developer" -> Icons.Default.Code
          "accessibility" -> Icons.Default.AccessibilityNew
          "storage" -> Icons.Default.Storage
          "device_info" -> Icons.Default.Info
          else -> Icons.Default.SettingsApplications
        }

        NexusGlassCard(
          onClick = if (shortcut.isAvailable) {
            { viewModel.openShortcut(shortcut) }
          } else null,
          testTag = "shortcut_${shortcut.id}",
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = if (shortcut.isAvailable) colors.accent else colors.textMuted,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = shortcut.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (shortcut.isAvailable) colors.textPrimary else colors.textMuted,
              )
              Text(
                text = shortcut.availabilityNote ?: shortcut.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (shortcut.isAvailable) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Open ${shortcut.title}",
                tint = colors.accentSecondary,
              )
            } else {
              StatusPill(text = "UNAVAILABLE", color = colors.textMuted)
            }
          }
        }
      }
    }
  }
}
