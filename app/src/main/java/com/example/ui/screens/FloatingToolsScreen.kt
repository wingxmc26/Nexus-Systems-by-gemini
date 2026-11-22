package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.QuickNoteEntity
import com.example.overlay.OverlayTimerCoordinator
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.nexusModuleRouteOptions
import com.example.ui.theme.NexusTextStyles
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.OverlayHubViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FloatingToolsScreen(
  viewModel: OverlayHubViewModel,
  onNavigateModule: (String) -> Unit,
  onBack: () -> Unit,
  contentPadding: PaddingValues,
) {
  val colors = NexusThemeExtras.colors
  val context = LocalContext.current
  val config by viewModel.floatingConfig.collectAsStateWithLifecycle()
  val notes by viewModel.notes.collectAsStateWithLifecycle()
  val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()
  val timerState by viewModel.timerState.collectAsStateWithLifecycle()
  val stopwatchDisplay by viewModel.stopwatchDisplay.collectAsStateWithLifecycle()
  val countdownDisplay by viewModel.countdownDisplay.collectAsStateWithLifecycle()
  val calcExpr by viewModel.calcExpression.collectAsStateWithLifecycle()
  val calcOut by viewModel.calcOutput.collectAsStateWithLifecycle()
  val banner by viewModel.statusBanner.collectAsStateWithLifecycle()

  val overlayGranted = remember(config) { viewModel.permissionManager.isOverlayGranted() }
  var selectedTab by remember { mutableIntStateOf(0) } // 0=Notes, 1=Calc, 2=Timer, 3=Shortcuts
  var editingNote by remember { mutableStateOf<QuickNoteEntity?>(null) }
  var showNoteDialog by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Floating Tools",
      subtitle = "Movable Bubble, Freeform Apps, Mini-Browser, Notes & Calc",
      onBack = onBack,
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .testTag("floating_tools_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (!banner.isNullOrBlank()) {
        item {
          NexusGlassCard(accentHighlight = true) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(banner.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
              TextButton(onClick = viewModel::clearBanner) { Text("OK") }
            }
          }
        }
      }

      item {
        NexusGlassCard(
          accentHighlight = config.enabled,
          testTag = "floating_service_control_card",
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Floating Overlay Bubble", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
              Text(
                if (overlayGranted) "Display Over Other Apps granted — bubble stays movable across apps."
                else "Requires Display Over Other Apps permission to render outside NEXUS.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
              )
            }
            Switch(
              checked = config.enabled && overlayGranted,
              onCheckedChange = { viewModel.setFloatingToolsEnabled(it) },
              modifier = Modifier.testTag("floating_tools_enable_switch"),
            )
          }

          if (!overlayGranted) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
              onClick = { context.startActivity(viewModel.permissionManager.buildOverlayIntent()) },
              modifier = Modifier.fillMaxWidth().testTag("floating_grant_overlay_button"),
            ) {
              Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text("Grant Display Over Other Apps")
            }
          }

          Spacer(Modifier.height(12.dp))
          Text("Bubble Size: ${config.bubbleSizeDp} dp", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
          Slider(
            value = config.bubbleSizeDp.toFloat(),
            onValueChange = { v -> viewModel.updateFloatingConfig { it.copy(bubbleSizeDp = v.toInt()) } },
            valueRange = 40f..80f,
          )

          Text("Overlay Opacity: ${(config.opacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
          Slider(
            value = config.opacity,
            onValueChange = { v -> viewModel.updateFloatingConfig { it.copy(opacity = v) } },
            valueRange = 0.35f..1.0f,
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Auto Edge Snapping", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
              Text("Snap bubble smoothly to nearest screen edge after drag", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            Switch(
              checked = config.edgeSnapEnabled,
              onCheckedChange = { snap -> viewModel.updateFloatingConfig { it.copy(edgeSnapEnabled = snap) } },
            )
          }

          Spacer(Modifier.height(10.dp))
          Text("Enabled Panel Tools", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
          Spacer(Modifier.height(6.dp))
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("NOTES", "CALCULATOR", "TIMER", "APPS", "BROWSER", "SHORTCUTS", "MODULES").forEach { toolKey ->
              val active = toolKey in config.selectedTools
              FilterChip(
                selected = active,
                onClick = {
                  viewModel.updateFloatingConfig { cur ->
                    val next = cur.selectedTools.toMutableSet()
                    if (toolKey in next && next.size > 1) next.remove(toolKey) else next.add(toolKey)
                    cur.copy(selectedTools = next)
                  }
                },
                label = { Text(toolKey) },
              )
            }
          }
        }
      }

      item {
        SectionHeader(title = "Interactive Toolbox (Shared with Bubble)", trailingText = "Live Synced")
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          val tabs = listOf(
            0 to ("Notes" to Icons.Default.EditNote),
            1 to ("Calc" to Icons.Default.Calculate),
            2 to ("Timer" to Icons.Default.Timer),
            3 to ("Links" to Icons.Default.Layers),
          )
          tabs.forEach { (idx, meta) ->
            FilterChip(
              selected = selectedTab == idx,
              onClick = { selectedTab = idx },
              leadingIcon = { Icon(meta.second, contentDescription = null, modifier = Modifier.size(16.dp)) },
              label = { Text(meta.first) },
              modifier = Modifier.weight(1f).testTag("floating_tab_$idx"),
            )
          }
        }
      }

      when (selectedTab) {
        0 -> {
          item {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("${notes.size} Persistent Notes", style = MaterialTheme.typography.titleSmall, color = colors.textSecondary)
              Button(
                onClick = {
                  editingNote = null
                  showNoteDialog = true
                },
                modifier = Modifier.testTag("add_quick_note_button"),
              ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("New Note")
              }
            }
          }
          items(notes, key = { it.id }) { note ->
            NexusGlassCard(
              onClick = {
                editingNote = note
                showNoteDialog = true
              },
              testTag = "quick_note_card_${note.id}",
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(note.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                  Spacer(Modifier.height(4.dp))
                  Text(note.content, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
                IconButton(onClick = { viewModel.deleteQuickNote(note.id) }) {
                  Icon(Icons.Default.Delete, contentDescription = "Delete Note", tint = colors.critical)
                }
              }
            }
          }
        }

        1 -> {
          item {
            NexusGlassCard(testTag = "floating_calculator_card") {
              Text(calcExpr.ifBlank { "Expression" }, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
              Spacer(Modifier.height(4.dp))
              Text(calcOut, style = NexusTextStyles.MetricLarge, color = colors.accent)
              Spacer(Modifier.height(12.dp))
              val rows = listOf(
                listOf("C", "(", ")", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "-"),
                listOf("1", "2", "3", "+"),
                listOf("0", ".", "⌫", "="),
              )
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.forEach { row ->
                  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { key ->
                      OutlinedButton(
                        onClick = { viewModel.pressCalcKey(key) },
                        modifier = Modifier.weight(1f).testTag("calc_key_$key"),
                      ) {
                        Text(key, style = MaterialTheme.typography.titleMedium)
                      }
                    }
                  }
                }
              }
            }
          }
        }

        2 -> {
          item {
            NexusGlassCard(testTag = "floating_timer_card") {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column {
                  Text("Stopwatch", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                  Text(stopwatchDisplay, style = NexusTextStyles.MetricLarge, color = colors.nominal)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Button(onClick = { OverlayTimerCoordinator.toggleStopwatch() }) {
                    Text(if (timerState.stopwatchRunning) "Pause" else "Start")
                  }
                  OutlinedButton(onClick = { OverlayTimerCoordinator.resetStopwatch() }) {
                    Text("Reset")
                  }
                }
              }

              Spacer(Modifier.height(16.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column {
                  Text("Countdown Timer", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                  Text(
                    countdownDisplay,
                    style = NexusTextStyles.MetricLarge,
                    color = if (timerState.countdownCompletedAlert) colors.critical else colors.accent,
                  )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Button(onClick = { OverlayTimerCoordinator.toggleCountdown() }) {
                    Text(if (timerState.countdownRunning) "Pause" else "Start")
                  }
                  OutlinedButton(onClick = { OverlayTimerCoordinator.resetCountdown() }) {
                    Text("Reset")
                  }
                }
              }
              Spacer(Modifier.height(10.dp))
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(60_000L to "1m", 180_000L to "3m", 300_000L to "5m", 900_000L to "15m").forEach { (ms, lbl) ->
                  FilterChip(
                    selected = timerState.countdownTotalMs == ms,
                    onClick = { OverlayTimerCoordinator.setCountdownPreset(ms) },
                    label = { Text(lbl) },
                    modifier = Modifier.weight(1f),
                  )
                }
              }
            }
          }
        }

        else -> {
          item {
            val floatingShortcuts = remember(shortcuts) {
              shortcuts.filter { com.example.overlay.OverlaySurfaceFilter.matchesFloatingTools(it.targetSurface) }
            }
            NexusGlassCard(testTag = "floating_shortcuts_links_card") {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Synced Shortcuts & Quick Actions", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                StatusPill("${floatingShortcuts.size} ITEMS", colors.accent)
              }
              Spacer(Modifier.height(8.dp))
              floatingShortcuts.forEach { s ->
                Row(
                  modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(s.label, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                    Text(s.category, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                  }
                  OutlinedButton(onClick = { viewModel.runShortcut(s, "FLOATING_TOOLS") }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Run")
                  }
                }
              }
              Spacer(Modifier.height(10.dp))
              Text("Direct NEXUS Modules", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
              Spacer(Modifier.height(6.dp))
              FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                nexusModuleRouteOptions.take(8).forEach { (route, name) ->
                  FilterChip(selected = false, onClick = { onNavigateModule(route) }, label = { Text(name) })
                }
              }
            }
          }
        }
      }
    }
  }

  if (showNoteDialog) {
    var title by remember(editingNote) { mutableStateOf(editingNote?.title.orEmpty()) }
    var content by remember(editingNote) { mutableStateOf(editingNote?.content.orEmpty()) }
    AlertDialog(
      onDismissRequest = { showNoteDialog = false },
      title = { Text(if (editingNote == null) "New Quick Note" else "Edit Quick Note") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("note_title_input"),
          )
          OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("Note Body") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("note_content_input"),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.saveQuickNote(editingNote?.id ?: 0L, title, content)
            showNoteDialog = false
          },
          modifier = Modifier.testTag("save_note_button"),
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { showNoteDialog = false }) { Text("Cancel") }
      },
    )
  }
}
