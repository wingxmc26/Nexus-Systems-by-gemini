package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.NexusActionDispatcher
import com.example.core.NexusActionSpec
import com.example.core.NexusActionType
import com.example.data.model.ManagedAppSummary
import com.example.ui.navigation.NexusRoutes
import com.example.ui.theme.NexusThemeExtras

val nexusModuleRouteOptions: List<Pair<String, String>> = listOf(
  NexusRoutes.BATTERY to "Battery & Charging",
  NexusRoutes.MUSIC to "Music Player",
  NexusRoutes.VIDEO to "Video Player",
  NexusRoutes.SOUND_BOOSTER to "Sound Booster & EQ",
  NexusRoutes.GESTURE_STUDIO to "Gesture Studio",
  NexusRoutes.FLOATING_TOOLS to "Floating Tools",
  NexusRoutes.EDGE_PANEL to "Edge Panel",
  NexusRoutes.SECURE_VAULT to "Secure Vault",
  NexusRoutes.APP_LOCK to "App Lock",
  NexusRoutes.NOTIFICATIONS to "Notification Studio",
  NexusRoutes.WIDGET_STUDIO to "Widget Studio",
  NexusRoutes.HARDWARE to "Device Info",
)

@Composable
fun ActionSpecListEditor(
  title: String,
  actions: List<NexusActionSpec>,
  onAddAction: () -> Unit,
  onRemoveAt: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = NexusThemeExtras.colors
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
      OutlinedButton(
        onClick = onAddAction,
        modifier = Modifier.testTag("add_action_button"),
      ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(Modifier.height(0.dp))
        Text("Add Action")
      }
    }
    if (actions.isEmpty()) {
      Text("No actions added yet.", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    } else {
      actions.forEachIndexed { index, spec ->
        NexusGlassCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(spec.label, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
              val details = listOf(spec.type, spec.param1, spec.param2).filter { it.isNotBlank() }.joinToString(" • ")
              Text(details, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { onRemoveAt(index) }) {
              Icon(Icons.Default.Delete, contentDescription = "Remove action", tint = colors.critical)
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionPickerDialog(
  catalog: List<NexusActionDispatcher.ActionCatalogOption>,
  launchableApps: List<ManagedAppSummary>,
  onDismiss: () -> Unit,
  onConfirm: (NexusActionSpec) -> Unit,
) {
  var selectedType by remember { mutableStateOf(NexusActionType.OPEN_NEXUS_MODULE) }
  var param1 by remember { mutableStateOf(NexusRoutes.BATTERY) }
  var param2 by remember { mutableStateOf("") }
  var brightnessSlider by remember { mutableFloatStateOf(45f) }
  var appQuery by remember { mutableStateOf("") }

  val filteredApps = remember(appQuery, launchableApps) {
    launchableApps
      .filter { it.isLaunchable }
      .filter {
        appQuery.isBlank() ||
          it.appName.contains(appQuery, ignoreCase = true) ||
          it.packageName.contains(appQuery, ignoreCase = true)
      }
      .take(40)
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Select Native Action") },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 460.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          catalog.forEach { opt ->
            FilterChip(
              selected = selectedType == opt.type,
              onClick = {
                selectedType = opt.type
                param1 = opt.defaultParam1
                param2 = opt.defaultParam2
              },
              label = { Text(opt.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
          }
        }

        when (selectedType) {
          NexusActionType.OPEN_NEXUS_MODULE -> {
            Text("Target NEXUS Module:", style = MaterialTheme.typography.labelMedium)
            LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
              items(nexusModuleRouteOptions) { (route, name) ->
                AssistChip(
                  onClick = { param1 = route },
                  label = { Text(if (param1 == route) "✓ $name" else name) },
                  modifier = Modifier.fillMaxWidth(),
                )
              }
            }
          }

          NexusActionType.LAUNCH_APP -> {
            OutlinedTextField(
              value = appQuery,
              onValueChange = { appQuery = it },
              label = { Text("Search installed apps") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(modifier = Modifier.heightIn(max = 170.dp)) {
              items(filteredApps, key = { it.packageName }) { app ->
                AssistChip(
                  onClick = {
                    param1 = app.packageName
                    param2 = app.appName
                  },
                  label = {
                    val mark = if (param1 == app.packageName) "✓ " else ""
                    Text("$mark${app.appName} (${app.packageName})", maxLines = 1, overflow = TextOverflow.Ellipsis)
                  },
                  modifier = Modifier.fillMaxWidth(),
                )
              }
            }
          }

          NexusActionType.SET_BRIGHTNESS -> {
            Text("Brightness: ${brightnessSlider.toInt()}%")
            Slider(
              value = brightnessSlider,
              onValueChange = {
                brightnessSlider = it
                param1 = it.toInt().toString()
              },
              valueRange = 5f..100f,
            )
          }

          NexusActionType.SET_AUTO_ROTATE -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf("toggle" to "Toggle", "true" to "Force ON", "false" to "Force OFF").forEach { (v, l) ->
                FilterChip(selected = param1 == v, onClick = { param1 = v }, label = { Text(l) })
              }
            }
          }

          NexusActionType.SHOW_NOTIFICATION -> {
            OutlinedTextField(
              value = param1,
              onValueChange = { param1 = it },
              label = { Text("Notification Title") },
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = param2,
              onValueChange = { param2 = it },
              label = { Text("Notification Body") },
              modifier = Modifier.fillMaxWidth(),
            )
          }

          NexusActionType.OPEN_SYSTEM_SETTINGS -> {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              listOf("display", "wifi", "bluetooth", "battery", "nfc").forEach { target ->
                FilterChip(selected = param1 == target, onClick = { param1 = target }, label = { Text(target.uppercase()) })
              }
            }
          }

          NexusActionType.HAPTIC_PULSE -> {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              listOf("35" to "Light", "65" to "Medium", "150" to "Strong").forEach { (ms, lbl) ->
                FilterChip(selected = param1 == ms, onClick = { param1 = ms }, label = { Text(lbl) })
              }
            }
          }

          else -> Unit
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val baseLabel = catalog.firstOrNull { it.type == selectedType }?.title ?: selectedType
          val computedLabel = when (selectedType) {
            NexusActionType.OPEN_NEXUS_MODULE -> {
              nexusModuleRouteOptions.firstOrNull { it.first == param1 }?.second ?: baseLabel
            }
            NexusActionType.LAUNCH_APP -> param2.ifBlank { param1.ifBlank { baseLabel } }
            NexusActionType.SET_BRIGHTNESS -> "Brightness ${param1.ifBlank { "45" }}%"
            NexusActionType.SET_AUTO_ROTATE -> "Auto-Rotate (${param1.ifBlank { "toggle" }})"
            NexusActionType.SHOW_NOTIFICATION -> "Notify: ${param1.ifBlank { "Alert" }}"
            else -> baseLabel
          }
          onConfirm(NexusActionSpec(selectedType, computedLabel, param1, param2))
        },
        modifier = Modifier.testTag("confirm_action_picker_button"),
      ) {
        Text("Add")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
