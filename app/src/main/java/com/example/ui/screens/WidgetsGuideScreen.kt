package com.example.ui.screens

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MainActivity
import com.example.data.model.BuiltInWidgetStyleConfig
import com.example.di.NexusContainer
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusThemeExtras
import com.example.widget.NexusBatteryWidgetProvider
import com.example.widget.NexusCustomWidgetProvider
import com.example.widget.NexusDepthClockWidgetProvider
import com.example.widget.NexusDeviceInfoWidgetProvider
import com.example.widget.NexusFavoritesWidgetProvider
import com.example.widget.NexusMusicWidgetProvider
import com.example.widget.NexusSystemStatusWidgetProvider
import com.example.widget.NexusWidgetUpdater
import com.example.widget.engine.CapabilityStatus
import com.example.widget.engine.DiagnosticSeverity
import com.example.widget.engine.WidgetCapability
import com.example.widget.engine.WidgetDiagnosticCenter
import com.example.widget.engine.WidgetEngineRegistry
import kotlinx.coroutines.launch

private data class HomeWidgetSpec(
  val id: String,
  val title: String,
  val description: String,
  val sizeLabel: String,
  val icon: ImageVector,
  val providerClass: Class<*>,
  val supportsStyleCustomization: Boolean = false,
)

private val builtinBgSwatches = listOf(
  0xFF050608L,
  0xFF10131CL,
  0xFF1A0912L,
  0xFF0A1916L,
  0xFFF8FAFCL,
)

private val builtinAccentSwatches = listOf(
  0xFFE11D48L,
  0xFF38BDF8L,
  0xFF10B981L,
  0xFFF59E0BL,
  0xFFA855F7L,
  0xFFFFFFFFL,
)

private val builtinTextSwatches = listOf(
  0xFFFFFFFFL,
  0xFFE5E7EBL,
  0xFF93C5FDL,
  0xFF0F172AL,
)

