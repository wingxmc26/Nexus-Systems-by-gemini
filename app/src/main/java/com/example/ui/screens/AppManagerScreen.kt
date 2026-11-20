package com.example.ui.screens

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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ManagedAppSummary
import com.example.data.model.MetricValue
import com.example.ui.components.EmptyOrErrorState
import com.example.ui.components.LoadingTelemetryState
import com.example.ui.components.MetricKeyValueRow
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.AppFilterMode
import com.example.ui.viewmodel.AppManagerViewModel
import com.example.ui.viewmodel.AppSortMode
import com.example.util.Formatters

@Composable
fun AppManagerScreen(
  viewModel: AppManagerViewModel,
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  DisposableEffect(lifecycleOwner) {
    val obs = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshApps()
      }
    }
    lifecycleOwner.lifecycle.addObserver(obs)
    onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
  }

  state.selectedApp?.let { app ->
    AppDetailDialog(
      app = app,
      onDismiss = { viewModel.selectApp(null) },
      onLaunch = { viewModel.launchApp(app) },
      onAppInfo = { viewModel.openAppInfo(app) },
      onUninstall = { viewModel.uninstallApp(app) },
      onShare = { viewModel.shareAppDetails(app) },
      onPin = { viewModel.pinToFavorites(app) },
    )
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Advanced App Manager",
      subtitle = "Packages, Storage Size, Permissions & Usage",
      onBack = onBack,
      trailingContent = {
        Row {
          IconButton(onClick = { viewModel.openDefaultAppsSettings() }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Settings, contentDescription = "Default Apps Settings", tint = colors.textPrimary)
          }
          IconButton(onClick = { viewModel.refreshApps() }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh apps", tint = colors.accent)
          }
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
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      if (!state.hasUsageAccess) {
        item {
          NexusGlassCard(accentHighlight = true) {
            SectionHeader(title = "Optional Usage Access", icon = Icons.Default.Apps)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Grant App Usage Access in Android Settings to view foreground screen time and recently used sorting.",
              style = MaterialTheme.typography.bodyMedium,
              color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { runCatching { context.startActivity(viewModel.buildUsageAccessIntent()) } }) {
              Text("Grant Usage Access")
            }
          }
        }
      }

      state.statusMessage?.let { msg ->
        item {
          NexusGlassCard(accentHighlight = true) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
              Text(msg, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
              TextButton(onClick = { viewModel.clearMessage() }) { Text("OK", color = colors.accent) }
            }
          }
        }
      }

      item {
        OutlinedTextField(
          value = state.searchQuery,
          onValueChange = viewModel::setSearchQuery,
          placeholder = { Text("Search installed apps or packages...") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().testTag("app_manager_search"),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          AppFilterMode.entries.forEach { mode ->
            FilterChip(
              selected = state.filterMode == mode,
              onClick = { viewModel.setFilterMode(mode) },
              label = { Text(mode.label) },
            )
          }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          AppSortMode.entries.forEach { sort ->
            FilterChip(
              selected = state.sortMode == sort,
              onClick = { viewModel.setSortMode(sort) },
              label = { Text("Sort: ${sort.label}") },
            )
          }
        }
      }

      if (state.isLoading) {
        item { LoadingTelemetryState("Inspecting installed packages...") }
      } else if (state.filteredApps.isEmpty()) {
        item {
          EmptyOrErrorState(
            icon = Icons.Default.Apps,
            title = "No Matching Apps",
            description = "Adjust your search filter or usage category.",
          )
        }
      } else {
        item {
          SectionHeader(title = "Installed Packages", trailingText = "${state.filteredApps.size} apps")
        }
        items(state.filteredApps, key = { it.packageName }) { app ->
          val iconBmp = remember(app.packageName) { runCatching { app.icon?.toBitmap(88, 88)?.asImageBitmap() }.getOrNull() }
          val sizeLabel = when (val s = app.apkSizeBytes) {
            is MetricValue.Available -> Formatters.formatBytes(s.value)
            is MetricValue.Unavailable -> "N/A"
          }
          NexusGlassCard(onClick = { viewModel.selectApp(app) }) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              if (iconBmp != null) {
                Image(bitmap = iconBmp, contentDescription = app.appName, modifier = Modifier.size(42.dp))
              } else {
                Icon(Icons.Default.Apps, contentDescription = null, tint = colors.accent, modifier = Modifier.size(36.dp))
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val usageNote = app.foregroundUsageMs?.takeIf { it > 0L }?.let { " • Usage ${Formatters.formatDuration(it)}" }.orEmpty()
                Text(
                  text = "v${app.versionName} • APK $sizeLabel • ${app.requestedPermissions.size} perms$usageNote",
                  style = MaterialTheme.typography.labelSmall,
                  color = colors.textMuted,
                )
              }
              if (app.isSystemApp) {
                StatusPill(text = "SYS", color = colors.textSecondary)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AppDetailDialog(
  app: ManagedAppSummary,
  onDismiss: () -> Unit,
  onLaunch: () -> Unit,
  onAppInfo: () -> Unit,
  onUninstall: () -> Unit,
  onShare: () -> Unit,
  onPin: () -> Unit,
) {
  val colors = NexusThemeExtras.colors
  val sizeText = (app.apkSizeBytes as? MetricValue.Available)?.let { Formatters.formatBytes(it.value) } ?: "Unavailable"
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.cardSurface,
    title = { Text(app.appName, color = colors.textPrimary) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = colors.accentSecondary)
        MetricKeyValueRow("Version", "${app.versionName} (${app.versionCode})")
        MetricKeyValueRow("APK Size", sizeText)
        MetricKeyValueRow("Target SDK", "API ${app.targetSdk}")
        MetricKeyValueRow("Type", if (app.isSystemApp) "System Package" else "User App")
        MetricKeyValueRow("Permissions", "${app.requestedPermissions.size} declared")
        app.foregroundUsageMs?.let {
          MetricKeyValueRow("7d Foreground", Formatters.formatDuration(it))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (app.isLaunchable) {
            Button(
              onClick = onLaunch,
              colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Launch")
            }
          }
          OutlinedButton(onClick = onAppInfo, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("App Info")
          }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onPin, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Pin")
          }
          OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Share")
          }
          if (!app.isSystemApp) {
            OutlinedButton(onClick = onUninstall, modifier = Modifier.weight(1f)) {
              Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = colors.critical, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Remove", color = colors.critical)
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Close") }
    },
  )
}
