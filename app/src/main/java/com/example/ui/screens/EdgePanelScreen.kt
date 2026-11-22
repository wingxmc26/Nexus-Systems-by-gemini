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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.NexusActionSpec
import com.example.data.local.EdgeSide
import com.example.ui.components.ActionPickerDialog
import com.example.ui.components.ActionSpecListEditor
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.OverlayHubViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EdgePanelScreen(
  viewModel: OverlayHubViewModel,
  onBack: () -> Unit,
  contentPadding: PaddingValues,
) {
  val colors = NexusThemeExtras.colors
  val context = LocalContext.current
  val config by viewModel.edgeConfig.collectAsStateWithLifecycle()
  val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()
  val apps by viewModel.launchableApps.collectAsStateWithLifecycle()
  val banner by viewModel.statusBanner.collectAsStateWithLifecycle()

  val overlayGranted = remember(config) { viewModel.permissionManager.isOverlayGranted() }
  var activeCategory by remember { mutableStateOf("All") }
  var showAddDialog by remember { mutableStateOf(false) }

  val filteredShortcuts = remember(shortcuts, activeCategory) {
    when (activeCategory) {
      "All" -> shortcuts
      "Favorites" -> shortcuts.filter { it.isFavorite }
      else -> shortcuts.filter { it.category.equals(activeCategory, ignoreCase = true) }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Edge Panel",
      subtitle = "Left/Right Swipe Handle, Categories, Favorites & Combos",
      onBack = onBack,
    )

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .testTag("edge_panel_list"),
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
          testTag = "edge_panel_config_card",
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Persistent Edge Handle Overlay", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
              Text(
                if (overlayGranted) "Swipe handle inward from ${config.side.name.lowercase()} edge to slide open shortcuts."
                else "Requires Display Over Other Apps permission.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
              )
            }
            Switch(
              checked = config.enabled && overlayGranted,
              onCheckedChange = { viewModel.setEdgePanelEnabled(it) },
              modifier = Modifier.testTag("edge_panel_enable_switch"),
            )
          }

          if (!overlayGranted) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
              onClick = { context.startActivity(viewModel.permissionManager.buildOverlayIntent()) },
              modifier = Modifier.fillMaxWidth().testTag("edge_grant_overlay_button"),
            ) {
              Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text("Grant Display Over Other Apps")
            }
          }

          Spacer(Modifier.height(12.dp))
          Text("Screen Edge Side", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
          Spacer(Modifier.height(6.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EdgeSide.entries.forEach { side ->
              FilterChip(
                selected = config.side == side,
                onClick = { viewModel.updateEdgeSide(side) },
                label = { Text("${side.name} EDGE") },
                modifier = Modifier.weight(1f).testTag("edge_side_${side.name}"),
              )
            }
          }

          Spacer(Modifier.height(10.dp))
          Text("Vertical Position: ${(config.verticalOffsetPct * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
          Slider(
            value = config.verticalOffsetPct,
            onValueChange = { v -> viewModel.updateEdgeConfig { it.copy(verticalOffsetPct = v) } },
            valueRange = 0.08f..0.82f,
          )

          Text("Handle Height: ${config.handleHeightDp} dp", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
          Slider(
            value = config.handleHeightDp.toFloat(),
            onValueChange = { v -> viewModel.updateEdgeConfig { it.copy(handleHeightDp = v.toInt()) } },
            valueRange = 72f..320f,
          )

          Text("Handle Activation Width: ${config.handleWidthDp} dp", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
          Slider(
            value = config.handleWidthDp.toFloat(),
            onValueChange = { v -> viewModel.updateEdgeConfig { it.copy(handleWidthDp = v.toInt()) } },
            valueRange = 14f..48f,
          )

          Text("Panel Width: ${config.panelWidthDp} dp", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
          Slider(
            value = config.panelWidthDp.toFloat(),
            onValueChange = { v -> viewModel.updateEdgeConfig { it.copy(panelWidthDp = v.toInt()) } },
            valueRange = 180f..340f,
          )

          Text("Swipe Sensitivity: ${String.format("%.2fx", config.sensitivity)}", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
          Slider(
            value = config.sensitivity,
            onValueChange = { v -> viewModel.updateEdgeConfig { it.copy(sensitivity = v) } },
            valueRange = 0.4f..2.0f,
          )

          Text("Panel Opacity: ${(config.opacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
          Slider(
            value = config.opacity,
            onValueChange = { v -> viewModel.updateEdgeConfig { it.copy(opacity = v) } },
            valueRange = 0.4f..1.0f,
          )
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SectionHeader(title = "Edge Panel Shortcuts & Combos", trailingText = "${filteredShortcuts.size} shown")
          Button(
            onClick = { showAddDialog = true },
            modifier = Modifier.testTag("add_edge_shortcut_button"),
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add Shortcut")
          }
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("All", "Favorites", "NEXUS", "Apps", "Quick Actions", "Combos").forEach { cat ->
            FilterChip(
              selected = activeCategory == cat,
              onClick = { activeCategory = cat },
              label = { Text(cat) },
            )
          }
        }
      }

      itemsIndexed(filteredShortcuts, key = { _, it -> it.id }) { idx, shortcut ->
        NexusGlassCard(testTag = "edge_shortcut_card_${shortcut.id}") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(shortcut.label, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                StatusPill(shortcut.category, colors.accent)
              }
              val actions = NexusActionSpec.parseList(shortcut.actionsJson)
              Text(
                "${actions.size} action(s) • Surface: ${shortcut.targetSurface}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
              )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = { viewModel.toggleShortcutFavorite(shortcut.id, !shortcut.isFavorite) }) {
                Icon(
                  imageVector = if (shortcut.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                  contentDescription = "Favorite",
                  tint = if (shortcut.isFavorite) colors.warning else colors.textSecondary,
                )
              }
              if (activeCategory == "All") {
                IconButton(onClick = { viewModel.moveShortcutUp(shortcuts, idx) }, enabled = idx > 0) {
                  Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up")
                }
                IconButton(onClick = { viewModel.moveShortcutDown(shortcuts, idx) }, enabled = idx < shortcuts.lastIndex) {
                  Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down")
                }
              }
              IconButton(onClick = { viewModel.runShortcut(shortcut, "EDGE_PANEL") }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Execute", tint = colors.nominal)
              }
              IconButton(onClick = { viewModel.deleteShortcut(shortcut.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.critical)
              }
            }
          }
        }
      }
    }
  }

  if (showAddDialog) {
    var label by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Quick Actions") }
    var targetSurface by remember { mutableStateOf("BOTH") }
    var actions by remember { mutableStateOf<List<NexusActionSpec>>(emptyList()) }
    var showActionPicker by remember { mutableStateOf(false) }

    AlertDialog(
      onDismissRequest = { showAddDialog = false },
      title = { Text("Create Edge / Floating Shortcut") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Shortcut Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("shortcut_name_input"),
          )
          Text("Category", style = MaterialTheme.typography.labelMedium)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("NEXUS", "Apps", "Quick Actions", "Combos").forEach { c ->
              FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
            }
          }
          Text("Show In", style = MaterialTheme.typography.labelMedium)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("BOTH", "EDGE_PANEL", "FLOATING_TOOLS").forEach { s ->
              FilterChip(selected = targetSurface == s, onClick = { targetSurface = s }, label = { Text(s) })
            }
          }
          ActionSpecListEditor(
            title = "Actions (${actions.size})",
            actions = actions,
            onAddAction = { showActionPicker = true },
            onRemoveAt = { i -> actions = actions.filterIndexed { index, _ -> index != i } },
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (actions.isNotEmpty()) {
              viewModel.addShortcut(
                label = label.ifBlank { actions.first().label },
                category = category,
                targetSurface = targetSurface,
                actions = actions,
              )
              showAddDialog = false
            }
          },
          enabled = actions.isNotEmpty(),
          modifier = Modifier.testTag("save_edge_shortcut_button"),
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
      },
    )

    if (showActionPicker) {
      ActionPickerDialog(
        catalog = viewModel.actionDispatcher.availableCatalog(),
        launchableApps = apps,
        onDismiss = { showActionPicker = false },
        onConfirm = { spec ->
          actions = actions + spec
          if (label.isBlank()) label = spec.label
          showActionPicker = false
        },
      )
    }
  }
}
