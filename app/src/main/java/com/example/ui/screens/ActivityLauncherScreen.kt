package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.FavoriteActivityEntity
import com.example.data.model.ExportedActivityInfo
import com.example.data.model.LaunchablePackageInfo
import com.example.data.model.ShortcutShape
import com.example.ui.components.EmptyOrErrorState
import com.example.ui.components.LoadingTelemetryState
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.ActivityLauncherViewModel
import com.example.ui.viewmodel.LauncherFilterMode
import java.io.File
import kotlinx.coroutines.launch

private data class ShortcutEditorTarget(
  val editId: Long? = null,
  val packageName: String,
  val activityClassName: String?,
  val defaultLabel: String,
  val originalAppName: String,
  val initialIconKey: String = "app_icon",
  val initialColorArgb: Long = 0xFFE11D48L,
  val initialShape: ShortcutShape = ShortcutShape.ROUNDED,
  val initialCustomIconPath: String? = null,
  val initialZoom: Float = 1.0f,
)

@Composable
fun ActivityLauncherScreen(
  viewModel: ActivityLauncherViewModel,
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val scope = rememberCoroutineScope()
  var editorTarget by remember { mutableStateOf<ShortcutEditorTarget?>(null) }
  var manualPackageQuery by remember { mutableStateOf("") }

  editorTarget?.let { target ->
    ShortcutEditorDialog(
      target = target,
      isPinSupported = state.isPinSupported,
      onUploadCustomIcon = { uri -> viewModel.saveUploadedIcon(uri) },
      onDismiss = { editorTarget = null },
      onSaveShortcut = { label, iconKey, colorArgb, shape, customPath, zoom ->
        viewModel.saveShortcut(
          editId = target.editId,
          packageName = target.packageName,
          activityClassName = target.activityClassName,
          customLabel = label,
          originalAppName = target.originalAppName,
          iconKey = iconKey,
          accentColorArgb = colorArgb,
          shape = shape,
          customIconPath = customPath,
          iconZoom = zoom,
        )
        editorTarget = null
      },
      onPinToHome = { label, iconKey, colorArgb, shape, customPath, zoom ->
        viewModel.pinHomeScreenShortcut(
          packageName = target.packageName,
          activityClassName = target.activityClassName,
          customLabel = label,
          accentColorArgb = colorArgb,
          iconKey = iconKey,
          shape = shape,
          customIconPath = customPath,
          iconZoom = zoom,
        )
        editorTarget = null
      },
    )
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Shortcut Studio",
      subtitle = "Custom Icons, Shapes, Activities & Pinning",
      onBack = onBack,
      trailingContent = {
        IconButton(onClick = { viewModel.reload() }) {
          Icon(Icons.Default.Refresh, contentDescription = "Reload Packages", tint = colors.textPrimary)
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
        placeholder = { Text("Filter apps, packages, or activities...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("launcher_search_input"),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = manualPackageQuery,
          onValueChange = { manualPackageQuery = it },
          placeholder = { Text("Inspect exact package (e.g. com.android.settings)") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.weight(1f),
        )
        Button(
          onClick = { viewModel.inspectPackage(manualPackageQuery) },
          colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
        ) {
          Text("Inspect")
        }
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        LauncherFilterMode.entries.forEach { mode ->
          FilterChip(
            selected = state.filterMode == mode,
            onClick = { viewModel.setFilterMode(mode) },
            label = { Text(mode.label) },
          )
        }
      }

      state.statusMessage?.let { msg ->
        NexusGlassCard(onClick = { viewModel.clearMessage() }) {
          Text(msg, style = MaterialTheme.typography.bodyMedium, color = colors.accentSecondary)
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    when {
      state.isLoading -> LoadingTelemetryState("Inspecting visible packages & exported activities...")
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
          state.manuallyInspectedPackage?.let { inspected ->
            item(key = "inspected_${inspected.packageName}") {
              SectionHeader(title = "Manual Package Lookup", trailingText = inspected.packageName)
              Spacer(modifier = Modifier.height(6.dp))
              PackageLauncherCard(
                pkg = inspected,
                onLaunchApp = { viewModel.launchTarget(inspected.packageName, null) },
                onLaunchActivity = { act -> viewModel.launchTarget(inspected.packageName, act.className) },
                onCustomizeShortcut = { act ->
                  editorTarget = ShortcutEditorTarget(
                    packageName = inspected.packageName,
                    activityClassName = act?.className,
                    defaultLabel = act?.label ?: inspected.appName,
                    originalAppName = inspected.appName,
                  )
                },
              )
            }
          }

          if (state.favorites.isNotEmpty() && state.filterMode == LauncherFilterMode.FAVORITES) {
            item {
              SectionHeader(title = "Saved Custom Shortcuts", icon = Icons.Default.Star, trailingText = "${state.favorites.size}")
            }
            items(state.favorites, key = { "fav_${it.id}" }) { fav ->
              SavedShortcutCard(
                fav = fav,
                isPinSupported = state.isPinSupported,
                onLaunch = { viewModel.launchTarget(fav.packageName, fav.activityClassName) },
                onEdit = {
                  editorTarget = ShortcutEditorTarget(
                    editId = fav.id,
                    packageName = fav.packageName,
                    activityClassName = fav.activityClassName,
                    defaultLabel = fav.customLabel,
                    originalAppName = fav.originalAppName,
                    initialIconKey = fav.iconKey,
                    initialColorArgb = fav.accentColorArgb,
                    initialShape = ShortcutShape.fromId(fav.iconShape),
                    initialCustomIconPath = fav.customIconPath,
                    initialZoom = fav.iconZoom,
                  )
                },
                onPin = {
                  scope.launch {
                    viewModel.pinHomeScreenShortcut(
                      packageName = fav.packageName,
                      activityClassName = fav.activityClassName,
                      customLabel = fav.customLabel,
                      accentColorArgb = fav.accentColorArgb,
                      iconKey = fav.iconKey,
                      shape = ShortcutShape.fromId(fav.iconShape),
                      customIconPath = fav.customIconPath,
                      iconZoom = fav.iconZoom,
                    )
                  }
                },
                onDelete = { viewModel.removeFavorite(fav.id) },
              )
            }
          }

          if (state.packages.isEmpty() && state.filterMode != LauncherFilterMode.FAVORITES) {
            item {
              EmptyOrErrorState(
                icon = Icons.Default.Apps,
                title = "No Matching Packages",
                description = "Only packages permitted by Android package visibility rules or manual lookup are shown.",
              )
            }
          } else if (state.filterMode != LauncherFilterMode.FAVORITES) {
            items(state.packages, key = { it.packageName }) { pkg ->
              PackageLauncherCard(
                pkg = pkg,
                onLaunchApp = { viewModel.launchTarget(pkg.packageName, null) },
                onLaunchActivity = { act -> viewModel.launchTarget(pkg.packageName, act.className) },
                onCustomizeShortcut = { act ->
                  editorTarget = ShortcutEditorTarget(
                    packageName = pkg.packageName,
                    activityClassName = act?.className,
                    defaultLabel = act?.label ?: pkg.appName,
                    originalAppName = pkg.appName,
                  )
                },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SavedShortcutCard(
  fav: FavoriteActivityEntity,
  isPinSupported: Boolean,
  onLaunch: () -> Unit,
  onEdit: () -> Unit,
  onPin: () -> Unit,
  onDelete: () -> Unit,
) {
  val colors = NexusThemeExtras.colors
  val customBmp = remember(fav.customIconPath) {
    fav.customIconPath?.takeIf { File(it).exists() }?.let {
      runCatching { com.example.util.NexusBitmapUtils.decodeSampledBitmap(it, 256, 256)?.asImageBitmap() }.getOrNull()
    }
  }
  val shape = when (ShortcutShape.fromId(fav.iconShape)) {
    ShortcutShape.CIRCLE -> CircleShape
    ShortcutShape.SQUIRCLE -> RoundedCornerShape(16.dp)
    ShortcutShape.ROUNDED -> RoundedCornerShape(10.dp)
    ShortcutShape.SQUARE -> RoundedCornerShape(3.dp)
  }

  NexusGlassCard(onClick = onLaunch, testTag = "saved_shortcut_${fav.id}") {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(shape)
          .background(Color(fav.accentColorArgb).copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
      ) {
        if (customBmp != null) {
          Image(
            bitmap = customBmp,
            contentDescription = fav.customLabel,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .fillMaxSize()
              .graphicsLayer(scaleX = fav.iconZoom, scaleY = fav.iconZoom),
          )
        } else {
          Icon(Icons.Default.Launch, contentDescription = null, tint = Color(fav.accentColorArgb))
        }
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(fav.customLabel, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Text(
          "${fav.originalAppName} • ${ShortcutShape.fromId(fav.iconShape).label}",
          style = MaterialTheme.typography.labelSmall,
          color = colors.textSecondary,
        )
      }
      if (isPinSupported) {
        IconButton(onClick = onPin) {
          Icon(Icons.Default.AddToHomeScreen, contentDescription = "Pin to Home", tint = colors.accent)
        }
      }
      IconButton(onClick = onEdit) {
        Icon(Icons.Default.Edit, contentDescription = "Edit Shortcut", tint = colors.textPrimary)
      }
      IconButton(onClick = onDelete) {
        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Shortcut", tint = colors.critical)
      }
    }
  }
}

@Composable
private fun PackageLauncherCard(
  pkg: LaunchablePackageInfo,
  onLaunchApp: () -> Unit,
  onLaunchActivity: (ExportedActivityInfo) -> Unit,
  onCustomizeShortcut: (ExportedActivityInfo?) -> Unit,
) {
  val colors = NexusThemeExtras.colors
  var expanded by remember(pkg.packageName) { mutableStateOf(false) }
  val iconBitmap = remember(pkg.packageName) {
    pkg.icon?.let { drawableToBitmap(it)?.asImageBitmap() }
  }

  NexusGlassCard(
    onClick = { expanded = !expanded },
    testTag = "launcher_pkg_${pkg.packageName}",
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (iconBitmap != null) {
        Image(
          bitmap = iconBitmap,
          contentDescription = "${pkg.appName} icon",
          modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(10.dp)),
        )
      } else {
        Icon(Icons.Default.Apps, contentDescription = null, tint = colors.accent, modifier = Modifier.size(42.dp))
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = pkg.appName,
          style = MaterialTheme.typography.titleMedium,
          color = colors.textPrimary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = pkg.packageName,
          style = MaterialTheme.typography.labelSmall,
          color = colors.textSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(onClick = { onCustomizeShortcut(null) }) {
        Icon(Icons.Default.AddToHomeScreen, contentDescription = "Create shortcut", tint = colors.accent)
      }
      Icon(
        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        contentDescription = if (expanded) "Collapse" else "Expand activities",
        tint = colors.textSecondary,
      )
    }

    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      StatusPill(text = "${pkg.exportedActivities.size} exported / ${pkg.totalActivityCount} total", color = colors.info)
      StatusPill(text = if (pkg.isSystemApp) "System" else "User", color = colors.textSecondary)
    }

    AnimatedVisibility(visible = expanded) {
      Column(modifier = Modifier.padding(top = 12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (pkg.hasLauncherIntent) {
            Button(
              onClick = onLaunchApp,
              colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Launch App")
            }
          }
          OutlinedButton(
            onClick = { onCustomizeShortcut(null) },
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Default.AddToHomeScreen, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Create Shortcut")
          }
        }

        if (pkg.exportedActivities.isNotEmpty()) {
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "Exported Activities",
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
          )
          Spacer(modifier = Modifier.height(6.dp))
          pkg.exportedActivities.take(30).forEach { act ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = act.simpleName,
                  style = MaterialTheme.typography.titleMedium,
                  color = colors.textPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = act.permissionRequired?.let { "Requires: ${it.substringAfterLast('.')}" } ?: act.className,
                  style = MaterialTheme.typography.labelSmall,
                  color = if (act.permissionRequired != null) colors.warning else colors.textMuted,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              IconButton(onClick = { onCustomizeShortcut(act) }) {
                Icon(Icons.Default.AddToHomeScreen, contentDescription = "Shortcut for ${act.simpleName}", tint = colors.textSecondary)
              }
              IconButton(onClick = { onLaunchActivity(act) }) {
                Icon(Icons.Default.Launch, contentDescription = "Launch ${act.simpleName}", tint = colors.accent)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ShortcutEditorDialog(
  target: ShortcutEditorTarget,
  isPinSupported: Boolean,
  onUploadCustomIcon: suspend (android.net.Uri) -> String?,
  onDismiss: () -> Unit,
  onSaveShortcut: (String, String, Long, ShortcutShape, String?, Float) -> Unit,
  onPinToHome: (String, String, Long, ShortcutShape, String?, Float) -> Unit,
) {
  val colors = NexusThemeExtras.colors
  val scope = rememberCoroutineScope()
  var label by remember(target) { mutableStateOf(target.defaultLabel) }
  var selectedIconKey by remember(target) { mutableStateOf(target.initialIconKey) }
  var selectedColor by remember(target) { mutableStateOf(target.initialColorArgb) }
  var selectedShape by remember(target) { mutableStateOf(target.initialShape) }
  var customIconPath by remember(target) { mutableStateOf(target.initialCustomIconPath) }
  var iconZoom by remember(target) { mutableFloatStateOf(target.initialZoom) }

  val iconPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null) {
      scope.launch {
        val savedPath = onUploadCustomIcon(uri)
        if (savedPath != null) {
          customIconPath = savedPath
          selectedIconKey = "custom_image"
        }
      }
    }
  }

  val iconKeys = listOf(
    "app_icon" to "App Icon",
    "bolt" to "Bolt",
    "star" to "Star",
    "terminal" to "Action",
    "settings" to "Gear",
  )
  val paletteColors = listOf(0xFFE11D48L, 0xFF3B82F6L, 0xFF10B981L, 0xFFF59E0BL, 0xFFFFFFFFL)

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.cardSurface,
    title = { Text(if (target.editId != null) "Edit Shortcut" else "Shortcut Studio", color = colors.textPrimary) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = label,
          onValueChange = { label = it },
          label = { Text("Shortcut Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )

        OutlinedButton(
          onClick = {
            iconPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
          },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(if (customIconPath != null) "Replace Custom Icon Image" else "Upload Custom Icon Image")
        }

        if (customIconPath != null) {
          Text("Custom Image Zoom (${String.format(java.util.Locale.US, "%.2fx", iconZoom)})", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
          Slider(
            value = iconZoom,
            onValueChange = { iconZoom = it },
            valueRange = 0.8f..1.8f,
            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
          )
        }

        Text("Icon Shape", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          ShortcutShape.entries.forEach { shape ->
            FilterChip(
              selected = selectedShape == shape,
              onClick = { selectedShape = shape },
              label = { Text(shape.label) },
            )
          }
        }

        Text("Built-in Style", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          iconKeys.forEach { (key, title) ->
            FilterChip(
              selected = selectedIconKey == key && customIconPath == null,
              onClick = {
                selectedIconKey = key
                customIconPath = null
              },
              label = { Text(title) },
            )
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          paletteColors.forEach { argb ->
            Box(
              modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(argb))
                .border(
                  BorderStroke(if (selectedColor == argb) 2.5.dp else 1.dp, Color.White.copy(alpha = 0.6f)),
                  CircleShape,
                )
                .clickable { selectedColor = argb },
            )
          }
        }
      }
    },
    confirmButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isPinSupported) {
          TextButton(
            onClick = {
              onPinToHome(label, selectedIconKey, selectedColor, selectedShape, customIconPath, iconZoom)
            },
          ) { Text("Pin to Home") }
        }
        Button(
          onClick = {
            onSaveShortcut(label, selectedIconKey, selectedColor, selectedShape, customIconPath, iconZoom)
          },
          colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
        ) { Text("Save") }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
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
