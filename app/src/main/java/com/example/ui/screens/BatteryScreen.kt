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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MetricValue
import com.example.ui.components.EmptyOrErrorState
import com.example.ui.components.MetricKeyValueRow
import com.example.ui.components.MetricValueRow
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.TelemetryArcGauge
import com.example.ui.components.TelemetrySparkline
import com.example.ui.theme.NexusTextStyles
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.BatteryViewModel
import com.example.util.Formatters
import java.util.Locale

@Composable
fun BatteryScreen(
  viewModel: BatteryViewModel,
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
  val history by viewModel.history.collectAsStateWithLifecycle()

  val pct = (telemetry.percentage as? MetricValue.Available)?.value
  val gaugeColor = when {
    telemetry.isCharging -> colors.nominal
    (pct ?: 100) <= 20 -> colors.critical
    else -> colors.accent
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Battery Intelligence",
      subtitle = "LIVE POWER & THERMAL TELEMETRY",
      onBack = onBack,
      trailingContent = {
        StatusPill(
          text = telemetry.statusLabel.uppercase(),
          color = gaugeColor,
        )
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
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        NexusGlassCard(accentHighlight = true, testTag = "battery_hero_card") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            TelemetryArcGauge(
              progress = (pct ?: 0) / 100f,
              centerValue = pct?.let { "$it%" } ?: "N/A",
              centerLabel = telemetry.chargeSource,
              size = 112.dp,
              strokeWidth = 10.dp,
              gaugeColor = gaugeColor,
            )
            Spacer(modifier = Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
              MetricValueRow(
                label = "Temperature",
                metric = telemetry.temperatureCelsius,
                formatter = { String.format(Locale.US, "%.1f °C", it) },
              )
              MetricValueRow(
                label = "Voltage",
                metric = telemetry.voltageVolts,
                formatter = { String.format(Locale.US, "%.3f V", it) },
              )
              MetricKeyValueRow(
                label = "Health",
                value = telemetry.healthLabel,
                valueColor = if (telemetry.healthLabel == "Good") colors.nominal else colors.warning,
              )
              MetricValueRow(
                label = "Technology",
                metric = telemetry.technology,
              )
            }
          }
        }
      }

      item {
        NexusGlassCard(testTag = "battery_electrical_card") {
          SectionHeader(title = "Power & Hardware Counters", icon = Icons.Default.BatteryChargingFull)
          Spacer(modifier = Modifier.height(6.dp))
          MetricKeyValueRow(label = "Charge Source", value = telemetry.chargeSource)
          MetricValueRow(
            label = "Instant Current",
            metric = telemetry.currentMicroAmps,
            formatter = { Formatters.formatCurrentMa(it) },
          )
          MetricValueRow(
            label = "Charge Counter",
            metric = telemetry.chargeCounterMicroAh,
            formatter = { "${it / 1000L} mAh" },
          )
          MetricValueRow(
            label = "Energy Counter",
            metric = telemetry.energyCounterNwh,
            formatter = { String.format(Locale.US, "%.2f Wh", it / 1_000_000_000.0) },
          )
          MetricValueRow(
            label = "Cycle Count",
            metric = telemetry.cycleCount,
            formatter = { "$it cycles" },
          )
        }
      }

      item {
        NexusGlassCard(testTag = "battery_history_controls") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            SectionHeader(
              title = "Charging History",
              icon = Icons.Default.History,
              trailingText = "${history.size} LOGS",
            )
          }
          Spacer(modifier = Modifier.height(8.dp))

          if (history.size >= 2) {
            val points = history.reversed().map { it.percentage.toFloat() }
            TelemetrySparkline(
              samples = points,
              lineColor = gaugeColor,
              fixedRange = 0f to 100f,
            )
            Spacer(modifier = Modifier.height(10.dp))
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Button(
              onClick = { viewModel.logSnapshotNow() },
              colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
              modifier = Modifier
                .weight(1f)
                .testTag("log_battery_snapshot_button"),
            ) {
              Icon(Icons.Default.Save, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Log Snapshot")
            }
            OutlinedButton(
              onClick = { viewModel.clearHistory() },
              enabled = history.isNotEmpty(),
              modifier = Modifier.testTag("clear_battery_history_button"),
            ) {
              Icon(Icons.Default.DeleteOutline, contentDescription = "Clear history")
              Spacer(modifier = Modifier.width(4.dp))
              Text("Clear")
            }
          }
        }
      }

      if (history.isEmpty()) {
        item {
          EmptyOrErrorState(
            icon = Icons.Default.History,
            title = "No Battery Logs Recorded Yet",
            description = "Tap 'Log Snapshot' or keep monitoring while battery state changes.",
          )
        }
      } else {
        items(history.take(30), key = { it.id }) { entry ->
          NexusGlassCard {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column {
                Text(
                  text = "${entry.percentage}% • ${entry.statusLabel}",
                  style = NexusTextStyles.MonoMetricSmall,
                  color = if (entry.isCharging) colors.nominal else colors.textPrimary,
                )
                Text(
                  text = "${Formatters.formatDateTime(entry.timestampMs)} • ${entry.chargeSource}",
                  style = MaterialTheme.typography.bodyMedium,
                  color = colors.textSecondary,
                )
              }
              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = entry.temperatureCelsius?.let { String.format(Locale.US, "%.1f °C", it) } ?: "N/A",
                  style = MaterialTheme.typography.labelMedium,
                  color = colors.textPrimary,
                )
                Text(
                  text = entry.voltageVolts?.let { String.format(Locale.US, "%.2f V", it) } ?: "N/A",
                  style = MaterialTheme.typography.labelSmall,
                  color = colors.textMuted,
                )
              }
            }
          }
        }
      }
    }
  }
}
