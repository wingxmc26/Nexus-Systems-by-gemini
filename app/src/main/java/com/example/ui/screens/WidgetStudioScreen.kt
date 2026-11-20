package com.example.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.webkit.WebView
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.WidgetStudioViewModel
import com.example.widget.NexusCustomWidgetProvider

@Composable
fun WidgetStudioScreen(
  viewModel: WidgetStudioViewModel,
  pendingWidgetId: Int? = null,
  pendingProjectId: Long? = null,
  onPendingTargetConsumed: () -> Unit = {},
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var webViewRef by remember { mutableStateOf<WebView?>(null) }

  LaunchedEffect(pendingWidgetId, pendingProjectId) {
    if (pendingWidgetId != null || pendingProjectId != null) {
      viewModel.openWidgetTarget(pendingWidgetId, pendingProjectId)
      onPendingTargetConsumed()
    }
  }

  val importHtmlLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) viewModel.importHtmlFromUri(uri)
  }

  val exportHtmlLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("text/html"),
  ) { uri ->
    if (uri != null) viewModel.exportHtmlToUri(uri)
  }

  val pickVisualImageLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) viewModel.importVisualImageForSelection(uri)
  }

  val appWidgetMgr = remember(context) { runCatching { AppWidgetManager.getInstance(context) }.getOrNull() }
  val canPin = remember(appWidgetMgr) {
    runCatching { appWidgetMgr?.isRequestPinAppWidgetSupported == true }.getOrDefault(false)
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Widget Studio",
      subtitle = "Visual Builder & Sandboxed HTML/CSS/JS",
      onBack = onBack,
      trailingContent = {
        IconButton(onClick = { viewModel.refreshLiveValues() }) {
          Icon(Icons.Default.Refresh, contentDescription = "Refresh live telemetry", tint = colors.textPrimary)
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
        NexusGlassCard {
          OutlinedTextField(
            value = state.projectName,
            onValueChange = { viewModel.setProjectName(it) },
            label = { Text("Widget Project Name") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("widget_name_input"),
          )
          Spacer(modifier = Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = state.editorMode == "VISUAL",
              onClick = { viewModel.setEditorMode("VISUAL") },
              label = { Text("Visual Builder") },
              modifier = Modifier.testTag("widget_tab_visual"),
            )
            FilterChip(
              selected = state.editorMode == "HTML",
              onClick = { viewModel.setEditorMode("HTML") },
              label = { Text("HTML/CSS/JS") },
              modifier = Modifier.testTag("widget_tab_html"),
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            viewModel.templates.forEach { tpl ->
              FilterChip(
                selected = false,
                onClick = { viewModel.applyTemplate(tpl) },
                label = { Text("Template: ${tpl.title}") },
              )
            }
          }
        }
      }

      if (state.placedInstances.isNotEmpty()) {
        item {
          NexusGlassCard {
            SectionHeader(
              title = "Home-Screen Instance Target",
              trailingText = "${state.placedInstances.size} placed",
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              modifier = Modifier.horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              FilterChip(
                selected = state.targetAppWidgetId == null,
                onClick = { viewModel.setTargetAppWidgetId(null) },
                label = { Text("Default Active Widget") },
              )
              state.placedInstances.forEach { inst ->
                FilterChip(
                  selected = state.targetAppWidgetId == inst.appWidgetId,
                  onClick = { viewModel.openWidgetTarget(inst.appWidgetId, inst.projectId) },
                  label = { Text("Slot #${inst.appWidgetId} (${inst.widthDp}×${inst.heightDp}dp)") },
                )
              }
            }
          }
        }
      }

      item {
        if (state.editorMode == "VISUAL") {
          VisualWidgetEditorSection(
            spec = state.visualSpec,
            selectedElementId = state.selectedElementId,
            liveValues = state.liveValues,
            onSelectElement = viewModel::selectElement,
            onDragElement = viewModel::moveSelectedElement,
            onAddElement = viewModel::addElement,
            onUpdateElement = viewModel::updateElement,
            onReorderLayer = viewModel::reorderSelectedLayer,
            onPickImageElement = { pickVisualImageLauncher.launch(arrayOf("image/*")) },
            onRemoveSelected = viewModel::removeSelectedElement,
            onUpdateContainer = viewModel::updateVisualStyle,
            onSelectPreset = viewModel::selectWidgetSizePreset,
            onSetCustomSize = viewModel::setCustomDimensions,
            onSetPadding = viewModel::setContainerPadding,
            onSetRefreshInterval = viewModel::setRefreshIntervalMinutes,
          )
        } else {
          HtmlWidgetEditorSection(
            htmlCode = state.htmlCode,
            cssCode = state.cssCode,
            jsCode = state.jsCode,
            spec = state.visualSpec,
            importedAssetNames = state.importedAssetNames,
            onToggleTransparent = viewModel::setHtmlTransparent,
            onSelectPreset = viewModel::selectWidgetSizePreset,
            onSetCustomSize = viewModel::setCustomDimensions,
            onSetRefreshInterval = viewModel::setRefreshIntervalMinutes,
            onHtmlChange = viewModel::setHtmlCode,
            onCssChange = viewModel::setCssCode,
            onJsChange = viewModel::setJsCode,
            onImportHtml = { importHtmlLauncher.launch(arrayOf("text/html", "text/plain")) },
            onExportHtml = { exportHtmlLauncher.launch("${state.projectName.ifBlank { "nexus_widget" }}.html") },
            onImportAssetUri = { uri -> viewModel.importHtmlAssetFromUri(uri, insertTagIntoHtml = true) },
            onDeleteAsset = viewModel::deleteImportedAsset,
            onWebViewReady = { webViewRef = it },
          )
        }
      }

      item {
        NexusGlassCard {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = { viewModel.saveProject(activateOnHomeScreen = false) },
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Save Project")
            }
            Button(
              onClick = {
                val bmp = if (state.editorMode == "HTML") {
                  captureWebViewSnapshot(webViewRef, state.visualSpec.htmlTransparent)
                } else null
                viewModel.saveProject(activateOnHomeScreen = true, capturedWebBitmap = bmp)
              },
              colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
              modifier = Modifier
                .weight(1f)
                .testTag("activate_custom_widget_button"),
            ) {
              Icon(Icons.Default.DashboardCustomize, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                if (state.targetAppWidgetId != null) {
                  "Apply to Slot #${state.targetAppWidgetId}"
                } else {
                  "Set Active Widget"
                },
              )
            }
          }
          if (canPin) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
              onClick = {
                runCatching {
                  appWidgetMgr?.requestPinAppWidget(
                    ComponentName(context, NexusCustomWidgetProvider::class.java),
                    null,
                    null,
                  )
                }
              },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(Icons.Default.AddToHomeScreen, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Pin Custom Studio Widget to Home")
            }
          }
          state.statusMessage?.let { msg ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = colors.nominal)
          }
        }
      }

      if (state.projects.isNotEmpty()) {
        item {
          SectionHeader(title = "Saved Custom Widgets", trailingText = "${state.projects.size}")
        }
        items(state.projects, key = { it.id }) { proj ->
          NexusGlassCard(
            accentHighlight = proj.isActiveHomeWidget,
            onClick = { viewModel.loadProject(proj) },
          ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  proj.name,
                  style = MaterialTheme.typography.titleMedium,
                  color = colors.textPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text("${proj.kind} mode", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
              }
              if (proj.isActiveHomeWidget) {
                StatusPill(text = "Active", color = colors.nominal)
                Spacer(modifier = Modifier.width(6.dp))
              }
              IconButton(onClick = { viewModel.duplicateProject(proj) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = colors.textSecondary)
              }
              IconButton(onClick = { viewModel.deleteProject(proj.id) }) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = colors.critical)
              }
            }
          }
        }
      }
    }
  }
}
