package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Launch
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.InstalledAppEntry
import com.example.data.model.MetricValue
import com.example.ui.components.EmptyOrErrorState
import com.example.ui.components.LoadingTelemetryState
import com.example.ui.components.MetricKeyValueRow
import com.example.ui.components.MetricValueRow
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.AppFilterMode
import com.example.ui.viewmodel.AppInspectorViewModel
import com.example.ui.viewmodel.AppSortMode
import com.example.util.Formatters

@Composable
fun AppInspectorScreen(
  viewModel: AppInspectorViewModel,
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "App Inspector",
      subtitle = "${state.apps.size} LAUNCHABLE PACKAGES VISIBLE",
      onBack = onBack,
      trailingContent = {
        IconButton(onClick = { viewModel.reload() }) {
          Icon(Icons.Default.Refresh, contentDescription = "Reload Apps", tint = colors.textPrimary)
        }
      },
    )

    Column(
      modifier = Modifier
        .padding(horizontal = 16.dp)
        .fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = state.searchQuery,
        onValueChange = { viewModel.setSearchQuery(it) },
        placeholder = { Text("Search app name or package...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("app_search_input"),
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        AppFilterMode.entries.forEach { mode ->
          FilterChip(
            selected = state.filterMode == mode,
            onClick = { viewModel.setFilterMode(mode) },
            label = { Text(mode.label) },
          )
        }
        AppSortMode.entries.forEach { sort ->
          FilterChip(
            selected = state.sortMode == sort,
            onClick = { viewModel.setSortMode(sort) },
            label = { Text("Sort: ${sort.label}") },
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    when {
      state.isLoading -> LoadingTelemetryState("Querying package manager...")
      state.error != null -> {
        Column(modifier = Modifier.padding(16.dp)) {
          EmptyOrErrorState(
            icon = Icons.Default.Apps,
            title = "Package Query Failed",
            description = state.error ?: "Unknown error",
          )
        }
      }
      state.apps.isEmpty() -> {
        Column(modifier = Modifier.padding(16.dp)) {
          EmptyOrErrorState(
            icon = Icons.Default.Apps,
            title = "No Matching Applications",
            description = "Adjust your search or filter. Only launchable apps visible under Android Package Visibility rules are listed.",
          )
        }
      }
      else -> {
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
          items(state.apps, key = { it.packageName }) { app ->
            AppPackageCard(app = app)
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppPackageCard(app: InstalledAppEntry) {
  val colors = NexusThemeExtras.colors
  val context = LocalContext.current
  var expanded by remember(app.packageName) { mutableStateOf(false) }

  val iconBitmap = remember(app.packageName) {
    app.icon?.let { drawableToBitmap(it)?.asImageBitmap() }
  }

  NexusGlassCard(
    onClick = { expanded = !expanded },
    testTag = "app_card_${app.packageName}",
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
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp)),
        )
      } else {
        Icon(
          imageVector = Icons.Default.Apps,
          contentDescription = null,
          tint = colors.accent,
          modifier = Modifier.size(44.dp),
        )
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
          text = app.packageName,
          style = MaterialTheme.typography.labelSmall,
          color = colors.textSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Icon(
        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        contentDescription = if (expanded) "Collapse" else "Expand",
        tint = colors.textSecondary,
      )
    }

    Spacer(modifier = Modifier.height(8.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      StatusPill(text = "v${app.versionName}", color = colors.info)
      StatusPill(
        text = when (val sz = app.apkSizeBytes) {
          is MetricValue.Available -> Formatters.formatBytes(sz.value)
          is MetricValue.Unavailable -> "Size N/A"
        },
        color = colors.accentSecondary,
      )
      StatusPill(
        text = if (app.isSystemApp) "SYSTEM" else "USER",
        color = if (app.isSystemApp) colors.textMuted else colors.nominal,
      )
    }

    AnimatedVisibility(visible = expanded) {
      Column(modifier = Modifier.padding(top = 12.dp)) {
        MetricKeyValueRow("Version Code", app.versionCode.toString())
        MetricKeyValueRow("Target SDK", "API ${app.targetSdk}")
        MetricValueRow("Base APK Size", app.apkSizeBytes) { Formatters.formatBytes(it) }
        MetricKeyValueRow(
          label = "Permissions",
          value = "${app.permissions.count { it.isGranted }} granted / ${app.permissions.size} declared",
        )

        if (app.permissions.isNotEmpty()) {
          Spacer(modifier = Modifier.height(6.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            app.permissions.take(12).forEach { perm ->
              StatusPill(
                text = perm.name,
                color = if (perm.isGranted) colors.nominal else colors.textMuted,
              )
            }
            if (app.permissions.size > 12) {
              StatusPill(text = "+${app.permissions.size - 12} more", color = colors.textSecondary)
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (app.canLaunch) {
            Button(
              onClick = {
                val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
                if (launch != null) {
                  launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                  runCatching { context.startActivity(launch) }
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Launch")
            }
          }
          OutlinedButton(
            onClick = {
              val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", app.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              }
              runCatching { context.startActivity(intent) }
            },
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("App Settings")
          }
        }
      }
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
