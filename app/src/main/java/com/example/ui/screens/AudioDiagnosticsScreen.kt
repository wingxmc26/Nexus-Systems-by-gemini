package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.system.SpeakerTestMode
import com.example.ui.components.EmptyOrErrorState
import com.example.ui.components.MetricKeyValueRow
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusTextStyles
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.AudioDiagnosticsViewModel
import java.util.Locale

@Composable
fun AudioDiagnosticsScreen(
  viewModel: AudioDiagnosticsViewModel,
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var permissionDenied by remember { mutableStateOf(false) }

  val micPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    if (granted) {
      permissionDenied = false
      viewModel.startMicTest()
    } else {
      permissionDenied = true
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      viewModel.stopSpeakerTest()
      viewModel.stopMicTest()
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Audio Diagnostics",
      subtitle = "SPEAKER SYNTHESIS & MIC INPUT VU",
      onBack = onBack,
      trailingContent = {
        IconButton(onClick = { viewModel.refreshEnvironment() }) {
          Icon(Icons.Default.Refresh, contentDescription = "Refresh Audio Devices", tint = colors.textPrimary)
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
        NexusGlassCard(accentHighlight = true, testTag = "audio_speaker_test_card") {
          SectionHeader(
            title = "Speaker Output Test",
            icon = Icons.Default.Speaker,
            trailingText = if (uiState.isSpeakerPlaying) "PLAYING" else "IDLE",
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Generates real-time PCM sine signals via AudioTrack to verify left/right channels and frequency response.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
          )
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            SpeakerTestMode.entries.forEach { mode ->
              FilterChip(
                selected = uiState.activeSpeakerMode == mode,
                onClick = {
                  if (uiState.isSpeakerPlaying) {
                    viewModel.startSpeakerTest(mode)
                  } else {
                    viewModel.startSpeakerTest(mode)
                  }
                },
                label = { Text(mode.label) },
              )
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
          Button(
            onClick = {
              if (uiState.isSpeakerPlaying) {
                viewModel.stopSpeakerTest()
              } else {
                viewModel.startSpeakerTest(uiState.activeSpeakerMode)
              }
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (uiState.isSpeakerPlaying) colors.critical else colors.accent,
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("speaker_test_toggle_button"),
          ) {
            Icon(
              imageVector = if (uiState.isSpeakerPlaying) Icons.Default.Stop else Icons.Default.VolumeUp,
              contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (uiState.isSpeakerPlaying) "Stop Speaker Tone" else "Play Test Tone (${uiState.activeSpeakerMode.label})")
          }
          uiState.speakerError?.let { err ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(err, style = MaterialTheme.typography.bodyMedium, color = colors.critical)
          }
        }
      }

      item {
        NexusGlassCard(testTag = "audio_mic_test_card") {
          SectionHeader(
            title = "Microphone Input Test",
            icon = Icons.Default.Mic,
            trailingText = if (uiState.isMicRunning) "RECORDING" else "IDLE",
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Measures live microphone input amplitude in dBFS without storing or transmitting audio. Permission is requested only when started.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
          )
          Spacer(modifier = Modifier.height(12.dp))

          // Live VU Meter & Waveform
          val levelFraction = (uiState.micSample.rmsNormalized * 3.5f).coerceIn(0f, 1f)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Input Level", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            Text(
              text = String.format(Locale.US, "%.1f dBFS", uiState.micSample.dbFs),
              style = NexusTextStyles.MonoMetricMedium,
              color = if (levelFraction > 0.75f) colors.warning else colors.accentSecondary,
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          LinearProgressIndicator(
            progress = { levelFraction },
            modifier = Modifier
              .fillMaxWidth()
              .height(10.dp)
              .clip(RoundedCornerShape(5.dp)),
            color = if (levelFraction > 0.8f) colors.critical else colors.nominal,
            trackColor = colors.elevatedSurface,
            strokeCap = StrokeCap.Round,
          )
          Spacer(modifier = Modifier.height(10.dp))
          MicWaveformCanvas(samples = uiState.micSample.waveform)
          Spacer(modifier = Modifier.height(12.dp))

          Button(
            onClick = {
              if (uiState.isMicRunning) {
                viewModel.stopMicTest()
              } else {
                val hasPerm = ContextCompat.checkSelfPermission(
                  context,
                  Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) {
                  permissionDenied = false
                  viewModel.startMicTest()
                } else {
                  micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
              }
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (uiState.isMicRunning) colors.critical else colors.accent,
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("mic_test_toggle_button"),
          ) {
            Icon(if (uiState.isMicRunning) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (uiState.isMicRunning) "Stop Microphone Test" else "Start Microphone Test")
          }

          if (permissionDenied) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Microphone permission was denied. Grant audio permission in Android App Settings to test the microphone.",
              style = MaterialTheme.typography.bodyMedium,
              color = colors.warning,
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
              onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                  data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
              },
            ) {
              Text("Open App Settings")
            }
          }

          uiState.micError?.let { err ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(err, style = MaterialTheme.typography.bodyMedium, color = colors.critical)
          }
        }
      }

      uiState.environment?.let { env ->
        item {
          NexusGlassCard(testTag = "audio_volumes_card") {
            SectionHeader(title = "Audio Routing & Volumes", icon = Icons.Default.GraphicEq)
            Spacer(modifier = Modifier.height(6.dp))
            MetricKeyValueRow("Ringer Mode", env.ringerModeLabel)
            MetricKeyValueRow("Media Volume", "${env.mediaVolume} / ${env.mediaMaxVolume}")
            MetricKeyValueRow("Ring Volume", "${env.ringVolume} / ${env.ringMaxVolume}")
            MetricKeyValueRow("Alarm Volume", "${env.alarmVolume} / ${env.alarmMaxVolume}")
            MetricKeyValueRow("Hardware Mic Mute", if (env.isMicrophoneMuted) "Muted" else "Unmuted")
          }
        }

        item {
          SectionHeader(
            title = "Supported Audio Endpoints",
            icon = Icons.Default.Speaker,
            trailingText = "${env.devices.size} DEVICES",
          )
        }

        if (env.devices.isEmpty()) {
          item {
            EmptyOrErrorState(
              icon = Icons.Default.Speaker,
              title = "No Audio Endpoints Reported",
              description = "AudioManager returned no active endpoints.",
            )
          }
        } else {
          items(env.devices, key = { "${it.id}_${it.isSink}_${it.isSource}" }) { dev ->
            NexusGlassCard {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(dev.productName, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                  Text(dev.typeLabel, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  if (dev.isSink) StatusPill("OUT", colors.info)
                  if (dev.isSource) StatusPill("IN", colors.nominal)
                }
              }
              Spacer(modifier = Modifier.height(6.dp))
              MetricKeyValueRow(
                label = "Sample Rates",
                value = if (dev.sampleRates.isEmpty()) "Dynamic / System" else dev.sampleRates.joinToString(", ") { "${it / 1000}k" },
              )
              MetricKeyValueRow(
                label = "Channels",
                value = if (dev.channelCounts.isEmpty()) "Dynamic" else dev.channelCounts.joinToString(", "),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MicWaveformCanvas(samples: List<Float>) {
  val colors = NexusThemeExtras.colors
  Canvas(
    modifier = Modifier
      .fillMaxWidth()
      .height(64.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(colors.background.copy(alpha = 0.65f))
      .padding(horizontal = 8.dp, vertical = 6.dp),
  ) {
    val midY = size.height / 2f
    drawLine(colors.glassBorder, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 1f)
    if (samples.isEmpty()) return@Canvas
    val barSpacing = size.width / samples.size.coerceAtLeast(1)
    samples.forEachIndexed { idx, s ->
      val x = idx * barSpacing + barSpacing / 2f
      val amp = (kotlin.math.abs(s) * size.height * 0.9f).coerceAtLeast(2.dp.toPx())
      drawLine(
        color = colors.accent,
        start = Offset(x, midY - amp / 2f),
        end = Offset(x, midY + amp / 2f),
        strokeWidth = (barSpacing * 0.55f).coerceAtLeast(2.dp.toPx()),
        cap = StrokeCap.Round,
      )
    }
  }
}
