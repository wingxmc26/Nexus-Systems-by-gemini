package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.DesignServices
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.FavoriteActivityEntity
import com.example.data.model.BatteryTelemetry
import com.example.data.model.DashboardModuleId
import com.example.data.model.HardwareTelemetry
import com.example.data.model.MetricValue
import com.example.data.model.StorageOverview
import com.example.ui.components.LoadingTelemetryState
import com.example.ui.components.MetricKeyValueRow
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.TelemetryArcGauge
import com.example.ui.navigation.NexusRoutes
import com.example.ui.theme.NexusTextStyles
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.MainViewModel
import com.example.util.Formatters
import java.util.Locale

private data class QuickToolTile(
  val route: String,
  val title: String,
  val subtitle: String,
  val icon: ImageVector,
  val testTag: String,
)

@Composable
fun DashboardScreen(
  viewModel: MainViewModel,
  onNavigate: (String) -> Unit,
  contentPadding: PaddingValues,
) {
  val colors = NexusThemeExtras.colors
  val prefs by viewModel.preferences.collectAsStateWithLifecycle()
  val battery by viewModel.batteryTelemetry.collectAsStateWithLifecycle()
  val chargingDurationMs by viewModel.chargingDurationMs.collectAsStateWithLifecycle()
  val favorites by viewModel.favorites.collectAsStateWithLifecycle()
  val overview by viewModel.dashboardState.collectAsStateWithLifecycle()

  val visibleCards = prefs.cardOrder.filter { it !in prefs.hiddenModules }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "NEXUS SYSTEM",
      subtitle = "Device Diagnostics & System Utilities",
      trailingContent = {
        IconButton(
          onClick = { onNavigate(NexusRoutes.CUSTOMIZE) },
          modifier = Modifier
            .size(48.dp)
            .testTag("customize_dashboard_button"),
        ) {
          Icon(
            imageVector = Icons.Default.Tune,
            contentDescription = "Customize Dashboard",
            tint = colors.textPrimary,
          )
        }
      },
    )

    if (overview.isLoading && overview.hardware == null) {
      LoadingTelemetryState("Reading device status...")
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
          start = 16.dp,
          end = 16.dp,
          top = 4.dp,
          bottom = contentPadding.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (visibleCards.isEmpty()) {
          item {
            NexusGlassCard(
              onClick = { onNavigate(NexusRoutes.CUSTOMIZE) },
              testTag = "empty_dashboard_card",
            ) {
              Text(
                text = "All dashboard cards are hidden",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Tap to open Dashboard Customization and enable summary cards.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
              )
            }
          }
        } else {
          items(visibleCards, key = { it.id }) { moduleId ->
            when (moduleId) {
              DashboardModuleId.BATTERY_CARD -> {
                BatteryDashboardCard(
                  battery = battery,
                  chargingDurationMs = chargingDurationMs,
                  onClick = { onNavigate(NexusRoutes.BATTERY) },
                )
              }
              DashboardModuleId.RAM_CARD -> {
                RamDashboardCard(
                  hardware = overview.hardware,
                  onClick = { onNavigate(NexusRoutes.RAM) },
                )
              }
              DashboardModuleId.STORAGE_CARD -> {
                StorageDashboardCard(
                  storage = overview.storage,
                  onClick = { onNavigate(NexusRoutes.STORAGE) },
                )
              }
              DashboardModuleId.DEVICE_OVERVIEW -> {
                DeviceDashboardCard(
                  hardware = overview.hardware,
                  onClick = { onNavigate(NexusRoutes.HARDWARE) },
                )
              }
              DashboardModuleId.FAVORITE_SHORTCUTS -> {
                FavoriteShortcutsDashboardCard(
                  favorites = favorites,
                  onLaunchFavorite = { viewModel.launchFavorite(it) },
                  onOpenLauncher = { onNavigate(NexusRoutes.LAUNCHER) },
                )
              }
              DashboardModuleId.QUICK_MODULES -> {
                SystemToolsGrid(onNavigate = onNavigate)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BatteryDashboardCard(
  battery: BatteryTelemetry,
  chargingDurationMs: Long,
  onClick: () -> Unit,
) {
  val colors = NexusThemeExtras.colors
  val pct = (battery.percentage as? MetricValue.Available)?.value
  val temp = (battery.temperatureCelsius as? MetricValue.Available)?.value
  val currentMa = (battery.currentMicroAmps as? MetricValue.Available)?.value
  val gaugeColor = when {
    battery.isCharging -> colors.nominal
    (pct ?: 100) <= 20 -> colors.critical
    else -> colors.accent
  }

  NexusGlassCard(
    accentHighlight = true,
    onClick = onClick,
    testTag = "dashboard_battery_card",
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TelemetryArcGauge(
        progress = (pct ?: 0) / 100f,
        centerValue = pct?.let { "$it%" } ?: "N/A",
        centerLabel = if (battery.isCharging) "Charging" else "Battery",
        gaugeColor = gaugeColor,
        size = 86.dp,
        strokeWidth = 8.dp,
      )
      Spacer(modifier = Modifier.width(16.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = if (battery.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
            contentDescription = null,
            tint = gaugeColor,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = "Battery & Charging",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
          )
        }
        Spacer(modifier = Modifier.height(6.dp))
        MetricKeyValueRow("Status", "${battery.statusLabel} (${battery.chargeSource})")
        if (battery.isCharging && chargingDurationMs > 0L) {
          MetricKeyValueRow(
            label = "Charging Time",
            value = Formatters.formatTimer(chargingDurationMs),
            valueColor = colors.nominal,
          )
        }
        MetricKeyValueRow(
          label = "Current",
          value = currentMa?.let { Formatters.formatCurrentMa(it, battery.isCharging) } ?: "Unavailable",
          valueColor = if (currentMa != null) colors.textPrimary else colors.textMuted,
        )
        MetricKeyValueRow(
          label = "Temperature",
          value = temp?.let { String.format(Locale.US, "%.1f °C", it) } ?: "Unavailable",
          valueColor = if ((temp ?: 0f) >= 42f) colors.warning else colors.textPrimary,
        )
      }
    }
  }
}

@Composable
private fun RamDashboardCard(
  hardware: HardwareTelemetry?,
  onClick: () -> Unit,
) {
  val colors = NexusThemeExtras.colors
  val ramFraction = if (hardware != null && hardware.ramTotalBytes > 0) {
    (hardware.ramUsedBytes.toFloat() / hardware.ramTotalBytes.toFloat()).coerceIn(0f, 1f)
  } else 0f

  NexusGlassCard(
    onClick = onClick,
    testTag = "dashboard_ram_card",
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Memory, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("RAM Usage", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
      }
      Text(
        text = "${(ramFraction * 100f).toInt()}%",
        style = NexusTextStyles.MetricMedium,
        color = colors.textPrimary,
      )
    }
    Spacer(modifier = Modifier.height(10.dp))
    LinearProgressIndicator(
      progress = { ramFraction },
      modifier = Modifier
        .fillMaxWidth()
        .height(7.dp)
        .clip(RoundedCornerShape(4.dp)),
      color = colors.accent,
      trackColor = colors.elevatedSurface,
      strokeCap = StrokeCap.Round,
    )
    Spacer(modifier = Modifier.height(8.dp))
    MetricKeyValueRow(
      label = "Used / Total",
      value = hardware?.let {
        "${Formatters.formatBytes(it.ramUsedBytes)} / ${Formatters.formatBytes(it.ramTotalBytes)}"
      } ?: "--",
    )
    MetricKeyValueRow(
      label = "Available RAM",
      value = hardware?.let { Formatters.formatBytes(it.ramAvailableBytes) } ?: "--",
      valueColor = colors.nominal,
    )
  }
}

@Composable
private fun StorageDashboardCard(
  storage: StorageOverview?,
  onClick: () -> Unit,
) {
  val colors = NexusThemeExtras.colors
  val fraction = if (storage != null && storage.internalTotalBytes > 0) {
    (storage.internalUsedBytes.toFloat() / storage.internalTotalBytes.toFloat()).coerceIn(0f, 1f)
  } else 0f

  NexusGlassCard(
    onClick = onClick,
    testTag = "dashboard_storage_card",
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Storage, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Storage Usage", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
      }
      Text(
        text = "${(fraction * 100f).toInt()}%",
        style = NexusTextStyles.MetricMedium,
        color = colors.textPrimary,
      )
    }
    Spacer(modifier = Modifier.height(10.dp))
    LinearProgressIndicator(
      progress = { fraction },
      modifier = Modifier
        .fillMaxWidth()
        .height(7.dp)
        .clip(RoundedCornerShape(4.dp)),
      color = colors.accent,
      trackColor = colors.elevatedSurface,
      strokeCap = StrokeCap.Round,
    )
    Spacer(modifier = Modifier.height(8.dp))
    MetricKeyValueRow(
      label = "Used / Total",
      value = storage?.let {
        "${Formatters.formatBytes(it.internalUsedBytes)} / ${Formatters.formatBytes(it.internalTotalBytes)}"
      } ?: "--",
    )
    MetricKeyValueRow(
      label = "Available Space",
      value = storage?.let { Formatters.formatBytes(it.internalAvailableBytes) } ?: "--",
      valueColor = colors.nominal,
    )
  }
}

@Composable
private fun DeviceDashboardCard(
  hardware: HardwareTelemetry?,
  onClick: () -> Unit,
) {
  val colors = NexusThemeExtras.colors
  NexusGlassCard(
    onClick = onClick,
    testTag = "dashboard_device_card",
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text(
            text = if (hardware != null) "${hardware.manufacturer} ${hardware.model}" else "Device Information",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = if (hardware != null) "Android ${hardware.androidVersion} (SDK ${hardware.sdkInt})" else "Reading OS details...",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
          )
        }
      }
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = "Open Device Information",
        tint = colors.textSecondary,
      )
    }
    Spacer(modifier = Modifier.height(10.dp))
    MetricKeyValueRow("Screen", hardware?.let { "${it.screenResolution} • ${it.refreshRateHz.toInt()} Hz" } ?: "--")
    MetricKeyValueRow("Uptime", hardware?.let { Formatters.formatDuration(it.elapsedUptimeMs) } ?: "--")
  }
}

