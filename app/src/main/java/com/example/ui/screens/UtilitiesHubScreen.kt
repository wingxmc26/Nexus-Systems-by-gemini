package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.navigation.NexusRoutes
import com.example.ui.theme.NexusThemeExtras

private data class UtilityEntry(
  val route: String,
  val title: String,
  val description: String,
  val icon: ImageVector,
  val tag: String,
  val testTag: String,
)

@Composable
fun UtilitiesHubScreen(
  onNavigate: (String) -> Unit,
  contentPadding: PaddingValues,
) {
  val colors = NexusThemeExtras.colors
  val utilities = listOf(
    UtilityEntry(NexusRoutes.STORAGE, "Storage Analyzer", "Volume metrics, SAF folder scanner, large file finder", Icons.Default.Storage, "SAF", "hub_storage"),
    UtilityEntry(NexusRoutes.NETWORK, "Network Monitor", "Wi-Fi link, IPv4/IPv6, TrafficStats & offline checks", Icons.Default.NetworkCheck, "OFFLINE", "hub_network"),
    UtilityEntry(NexusRoutes.SENSORS, "Sensor Lab", "Live waveforms, compass, tilt level & availability", Icons.Default.Sensors, "LIVE", "hub_sensors"),
    UtilityEntry(NexusRoutes.DISPLAY, "Display Tester", "Dead pixels, grayscale banding & multi-touch", Icons.Default.Monitor, "TEST", "hub_display"),
    UtilityEntry(NexusRoutes.AUDIO, "Audio Diagnostics", "Speaker frequency test & microphone VU meter", Icons.Default.GraphicEq, "PCM", "hub_audio"),
    UtilityEntry(NexusRoutes.APPS, "App Inspector", "Launchable apps, APK size, permissions & launch", Icons.Default.Apps, "PKG", "hub_apps"),
    UtilityEntry(NexusRoutes.SHORTCUTS, "System Shortcuts", "Official Android Settings intents", Icons.Default.SettingsApplications, "OS", "hub_shortcuts"),
    UtilityEntry(NexusRoutes.CUSTOMIZE, "Dashboard Customization", "Card ordering, visibility, accent color & AMOLED", Icons.Default.Tune, "UI", "hub_customize"),
  )

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Diagnostic Suite",
      subtitle = "HARDWARE LABS & SYSTEM UTILITIES",
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
        SectionHeader(title = "Labs & Tools", trailingText = "${utilities.size} UTILITIES")
      }
      items(utilities, key = { it.route }) { item ->
        NexusGlassCard(
          onClick = { onNavigate(item.route) },
          testTag = item.testTag,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.accent.copy(alpha = 0.14f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(item.icon, contentDescription = null, tint = colors.accent)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(item.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
              Spacer(modifier = Modifier.height(2.dp))
              Text(item.description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = "Open ${item.title}",
              tint = colors.textSecondary,
            )
          }
        }
      }
    }
  }
}
