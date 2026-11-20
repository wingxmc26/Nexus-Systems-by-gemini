package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderDelete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DirectFileEntry
import com.example.data.model.MetricValue
import com.example.data.model.SafFileEntry
import com.example.ui.components.EmptyOrErrorState
import com.example.ui.components.LoadingTelemetryState
import com.example.ui.components.MetricKeyValueRow
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.TelemetryArcGauge
import com.example.ui.theme.NexusTextStyles
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.StorageViewModel
import com.example.util.Formatters

@Composable
fun StorageScreen(
  viewModel: StorageViewModel,
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  // 0 = SAF Analyzer & Cleanup (keeps legacy test tags visible immediately), 1 = Direct File Browser
  var storageMode by remember { mutableIntStateOf(0) }
  var selectedSafTab by remember { mutableIntStateOf(0) }
  var pendingDeleteEntry by remember { mutableStateOf<SafFileEntry?>(null) }
  var showNewFolderDialog by remember { mutableStateOf(false) }
  var newFolderName by remember { mutableStateOf("") }
  var renameTarget by remember { mutableStateOf<DirectFileEntry?>(null) }
  var renameText by remember { mutableStateOf("") }
  var showZipDialog by remember { mutableStateOf(false) }
  var zipArchiveName by remember { mutableStateOf("nexus_archive") }
  var pendingDeleteDirectPaths by remember { mutableStateOf<List<String>?>(null) }

  DisposableEffect(lifecycleOwner) {
    val obs = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshAllFilesPermission()
        viewModel.refreshOverview()
      }
    }
    lifecycleOwner.lifecycle.addObserver(obs)
    onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
  }

  val safLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
  ) { uri ->
    if (uri != null) {
      viewModel.analyzeSafTree(uri)
    }
  }

  BackHandler(enabled = (storageMode == 0 && selectedSafTab == 4 && state.currentBrowsePath.isNotEmpty()) || (storageMode == 1 && state.hasAllFilesAccess)) {
    if (storageMode == 0) {
      viewModel.navigateUp()
    } else if (!viewModel.navigateDirectUp()) {
      storageMode = 0
    }
  }

  pendingDeleteEntry?.let { entry ->
    AlertDialog(
      onDismissRequest = { pendingDeleteEntry = null },
      containerColor = colors.cardSurface,
      title = { Text(if (entry.isDirectory) "Delete Empty Folder?" else "Delete File?", color = colors.textPrimary) },
      text = {
        Text(
          text = "Permanently remove \"${entry.name}\" (${if (entry.isDirectory) "directory" else Formatters.formatBytes(entry.sizeBytes)})? This action cannot be undone.",
          color = colors.textSecondary,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteEntry(entry)
            pendingDeleteEntry = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = colors.critical),
          modifier = Modifier.testTag("confirm_delete_saf_button"),
        ) { Text("Delete") }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeleteEntry = null }) { Text("Cancel", color = colors.textSecondary) }
      },
    )
  }

  if (showNewFolderDialog) {
    AlertDialog(
      onDismissRequest = { showNewFolderDialog = false },
      containerColor = colors.cardSurface,
      title = { Text("Create Folder", color = colors.textPrimary) },
      text = {
        OutlinedTextField(value = newFolderName, onValueChange = { newFolderName = it }, label = { Text("Folder name") }, singleLine = true)
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.createFolder(newFolderName)
            newFolderName = ""
            showNewFolderDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
        ) { Text("Create") }
      },
      dismissButton = {
        TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") }
      },
    )
  }

  renameTarget?.let { target ->
    AlertDialog(
      onDismissRequest = { renameTarget = null },
      containerColor = colors.cardSurface,
      title = { Text("Rename Item", color = colors.textPrimary) },
      text = {
        OutlinedTextField(value = renameText, onValueChange = { renameText = it }, label = { Text("New name") }, singleLine = true)
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.renameDirectFile(target.path, renameText)
            renameTarget = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
        ) { Text("Rename") }
      },
      dismissButton = {
        TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
      },
    )
  }

  if (showZipDialog) {
    AlertDialog(
      onDismissRequest = { showZipDialog = false },
      containerColor = colors.cardSurface,
      title = { Text("Compress Selected to ZIP", color = colors.textPrimary) },
      text = {
        OutlinedTextField(value = zipArchiveName, onValueChange = { zipArchiveName = it }, label = { Text("Archive name (.zip)") }, singleLine = true)
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.compressSelectedToZip(zipArchiveName)
            showZipDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
        ) { Text("Compress") }
      },
      dismissButton = {
        TextButton(onClick = { showZipDialog = false }) { Text("Cancel") }
      },
    )
  }

  pendingDeleteDirectPaths?.let { paths ->
    AlertDialog(
      onDismissRequest = { pendingDeleteDirectPaths = null },
      containerColor = colors.cardSurface,
      title = { Text("Delete ${paths.size} Item(s)?", color = colors.textPrimary) },
      text = {
        Text("Selected files or folders will be permanently deleted from device storage.", color = colors.textSecondary)
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteDirectPaths(paths)
            pendingDeleteDirectPaths = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = colors.critical),
        ) { Text("Delete") }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeleteDirectPaths = null }) { Text("Cancel") }
      },
    )
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Storage Manager",
      subtitle = "Volumes, Direct File Browser, Archives & SAF Cleanup",
      onBack = onBack,
    )

    if (state.isLoadingOverview && state.overview == null) {
      LoadingTelemetryState("Reading storage volumes...")
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
        state.overview?.let { ov ->
          val usedRatio = if (ov.internalTotalBytes > 0) {
            (ov.internalUsedBytes.toFloat() / ov.internalTotalBytes).coerceIn(0f, 1f)
          } else 0f

          item {
            NexusGlassCard(accentHighlight = true, testTag = "storage_overview_card") {
              Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TelemetryArcGauge(
                  progress = usedRatio,
                  centerValue = "${(usedRatio * 100).toInt()}%",
                  centerLabel = "Used",
                  size = 94.dp,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                  SectionHeader(title = "Internal Storage", icon = Icons.Default.Storage)
                  MetricKeyValueRow("Total", Formatters.formatBytes(ov.internalTotalBytes))
                  MetricKeyValueRow("Used", Formatters.formatBytes(ov.internalUsedBytes))
                  MetricKeyValueRow("Available", Formatters.formatBytes(ov.internalAvailableBytes), colors.nominal)
                }
              }

              if (ov.externalLabel != null && ov.externalTotalBytes is MetricValue.Available &&
                ov.externalAvailableBytes is MetricValue.Available
              ) {
                Spacer(modifier = Modifier.height(8.dp))
                MetricKeyValueRow(
                  label = ov.externalLabel,
                  value = "${Formatters.formatBytes(ov.externalAvailableBytes.value)} free / ${Formatters.formatBytes(ov.externalTotalBytes.value)}",
                )
              }
            }
          }
        }

        state.statusMessage?.let { msg ->
          item {
            NexusGlassCard(accentHighlight = true) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text(msg, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                TextButton(onClick = { viewModel.clearMessage() }) { Text("Dismiss", color = colors.accent) }
              }
            }
          }
        }

        item {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = storageMode == 0,
              onClick = { storageMode = 0 },
              label = { Text("SAF Analyzer & Cleanup") },
              modifier = Modifier.testTag("storage_mode_saf"),
            )
            FilterChip(
              selected = storageMode == 1,
              onClick = { storageMode = 1 },
              label = { Text("Direct File Browser") },
              modifier = Modifier.testTag("storage_mode_direct"),
            )
          }
        }

        if (storageMode == 1) {
          directFileManagerSection(
            state = state,
            viewModel = viewModel,
            onGrantAllFilesAccess = { runCatching { context.startActivity(viewModel.buildAllFilesAccessIntent()) } },
            onRequestNewFolder = { showNewFolderDialog = true },
            onRequestRename = { entry ->
              renameTarget = entry
              renameText = entry.name
            },
            onRequestZipCompress = { showZipDialog = true },
            onRequestDeletePaths = { paths -> pendingDeleteDirectPaths = paths },
          )
        } else {
          item {
            NexusGlassCard(testTag = "storage_saf_card") {
              SectionHeader(title = "Downloads & Folder Analyzer", icon = Icons.Default.FolderOpen)
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Authorize Downloads or any folder using Android Storage Access Framework (SAF) to browse files, find large files, detect SHA-256 duplicates, locate empty folders, and safely delete unwanted items.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
              )
              Spacer(modifier = Modifier.height(12.dp))
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                  onClick = { safLauncher.launch(viewModel.buildDownloadsInitialUri()) },
                  colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                  modifier = Modifier.weight(1f).testTag("browse_downloads_button"),
                ) {
                  Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Downloads")
                }
                OutlinedButton(
                  onClick = { safLauncher.launch(null) },
                  modifier = Modifier.weight(1f).testTag("select_saf_folder_button"),
                ) {
                  Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(if (state.safAnalysis.treeUriString == null) "Pick Folder" else "Change")
                }
              }

              if (state.safAnalysis.isScanning) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                  modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                  color = colors.accent,
                  trackColor = colors.elevatedSurface,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = "Scanning: ${state.safAnalysis.scannedCount} files (${Formatters.formatBytes(state.safAnalysis.totalScannedBytes)})",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accentSecondary,
                  )
                  TextButton(onClick = { viewModel.cancelScan() }) { Text("Stop", color = colors.textSecondary) }
                }
              }
            }
          }

          val saf = state.safAnalysis
          if (saf.errorMessage != null) {
            item {
              EmptyOrErrorState(Icons.Default.Storage, "Folder Scan Error", saf.errorMessage)
            }
          } else if (!saf.isScanning && saf.treeUriString != null) {
            item {
              Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                FilterChip(selected = selectedSafTab == 0, onClick = { selectedSafTab = 0 }, label = { Text("Categories") }, modifier = Modifier.testTag("storage_tab_categories"))
                FilterChip(selected = selectedSafTab == 1, onClick = { selectedSafTab = 1 }, label = { Text("Large (${saf.largestFiles.size})") }, modifier = Modifier.testTag("storage_tab_large"))
                FilterChip(selected = selectedSafTab == 2, onClick = { selectedSafTab = 2 }, label = { Text("Duplicates (${saf.duplicateGroups.size})") }, modifier = Modifier.testTag("storage_tab_duplicates"))
                FilterChip(selected = selectedSafTab == 3, onClick = { selectedSafTab = 3 }, label = { Text("Empty Folders (${saf.emptyFolders.size})") }, modifier = Modifier.testTag("storage_tab_empty_folders"))
                FilterChip(selected = selectedSafTab == 4, onClick = { selectedSafTab = 4 }, label = { Text("Browser") }, modifier = Modifier.testTag("storage_tab_browser"))
              }
            }

            when (selectedSafTab) {
              0 -> {
                item {
                  NexusGlassCard {
                    SectionHeader(saf.rootName ?: "Folder Summary", Icons.Default.PieChart, "${saf.scannedCount} files")
                    Spacer(modifier = Modifier.height(6.dp))
                    MetricKeyValueRow("Total Folder Size", Formatters.formatBytes(saf.totalScannedBytes))
                    MetricKeyValueRow("Duplicate Sets", "${saf.duplicateGroups.size}")
                    MetricKeyValueRow("Empty Folders", "${saf.emptyFolders.size}")
                  }
                }
                items(saf.categories, key = { it.category.name }) { cat ->
                  val share = if (saf.totalScannedBytes > 0) (cat.totalBytes.toFloat() / saf.totalScannedBytes).coerceIn(0f, 1f) else 0f
                  NexusGlassCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                      Text(cat.category.label, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                      Text("${Formatters.formatBytes(cat.totalBytes)} (${cat.fileCount})", style = NexusTextStyles.MetricSmall, color = colors.accentSecondary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                      progress = { share },
                      modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                      color = colors.accent,
                      trackColor = colors.elevatedSurface,
                      strokeCap = StrokeCap.Round,
                    )
                  }
                }
              }
              1 -> {
                if (saf.largestFiles.isEmpty()) {
                  item { EmptyOrErrorState(Icons.Default.InsertDriveFile, "No Files Found", "The selected directory contains no readable files.") }
                } else {
                  items(saf.largestFiles, key = { it.uriString }) { file ->
                    SafFileRowCard(file = file, onDeleteRequest = { pendingDeleteEntry = file })
                  }
                }
              }
              2 -> {
                if (saf.duplicateGroups.isEmpty()) {
                  item { EmptyOrErrorState(Icons.Default.ContentCopy, "No Duplicates Detected", "All files up to 100 MB in this folder have unique SHA-256 content hashes.") }
                } else {
                  items(saf.duplicateGroups, key = { "${it.sha256}_${it.sizeBytes}" }) { group ->
                    NexusGlassCard(testTag = "duplicate_group_${group.sha256.take(8)}") {
                      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                          Text("${group.files.size} identical files • ${Formatters.formatBytes(group.sizeBytes)} each", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                          Text("SHA-256: ${group.sha256.take(16)}…", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                        }
                        StatusPill(text = Formatters.formatBytes(group.sizeBytes * (group.files.size - 1)), color = colors.warning)
                      }
                      Spacer(modifier = Modifier.height(8.dp))
                      group.files.forEach { dupFile ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                          Column(modifier = Modifier.weight(1f)) {
                            Text(dupFile.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(dupFile.relativePath, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                          }
                          IconButton(onClick = { pendingDeleteEntry = dupFile }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${dupFile.name}", tint = colors.critical)
                          }
                        }
                      }
                    }
                  }
                }
              }
              3 -> {
                if (saf.emptyFolders.isEmpty()) {
                  item { EmptyOrErrorState(Icons.Default.FolderDelete, "No Empty Folders", "Every scanned subdirectory contains at least one file or subfolder.") }
                } else {
                  items(saf.emptyFolders, key = { it.uriString }) { folder ->
                    NexusGlassCard {
                      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = colors.accent)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                          Text(folder.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                          Text(folder.relativePath, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { pendingDeleteEntry = folder }, modifier = Modifier.size(48.dp)) {
                          Icon(Icons.Default.DeleteOutline, contentDescription = "Delete empty folder ${folder.name}", tint = colors.critical)
                        }
                      }
                    }
                  }
                }
              }
              4 -> {
                item {
                  NexusGlassCard {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                      if (state.currentBrowsePath.isNotEmpty()) {
                        IconButton(onClick = { viewModel.navigateUp() }) {
                          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Parent folder", tint = colors.textPrimary)
                        }
                      }
                      Text(
                        text = "/${saf.rootName ?: "Root"}${if (state.currentBrowsePath.isNotEmpty()) "/${state.currentBrowsePath}" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accentSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }
                }
                val children = state.currentFolderChildren
                if (children.isEmpty()) {
                  item { EmptyOrErrorState(Icons.Default.Folder, "Empty Folder", "No items inside this subfolder.") }
                } else {
                  items(children, key = { it.uriString }) { item ->
                    NexusGlassCard(onClick = if (item.isDirectory) { { viewModel.openFolder(item.relativePath) } } else null) {
                      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, contentDescription = null, tint = if (item.isDirectory) colors.accent else colors.textSecondary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                          Text(item.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                          Text(if (item.isDirectory) "Directory" else "${item.category.label} • ${Formatters.formatBytes(item.sizeBytes)}", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                        }
                        IconButton(onClick = { pendingDeleteEntry = item }, modifier = Modifier.size(48.dp)) {
                          Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${item.name}", tint = colors.textSecondary)
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SafFileRowCard(
  file: SafFileEntry,
  onDeleteRequest: () -> Unit,
) {
  val colors = NexusThemeExtras.colors
  NexusGlassCard {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f)) {
        Text(file.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(file.relativePath, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      Spacer(modifier = Modifier.width(8.dp))
      StatusPill(text = Formatters.formatBytes(file.sizeBytes), color = colors.accent)
      IconButton(onClick = onDeleteRequest, modifier = Modifier.size(48.dp)) {
        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${file.name}", tint = colors.critical)
      }
    }
  }
}
