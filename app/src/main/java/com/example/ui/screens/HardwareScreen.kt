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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MetricValue
import com.example.ui.components.EmptyOrErrorState
import com.example.ui.components.LoadingTelemetryState
import com.example.ui.components.MetricKeyValueRow
import com.example.ui.components.MetricValueRow
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.HardwareUiState
import com.example.ui.viewmodel.HardwareViewModel
import com.example.util.Formatters
import java.util.Locale

@Composable
fun HardwareScreen(
  viewModel: HardwareViewModel,
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Device Information",
      subtitle = "Hardware, Processor, GPU & OS Details",
      onBack = onBack,
      trailingContent = {
        IconButton(
          onClick = { viewModel.refresh() },
          modifier = Modifier.testTag("hardware_refresh_button"),
        ) {
          Icon(Icons.Default.Refresh, contentDescription = "Refresh Hardware", tint = colors.textPrimary)
        }
      },
    )

    when (val s = state) {
      is HardwareUiState.Loading -> LoadingTelemetryState("Probing hardware interfaces...")
      is HardwareUiState.Error -> {
        Column(modifier = Modifier.padding(16.dp)) {
          EmptyOrErrorState(
            icon = Icons.Default.ErrorOutline,
            title = "Hardware Probe Error",
            description = s.message,
          )
        }
      }
      is HardwareUiState.Success -> {
        val hw = s.data
        val ramPct = if (hw.ramTotalBytes > 0) (hw.ramUsedBytes.toFloat() / hw.ramTotalBytes).coerceIn(0f, 1f) else 0f

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
            NexusGlassCard(accentHighlight = true, testTag = "hardware_device_card") {
              SectionHeader(title = "Device & OS Build", icon = Icons.Default.PhoneAndroid)
              Spacer(modifier = Modifier.height(6.dp))
              MetricKeyValueRow("Model", "${hw.manufacturer} ${hw.model}")
              MetricKeyValueRow("Device / Board", "${hw.deviceCode} / ${hw.board}")
              MetricKeyValueRow("Android Version", "${hw.androidVersion} (API ${hw.sdkInt})")
              MetricValueRow("Security Patch", hw.securityPatch)
              MetricKeyValueRow("Build ID", hw.buildId)
              MetricValueRow("Kernel", hw.kernelVersion)
              MetricKeyValueRow("Total Uptime", Formatters.formatDuration(hw.elapsedUptimeMs))
              MetricKeyValueRow("Awake Uptime", Formatters.formatDuration(hw.awakeUptimeMs))
            }
          }

          item {
            NexusGlassCard(testTag = "hardware_ram_card") {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                SectionHeader(title = "System Memory (RAM)", icon = Icons.Default.Memory)
              }
              Spacer(modifier = Modifier.height(8.dp))
              LinearProgressIndicator(
                progress = { ramPct },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(8.dp)
                  .clip(RoundedCornerShape(4.dp)),
                color = if (hw.isLowMemory) colors.critical else colors.accent,
                trackColor = colors.elevatedSurface,
                strokeCap = StrokeCap.Round,
              )
              Spacer(modifier = Modifier.height(10.dp))
              MetricKeyValueRow("Total RAM", Formatters.formatBytes(hw.ramTotalBytes))
              MetricKeyValueRow("Used RAM", Formatters.formatBytes(hw.ramUsedBytes))
              MetricKeyValueRow("Available RAM", Formatters.formatBytes(hw.ramAvailableBytes), colors.nominal)
              MetricKeyValueRow("Low Memory State", if (hw.isLowMemory) "YES (Pressured)" else "Nominal")
            }
          }

          item {
            NexusGlassCard(testTag = "hardware_cpu_card") {
              SectionHeader(
                title = "CPU Architecture & Clocks",
                icon = Icons.Default.DeveloperBoard,
                trailingText = "${hw.cpuCoreCount} CORES",
              )
              Spacer(modifier = Modifier.height(6.dp))
              MetricValueRow("Processor / Hardware", hw.cpuHardwareName)
              MetricKeyValueRow("ABIs", hw.cpuAbis)
              Spacer(modifier = Modifier.height(6.dp))
              hw.cpuCoreFrequencies.forEach { core ->
                val curText = when (val c = core.currentKhz) {
                  is MetricValue.Available -> Formatters.formatFrequencyKhz(c.value)
                  is MetricValue.Unavailable -> c.reason
                }
                val maxText = when (val m = core.maxKhz) {
                  is MetricValue.Available -> " / ${Formatters.formatFrequencyKhz(m.value)}"
                  is MetricValue.Unavailable -> ""
                }
                MetricKeyValueRow(
                  label = "Core ${core.coreIndex}",
                  value = "$curText$maxText",
                  valueColor = if (core.currentKhz is MetricValue.Available) colors.textPrimary else colors.textMuted,
                )
              }
            }
          }

          item {
            NexusGlassCard(testTag = "hardware_gpu_card") {
              SectionHeader(title = "GPU & Display Pipeline", icon = Icons.Default.Layers)
              Spacer(modifier = Modifier.height(6.dp))
              MetricValueRow("GPU Vendor", hw.gpuVendor)
              MetricValueRow("GPU Renderer", hw.gpuRenderer)
              MetricValueRow("OpenGL ES", hw.gpuVersion)
              MetricKeyValueRow("Screen Resolution", hw.screenResolution)
              MetricKeyValueRow("Density", "${hw.screenDensityDpi} dpi")
              MetricKeyValueRow("Refresh Rate", String.format(Locale.US, "%.1f Hz", hw.refreshRateHz))
            }
          }

          item {
            NexusGlassCard(testTag = "hardware_thermal_card") {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                SectionHeader(title = "Thermal Sensors", icon = Icons.Default.Thermostat)
              }
              Spacer(modifier = Modifier.height(6.dp))
              MetricValueRow("OS Thermal Status", hw.thermalStatus)
              if (hw.thermalReadings.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                StatusPill(
                  text = "Sysfs thermal zones restricted by SELinux on unrooted Android",
                  color = colors.textMuted,
                )
              } else {
                hw.thermalReadings.forEach { tr ->
                  MetricKeyValueRow(
                    label = tr.label,
                    value = String.format(Locale.US, "%.1f °C", tr.temperatureCelsius),
                    valueColor = if (tr.temperatureCelsius >= 45f) colors.warning else colors.textPrimary,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
