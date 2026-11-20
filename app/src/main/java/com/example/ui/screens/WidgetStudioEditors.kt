package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.VisualElementKind
import com.example.data.model.VisualWidgetElement
import com.example.data.model.VisualWidgetSpec
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.NexusThemeExtras
import com.example.util.NexusBitmapUtils
import com.example.widget.NexusWidgetJsBridge
import com.example.widget.WidgetSizePreset
import java.io.File
import kotlin.math.roundToInt

@Composable
fun VisualWidgetEditorSection(
  spec: VisualWidgetSpec,
  selectedElementId: String?,
  liveValues: Map<VisualElementKind, String>,
  onSelectElement: (String?) -> Unit,
  onDragElement: (Float, Float) -> Unit,
  onAddElement: (VisualElementKind) -> Unit,
  onUpdateElement: (VisualWidgetElement) -> Unit,
  onReorderLayer: (Boolean) -> Unit,
  onPickImageElement: () -> Unit,
  onRemoveSelected: () -> Unit,
  onUpdateContainer: ((VisualWidgetSpec) -> VisualWidgetSpec) -> Unit,
  onSelectPreset: (WidgetSizePreset) -> Unit,
  onSetCustomSize: (Int, Int) -> Unit,
  onSetPadding: (Int) -> Unit,
  onSetRefreshInterval: (Int) -> Unit,
) {
  val colors = NexusThemeExtras.colors
  val density = LocalDensity.current
  val aspectRatio = (spec.targetWidthDp.toFloat() / spec.targetHeightDp.coerceAtLeast(56).toFloat())
    .coerceIn(0.45f, 3.6f)

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    NexusGlassCard(accentHighlight = true) {
      SectionHeader(
        title = "WYSIWYG Canvas (${spec.targetWidthDp}×${spec.targetHeightDp} dp)",
        trailingText = "Drag & resize",
      )
      Spacer(modifier = Modifier.height(8.dp))

      // Exact proportional 1:1 preview synced with WidgetBitmapRenderer
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 96.dp, max = 360.dp),
        contentAlignment = Alignment.Center,
      ) {
        BoxWithConstraints(
          modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(spec.cornerRadiusDp.dp))
            .let { mod ->
              if (spec.isTransparent) mod
              else if (spec.gradientEndColorArgb != null) {
                mod.background(Brush.linearGradient(listOf(Color(spec.backgroundColorArgb), Color(spec.gradientEndColorArgb))))
              } else {
                mod.background(Color(spec.backgroundColorArgb))
              }
            }
            .border(
              if (spec.isTransparent) 1.dp else spec.borderWidthDp.dp,
              if (spec.isTransparent) colors.glassBorder else Color(spec.borderColorArgb),
              RoundedCornerShape(spec.cornerRadiusDp.dp),
            ),
        ) {
          val scaleRatio = (maxWidth.value / spec.targetWidthDp.coerceAtLeast(72).toFloat()).coerceIn(0.4f, 1.5f)
          val containerPadDp = (spec.containerPaddingDp * scaleRatio).dp
          BoxWithConstraints(
            modifier = Modifier
              .fillMaxSize()
              .padding(containerPadDp),
          ) {
            val canvasW = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val canvasH = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

            spec.elements.filter { it.visible }.sortedBy { it.layerOrder }.forEach { el ->
              val selected = el.id == selectedElementId
              val xPx = (el.xFraction * canvasW).roundToInt()
              val yPx = (el.yFraction * canvasH).roundToInt()
              val elemW = maxWidth * el.widthFraction
              val elemH = maxHeight * el.heightFraction
              val text = when (el.kind) {
                VisualElementKind.TEXT -> el.label.ifBlank { "Custom Text" }
                else -> {
                  val v = liveValues[el.kind] ?: el.kind.label
                  if (el.label.isNotBlank()) "${el.label}: $v" else v
                }
              }

              Box(
                modifier = Modifier
                  .offset { IntOffset(xPx, yPx) }
                  .size(width = elemW, height = elemH)
                  .clip(RoundedCornerShape((el.cornerRadiusDp * scaleRatio).dp))
                  .background(Color(el.backgroundColorArgb))
                  .then(
                    if (selected) Modifier.border(1.5.dp, colors.accent, RoundedCornerShape((el.cornerRadiusDp * scaleRatio).dp))
                    else Modifier
                  )
                  .pointerInput(el.id, canvasW, canvasH) {
                    detectDragGestures(
                      onDragStart = { onSelectElement(el.id) },
                      onDrag = { change, dragAmount ->
                        change.consume()
                        onDragElement(dragAmount.x / canvasW, dragAmount.y / canvasH)
                      },
                    )
                  }
                  .clickable { onSelectElement(el.id) }
                  .padding((el.paddingDp * scaleRatio).dp),
                contentAlignment = when (el.alignment) {
                  1 -> Alignment.Center
                  2 -> Alignment.CenterEnd
                  else -> Alignment.CenterStart
                },
              ) {
                if (el.kind == VisualElementKind.IMAGE) {
                  val bmp = remember(el.imageUriOrPath) {
                    el.imageUriOrPath?.takeIf { File(it).exists() }?.let {
                      NexusBitmapUtils.decodeSampledBitmap(it, 480, 480)
                    }
                  }
                  if (bmp != null) {
                    Image(
                      bitmap = bmp.asImageBitmap(),
                      contentDescription = "Widget Image Element",
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize(),
                    )
                  } else {
                    Text(
                      text = "Tap Pick Image",
                      color = Color(el.textColorArgb),
                      fontSize = 11.sp,
                    )
                  }
                } else {
                  Text(
                    text = text,
                    color = Color(el.textColorArgb),
                    fontSize = (el.fontSizeSp * scaleRatio).coerceAtLeast(9f).sp,
                    fontWeight = if (el.fontWeightBold) FontWeight.Bold else FontWeight.Normal,
                    textAlign = when (el.alignment) {
                      1 -> TextAlign.Center
                      2 -> TextAlign.End
                      else -> TextAlign.Start
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }
      }
    }

    NexusGlassCard {
      SectionHeader(title = "Widget Size & DP Scaling")
      Spacer(modifier = Modifier.height(6.dp))
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        WidgetSizePreset.entries.forEach { preset ->
          FilterChip(
            selected = spec.sizePresetName.equals(preset.name, ignoreCase = true),
            onClick = { onSelectPreset(preset) },
            label = { Text(preset.label) },
          )
        }
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        "Width: ${spec.targetWidthDp} dp • Height: ${spec.targetHeightDp} dp",
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
      )
      Slider(
        value = spec.targetWidthDp.toFloat(),
        onValueChange = { w -> onSetCustomSize(w.toInt(), spec.targetHeightDp) },
        valueRange = 120f..440f,
        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
      )
      Slider(
        value = spec.targetHeightDp.toFloat(),
        onValueChange = { h -> onSetCustomSize(spec.targetWidthDp, h.toInt()) },
        valueRange = 72f..440f,
        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
      )
      Text(
        "Container Padding: ${spec.containerPaddingDp} dp",
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
      )
      Slider(
        value = spec.containerPaddingDp.toFloat(),
        onValueChange = { onSetPadding(it.toInt()) },
        valueRange = 0f..32f,
        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
      )
      Text("Auto Refresh Interval", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        listOf(0 to "Manual", 1 to "1 min", 5 to "5 min", 15 to "15 min", 30 to "30 min").forEach { (mins, lbl) ->
          FilterChip(
            selected = spec.refreshIntervalMinutes == mins,
            onClick = { onSetRefreshInterval(mins) },
            label = { Text(lbl) },
          )
        }
      }
    }

    NexusGlassCard {
      SectionHeader(title = "Add Element")
      Spacer(modifier = Modifier.height(6.dp))
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        VisualElementKind.entries.forEach { kind ->
          FilterChip(
            selected = false,
            onClick = {
              if (kind == VisualElementKind.IMAGE) onPickImageElement() else onAddElement(kind)
            },
            leadingIcon = {
              Icon(
                if (kind == VisualElementKind.IMAGE) Icons.Default.Image else Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
            },
            label = { Text(kind.label) },
          )
        }
      }
    }

    val selected = spec.elements.firstOrNull { it.id == selectedElementId }
    if (selected != null) {
      NexusGlassCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SectionHeader(title = "Selected: ${selected.kind.label}")
          Row {
            IconButton(onClick = { onReorderLayer(false) }) {
              Icon(Icons.Default.ArrowDownward, contentDescription = "Send backward", tint = colors.textSecondary)
            }
            IconButton(onClick = { onReorderLayer(true) }) {
              Icon(Icons.Default.ArrowUpward, contentDescription = "Bring forward", tint = colors.textSecondary)
            }
            IconButton(onClick = onRemoveSelected) {
              Icon(Icons.Default.Delete, contentDescription = "Remove element", tint = colors.critical)
            }
          }
        }
        if (selected.kind == VisualElementKind.IMAGE) {
          OutlinedButton(onClick = onPickImageElement, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Choose / Replace Local Image")
          }
          Spacer(modifier = Modifier.height(6.dp))
        } else {
          OutlinedTextField(
            value = selected.label,
            onValueChange = { onUpdateElement(selected.copy(label = it)) },
            label = { Text(if (selected.kind == VisualElementKind.TEXT) "Display Text" else "Optional Prefix") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text("Text Size: ${selected.fontSizeSp.toInt()} sp", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
          Slider(
            value = selected.fontSizeSp,
            onValueChange = { onUpdateElement(selected.copy(fontSizeSp = it)) },
            valueRange = 10f..68f,
            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
          )
        }
        Text(
          "Element Bounds: ${(selected.widthFraction * 100).toInt()}% W × ${(selected.heightFraction * 100).toInt()}% H",
          style = MaterialTheme.typography.labelSmall,
          color = colors.textSecondary,
        )
        Slider(
          value = selected.widthFraction,
          onValueChange = { onUpdateElement(selected.copy(widthFraction = it)) },
          valueRange = 0.12f..1f,
          colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
        )
        Slider(
          value = selected.heightFraction,
          onValueChange = { onUpdateElement(selected.copy(heightFraction = it)) },
          valueRange = 0.08f..1f,
          colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf(0 to "Left", 1 to "Center", 2 to "Right").forEach { (align, name) ->
            FilterChip(
              selected = selected.alignment == align,
              onClick = { onUpdateElement(selected.copy(alignment = align)) },
              label = { Text(name) },
            )
          }
          FilterChip(
            selected = selected.fontWeightBold,
            onClick = { onUpdateElement(selected.copy(fontWeightBold = !selected.fontWeightBold)) },
            label = { Text("Bold") },
          )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          listOf(0xFFFFFFFFL, 0xFFE11D48L, 0xFF10B981L, 0xFF3B82F6L, 0xFFF59E0BL, 0xFF9CA3AFL).forEach { argb ->
            Box(
              modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(argb))
                .border(BorderStroke(if (selected.textColorArgb == argb) 2.dp else 1.dp, Color.White), CircleShape)
                .clickable { onUpdateElement(selected.copy(textColorArgb = argb)) },
            )
          }
        }
      }
    }

    NexusGlassCard {
      SectionHeader(title = "Container Styling")
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Transparent Background", color = colors.textPrimary)
        Switch(
          checked = spec.isTransparent,
          onCheckedChange = { v -> onUpdateContainer { it.copy(isTransparent = v) } },
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Subtle Glass Sheen", color = colors.textPrimary)
        Switch(
          checked = spec.isGlassStyle,
          onCheckedChange = { v -> onUpdateContainer { it.copy(isGlassStyle = v) } },
        )
      }
      Text("Corner Radius: ${spec.cornerRadiusDp} dp", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
      Slider(
        value = spec.cornerRadiusDp.toFloat(),
        onValueChange = { v -> onUpdateContainer { it.copy(cornerRadiusDp = v.toInt()) } },
        valueRange = 0f..36f,
        colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
      )
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HtmlWidgetEditorSection(
  htmlCode: String,
  cssCode: String,
  jsCode: String,
  spec: VisualWidgetSpec,
  importedAssetNames: List<String>,
  onToggleTransparent: (Boolean) -> Unit,
  onSelectPreset: (WidgetSizePreset) -> Unit,
  onSetCustomSize: (Int, Int) -> Unit,
  onSetRefreshInterval: (Int) -> Unit,
  onHtmlChange: (String) -> Unit,
  onCssChange: (String) -> Unit,
  onJsChange: (String) -> Unit,
  onImportHtml: () -> Unit,
  onExportHtml: () -> Unit,
  onImportAssetUri: (Uri) -> Unit,
  onDeleteAsset: (String) -> Unit,
  onWebViewReady: (WebView) -> Unit,
) {
  val colors = NexusThemeExtras.colors
  val context = LocalContext.current
  val assetsDir = remember(context) {
    File(context.filesDir, NexusWidgetJsBridge.ASSETS_DIR).apply { if (!exists()) mkdirs() }
  }
  var codeTab by remember { mutableIntStateOf(0) } // 0=HTML, 1=CSS, 2=JS
  var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

  val webFileChooserLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments(),
  ) { uris ->
    val cb = pendingFileCallback
    pendingFileCallback = null
    if (cb != null) {
      cb.onReceiveValue(uris.toTypedArray())
    }
  }

  val localMediaPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) onImportAssetUri(uri)
  }

  val isTransparent = spec.htmlTransparent
  val aspectRatio = (spec.targetWidthDp.toFloat() / spec.targetHeightDp.coerceAtLeast(56).toFloat())
    .coerceIn(0.45f, 3.6f)

  val transparentOverrideCss = if (isTransparent) {
    "\nhtml, body { background: transparent !important; background-color: transparent !important; margin: 0 !important; padding: 0 !important; }"
  } else {
    "\nhtml, body { margin: 0 !important; }"
  }

  val composedDoc = remember(htmlCode, cssCode, jsCode, isTransparent, spec.targetWidthDp, spec.targetHeightDp) {
    """<!DOCTYPE html>
<html>
<head><meta charset="utf-8"><meta name="viewport" content="width=${spec.targetWidthDp},initial-scale=1.0,maximum-scale=1.0,user-scalable=no">
<style>* { box-sizing: border-box; } $cssCode$transparentOverrideCss</style></head>
<body>$htmlCode
<script>$jsCode</script>
</body></html>"""
  }

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    NexusGlassCard(accentHighlight = true) {
      SectionHeader(
        title = "HTML/CSS/JS Preview (${spec.targetWidthDp}×${spec.targetHeightDp} dp)",
        trailingText = "Live WebView",
      )
      Spacer(modifier = Modifier.height(8.dp))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 110.dp, max = 360.dp),
        contentAlignment = Alignment.Center,
      ) {
        AndroidView(
          modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, colors.glassBorder, RoundedCornerShape(14.dp)),
          factory = { ctx ->
            WebView(ctx).apply {
              setBackgroundColor(if (isTransparent) android.graphics.Color.TRANSPARENT else android.graphics.Color.BLACK)
              isVerticalScrollBarEnabled = false
              isHorizontalScrollBarEnabled = false
              settings.javaScriptEnabled = true
              settings.domStorageEnabled = true
              settings.allowFileAccess = true
              settings.allowContentAccess = true
              settings.useWideViewPort = true
              settings.loadWithOverviewMode = true
              settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
              val bridge = NexusWidgetJsBridge(ctx, spec.targetWidthDp, spec.targetHeightDp)
              addJavascriptInterface(bridge, "NexusBridge")
              addJavascriptInterface(bridge, "NexusWidget")
              webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                  webView: WebView?,
                  filePathCallback: ValueCallback<Array<Uri>>?,
                  fileChooserParams: FileChooserParams?,
                ): Boolean {
                  pendingFileCallback?.onReceiveValue(null)
                  pendingFileCallback = filePathCallback
                  val mimeTypes = fileChooserParams?.acceptTypes
                    ?.filter { !it.isNullOrBlank() }
                    ?.toTypedArray()
                    ?.takeIf { it.isNotEmpty() }
                    ?: arrayOf("image/*", "audio/*", "text/html")
                  webFileChooserLauncher.launch(mimeTypes)
                  return true
                }
              }
              webViewClient = object : WebViewClient() {}
              onWebViewReady(this)
            }
          },
          update = { webView ->
            onWebViewReady(webView)
            webView.setBackgroundColor(if (isTransparent) android.graphics.Color.TRANSPARENT else android.graphics.Color.BLACK)
            if (webView.tag != composedDoc) {
              webView.tag = composedDoc
              webView.loadDataWithBaseURL(
                "file://${assetsDir.absolutePath}/",
                composedDoc,
                "text/html",
                "UTF-8",
                null,
              )
            }
          },
        )
      }
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("Transparent Outer Background", color = colors.textPrimary, style = MaterialTheme.typography.titleSmall)
          Text(
            "Removes dark outer box & enables glass HTML cards",
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
          )
        }
        Switch(
          checked = isTransparent,
          onCheckedChange = onToggleTransparent,
        )
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        "JS APIs: NexusBridge (getBatteryJson/getClockJson...) & NexusWidget (getAllTelemetryJson(), getAssetsJson(), getWidgetMetaJson()).",
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
      )
    }

    NexusGlassCard {
      SectionHeader(title = "HTML Slot Size & Refresh")
      Spacer(modifier = Modifier.height(6.dp))
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        WidgetSizePreset.entries.forEach { preset ->
          FilterChip(
            selected = spec.sizePresetName.equals(preset.name, ignoreCase = true),
            onClick = { onSelectPreset(preset) },
            label = { Text(preset.label) },
          )
        }
      }
      Text(
        "Viewport: ${spec.targetWidthDp} dp × ${spec.targetHeightDp} dp",
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
      )
      Slider(
        value = spec.targetWidthDp.toFloat(),
        onValueChange = { onSetCustomSize(it.toInt(), spec.targetHeightDp) },
        valueRange = 120f..440f,
      )
      Slider(
        value = spec.targetHeightDp.toFloat(),
        onValueChange = { onSetCustomSize(spec.targetWidthDp, it.toInt()) },
        valueRange = 72f..440f,
      )
      Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        listOf(0 to "Manual", 1 to "1 min", 5 to "5 min", 15 to "15 min").forEach { (mins, lbl) ->
          FilterChip(
            selected = spec.refreshIntervalMinutes == mins,
            onClick = { onSetRefreshInterval(mins) },
            label = { Text(lbl) },
          )
        }
      }
    }

    NexusGlassCard {
      SectionHeader(title = "Local Images & Audio Assets")
      Spacer(modifier = Modifier.height(6.dp))
      OutlinedButton(
        onClick = { localMediaPickerLauncher.launch(arrayOf("image/*", "audio/*")) },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Import Image or Audio into Widget")
      }
      if (importedAssetNames.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          importedAssetNames.forEach { name ->
            FilterChip(
              selected = false,
              onClick = { onDeleteAsset(name) },
              label = { Text("$name ✕") },
            )
          }
        }
      }
    }

    NexusGlassCard {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(selected = codeTab == 0, onClick = { codeTab = 0 }, label = { Text("HTML") })
        FilterChip(selected = codeTab == 1, onClick = { codeTab = 1 }, label = { Text("CSS") })
        FilterChip(selected = codeTab == 2, onClick = { codeTab = 2 }, label = { Text("JavaScript") })
      }
      Spacer(modifier = Modifier.height(6.dp))
      when (codeTab) {
        0 -> OutlinedTextField(
          value = htmlCode,
          onValueChange = onHtmlChange,
          label = { Text("HTML Markup") },
          minLines = 6,
          maxLines = 10,
          modifier = Modifier.fillMaxWidth(),
        )
        1 -> OutlinedTextField(
          value = cssCode,
          onValueChange = onCssChange,
          label = { Text("CSS Stylesheet") },
          minLines = 6,
          maxLines = 10,
          modifier = Modifier.fillMaxWidth(),
        )
        else -> OutlinedTextField(
          value = jsCode,
          onValueChange = onJsChange,
          label = { Text("Widget Script") },
          minLines = 6,
          maxLines = 10,
          modifier = Modifier.fillMaxWidth(),
        )
      }
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(onClick = onImportHtml, modifier = Modifier.weight(1f)) {
          Text("Import .html")
        }
        Button(
          onClick = onExportHtml,
          colors = ButtonDefaults.buttonColors(containerColor = colors.elevatedSurface),
          modifier = Modifier.weight(1f),
        ) {
          Text("Export .html")
        }
      }
    }
  }
}

fun captureWebViewSnapshot(webView: WebView?, transparent: Boolean = true): Bitmap? {
  val wv = webView ?: return null
  if (wv.width <= 0 || wv.height <= 0) return null
  return runCatching {
    val bmp = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    if (!transparent) {
      c.drawColor(android.graphics.Color.BLACK)
    }
    wv.draw(c)
    bmp
  }.getOrNull()
}
