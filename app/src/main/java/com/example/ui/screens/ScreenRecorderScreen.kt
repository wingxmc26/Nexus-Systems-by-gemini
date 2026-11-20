package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.RecordedMediaItem
import com.example.ui.components.EmptyOrErrorState
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusTextStyles
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.ScreenRecorderViewModel
import com.example.util.Formatters

@Composable
fun ScreenRecorderScreen(
  viewModel: ScreenRecorderViewModel,
  onPlayVideo: (Uri, String) -> Unit,
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var pendingMode by remember { mutableStateOf("RECORD") }
  var renameTarget by remember { mutableStateOf<RecordedMediaItem?>(null) }
  var renameText by remember { mutableStateOf("") }
  var deleteTarget by remember { mutableStateOf<RecordedMediaItem?>(null) }

  val projectionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
  ) { res ->
    val data = res.data
    if (res.resultCode == Activity.RESULT_OK && data != null) {
      if (pendingMode == "SHOT") {
        viewModel.captureScreenshotWithConsent(res.resultCode, data)
      } else {
        viewModel.startRecordingWithConsent(res.resultCode, data)
      }
    }
  }

  val micPermLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    viewModel.refreshPermissions()
    viewModel.setRecordMic(granted)
  }

  val notifPermLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) {
    viewModel.refreshPermissions()
  }

  LaunchedEffect(state.serviceState.isRecording, state.serviceState.lastSavedMessage) {
    if (!state.serviceState.isRecording && state.serviceState.lastSavedMessage != null) {
      viewModel.refreshLibrary()
    }
  }

  renameTarget?.let { item ->
    AlertDialog(
      onDismissRequest = { renameTarget = null },
      containerColor = colors.cardSurface,
      title = { Text("Rename Recording", color = colors.textPrimary) },
      text = {
        OutlinedTextField(value = renameText, onValueChange = { renameText = it }, label = { Text("New name") }, singleLine = true)
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.renameItem(item, renameText)
            renameTarget = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
        ) { Text("Save") }
      },
      dismissButton = {
        TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
      },
    )
  }

  deleteTarget?.let { item ->
    AlertDialog(
      onDismissRequest = { deleteTarget = null },
      containerColor = colors.cardSurface,
      title = { Text("Delete Capture?", color = colors.textPrimary) },
      text = { Text("Permanently remove \"${item.name}\"?", color = colors.textSecondary) },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteItem(item)
            deleteTarget = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = colors.critical),
        ) { Text("Delete") }
      },
      dismissButton = {
        TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
      },
    )
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Screen Recorder Studio",
      subtitle = "MediaProjection MP4 Capture & Screenshots",
      onBack = onBack,
      trailingContent = {
        IconButton(onClick = { viewModel.refreshLibrary() }, modifier = Modifier.size(48.dp)) {
          Icon(Icons.Default.Refresh, contentDescription = "Refresh library", tint = colors.accent)
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
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        val rec = state.serviceState
        NexusGlassCard(accentHighlight = true, testTag = "recorder_control_card") {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = when {
                rec.isRecording && rec.isPaused -> "PAUSED • ${Formatters.formatTimer(rec.elapsedMs)}"
                rec.isRecording -> "RECORDING • ${Formatters.formatTimer(rec.elapsedMs)}"
                else -> "READY TO CAPTURE"
              },
              style = NexusTextStyles.MetricMedium,
              color = if (rec.isRecording) colors.accent else colors.textPrimary,
            )
            StatusPill(
              text = if (rec.isRecording) "LIVE" else "STANDBY",
              color = if (rec.isRecording) colors.critical else colors.nominal,
            )
          }
          Spacer(modifier = Modifier.height(10.dp))

          if (rec.isRecording) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              OutlinedButton(onClick = { viewModel.togglePauseResume() }, modifier = Modifier.weight(1f)) {
                Icon(if (rec.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (rec.isPaused) "Resume" else "Pause")
              }
              Button(
                onClick = { viewModel.stopRecording() },
                colors = ButtonDefaults.buttonColors(containerColor = colors.critical),
                modifier = Modifier.weight(1f),
              ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Stop")
              }
            }
          } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(
                onClick = {
                  if (!state.hasPostNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                  }
                  pendingMode = "RECORD"
                  projectionLauncher.launch(viewModel.createScreenCaptureIntent())
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                modifier = Modifier.weight(1f).testTag("start_screen_record_button"),
              ) {
                Icon(Icons.Default.FiberManualRecord, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Record Screen")
              }
              OutlinedButton(
                onClick = {
                  pendingMode = "SHOT"
                  projectionLauncher.launch(viewModel.createScreenCaptureIntent())
                },
                modifier = Modifier.weight(1f).testTag("take_screenshot_button"),
              ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Screenshot")
              }
            }
          }

          (rec.errorMessage ?: rec.lastSavedMessage ?: state.statusMessage)?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = msg,
              style = MaterialTheme.typography.bodyMedium,
              color = if (rec.errorMessage != null) colors.critical else colors.nominal,
            )
          }
        }
      }

      item {
        NexusGlassCard {
          SectionHeader(title = "Capture Settings", icon = Icons.Default.Videocam)
          Spacer(modifier = Modifier.height(8.dp))
          Text("Resolution", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
          Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("720p", "1080p", "Native").forEach { res ->
              FilterChip(selected = state.config.resolutionLabel == res, onClick = { viewModel.setResolution(res) }, label = { Text(res) })
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text("Frame Rate & Bitrate", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
          Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(30, 60).forEach { fps ->
              FilterChip(selected = state.config.fps == fps, onClick = { viewModel.setFps(fps) }, label = { Text("$fps FPS") })
            }
            listOf(4, 8, 12).forEach { mbps ->
              FilterChip(selected = state.config.bitrateMbps == mbps, onClick = { viewModel.setBitrateMbps(mbps) }, label = { Text("$mbps Mbps") })
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Record Microphone Audio", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
              Text(
                text = if (state.hasMicPermission) "Narration audio included when toggled on." else "Requires microphone runtime permission.",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
              )
            }
            Switch(
              checked = state.config.recordMicrophone,
              onCheckedChange = { checked ->
                if (checked && !state.hasMicPermission) {
                  micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                  viewModel.setRecordMic(checked)
                }
              },
            )
          }
        }
      }

      if (state.recordings.isEmpty()) {
        item {
          EmptyOrErrorState(
            icon = Icons.Default.Videocam,
            title = "No Captures Saved",
            description = "Saved screen recordings appear in Movies/NexusScreenRecordings and screenshots in Pictures/NexusScreenshots.",
          )
        }
      } else {
        item {
          SectionHeader(title = "Saved Captures", trailingText = "${state.recordings.size} items")
        }
        items(state.recordings, key = { it.id }) { item ->
          NexusGlassCard {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = if (item.isScreenshot) Icons.Default.Image else Icons.Default.Videocam,
                contentDescription = null,
                tint = colors.accent,
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                  text = Formatters.formatBytes(item.sizeBytes) + if (!item.isScreenshot && item.durationMs > 0L) " • ${Formatters.formatTimer(item.durationMs)}" else "",
                  style = MaterialTheme.typography.labelSmall,
                  color = colors.textSecondary,
                )
              }
              if (!item.isScreenshot) {
                IconButton(onClick = { onPlayVideo(Uri.parse(item.pathOrUri), item.name) }, modifier = Modifier.size(40.dp)) {
                  Icon(Icons.Default.PlayArrow, contentDescription = "Play recording", tint = colors.accent)
                }
              }
              IconButton(onClick = { viewModel.shareItem(item) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Share, contentDescription = "Share recording", tint = colors.textSecondary)
              }
              IconButton(
                onClick = {
                  renameTarget = item
                  renameText = item.name.substringBeforeLast('.')
                },
                modifier = Modifier.size(40.dp),
              ) {
                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Rename", tint = colors.textSecondary)
              }
              IconButton(onClick = { deleteTarget = item }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = colors.critical)
              }
            }
          }
        }
      }
    }
  }
}