@Composable
private fun FavoriteShortcutsDashboardCard(
  favorites: List<FavoriteActivityEntity>,
  onLaunchFavorite: (FavoriteActivityEntity) -> Unit,
  onOpenLauncher: () -> Unit,
) {
  val colors = NexusThemeExtras.colors
  NexusGlassCard(testTag = "dashboard_favorites_card") {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Star, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Favorite Shortcuts", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
      }
      StatusPill(text = if (favorites.isEmpty()) "ADD" else "${favorites.size} PINNED", color = colors.accent)
    }
    Spacer(modifier = Modifier.height(10.dp))
    if (favorites.isEmpty()) {
      NexusGlassCard(
        onClick = onOpenLauncher,
        testTag = "empty_favorites_cta",
      ) {
        Text(
          text = "No favorite shortcuts added yet",
          style = MaterialTheme.typography.bodyMedium,
          color = colors.textPrimary,
        )
        Text(
          text = "Tap to open Package Activity Launcher and pin apps or activities.",
          style = MaterialTheme.typography.bodyMedium,
          color = colors.textSecondary,
        )
      }
    } else {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        favorites.forEach { fav ->
          NexusGlassCard(
            onClick = { onLaunchFavorite(fav) },
            modifier = Modifier.width(148.dp),
            testTag = "favorite_chip_${fav.id}",
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(28.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(Color(fav.accentColorArgb).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Default.Launch,
                  contentDescription = null,
                  tint = Color(fav.accentColorArgb),
                  modifier = Modifier.size(15.dp),
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = fav.customLabel,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = fav.originalAppName,
              style = MaterialTheme.typography.labelSmall,
              color = colors.textSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SystemToolsGrid(onNavigate: (String) -> Unit) {
  val tools = listOf(
    QuickToolTile(NexusRoutes.BATTERY, "Battery & Charging", "Current, health & timer", Icons.Default.BatteryChargingFull, "quick_battery"),
    QuickToolTile(NexusRoutes.RAM, "RAM Manager", "Memory & cache trim", Icons.Default.Memory, "quick_ram"),
    QuickToolTile(NexusRoutes.STORAGE, "Storage Manager", "Files, ZIP & cleanup", Icons.Default.FolderOpen, "quick_storage"),
    QuickToolTile(NexusRoutes.MUSIC, "Music Player", "Offline audio & queue", Icons.Default.MusicNote, "quick_music"),
    QuickToolTile(NexusRoutes.VIDEO, "Video Player", "ExoPlayer, PiP & subs", Icons.Default.VideoLibrary, "quick_video"),
    QuickToolTile(NexusRoutes.SOUND_BOOSTER, "Sound Booster", "Hardware EQ & bass", Icons.Default.Equalizer, "quick_sound_booster"),
    QuickToolTile(NexusRoutes.WALLPAPER, "Live Wallpaper", "Video, photo & GIF studio", Icons.Default.Wallpaper, "quick_wallpaper"),
    QuickToolTile(NexusRoutes.LAUNCHER, "Shortcut Studio", "Custom icons & pin", Icons.Default.RocketLaunch, "quick_launcher"),
    QuickToolTile(NexusRoutes.WIDGET_STUDIO, "Widget Studio", "Visual & HTML builder", Icons.Default.DesignServices, "quick_widget_studio"),
    QuickToolTile(NexusRoutes.DEPTH_CLOCK, "Depth Clock", "Layered clock widgets", Icons.Default.AccessTime, "quick_depth_clock"),
    QuickToolTile(NexusRoutes.APP_MANAGER, "App Manager", "Packages & permissions", Icons.Default.Apps, "quick_app_manager"),
    QuickToolTile(NexusRoutes.NOTIFICATIONS, "Notifications", "Local history & filters", Icons.Default.Notifications, "quick_notifications"),
    QuickToolTile(NexusRoutes.RECORDER, "Screen Recorder", "MP4 capture & shot", Icons.Default.Videocam, "quick_recorder"),
    QuickToolTile(NexusRoutes.SHORTCUTS, "Quick Controls", "Brightness & settings", Icons.Default.SettingsApplications, "quick_controls"),
    QuickToolTile(NexusRoutes.HARDWARE, "Device Info", "SoC, screen & hardware", Icons.Default.PhoneAndroid, "quick_hardware"),
    QuickToolTile(NexusRoutes.SENSORS, "Sensor Lab", "Live sensor waveforms", Icons.Default.Sensors, "quick_sensors"),
    QuickToolTile(NexusRoutes.DISPLAY, "Display Tester", "Pixel & touch tests", Icons.Default.Monitor, "quick_display"),
    QuickToolTile(NexusRoutes.WIDGETS, "Home Widgets", "Pin 7 system widgets", Icons.Default.Widgets, "quick_widgets"),
  )

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    SectionHeader(title = "NEXUS Utility Suite", trailingText = "${tools.size} modules")
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val cols = if (maxWidth >= 600.dp) 3 else 2
      val rows = tools.chunked(cols)
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { rowItems ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            rowItems.forEach { item ->
              val colors = NexusThemeExtras.colors
              NexusGlassCard(
                modifier = Modifier.weight(1f),
                onClick = { onNavigate(item.route) },
                testTag = item.testTag,
              ) {
                Box(
                  modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.accent.copy(alpha = 0.14f)),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp),
                  )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                  text = item.title,
                  style = MaterialTheme.typography.titleMedium,
                  color = colors.textPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = item.subtitle,
                  style = MaterialTheme.typography.bodyMedium,
                  color = colors.textSecondary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
            repeat(cols - rowItems.size) {
              Spacer(modifier = Modifier.weight(1f))
            }
          }
        }
      }
    }
  }
}
