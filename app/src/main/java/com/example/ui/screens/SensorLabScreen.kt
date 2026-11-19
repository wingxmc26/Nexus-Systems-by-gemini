package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CoreSensorType
import com.example.data.model.SensorAvailability
import com.example.ui.components.EmptyOrErrorState
import com.example.ui.components.MetricKeyValueRow
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.TelemetrySparkline
import com.example.ui.theme.NexusTextStyles
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.SensorLabViewModel
import com.example.ui.viewmodel.SensorSamplingRate
import java.util.Locale

@Composable
fun SensorLabScreen(
  viewModel: SensorLabViewModel,
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val availability = viewModel.availability
  val availableSensors = availability.filter { it.isAvailable }
  val isMonitoring by viewModel.isMonitoring.collectAsStateWithLifecycle()
  val samplingRate by viewModel.samplingRate.collectAsStateWithLifecycle()
  val selectedSensor by viewModel.selectedTestSensor.collectAsStateWithLifecycle()
  val liveReadings by viewModel.liveReadings.collectAsStateWithLifecycle()
  val azimuth by viewModel.azimuthDegrees.collectAsStateWithLifecycle()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Sensor Lab",
      subtitle = "REAL-TIME HARDWARE TELEMETRY",
      onBack = onBack,
      trailingContent = {
        IconButton(
          onClick = { viewModel.toggleMonitoring() },
          modifier = Modifier.testTag("sensor_toggle_monitoring"),
        ) {
          Icon(
            imageVector = if (isMonitoring) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isMonitoring) "Pause Sensors" else "Resume Sensors",
            tint = colors.accent,
          )
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
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        NexusGlassCard(testTag = "sensor_rate_card") {
          SectionHeader(
            title = "Sampling Rate & Stream",
            icon = Icons.Default.Speed,
            trailingText = if (isMonitoring) "LIVE" else "PAUSED",
          )
          Spacer(modifier = Modifier.height(6.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            SensorSamplingRate.entries.forEach { rate ->
              FilterChip(
                selected = samplingRate == rate,
                onClick = { viewModel.setSamplingRate(rate) },
                label = { Text(rate.label) },
              )
            }
          }
        }
      }

      if (availableSensors.isEmpty()) {
        item {
          EmptyOrErrorState(
            icon = Icons.Default.Sensors,
            title = "No Supported Hardware Sensors",
            description = "This device or virtual environment did not expose any standard motion/environment sensors.",
          )
        }
      } else {
        item {
          NexusGlassCard(accentHighlight = true, testTag = "sensor_test_bench") {
            SectionHeader(title = "Interactive Sensor Test Bench", icon = Icons.Default.Explore)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              availableSensors.forEach { avail ->
                FilterChip(
                  selected = selectedSensor == avail.coreType,
                  onClick = { viewModel.selectSensorForTest(avail.coreType) },
                  label = { Text(avail.coreType.displayName) },
                )
              }
            }

            val activeType = selectedSensor ?: availableSensors.first().coreType
            val reading = liveReadings[activeType]

            Spacer(modifier = Modifier.height(12.dp))
            if (reading == null) {
              Text(
                text = if (isMonitoring) "Waiting for next sensor event from hardware..." else "Stream paused. Tap Play to resume.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
              )
            } else {
              TelemetrySparkline(
                samples = reading.history,
                lineColor = colors.accent,
              )
              Spacer(modifier = Modifier.height(10.dp))
              reading.values.forEachIndexed { idx, v ->
                val axisName = activeType.axisLabels.getOrElse(idx) { "V$idx" }
                MetricKeyValueRow(
                  label = "$axisName (${activeType.unit})",
                  value = String.format(Locale.US, "%.3f", v),
                )
              }

              if (activeType == CoreSensorType.ACCELEROMETER && reading.values.size >= 2) {
                Spacer(modifier = Modifier.height(10.dp))
                TiltLevelIndicator(
                  x = reading.values[0],
                  y = reading.values[1],
                )
              }
              if (activeType == CoreSensorType.MAGNETOMETER && azimuth != null) {
                Spacer(modifier = Modifier.height(8.dp))
                MetricKeyValueRow(
                  label = "Computed Compass Heading",
                  value = String.format(Locale.US, "%.1f°", azimuth),
                  valueColor = colors.accentSecondary,
                )
              }
            }
          }
        }

        item {
          SectionHeader(
            title = "Live Active Sensors",
            icon = Icons.Default.Sensors,
            trailingText = "${availableSensors.size} PRESENT",
          )
        }

        items(availableSensors, key = { it.coreType.name }) { sensorInfo ->
          val reading = liveReadings[sensorInfo.coreType]
          NexusGlassCard(
            onClick = { viewModel.selectSensorForTest(sensorInfo.coreType) },
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(sensorInfo.coreType.displayName, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text(
                  text = "${sensorInfo.sensorName ?: "Hardware Sensor"} • ${sensorInfo.vendor ?: "Unknown"}",
                  style = MaterialTheme.typography.labelSmall,
                  color = colors.textSecondary,
                )
              }
              StatusPill(text = "ACTIVE", color = colors.nominal)
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (reading != null) {
              val formatted = reading.values.mapIndexed { idx, v ->
                val axis = sensorInfo.coreType.axisLabels.getOrElse(idx) { "V$idx" }
                "$axis: ${String.format(Locale.US, "%.2f", v)}"
              }.joinToString("  |  ")
              Text(
                text = "$formatted ${sensorInfo.coreType.unit}",
                style = NexusTextStyles.MonoMetricSmall,
                color = colors.accentSecondary,
              )
            } else {
              Text(
                text = "Awaiting hardware callback...",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            MetricKeyValueRow(
              label = "Max Range / Power",
              value = "${sensorInfo.maxRange ?: 0f} ${sensorInfo.coreType.unit} • ${sensorInfo.powerMa ?: 0f} mA",
            )
          }
        }
      }

      item {
        SensorAvailabilityMatrixCard(availability = availability)
      }
    }
  }
}

