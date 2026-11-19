package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MetricValue
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
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var selectedTab by remember { mutableIntStateOf(0) } // 0 = Categories, 1 = Large Files, 2 = Browser

  val safLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
  ) { uri ->
    if (uri != null) {
      viewModel.analyzeSafTree(uri)
    }
  }

  BackHandler(enabled = selectedTab == 2 && state.currentBrowsePath.isNotEmpty()) {
    viewModel.navigateUp()
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Storage Analyzer",
      subtitle = "SCOPED SAF & VOLUME TELEMETRY",
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        state.overview?.let { ov ->
          val usedRatio = if (ov.internalTotalBytes > 0) {
            (ov.internalUsedBytes.toFloat() / ov.internalTotalBytes).coerceIn(0f, 1f)
          } else 0f

          item {
            NexusGlassCard(accentHighlight = true, testTag = "storage_overview_card") {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                TelemetryArcGauge(
                  progress = usedRatio,
                  centerValue = "${(usedRatio * 100).toInt()}%",
                  centerLabel = "USED",
                  size = 98.dp,
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
                Spacer(modifier = Modifier.height(10.dp))
                MetricKeyValueRow(
                  label = ov.externalLabel,
                  value = "${Formatters.formatBytes(ov.externalAvailableBytes.value)} free / ${Formatters.formatBytes(ov.externalTotalBytes.value)}",
                )
              }
            }
          }
        }

        item {
          NexusGlassCard(testTag = "storage_saf_card") {
            SectionHeader(title = "Folder & Large File Analyzer", icon = Icons.Default.FolderOpen)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Uses Android Storage Access Framework (SAF) to inspect any folder you authorize without broad storage permissions.",
              style = MaterialTheme.typography.bodyMedium,
              color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Button(
                onClick = { safLauncher.launch(null) },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                modifier = Modifier
                  .weight(1f)
                  .testTag("select_saf_folder_button"),
              ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (state.safAnalysis.treeUriString == null) "Select Folder to Analyze" else "Change Folder")
              }
              if (state.safAnalysis.isScanning) {
                OutlinedButton(onClick = { viewModel.cancelScan() }) {
                  Text("Stop")
                }
              }
            }

            if (state.safAnalysis.isScanning) {
              Spacer(modifier = Modifier.height(12.dp))
              LinearProgressIndicator(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(6.dp)
                  .clip(RoundedCornerShape(3.dp)),
                color = colors.accent,
                trackColor = colors.elevatedSurface,
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Scanning: ${state.safAnalysis.scannedCount} files (${Formatters.formatBytes(state.safAnalysis.totalScannedBytes)})",
                style = MaterialTheme.typography.labelMedium,
                color = colors.accentSecondary,
              )
            }
          }
        }

        val saf = state.safAnalysis
        if (saf.errorMessage != null) {
          item {
            EmptyOrErrorState(
              icon = Icons.Default.Storage,
              title = "Folder Scan Error",
              description = saf.errorMessage,
            )
          }
        } else if (!saf.isScanning && saf.treeUriString != null) {
          item {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                label = { Text("Categories") },
                modifier = Modifier.testTag("storage_tab_categories"),
              )
              FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                label = { Text("Large Files") },
                modifier = Modifier.testTag("storage_tab_large"),
              )
              FilterChip(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                label = { Text("Browser") },
                modifier = Modifier.testTag("storage_tab_browser"),
              )
            }
          }

          when (selectedTab) {
            0 -> {
              item {
                NexusGlassCard {
                  SectionHeader(
                    title = saf.rootName ?: "Folder Summary",
                    icon = Icons.Default.PieChart,
                    trailingText = "${saf.scannedCount} FILES",
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  MetricKeyValueRow("Total Folder Size", Formatters.formatBytes(saf.totalScannedBytes))
                }
              }
              items(saf.categories, key = { it.category.name }) { cat ->
                val share = if (saf.totalScannedBytes > 0) {
                  (cat.totalBytes.toFloat() / saf.totalScannedBytes).coerceIn(0f, 1f)
                } else 0f
                NexusGlassCard {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Text(cat.category.label, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text(
                      text = "${Formatters.formatBytes(cat.totalBytes)} (${cat.fileCount})",
                      style = NexusTextStyles.MonoMetricSmall,
                      color = colors.accentSecondary,
                    )
                  }
                  Spacer(modifier = Modifier.height(8.dp))
                  LinearProgressIndicator(
                    progress = { share },
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(6.dp)
                      .clip(RoundedCornerShape(3.dp)),
                    color = colors.accent,
                    trackColor = colors.elevatedSurface,
                    strokeCap = StrokeCap.Round,
                  )
                }
              }
            }
            1 -> {
              if (saf.largestFiles.isEmpty()) {
                item {
                  EmptyOrErrorState(
                    icon = Icons.Default.InsertDriveFile,
                    title = "No Files Found",
                    description = "The selected directory contains no readable files.",
                  )
                }
              } else {
                items(saf.largestFiles, key = { it.uriString }) { file ->
                  NexusGlassCard {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = file.name,
                          style = MaterialTheme.typography.titleMedium,
                          color = colors.textPrimary,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                          text = file.relativePath,
                          style = MaterialTheme.typography.bodyMedium,
                          color = colors.textSecondary,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                      }
                      Spacer(modifier = Modifier.width(8.dp))
                      StatusPill(text = Formatters.formatBytes(file.sizeBytes), color = colors.accent)
                    }
                  }
                }
              }
            }
            2 -> {
              item {
                NexusGlassCard {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
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
                item {
                  EmptyOrErrorState(
                    icon = Icons.Default.Folder,
                    title = "Empty Folder",
                    description = "No items inside this subfolder.",
                  )
                }
              } else {
                items(children, key = { it.uriString }) { item ->
                  NexusGlassCard(
                    onClick = if (item.isDirectory) {
                      { viewModel.openFolder(item.relativePath) }
                    } else null,
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Icon(
                        imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = if (item.isDirectory) colors.accent else colors.textSecondary,
                      )
                      Spacer(modifier = Modifier.width(12.dp))
                      Column(modifier = Modifier.weight(1f)) {
                        Text(
                          text = item.name,
                          style = MaterialTheme.typography.titleMedium,
                          color = colors.textPrimary,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                          text = if (item.isDirectory) "Directory" else item.category.label,
                          style = MaterialTheme.typography.labelSmall,
                          color = colors.textMuted,
                        )
                      }
                      if (!item.isDirectory) {
                        Text(
                          text = Formatters.formatBytes(item.sizeBytes),
                          style = NexusTextStyles.MonoMetricSmall,
                          color = colors.textPrimary,
                        )
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