@Composable
fun WidgetsGuideScreen(
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val container = remember(context) { NexusContainer.get(context) }
  val prefsRepo = container.preferencesRepository
  val coordinator = container.widgetDataHub.permissionCoordinator
  val appWidgetManager = remember(context) { runCatching { AppWidgetManager.getInstance(context) }.getOrNull() }
  val canRequestPin = remember(appWidgetManager) {
    runCatching { appWidgetManager?.isRequestPinAppWidgetSupported == true }.getOrDefault(false)
  }
  var feedbackText by remember { mutableStateOf<String?>(null) }
  var expandedCustomizerId by remember { mutableStateOf<String?>(null) }
  var showEngineDetails by remember { mutableStateOf(false) }
  var capabilityRefreshTick by remember { mutableIntStateOf(0) }
  val diagnosticEntries by WidgetDiagnosticCenter.entries.collectAsStateWithLifecycle()
  val styleConfigs = remember { mutableStateMapOf<String, BuiltInWidgetStyleConfig>() }

  val runtimePermLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
  ) {
    capabilityRefreshTick++
    scope.launch { NexusWidgetUpdater.refreshAll(context) }
  }

  val widgets = remember {
    listOf(
      HomeWidgetSpec(
        id = "custom_studio",
        title = "Custom Studio Widget",
        description = "Displays your active Visual Builder or rendered HTML/CSS/JS custom widget created inside Widget Studio (Small, Medium, Large 4×4, Wide, iOS Clock).",
        sizeLabel = "Resizable",
        icon = Icons.Default.DashboardCustomize,
        providerClass = NexusCustomWidgetProvider::class.java,
      ),
      HomeWidgetSpec(
        id = "depth_clock",
        title = "Depth Clock Widget",
        description = "Shows your layered Digital/Analog/Dual clock with custom typography, depth mask, and high-DPI scaling up to 4×4.",
        sizeLabel = "2×2 – 4×4",
        icon = Icons.Default.AccessTime,
        providerClass = NexusDepthClockWidgetProvider::class.java,
      ),
      HomeWidgetSpec(
        id = "music",
        title = "Music Playback Widget",
        description = "Shows current local track metadata with Play/Pause, Previous, and Next controls.",
        sizeLabel = "4 × 1",
        icon = Icons.Default.MusicNote,
        providerClass = NexusMusicWidgetProvider::class.java,
        supportsStyleCustomization = true,
      ),
      HomeWidgetSpec(
        id = "battery",
        title = "Battery & Charging",
        description = "Shows live battery percentage, charge source, live current in mA, temperature, and charging session duration.",
        sizeLabel = "3 × 2",
        icon = Icons.Default.BatteryChargingFull,
        providerClass = NexusBatteryWidgetProvider::class.java,
        supportsStyleCustomization = true,
      ),
      HomeWidgetSpec(
        id = "system_status",
        title = "System Status",
        description = "At-a-glance summary of RAM usage, battery percentage, and available internal storage.",
        sizeLabel = "3 × 2",
        icon = Icons.Default.Memory,
        providerClass = NexusSystemStatusWidgetProvider::class.java,
        supportsStyleCustomization = true,
      ),
      HomeWidgetSpec(
        id = "device_info",
        title = "Device Info",
        description = "Displays manufacturer & model, Android OS / SDK level, uptime, and display specs.",
        sizeLabel = "3 × 2",
        icon = Icons.Default.PhoneAndroid,
        providerClass = NexusDeviceInfoWidgetProvider::class.java,
        supportsStyleCustomization = true,
      ),
      HomeWidgetSpec(
        id = "favorites",
        title = "Favorite Shortcuts",
        description = "Quick access to your pinned favorite apps & exported activities configured in Shortcut Studio.",
        sizeLabel = "3 × 2",
        icon = Icons.Default.Star,
        providerClass = NexusFavoritesWidgetProvider::class.java,
        supportsStyleCustomization = true,
      ),
    )
  }

  LaunchedEffect(widgets) {
    widgets.filter { it.supportsStyleCustomization }.forEach { spec ->
      styleConfigs[spec.id] = prefsRepo.getBuiltInWidgetStyle(spec.id)
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Home Widgets",
      subtitle = "${widgets.size} AMOLED Home-Screen Widgets",
      onBack = onBack,
      trailingContent = {
        IconButton(
          onClick = {
            scope.launch {
              NexusWidgetUpdater.refreshAll(context)
              feedbackText = "Updated all placed Nexus System home-screen widgets."
            }
          },
          modifier = Modifier
            .size(48.dp)
            .testTag("refresh_widgets_button"),
        ) {
          Icon(Icons.Default.Refresh, contentDescription = "Refresh All Widgets", tint = colors.textPrimary)
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
        NexusGlassCard(accentHighlight = true, testTag = "widgets_info_card") {
          SectionHeader(title = "Add to Home Screen")
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = if (canRequestPin) {
              "Tap 'Pin Widget to Home' on any widget below to place it onto your launcher, or customize built-in widget colors, transparency, typography, and alignment directly."
            } else {
              "Long-press an empty area on your home screen, select Widgets, and choose any NEXUS SYSTEM widget."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedButton(
            onClick = {
              scope.launch {
                NexusWidgetUpdater.refreshAll(context)
                capabilityRefreshTick++
                feedbackText = "All widget providers refreshed."
              }
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Refresh Placed Widgets Now")
          }
        }
      }

      item {
        val activeTotal = remember(capabilityRefreshTick) { WidgetEngineRegistry.activeWidgetCount(context) }
        val keyCaps = remember(capabilityRefreshTick) {
          listOf(
            WidgetCapability.MEDIA_SESSION,
            WidgetCapability.CALENDAR,
            WidgetCapability.LOCATION_WEATHER,
            WidgetCapability.NETWORK,
          ).map { it to coordinator.statusFor(it) }
        }
        NexusGlassCard(testTag = "widget_engine_status_card") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              SectionHeader(title = "Widget Engine & Capabilities")
              Text(
                text = "$activeTotal active home-screen slots • ${diagnosticEntries.size} engine logs",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
              )
            }
            TextButton(onClick = { showEngineDetails = !showEngineDetails }) {
              Text(if (showEngineDetails) "Hide" else "Inspect")
            }
          }
          if (showEngineDetails) {
            Spacer(modifier = Modifier.height(6.dp))
            keyCaps.forEach { (cap, status) ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(cap.displayName, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                  Text(cap.explanation, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (status == CapabilityStatus.GRANTED) {
                  StatusPill(text = "Granted", color = colors.nominal)
                } else {
                  OutlinedButton(
                    onClick = {
                      val perms = coordinator.requiredRuntimePermissions(cap)
                      if (perms.isNotEmpty()) {
                        runtimePermLauncher.launch(perms.toTypedArray())
                      } else {
                        coordinator.buildActionableIntent(cap)?.let { intent ->
                          runCatching { context.startActivity(intent) }
                        }
                      }
                    },
                    modifier = Modifier.testTag("grant_capability_${cap.id}"),
                  ) {
                    Text(if (status == CapabilityStatus.MISSING_SPECIAL_SETTINGS) "Settings" else "Grant")
                  }
                }
              }
            }
            if (diagnosticEntries.isNotEmpty()) {
              Spacer(modifier = Modifier.height(8.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Recent Widget Engine Diagnostics", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
                TextButton(onClick = { WidgetDiagnosticCenter.clear() }) {
                  Text("Clear")
                }
              }
              diagnosticEntries.take(6).forEach { entry ->
                val badgeColor = when (entry.severity) {
                  DiagnosticSeverity.ERROR -> colors.accent
                  DiagnosticSeverity.WARN -> colors.warning
                  DiagnosticSeverity.INFO -> colors.accentSecondary
                }
                Text(
                  text = "[${entry.category}] ${entry.message}",
                  style = MaterialTheme.typography.labelSmall,
                  color = badgeColor,
                  modifier = Modifier.padding(vertical = 2.dp),
                )
              }
            }
          }
        }
      }

      feedbackText?.let { msg ->
        item {
          NexusGlassCard {
            Text(
              text = msg,
              style = MaterialTheme.typography.bodyMedium,
              color = colors.accentSecondary,
            )
          }
        }
      }

      items(widgets, key = { it.id }) { spec ->
        val placedCount = remember(appWidgetManager, spec) {
          runCatching {
            appWidgetManager?.getAppWidgetIds(ComponentName(context, spec.providerClass))?.size ?: 0
          }.getOrDefault(0)
        }
        val currentStyle = styleConfigs[spec.id] ?: BuiltInWidgetStyleConfig()
        val isCustomizing = expandedCustomizerId == spec.id

        NexusGlassCard(testTag = "widget_card_${spec.id}") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.accent.copy(alpha = 0.14f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(spec.icon, contentDescription = null, tint = colors.accent)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(spec.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
              Text(
                "Size ${spec.sizeLabel} • $placedCount active" +
                  if (currentStyle.isCustomized) " • Custom Styled" else "",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
              )
            }
            StatusPill(
              text = if (placedCount > 0) "Active" else "Ready",
              color = if (placedCount > 0) colors.nominal else colors.accentSecondary,
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = spec.description,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
          )

          if (spec.supportsStyleCustomization) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
              onClick = {
                expandedCustomizerId = if (isCustomizing) null else spec.id
              },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("customize_builtin_${spec.id}"),
            ) {
              Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(if (isCustomizing) "Hide Appearance Controls" else "Customize Appearance")
            }

            if (isCustomizing) {
              Spacer(modifier = Modifier.height(10.dp))
              BuiltInStyleEditorPanel(
                style = currentStyle,
                onChange = { updated -> styleConfigs[spec.id] = updated },
                onApply = {
                  val toSave = (styleConfigs[spec.id] ?: BuiltInWidgetStyleConfig()).copy(isCustomized = true)
                  styleConfigs[spec.id] = toSave
                  scope.launch {
                    prefsRepo.saveBuiltInWidgetStyle(spec.id, toSave)
                    NexusWidgetUpdater.refreshAll(context)
                    feedbackText = "Applied custom style to ${spec.title}."
                  }
                },
                onReset = {
                  styleConfigs[spec.id] = BuiltInWidgetStyleConfig()
                  scope.launch {
                    prefsRepo.resetBuiltInWidgetStyle(spec.id)
                    NexusWidgetUpdater.refreshAll(context)
                    feedbackText = "Reset ${spec.title} to default AMOLED theme."
                  }
                },
              )
            }
          }

          if (canRequestPin) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
              onClick = {
                val component = ComponentName(context, spec.providerClass)
                val callbackIntent = PendingIntent.getActivity(
                  context,
                  spec.id.hashCode(),
                  Intent(context, MainActivity::class.java),
                  PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val accepted = runCatching {
                  appWidgetManager?.requestPinAppWidget(component, null, callbackIntent) ?: false
                }.getOrDefault(false)
                feedbackText = if (accepted) {
                  "Launcher prompt requested for ${spec.title}."
                } else {
                  "Launcher declined direct pin. Add ${spec.title} from the home-screen Widgets picker."
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("pin_widget_${spec.id}"),
            ) {
              Icon(Icons.Default.AddToHomeScreen, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Pin Widget to Home")
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BuiltInStyleEditorPanel(
  style: BuiltInWidgetStyleConfig,
  onChange: (BuiltInWidgetStyleConfig) -> Unit,
  onApply: () -> Unit,
  onReset: () -> Unit,
) {
  val colors = NexusThemeExtras.colors
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(colors.background.copy(alpha = 0.55f))
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("Background Color", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      builtinBgSwatches.forEach { argb ->
        Box(
          modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .border(
              width = if (style.backgroundColorArgb == argb) 2.5.dp else 1.dp,
              color = if (style.backgroundColorArgb == argb) colors.accent else colors.glassBorder,
              shape = CircleShape,
            )
            .clickable { onChange(style.copy(backgroundColorArgb = argb, isCustomized = true)) },
        )
      }
    }

    Text("Opacity ${(style.opacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
    Slider(
      value = style.opacity,
      onValueChange = { onChange(style.copy(opacity = it, isCustomized = true)) },
      valueRange = 0f..1f,
    )

    Text("Accent Color", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      builtinAccentSwatches.forEach { argb ->
        Box(
          modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .border(
              width = if (style.accentColorArgb == argb) 2.5.dp else 1.dp,
              color = if (style.accentColorArgb == argb) Color.White else colors.glassBorder,
              shape = CircleShape,
            )
            .clickable { onChange(style.copy(accentColorArgb = argb, isCustomized = true)) },
        )
      }
    }

    Text("Text Color", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      builtinTextSwatches.forEach { argb ->
        Box(
          modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .border(
              width = if (style.textColorArgb == argb) 2.5.dp else 1.dp,
              color = if (style.textColorArgb == argb) colors.accent else colors.glassBorder,
              shape = CircleShape,
            )
            .clickable {
              val sec = if (argb == 0xFF0F172AL) 0xFF475569L else 0xFF9CA3AFL
              onChange(style.copy(textColorArgb = argb, secondaryTextColorArgb = sec, isCustomized = true))
            },
        )
      }
    }

    Text("Text Size ${(style.fontSizeScale * 100).toInt()}% • Padding ${style.paddingDp}dp • Radius ${style.cornerRadiusDp}dp", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
    Slider(
      value = style.fontSizeScale,
      onValueChange = { onChange(style.copy(fontSizeScale = it, isCustomized = true)) },
      valueRange = 0.8f..1.45f,
    )
    Slider(
      value = style.paddingDp.toFloat(),
      onValueChange = { onChange(style.copy(paddingDp = it.toInt(), isCustomized = true)) },
      valueRange = 6f..28f,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf(0 to "Left", 1 to "Center", 2 to "Right").forEach { (alignVal, alignLabel) ->
        FilterChip(
          selected = style.alignment == alignVal,
          onClick = { onChange(style.copy(alignment = alignVal, isCustomized = true)) },
          label = { Text(alignLabel) },
        )
      }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Text("Bold Text", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
      Switch(checked = style.fontWeightBold, onCheckedChange = { onChange(style.copy(fontWeightBold = it, isCustomized = true)) })
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Text("Show Secondary Details", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
      Switch(checked = style.showSecondaryDetails, onCheckedChange = { onChange(style.copy(showSecondaryDetails = it, isCustomized = true)) })
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) {
        Text("Reset")
      }
      Button(
        onClick = onApply,
        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
        modifier = Modifier.weight(1f),
      ) {
        Text("Apply Style")
      }
    }
  }
}