@Composable
private fun TiltLevelIndicator(x: Float, y: Float) {
  val colors = NexusThemeExtras.colors
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f - 4.dp.toPx()
        drawCircle(color = colors.elevatedSurface, radius = radius)
        drawCircle(color = colors.glassBorder, radius = radius, style = Stroke(width = 1.5.dp.toPx()))
        drawCircle(color = colors.glassBorder, radius = radius * 0.45f, style = Stroke(width = 1.dp.toPx()))
        val normX = (-x / 9.81f).coerceIn(-1f, 1f)
        val normY = (y / 9.81f).coerceIn(-1f, 1f)
        val dotOffset = Offset(
          x = center.x + normX * (radius * 0.7f),
          y = center.y + normY * (radius * 0.7f),
        )
        drawCircle(color = colors.accent, radius = 7.dp.toPx(), center = dotOffset)
      }
    }
    Column {
      Text("2D Accelerometer Tilt", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
      Text(
        text = "X: ${String.format(Locale.US, "%.2f", x)} m/s²  Y: ${String.format(Locale.US, "%.2f", y)} m/s²",
        style = NexusTextStyles.MonoMetricSmall,
        color = colors.textSecondary,
      )
    }
  }
}

@Composable
private fun SensorAvailabilityMatrixCard(availability: List<SensorAvailability>) {
  val colors = NexusThemeExtras.colors
  NexusGlassCard(testTag = "sensor_availability_matrix") {
    SectionHeader(title = "Hardware Availability Matrix", icon = Icons.Default.Sensors)
    Spacer(modifier = Modifier.height(6.dp))
    availability.forEach { item ->
      MetricKeyValueRow(
        label = item.coreType.displayName,
        value = if (item.isAvailable) "Present" else "Not Present",
        valueColor = if (item.isAvailable) colors.nominal else colors.textMuted,
      )
    }
  }
}
