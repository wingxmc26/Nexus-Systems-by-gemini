package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

private data class QuickModuleItem(
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
  val overview by viewModel.dashboardState.collectAsStateWithLifecycle()

  val visibleCards = prefs.cardOrder.filter { it !in prefs.hiddenModules }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "NEXUS SYSTEM",
      subtitle = "OFFLINE TELEMETRY ENGINE",
      trailingContent = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          StatusPill(text = "OFFLINE", color = colors.nominal)
          Spacer(modifier = Modifier.width(6.dp))
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
        }
      },
    )

    if (overview.isLoading && overview.hardware == null) {
      LoadingTelemetryState("Initializing system telemetry...")
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
          start = 16.dp,
          end = 16.dp,
          top = 4.dp,
          bottom = contentPadding.calculateBottomPadding() + 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
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
                text = "Tap here to open Dashboard Customization and re-enable telemetry modules.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
              )
            }
          }
        } else {
          items(visibleCards, key = { it.id }) { moduleId ->
            when (moduleId) {
              DashboardModuleId.DEVICE_OVERVIEW -> {
                DeviceOverviewCard(
                  hardware = overview.hardware,
                  onClick = { onNavigate(NexusRoutes.HARDWARE) },
                )
              }
              DashboardModuleId.BATTERY_CARD -> {
                BatterySummaryCard(
                  battery = battery,
                  onClick = { onNavigate(NexusRoutes.BATTERY) },
                )
              }
              DashboardModuleId.MEMORY_STORAGE_CARD -> {
                MemoryStorageCard(
                  hardware = overview.hardware,
                  storage = overview.storage,
                  onHardwareClick = { onNavigate(NexusRoutes.HARDWARE) },
                  onStorageClick = { onNavigate(NexusRoutes.STORAGE) },
                )
              }
              DashboardModuleId.CPU_CARD -> {
                CpuSummaryCard(
                  hardware = overview.hardware,
                  onClick = { onNavigate(NexusRoutes.HARDWARE) },
                )
              }
              DashboardModuleId.QUICK_MODULES -> {
                QuickAccessModulesSection(onNavigate = onNavigate)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DeviceOverviewCard(
  hardware: HardwareTelemetry?,
  onClick: () -> Unit,
) {
  val colors = NexusThemeExtras.colors
  NexusGlassCard(
    accentHighlight = true,
    onClick = onClick,
    testTag = "dashboard_device_card",
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = if (hardware != null) "${hardware.manufacturer} ${hardware.model}" else "Device Info",
          style = MaterialTheme.typography.titleLarge,
          color = colors.textPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = if (hardware != null) {
            "Android ${hardware.androidVersion} (API ${hardware.sdkInt}) • ${hardware.deviceCode}"
          } else {
            "Reading OS fingerprint..."
          },
          style = MaterialTheme.typography.bodyMedium,
          color = colors.textSecondary,
        )
      }
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = "Open Hardware Monitor",
        tint = colors.accent,
      )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      CompactTelemetryPill(
        label = "UPTIME",
        value = hardware?.let { Formatters.formatDuration(it.elapsedUptimeMs) } ?: "--",
        modifier = Modifier.weight(1f),
      )
      CompactTelemetryPill(
        label = "DISPLAY",
        value = hardware?.let { "${it.refreshRateHz.toInt()} Hz" } ?: "--",
        modifier = Modifier.weight(1f),
      )
    }
  }
}

@Composable
private fun BatterySummaryCard(
  battery: BatteryTelemetry,
  onClick: () -> Unit,
) {
  val colors = NexusThemeExtras.colors
  val pct = (battery.percentage as? MetricValue.Available)?.value
  val temp = (battery.temperatureCelsius as? MetricValue.Available)?.value
  val gaugeColor = when {
    battery.isCharging -> colors.nominal
    (pct ?: 100) <= 20 -> colors.critical
    else -> colors.accent
  }

  NexusGlassCard(
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
        centerLabel = if (battery.isCharging) "CHG" else "BAT",
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
            text = "Battery Intelligence",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
          )
        }
        Spacer(modifier = Modifier.height(6.dp))
        MetricKeyValueRow(label = "Status", value = "${battery.statusLabel} (${battery.chargeSource})")
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
private fun MemoryStorageCard(
  hardware: HardwareTelemetry?,
  storage: StorageOverview?,
  onHardwareClick: () -> Unit,
  onStorageClick: () -> Unit,
) {
  val colors = NexusThemeExtras.colors
  val ramFraction = if (hardware != null && hardware.ramTotalBytes > 0) {
    (hardware.ramUsedBytes.toFloat() / hardware.ramTotalBytes.toFloat()).coerceIn(0f, 1f)
  } else 0f

  val storageFraction = if (storage != null && storage.internalTotalBytes > 0) {
    (storage.internalUsedBytes.toFloat() / storage.internalTotalBytes.toFloat()).coerceIn(0f, 1f)
  } else 0f

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    NexusGlassCard(
      modifier = Modifier.weight(1f),
      onClick = onHardwareClick,
      testTag = "dashboard_ram_card",
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Memory, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("RAM Usage", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
      }
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = hardware?.let { Formatters.formatBytes(it.ramUsedBytes) } ?: "--",
        style = NexusTextStyles.MonoMetricMedium,
        color = colors.textPrimary,
      )
      Text(
        text = hardware?.let { "of ${Formatters.formatBytes(it.ramTotalBytes)}" } ?: "Reading...",
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
      )
      Spacer(modifier = Modifier.height(10.dp))
      LinearProgressIndicator(
        progress = { ramFraction },
        modifier = Modifier
          .fillMaxWidth()
          .height(6.dp)
          .clip(RoundedCornerShape(3.dp)),
        color = colors.accent,
        trackColor = colors.elevatedSurface,
        strokeCap = StrokeCap.Round,
      )
    }

    NexusGlassCard(
      modifier = Modifier.weight(1f),
      onClick = onStorageClick,
      testTag = "dashboard_storage_card",
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Storage, contentDescription = null, tint = colors.accentSecondary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Storage Free", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
      }
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = storage?.let { Formatters.formatBytes(it.internalAvailableBytes) } ?: "--",
        style = NexusTextStyles.MonoMetricMedium,
        color = colors.textPrimary,
      )
      Text(
        text = storage?.let { "of ${Formatters.formatBytes(it.internalTotalBytes)}" } ?: "Reading...",
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
      )
      Spacer(modifier = Modifier.height(10.dp))
      LinearProgressIndicator(
        progress = { storageFraction },
        modifier = Modifier
          .fillMaxWidth()
          .height(6.dp)
          .clip(RoundedCornerShape(3.dp)),
        color = colors.accentSecondary,
        trackColor = colors.elevatedSurface,
        strokeCap = StrokeCap.Round,
      )
    }
  }
}

