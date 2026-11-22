package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
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
  val testTag: String,
)

private data class UtilityCategoryGroup(
  val header: String,
  val entries: List<UtilityEntry>,
)

@Composable
fun UtilitiesHubScreen(
  onNavigate: (String) -> Unit,
  contentPadding: PaddingValues,
) {
  val colors = NexusThemeExtras.colors
  val groups = listOf(
    UtilityCategoryGroup(
      header = "Device",
      entries = listOf(
        UtilityEntry(NexusRoutes.BATTERY, "Battery & Charging", "Live current (mA), charging timer, sessions & history", Icons.Default.BatteryChargingFull, "hub_battery"),
        UtilityEntry(NexusRoutes.HARDWARE, "Device Information", "Manufacturer, Android version, processor, GPU & screen specs", Icons.Default.PhoneAndroid, "hub_hardware"),
        UtilityEntry(NexusRoutes.SHORTCUTS, "Quick System Controls", "Direct brightness, timeout & rotate plus settings shortcuts", Icons.Default.SettingsApplications, "hub_shortcuts"),
      ),
    ),
    UtilityCategoryGroup(
      header = "Privacy & Encryption",
      entries = listOf(
        UtilityEntry(NexusRoutes.APP_LOCK, "App Lock", "Pattern, PIN, Password, Biometric & Background Guard", Icons.Default.Lock, "hub_app_lock"),
        UtilityEntry(NexusRoutes.SECURE_VAULT, "Secure Vault", "AES-256-GCM Keystore vault, folders & encrypted backup", Icons.Default.EnhancedEncryption, "hub_secure_vault"),
      ),
    ),
    UtilityCategoryGroup(
      header = "Floating, Edge & Gestures",
      entries = listOf(
        UtilityEntry(NexusRoutes.FLOATING_TOOLS, "Floating Tools", "Freeform Apps launcher, floating WebView browser, Notes & Calc", Icons.Default.BubbleChart, "hub_floating_tools"),
        UtilityEntry(NexusRoutes.EDGE_PANEL, "Edge Panel", "Left/right swipe drawer with categories, favorites & combos", Icons.AutoMirrored.Filled.ViewSidebar, "hub_edge_panel"),
        UtilityEntry(NexusRoutes.GESTURE_STUDIO, "Gesture Studio", "Swipes, double-tap, long-press, 2-finger & zones", Icons.Default.Gesture, "hub_gesture_studio"),
      ),
    ),
    UtilityCategoryGroup(
      header = "Customization",
      entries = listOf(
        UtilityEntry(NexusRoutes.WALLPAPER, "Live Wallpaper Studio", "Video, image & GIF library with crop, speed & apply", Icons.Default.Wallpaper, "hub_wallpaper"),
        UtilityEntry(NexusRoutes.LAUNCHER, "Shortcut Studio", "Custom app icons, shapes, activity inspection & pinning", Icons.Default.RocketLaunch, "hub_launcher"),
        UtilityEntry(NexusRoutes.WIDGET_STUDIO, "Widget Studio", "Visual drag canvas, HTML/CSS/JS widgets & Auto-Fit DP", Icons.Default.DesignServices, "hub_widget_studio"),
        UtilityEntry(NexusRoutes.DEPTH_CLOCK, "Depth Clock Studio", "Digital/Analog layered clock widget with custom art", Icons.Default.AccessTime, "hub_depth_clock"),
      ),
    ),
    UtilityCategoryGroup(
      header = "Media",
      entries = listOf(
        UtilityEntry(NexusRoutes.MUSIC, "Music Player", "Offline Media3 audio player, playlists, queue & lyrics", Icons.Default.MusicNote, "hub_music"),
        UtilityEntry(NexusRoutes.VIDEO, "Video Player", "Offline ExoPlayer with .SRT/.VTT subtitles, gestures & PiP", Icons.Default.VideoLibrary, "hub_video"),
        UtilityEntry(NexusRoutes.SOUND_BOOSTER, "Sound Booster & EQ", "Hardware 10-band equalizer, bass boost, virtualizer & gain", Icons.Default.Equalizer, "hub_sound_booster"),
      ),
    ),
    UtilityCategoryGroup(
      header = "Management & Tools",
      entries = listOf(
        UtilityEntry(NexusRoutes.APP_MANAGER, "Advanced App Manager", "Packages, permissions, APK sizes & optional usage stats", Icons.Default.Apps, "hub_app_manager"),
        UtilityEntry(NexusRoutes.NOTIFICATIONS, "Notification Studio", "Local notification history, filters, pinning & dismiss", Icons.Default.Notifications, "hub_notifications"),
        UtilityEntry(NexusRoutes.RECORDER, "Screen Recorder Studio", "MP4 screen capture, screenshots & mic narration", Icons.Default.Videocam, "hub_recorder"),
        UtilityEntry(NexusRoutes.WIDGETS, "Home Widgets", "Pin any of 7 system, clock, music & custom widgets", Icons.Default.Widgets, "hub_widgets"),
        UtilityEntry(NexusRoutes.PERMISSIONS, "Permissions & Privacy Audit", "Inspect & manage optional capabilities in Settings", Icons.Default.Security, "hub_permissions"),
        UtilityEntry(NexusRoutes.CUSTOMIZE, "Dashboard Customization", "Card ordering, visibility, accent color & AMOLED", Icons.Default.Tune, "hub_customize"),
      ),
    ),
  )

  val quickAdvancedModules = listOf(
    Triple(NexusRoutes.APP_LOCK, "App Lock", Icons.Default.Lock),
    Triple(NexusRoutes.SECURE_VAULT, "Vault", Icons.Default.EnhancedEncryption),
    Triple(NexusRoutes.FLOATING_TOOLS, "Floating", Icons.Default.BubbleChart),
    Triple(NexusRoutes.EDGE_PANEL, "Edge Panel", Icons.AutoMirrored.Filled.ViewSidebar),
    Triple(NexusRoutes.GESTURE_STUDIO, "Gestures", Icons.Default.Gesture),
  )

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "NEXUS Suite",
      subtitle = "Privacy, Overlays, Gestures, Customization & Media",
    )

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      quickAdvancedModules.forEach { (route, label, icon) ->
        AssistChip(
          onClick = { onNavigate(route) },
          label = { Text(label) },
          leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.accent) },
        )
      }
    }

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(bottom = contentPadding.calculateBottomPadding())
        .testTag("utilities_hub_list"),
      contentPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 4.dp,
        bottom = 24.dp,
      ),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      groups.forEach { group ->
        item(key = "header_${group.header}") {
          SectionHeader(title = group.header, trailingText = "${group.entries.size} modules")
        }
        items(group.entries, key = { it.route }) { item ->
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
}
