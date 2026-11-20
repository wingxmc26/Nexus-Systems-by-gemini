package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DirectFileEntry
import com.example.ui.components.EmptyOrErrorState
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.DirectSortMode
import com.example.ui.viewmodel.StorageUiState
import com.example.ui.viewmodel.StorageViewModel
import com.example.util.Formatters

fun LazyListScope.directFileManagerSection(
  state: StorageUiState,
  viewModel: StorageViewModel,
  onGrantAllFilesAccess: () -> Unit,
  onRequestNewFolder: () -> Unit,
  onRequestRename: (DirectFileEntry) -> Unit,
  onRequestZipCompress: () -> Unit,
  onRequestDeletePaths: (List<String>) -> Unit,
) {
  if (!state.hasAllFilesAccess) {
    item {
      val colors = NexusThemeExtras.colors
      NexusGlassCard(accentHighlight = true, testTag = "all_files_access_card") {
        SectionHeader(title = "All Files Access Needed for Direct Browser", icon = Icons.Default.Lock)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "Grant All Files Access to browse device folders directly, rename, copy, move, create folders, extract/compress ZIP archives, and scan Recent & Large files offline. Android 11+ still restricts /Android/data and /Android/obb.",
          style = MaterialTheme.typography.bodyMedium,
          color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
          onClick = onGrantAllFilesAccess,
          colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
          modifier = Modifier.fillMaxWidth().testTag("grant_all_files_button"),
        ) {
          Text("Grant All Files Access")
        }
      }
    }
    return
  }

  item {
    val colors = NexusThemeExtras.colors
    NexusGlassCard {
      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(onClick = { viewModel.openDirectDirectory(viewModel.getRootStoragePath()) }) {
          Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Internal")
        }
        OutlinedButton(onClick = { viewModel.openDownloadsDirect() }) {
          Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Downloads")
        }
        OutlinedButton(onClick = { viewModel.openPicturesDirect() }) {
          Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Pictures")
        }
        OutlinedButton(onClick = onRequestNewFolder) {
          Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("New Folder")
        }
        OutlinedButton(onClick = { viewModel.scanDirectOverview() }) {
          Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Scan Insights")
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { viewModel.navigateDirectUp() }) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Parent folder", tint = colors.textPrimary)
        }
        Text(
          text = state.directCurrentPath.removePrefix(viewModel.getRootStoragePath()).ifBlank { "/Internal Storage" },
          style = MaterialTheme.typography.labelMedium,
          color = colors.accentSecondary,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
      }

      if (state.isProtectedDirectLocation) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "Android 11+ scoped storage restriction applies inside /Android/data and /Android/obb.",
          style = MaterialTheme.typography.labelSmall,
          color = colors.warning,
        )
      }

      Spacer(modifier = Modifier.height(8.dp))
      OutlinedTextField(
        value = state.directSearchQuery,
        onValueChange = viewModel::setDirectSearch,
        placeholder = { Text("Search current folder...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        DirectSortMode.entries.forEach { mode ->
          FilterChip(
            selected = state.directSortMode == mode,
            onClick = { viewModel.setDirectSort(mode) },
            label = { Text("Sort: ${mode.label}") },
          )
        }
      }
    }
  }

  if (state.clipboardPaths.isNotEmpty()) {
    item {
      val colors = NexusThemeExtras.colors
      NexusGlassCard(accentHighlight = true) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
          Text(
            text = "${state.clipboardPaths.size} item(s) ready to ${if (state.clipboardIsMove) "move" else "copy"}",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
              onClick = { viewModel.pasteClipboardHere() },
              colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
            ) {
              Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Paste")
            }
            TextButton(onClick = { viewModel.clearClipboard() }) { Text("Clear") }
          }
        }
      }
    }
  }

  if (state.selectedDirectPaths.isNotEmpty()) {
    item {
      val colors = NexusThemeExtras.colors
      val selected = state.selectedDirectPaths.toList()
      NexusGlassCard(accentHighlight = true) {
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          StatusPill(text = "${selected.size} selected", color = colors.accent)
          OutlinedButton(onClick = { viewModel.copyPathsToClipboard(selected) }) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Copy")
          }
          OutlinedButton(onClick = { viewModel.movePathsToClipboard(selected) }) {
            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Move")
          }
          OutlinedButton(onClick = onRequestZipCompress) {
            Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Zip")
          }
          OutlinedButton(onClick = { onRequestDeletePaths(selected) }) {
            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = colors.critical, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Delete", color = colors.critical)
          }
          TextButton(onClick = { viewModel.clearDirectSelection() }) { Text("Cancel") }
        }
      }
    }
  }

  if (state.isDirectScanning) {
    item {
      val colors = NexusThemeExtras.colors
      NexusGlassCard {
        Text("Scanning recent & large files across storage...", color = colors.textSecondary)
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = colors.accent)
      }
    }
  } else if (state.directLargeFiles.isNotEmpty() || state.directRecentFiles.isNotEmpty()) {
    item {
      val colors = NexusThemeExtras.colors
      NexusGlassCard {
        SectionHeader(
          title = "Quick Insights",
          trailingText = "${state.directLargeFiles.size} large (>=10MB)",
        )
        Spacer(modifier = Modifier.height(6.dp))
        state.directLargeFiles.take(5).forEach { f ->
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(f.name, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            Text(Formatters.formatBytes(f.sizeBytes), style = MaterialTheme.typography.labelSmall, color = colors.accentSecondary)
          }
        }
      }
    }
  }

  if (state.directError != null) {
    item {
      EmptyOrErrorState(
        icon = Icons.Default.Lock,
        title = "Directory Unavailable",
        description = state.directError,
      )
    }
  } else if (state.filteredDirectEntries.isEmpty()) {
    item {
      EmptyOrErrorState(
        icon = Icons.Default.Folder,
        title = "Folder Is Empty",
        description = "No files or subdirectories match the current view.",
      )
    }
  } else {
    items(state.filteredDirectEntries, key = { it.path }) { entry ->
      val colors = NexusThemeExtras.colors
      val isSelected = entry.path in state.selectedDirectPaths
      val calculatedSize = state.calculatedFolderSizes[entry.path]
      NexusGlassCard(
        accentHighlight = isSelected,
        onClick = {
          if (entry.isDirectory && !entry.isRestricted) {
            viewModel.openDirectDirectory(entry.path)
          } else {
            viewModel.toggleDirectSelection(entry.path)
          }
        },
      ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = { viewModel.toggleDirectSelection(entry.path) },
            modifier = Modifier.size(40.dp),
          ) {
            Icon(
              imageVector = if (isSelected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
              contentDescription = "Select ${entry.name}",
              tint = if (isSelected) colors.accent else colors.textSecondary,
            )
          }
          Icon(
            imageVector = when {
              entry.isRestricted -> Icons.Default.Lock
              entry.isDirectory -> Icons.Default.Folder
              entry.name.endsWith(".zip", true) -> Icons.Default.FolderZip
              else -> Icons.Default.InsertDriveFile
            },
            contentDescription = null,
            tint = if (entry.isDirectory) colors.accent else colors.textSecondary,
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = entry.name,
              style = MaterialTheme.typography.titleMedium,
              color = colors.textPrimary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            val subtitle = when {
              entry.isRestricted -> "OS Protected Folder"
              entry.isDirectory -> "${entry.childCount} items" + (calculatedSize?.let { " • ${Formatters.formatBytes(it)}" } ?: "")
              else -> "${entry.category.label} • ${Formatters.formatBytes(entry.sizeBytes)}"
            }
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
          }
          if (entry.isDirectory && !entry.isRestricted && calculatedSize == null) {
            TextButton(onClick = { viewModel.calculateFolderSize(entry.path) }) {
              Text("Size", color = colors.accentSecondary)
            }
          }
          if (!entry.isDirectory && entry.name.endsWith(".zip", true)) {
            IconButton(onClick = { viewModel.extractZip(entry.path) }) {
              Icon(Icons.Default.Unarchive, contentDescription = "Extract ZIP", tint = colors.accentSecondary)
            }
          }
          if (!entry.isDirectory) {
            IconButton(onClick = { viewModel.shareDirectFile(entry.path) }) {
              Icon(Icons.Default.Share, contentDescription = "Share ${entry.name}", tint = colors.textSecondary)
            }
          }
          IconButton(onClick = { onRequestRename(entry) }) {
            Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Rename ${entry.name}", tint = colors.textSecondary)
          }
        }
      }
    }
  }
}
