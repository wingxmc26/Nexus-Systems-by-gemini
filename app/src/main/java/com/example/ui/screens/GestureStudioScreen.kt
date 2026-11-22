package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gesture.ActivationZone
import com.example.gesture.GestureType
import com.example.gesture.nexusGesturePad
import com.example.ui.components.ActionPickerDialog
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.GestureStudioViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GestureStudioScreen(
  viewModel: GestureStudioViewModel,
  onBack: () -> Unit,
  contentPadding: PaddingValues,
) {
  val colors = NexusThemeExtras.colors
  val context = LocalContext.current
  val density = LocalDensity.current.density
  val profiles by viewModel.profiles.collectAsStateWithLifecycle()
  val mappings by viewModel.activeMappings.collectAsStateWithLifecycle()
  val apps by viewModel.launchableApps.collectAsStateWithLifecycle()
  val lastDetected by viewModel.lastDetectedSummary.collectAsStateWithLifecycle()
  val logs by viewModel.logs.collectAsStateWithLifecycle()

  val activeProfile = profiles.firstOrNull { it.isActive } ?: profiles.firstOrNull()
  val perm = viewModel.permissionManager
  val overlayGranted = remember(profiles) { perm.isOverlayGranted() }
  val accessibilityEnabled = remember(profiles) { perm.isAccessibilityServiceEnabled() }

  var showAddProfileDialog by remember { mutableStateOf(false) }
  var pendingGestureType by remember { mutableStateOf(GestureType.SWIPE_UP) }
  var pendingZone by remember { mutableStateOf(ActivationZone.FULL_SCREEN) }
  var showActionPicker by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Gesture Studio",
      subtitle = "Multi-Touch, Zones, Sensitivity & Optional System Mode",
      onBack = onBack,
    )

    LazyColumn(
      modifier = Modifier.fillMaxSize().testTag("gesture_studio_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        NexusGlassCard(testTag = "gesture_profile_card") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("Gesture Profiles", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            OutlinedButton(onClick = { showAddProfileDialog = true }) { Text("+ Profile") }
          }
          Spacer(Modifier.height(8.dp))
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            profiles.forEach { p ->
              FilterChip(
                selected = p.isActive,
                onClick = { viewModel.activateProfile(p.id) },
                label = { Text(p.name) },
              )
            }
          }

          activeProfile?.let { prof ->
            Spacer(Modifier.height(10.dp))
            Text("Sensitivity: ${"%.2f".format(prof.sensitivity)}x", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            Slider(
              value = prof.sensitivity,
              onValueChange = { viewModel.updateProfileSettings(prof, sensitivity = it) },
              valueRange = 0.4f..2.0f,
            )
            Text("Edge Zone Width: ${prof.zoneWidthDp} dp", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            Slider(
              value = prof.zoneWidthDp.toFloat(),
              onValueChange = { viewModel.updateProfileSettings(prof, zoneWidthDp = it.toInt()) },
              valueRange = 16f..72f,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
              Column(modifier = Modifier.weight(1f)) {
                Text("In-App Global Gesture Mode", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                Text("Active across NEXUS screens in supported zones", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
              }
              Switch(
                checked = prof.inAppEnabled,
                onCheckedChange = { viewModel.updateProfileSettings(prof, inAppEnabled = it) },
              )
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
              Column(modifier = Modifier.weight(1f)) {
                Text("System-Wide Edge Strip Mode", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                Text(
                  if (overlayGranted) "Attaches gestures onto the Edge Panel overlay strip"
                  else "Requires Display Over Other Apps permission",
                  style = MaterialTheme.typography.bodySmall,
                  color = colors.textSecondary,
                )
              }
              Switch(
                checked = prof.systemOverlayEnabled && overlayGranted,
                onCheckedChange = { want ->
                  if (want && !overlayGranted) {
                    context.startActivity(perm.buildOverlayIntent())
                  } else {
                    viewModel.updateProfileSettings(prof, systemOverlayEnabled = want)
                  }
                },
              )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
              StatusPill(
                text = if (accessibilityEnabled) "Accessibility Actions ON" else "Accessibility Optional",
                color = if (accessibilityEnabled) colors.nominal else colors.textSecondary,
              )
              if (!accessibilityEnabled) {
                OutlinedButton(onClick = { context.startActivity(perm.buildAccessibilitySettingsIntent()) }) {
                  Text("Accessibility Settings")
                }
              }
            }
          }
        }
      }

      item {
        val sens = activeProfile?.sensitivity ?: 1.0f
        NexusGlassCard(accentHighlight = true, testTag = "gesture_live_test_pad") {
          SectionHeader("Interactive Multi-Touch Gesture Pad", Icons.Default.TouchApp)
          Spacer(Modifier.height(6.dp))
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(170.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(colors.elevatedSurface)
              .border(1.dp, colors.accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
              .nexusGesturePad(
                sensitivity = sens,
                density = density,
                onGestureRecognized = { type, x, y, w, h ->
                  viewModel.onTestPadGesture(context, type, x, y, w, h)
                },
              )
              .padding(16.dp),
            contentAlignment = Alignment.Center,
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                "Swipe (4-way), Double-Tap, Long-Press, or Two-Finger Tap/Swipe",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
              )
              Spacer(Modifier.height(8.dp))
              Text(
                lastDetected,
                style = MaterialTheme.typography.titleSmall,
                color = colors.accent,
              )
            }
          }
        }
      }

      item {
        NexusGlassCard {
          Text("Map New Gesture", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
          Spacer(Modifier.height(6.dp))
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GestureType.entries.forEach { g ->
              FilterChip(selected = pendingGestureType == g, onClick = { pendingGestureType = g }, label = { Text(g.label) })
            }
          }
          Spacer(Modifier.height(6.dp))
          Text("Activation Zone:", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActivationZone.entries.forEach { z ->
              FilterChip(selected = pendingZone == z, onClick = { pendingZone = z }, label = { Text(z.label) })
            }
          }
          Spacer(Modifier.height(8.dp))
          Button(
            onClick = { showActionPicker = true },
            modifier = Modifier.fillMaxWidth().testTag("map_gesture_action_button"),
          ) {
            Text("Assign Action to ${pendingGestureType.label}")
          }
        }
      }

      item {
        SectionHeader("Active Mappings", trailingText = "${mappings.size} total")
      }
      items(mappings, key = { it.id }) { m ->
        NexusGlassCard {
          Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
              Text("${m.gestureType} (${m.activationZone})", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
              Text(m.label, style = MaterialTheme.typography.bodySmall, color = colors.accent)
            }
            IconButton(onClick = { viewModel.deleteMapping(m.id) }) {
              Icon(Icons.Default.Delete, contentDescription = "Delete mapping", tint = colors.critical)
            }
          }
        }
      }

      if (logs.isNotEmpty()) {
        item { SectionHeader("Recent Gesture Triggers") }
        items(logs.take(10), key = { it.id }) { log ->
          NexusGlassCard {
            Text(log.sourceName, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            Text(log.details, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
          }
        }
      }
    }
  }

  if (showActionPicker && activeProfile != null) {
    ActionPickerDialog(
      catalog = viewModel.actionDispatcher.availableCatalog(),
      launchableApps = apps,
      onDismiss = { showActionPicker = false },
      onConfirm = { spec ->
        viewModel.saveMapping(activeProfile.id, pendingGestureType, pendingZone.name, spec)
        showActionPicker = false
      },
    )
  }

  if (showAddProfileDialog) {
    var profileName by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showAddProfileDialog = false },
      title = { Text("New Gesture Profile") },
      text = {
        OutlinedTextField(value = profileName, onValueChange = { profileName = it }, label = { Text("Profile Name") })
      },
      confirmButton = {
        Button(onClick = {
          viewModel.createProfile(profileName)
          showAddProfileDialog = false
        }) { Text("Create") }
      },
      dismissButton = { TextButton(onClick = { showAddProfileDialog = false }) { Text("Cancel") } },
    )
  }
}
