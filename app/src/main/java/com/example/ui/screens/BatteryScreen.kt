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
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.HorizontalDivider
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
  val sessions by viewModel.sessions.collectAsStateWithLifecycle()
  val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
  val chargingDurationMs by viewModel.chargingDurationMs.collectAsStateWithLifecycle()

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
      title = "Battery & Charging",
      subtitle = "Live battery status & charging time log",
      onBack = onBack,
      trailingContent = {
        StatusPill(
          text = telemetry.statusLabel,
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
      verticalArrangement = Arrangement.spacedBy(12.dp),
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
              size = 104.dp,
              strokeWidth = 9.dp,
              gaugeColor = gaugeColor,
            )
            Spacer(modifier = Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
              MetricKeyValueRow("Status", telemetry.statusLabel)
              MetricKeyValueRow("Source", telemetry.chargeSource)
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
            }
          }
        }
      }

      item {
        NexusGlassCard(testTag = "battery_timer_card") {
          SectionHeader(
            title = "Charging Duration Counter",
            icon = Icons.Default.Timer,
            trailingText = if (telemetry.isCharging) "Active" else "Idle",
          )
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text(
                text = if (telemetry.isCharging && activeSession != null) {
                  Formatters.formatTimer(chargingDurationMs)
                } else {
                  "00:00:00"
                },
                style = NexusTextStyles.MetricLarge,
                color = if (telemetry.isCharging) colors.nominal else colors.textMuted,
              )
              Text(
                text = if (telemetry.isCharging && activeSession != null) {
                  "Started at ${Formatters.formatClock12h(activeSession!!.startTimeMs)} (${activeSession!!.startPercentage}%)"
                } else {
                  "Connect charger to start live charging duration tracking"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
              )
            }
          }
        }
      }

      item {
        NexusGlassCard(testTag = "battery_electrical_card") {
          SectionHeader(title = "Battery Details", icon = Icons.Default.BatteryChargingFull)
          Spacer(modifier = Modifier.height(6.dp))
          MetricValueRow(
            label = if (telemetry.isCharging) "Charging Current" else "Discharging Current",
            metric = telemetry.currentMicroAmps,
            formatter = { Formatters.formatCurrentMa(it, telemetry.isCharging) },
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
        NexusGlassCard(testTag = "battery_sessions_card") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            SectionHeader(
              title = "Charging Time Log",
              icon = Icons.Default.Timer,
              trailingText = "${sessions.size} logged",
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          if (sessions.isEmpty()) {
            EmptyOrErrorState(
              icon = Icons.Default.BatteryChargingFull,
              title = "No Charging Logs Yet",
              description = "Connect and unplug your charger to automatically record entries like: Charging 67-89% in 23 minutes at 8:30 PM.",
            )
          } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              sessions.take(25).forEachIndexed { index, session ->
                val displayTs = if (session.isActive) {
                  session.startTimeMs
                } else {
                  session.endTimeMs ?: System.currentTimeMillis()
                }
                val endMs = session.endTimeMs ?: System.currentTimeMillis()
                val durMs = (endMs - session.startTimeMs).coerceAtLeast(0L)
                val logSummary = Formatters.formatChargingSessionLog(
                  startPct = session.startPercentage,
                  endPct = session.endPercentage,
                  durationMs = durMs,
                  timestampMs = displayTs,
                  isActive = session.isActive,
                )

                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                ) {
                  Text(
                    text = logSummary,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (session.isActive) colors.nominal else colors.textPrimary,
                  )
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = "${session.chargeSource} • ${if (session.isActive) "Charging Now" else Formatters.formatDateTime(displayTs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                  )
                }
                if (index < sessions.take(25).lastIndex) {
                  HorizontalDivider(color = colors.glassBorder)
                }
              }
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
              onClick = { viewModel.clearSessions() },
              modifier = Modifier.testTag("clear_charging_logs_button"),
            ) {
              Icon(Icons.Default.DeleteOutline, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Clear Charging Log")
            }
          }
        }
      }
    }
  }
}
