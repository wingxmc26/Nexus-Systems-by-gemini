package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.theme.AccentPalette
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.CustomizationViewModel

@Composable
fun CustomizationScreen(
  viewModel: CustomizationViewModel,
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val prefs by viewModel.preferences.collectAsStateWithLifecycle()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Dashboard Customization",
      subtitle = "LOCAL PREFERENCES & THEME ENGINE",
      onBack = onBack,
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
        NexusGlassCard(accentHighlight = true, testTag = "customize_accent_card") {
          SectionHeader(title = "Accent Color", icon = Icons.Default.Palette)
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            AccentPalette.entries.forEach { palette ->
              val selected = prefs.accentPalette == palette
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                  .clip(MaterialTheme.shapes.small)
                  .clickable { viewModel.setAccentPalette(palette) }
                  .padding(6.dp)
                  .testTag("accent_${palette.id}"),
              ) {
                Box(
                  modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(palette.primary)
                    .border(
                      BorderStroke(if (selected) 2.5.dp else 1.dp, if (selected) Color.White else colors.glassBorder),
                      CircleShape,
                    ),
                  contentAlignment = Alignment.Center,
                ) {
                  if (selected) {
                    Icon(Icons.Default.Check, contentDescription = "Selected ${palette.displayName}", tint = Color.White)
                  }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = palette.displayName.substringBefore(" "),
                  style = MaterialTheme.typography.labelSmall,
                  color = if (selected) colors.textPrimary else colors.textSecondary,
                )
              }
            }
          }
        }
      }

      item {
        NexusGlassCard(testTag = "customize_theme_card") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Icon(Icons.Default.DarkMode, contentDescription = null, tint = colors.accent)
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text("AMOLED Pure Black", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text(
                  text = "True #000000 pixel-off background vs matte obsidian",
                  style = MaterialTheme.typography.bodyMedium,
                  color = colors.textSecondary,
                )
              }
            }
            Switch(
              checked = prefs.amoledMode,
              onCheckedChange = { viewModel.setAmoledMode(it) },
              modifier = Modifier.testTag("amoled_toggle"),
            )
          }
        }
      }

      item {
        NexusGlassCard(testTag = "customize_refresh_card") {
          SectionHeader(title = "Telemetry Polling Interval", icon = Icons.Default.Speed)
          Spacer(modifier = Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1000L to "1s (Fast)", 2000L to "2s (Balanced)", 5000L to "5s (Eco)").forEach { (ms, label) ->
              FilterChip(
                selected = prefs.refreshIntervalMs == ms,
                onClick = { viewModel.setRefreshIntervalMs(ms) },
                label = { Text(label) },
              )
            }
          }
        }
      }

      item {
        SectionHeader(
          title = "Dashboard Card Order & Visibility",
          icon = Icons.Default.Reorder,
          trailingText = "${prefs.cardOrder.size - prefs.hiddenModules.size} ACTIVE",
        )
      }

      itemsIndexed(prefs.cardOrder, key = { _, item -> item.id }) { index, moduleId ->
        val visible = moduleId !in prefs.hiddenModules
        NexusGlassCard(testTag = "reorder_card_${moduleId.id}") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(moduleId.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
              Text(moduleId.subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
            IconButton(
              onClick = { viewModel.moveCardUp(moduleId) },
              enabled = index > 0,
              modifier = Modifier.testTag("move_up_${moduleId.id}"),
            ) {
              Icon(Icons.Default.ArrowUpward, contentDescription = "Move ${moduleId.title} up")
            }
            IconButton(
              onClick = { viewModel.moveCardDown(moduleId) },
              enabled = index < prefs.cardOrder.lastIndex,
              modifier = Modifier.testTag("move_down_${moduleId.id}"),
            ) {
              Icon(Icons.Default.ArrowDownward, contentDescription = "Move ${moduleId.title} down")
            }
            Switch(
              checked = visible,
              onCheckedChange = { viewModel.toggleModuleVisibility(moduleId, it) },
              modifier = Modifier.testTag("toggle_visible_${moduleId.id}"),
            )
          }
        }
      }

      item {
        OutlinedButton(
          onClick = { viewModel.resetDefaults() },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("reset_customization_button"),
        ) {
          Icon(Icons.Default.Refresh, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Reset Preferences to Defaults")
        }
      }
    }
  }
}
