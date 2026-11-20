package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ManagedAppSummary
import com.example.data.model.MetricValue
import com.example.ui.components.EmptyOrErrorState
import com.example.ui.components.LoadingTelemetryState
import com.example.ui.components.MetricKeyValueRow
import com.example.ui.components.MetricValueRow
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.TelemetryArcGauge
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.RamAppFilter
import com.example.ui.viewmodel.RamManagerViewModel
import com.example.util.Formatters

@Composable
fun RamManagerScreen(
  viewModel: RamManagerViewModel,
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "RAM Manager",
      subtitle = "System memory overview & app info controls",
      onBack = onBack,
      trailingContent = {
        IconButton(
          onClick = {
            viewModel.refreshMemoryNow()
            viewModel.reloadApps()
          },
          modifier = Modifier.testTag("ram_refresh_button"),
        ) {
          Icon(Icons.Default.Refresh, contentDescription = "Refresh RAM", tint = colors.textPrimary)
        }
      },
    )

    if (state.isLoadingMemory && state.memory == null) {
      LoadingTelemetryState("Reading system memory...")
    } else {
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
        state.memory?.let { mem ->
          item {
            NexusGlassCard(accentHighlight = true, testTag = "ram_overview_card") {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                TelemetryArcGauge(
                  progress = mem.usagePercent / 100f,
                  centerValue = "${mem.usagePercent.toInt()}%",
                  centerLabel = "Used",
                  size = 100.dp,
                  gaugeColor = if (mem.isLowMemory) colors.critical else colors.accent,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                  MetricKeyValueRow("Total RAM", Formatters.formatBytes(mem.totalRamBytes))
                  MetricKeyValueRow("Used RAM", Formatters.formatBytes(mem.usedRamBytes))
                  MetricKeyValueRow("Available RAM", Formatters.formatBytes(mem.availableRamBytes), colors.nominal)
                  MetricKeyValueRow("Memory Pressure", if (mem.isLowMemory) "Low Memory" else "Normal")
                }
              }
            }
          }

          item {
            NexusGlassCard(testTag = "ram_system_details_card") {
              SectionHeader(title = "System Memory Details", icon = Icons.Default.Memory)
              Spacer(modifier = Modifier.height(6.dp))
              MetricKeyValueRow("Low Memory Threshold", Formatters.formatBytes(mem.lowMemoryThresholdBytes))
              MetricValueRow("Nexus App Memory (PSS)", mem.nexusAppPssKb) { Formatters.formatBytes(it * 1024L) }
              MetricValueRow("Kernel Page Cache", mem.cachedMemoryBytes) { Formatters.formatBytes(it) }
              MetricValueRow("Kernel Buffers", mem.buffersBytes) { Formatters.formatBytes(it) }
              MetricValueRow("Swap Total", mem.swapTotalBytes) { Formatters.formatBytes(it) }
              MetricValueRow("Swap Free", mem.swapFreeBytes) { Formatters.formatBytes(it) }
              MetricKeyValueRow("Visible Processes (Self/OS)", "${mem.visibleProcessCount}")
              Spacer(modifier = Modifier.height(10.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Button(
                  onClick = { viewModel.clearOwnCache() },
                  colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("ram_clear_cache_button"),
                ) {
                  Icon(Icons.Default.CleaningServices, contentDescription = null)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Trim App Cache")
                }
                OutlinedButton(
                  onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_SETTINGS).apply {
                      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                  },
                ) {
                  Icon(Icons.Default.Settings, contentDescription = null)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Android Apps")
                }
              }
              state.actionMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(msg, style = MaterialTheme.typography.bodyMedium, color = colors.nominal)
              }
            }
          }
        }

        item {
          NexusGlassCard(testTag = "ram_restriction_notice") {
            Row(verticalAlignment = Alignment.Top) {
              Icon(Icons.Default.Info, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = "Android restricts standard third-party apps from force-stopping or killing other apps' background processes without root. Tap App Info below to manage or force-stop an app via Android Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
              )
            }
          }
        }

        item {
          SectionHeader(
            title = "Installed Applications",
            icon = Icons.Default.Apps,
            trailingText = "${state.apps.size} visible",
          )
          Spacer(modifier = Modifier.height(6.dp))
          OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Search installed apps...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("ram_app_search"),
          )
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            RamAppFilter.entries.forEach { f ->
              FilterChip(
                selected = state.filter == f,
                onClick = { viewModel.setFilter(f) },
                label = { Text(f.label) },
              )
            }
          }
        }

        if (state.isLoadingApps) {
          item { LoadingTelemetryState("Loading visible applications...") }
        } else if (state.apps.isEmpty()) {
          item {
            EmptyOrErrorState(
              icon = Icons.Default.Apps,
              title = "No Apps Found",
              description = "No launchable apps matched your current search or filter.",
            )
          }
        } else {
          items(state.apps, key = { it.packageName }) { app ->
            ManagedAppRow(app = app)
          }
        }
      }
    }
  }
}

@Composable
private fun ManagedAppRow(app: ManagedAppSummary) {
  val colors = NexusThemeExtras.colors
  val context = LocalContext.current
  val iconBitmap = remember(app.packageName) {
    app.icon?.let { drawableToBitmap(it)?.asImageBitmap() }
  }

  NexusGlassCard(
    onClick = {
      val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", app.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      runCatching { context.startActivity(intent) }
    },
    testTag = "ram_app_${app.packageName}",
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (iconBitmap != null) {
        Image(
          bitmap = iconBitmap,
          contentDescription = "${app.appName} icon",
          modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp)),
        )
      } else {
        Icon(Icons.Default.Apps, contentDescription = null, tint = colors.accent, modifier = Modifier.size(40.dp))
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = app.appName,
          style = MaterialTheme.typography.titleMedium,
          color = colors.textPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${app.packageName} • v${app.versionName}",
          style = MaterialTheme.typography.bodyMedium,
          color = colors.textSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(modifier = Modifier.width(8.dp))
      StatusPill(text = "App Info", color = colors.accent)
    }
  }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap? {
  return runCatching {
    val w = drawable.intrinsicWidth.coerceIn(48, 128)
    val h = drawable.intrinsicHeight.coerceIn(48, 128)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    bitmap
  }.getOrNull()
}