@Composable
private fun CpuSummaryCard(
  hardware: HardwareTelemetry?,
  onClick: () -> Unit,
) {
  val colors = NexusThemeExtras.colors
  val activeFreq = hardware?.cpuCoreFrequencies
    ?.mapNotNull { (it.currentKhz as? MetricValue.Available)?.value }
    ?.maxOrNull()

  NexusGlassCard(
    onClick = onClick,
    testTag = "dashboard_cpu_card",
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.DeveloperBoard, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("CPU & Architecture", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
      }
      StatusPill(
        text = hardware?.let { "${it.cpuCoreCount} CORES" } ?: "--",
        color = colors.accent,
      )
    }
    Spacer(modifier = Modifier.height(10.dp))
    MetricKeyValueRow(
      label = "Hardware / SoC",
      value = when (val hw = hardware?.cpuHardwareName) {
        is MetricValue.Available -> hw.value
        is MetricValue.Unavailable -> hw.reason
        null -> "Reading..."
      },
    )
    MetricKeyValueRow(
      label = "Supported ABIs",
      value = hardware?.cpuAbis ?: "--",
    )
    MetricKeyValueRow(
      label = "Peak Core Clock",
      value = activeFreq?.let { Formatters.formatFrequencyKhz(it) } ?: "Restricted by Android OS",
      valueColor = if (activeFreq != null) colors.nominal else colors.textMuted,
    )
  }
}

@Composable
private fun QuickAccessModulesSection(onNavigate: (String) -> Unit) {
  val modules = listOf(
    QuickModuleItem(NexusRoutes.BATTERY, "Battery Intel", "Voltage, thermals & logs", Icons.Default.BatteryChargingFull, "quick_battery"),
    QuickModuleItem(NexusRoutes.HARDWARE, "Hardware", "CPU, RAM, GPU & kernel", Icons.Default.Memory, "quick_hardware"),
    QuickModuleItem(NexusRoutes.STORAGE, "Storage Lab", "SAF scan & large files", Icons.Default.Storage, "quick_storage"),
    QuickModuleItem(NexusRoutes.NETWORK, "Network", "Interfaces, IP & traffic", Icons.Default.NetworkCheck, "quick_network"),
    QuickModuleItem(NexusRoutes.SENSORS, "Sensor Lab", "Live waveforms & test", Icons.Default.Sensors, "quick_sensors"),
    QuickModuleItem(NexusRoutes.DISPLAY, "Display Test", "Dead pixels & multi-touch", Icons.Default.Monitor, "quick_display"),
    QuickModuleItem(NexusRoutes.AUDIO, "Audio Lab", "Speaker sweep & mic VU", Icons.Default.GraphicEq, "quick_audio"),
    QuickModuleItem(NexusRoutes.APPS, "App Inspector", "Packages & permissions", Icons.Default.Apps, "quick_apps"),
    QuickModuleItem(NexusRoutes.SHORTCUTS, "OS Shortcuts", "System settings intents", Icons.Default.SettingsApplications, "quick_shortcuts"),
    QuickModuleItem(NexusRoutes.CUSTOMIZE, "Customize", "AMOLED, accent & order", Icons.Default.Tune, "quick_customize"),
  )

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    SectionHeader(title = "All Diagnostic Modules", trailingText = "${modules.size} MODULES")
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val cols = if (maxWidth >= 600.dp) 3 else 2
      val rows = modules.chunked(cols)
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { rowItems ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            rowItems.forEach { item ->
              QuickModuleTile(
                item = item,
                onClick = { onNavigate(item.route) },
                modifier = Modifier.weight(1f),
              )
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

@Composable
private fun QuickModuleTile(
  item: QuickModuleItem,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = NexusThemeExtras.colors
  NexusGlassCard(
    modifier = modifier,
    onClick = onClick,
    testTag = item.testTag,
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(colors.accent.copy(alpha = 0.14f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = item.icon,
        contentDescription = item.title,
        tint = colors.accent,
        modifier = Modifier.size(20.dp),
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

@Composable
private fun CompactTelemetryPill(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  val colors = NexusThemeExtras.colors
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(colors.background.copy(alpha = 0.6f))
      .padding(horizontal = 12.dp, vertical = 8.dp),
  ) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = value,
      style = NexusTextStyles.MonoMetricSmall,
      color = colors.textPrimary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
