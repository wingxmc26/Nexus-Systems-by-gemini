package com.example.ui.screens

import android.app.WallpaperManager
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.view.TextureView
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.WallpaperMediaType
import com.example.ui.components.LoadingTelemetryState
import com.example.ui.components.MetricKeyValueRow
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.LiveWallpaperViewModel
import com.example.util.NexusBitmapUtils
import java.io.File
import java.util.Locale

@Composable
fun LiveWallpaperStudioScreen(
  viewModel: LiveWallpaperViewModel,
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  val mediaPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null) viewModel.importMedia(uri)
  }

  val getContentFallback = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent(),
  ) { uri ->
    if (uri != null) viewModel.importMedia(uri)
  }

  val gifOrFilePicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) viewModel.importMedia(uri)
  }

  val wallpaperLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
  ) {
    viewModel.refreshWallpaperStatus()
  }

  LaunchedEffect(Unit) {
    viewModel.refreshWallpaperStatus()
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Wallpaper Studio",
      subtitle = "Video, GIF & Photo Live Engine",
      onBack = onBack,
      trailingContent = {
        StatusPill(
          text = if (state.config.isCurrentlySetAsWallpaper) "Live Active" else "Ready",
          color = if (state.config.isCurrentlySetAsWallpaper) colors.nominal else colors.textSecondary,
        )
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
        NexusGlassCard(accentHighlight = true, testTag = "wallpaper_preview_card") {
          SectionHeader(
            title = "Live Preview (${state.config.mediaType.label})",
            icon = Icons.Default.Wallpaper,
          )
          Spacer(modifier = Modifier.height(10.dp))

          val localPath = state.activeLocalPath
          if (state.isImporting) {
            LoadingTelemetryState("Importing wallpaper media locally...")
          } else if (localPath != null && File(localPath).exists()) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 14f)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, colors.glassBorder, RoundedCornerShape(14.dp))
                .clipToBounds(),
            ) {
              when (state.config.mediaType) {
                WallpaperMediaType.VIDEO -> {
                  key(localPath, state.config.scaleCrop, state.config.loopPlayback) {
                    WallpaperVideoPreview(
                      videoPath = localPath,
                      muteAudio = state.config.muteAudio,
                      scaleCrop = state.config.scaleCrop,
                      loopPlayback = state.config.loopPlayback,
                      playbackSpeed = state.config.playbackSpeed,
                      modifier = Modifier.fillMaxSize(),
                    )
                  }
                }
                WallpaperMediaType.IMAGE -> {
                  val bmp = remember(localPath) {
                    runCatching {
                      NexusBitmapUtils.decodeSampledBitmap(localPath, 1080, 1680)?.asImageBitmap()
                    }.getOrNull()
                  }
                  if (bmp != null) {
                    Image(
                      bitmap = bmp,
                      contentDescription = "Wallpaper Image Preview",
                      contentScale = if (state.config.scaleCrop) ContentScale.Crop else ContentScale.Fit,
                      modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                          scaleX = state.config.zoomScale
                          scaleY = state.config.zoomScale
                          translationX = state.config.offsetXPercent * size.width
                          translationY = state.config.offsetYPercent * size.height
                        },
                    )
                  } else {
                    Text(
                      text = "Unable to decode selected photo preview.",
                      style = MaterialTheme.typography.bodyMedium,
                      color = colors.textSecondary,
                      modifier = Modifier.align(Alignment.Center),
                    )
                  }
                }
                WallpaperMediaType.GIF -> {
                  WallpaperGifPreview(
                    gifPath = localPath,
                    scaleCrop = state.config.scaleCrop,
                    zoomScale = state.config.zoomScale,
                    offsetXPercent = state.config.offsetXPercent,
                    offsetYPercent = state.config.offsetYPercent,
                    modifier = Modifier.fillMaxSize(),
                  )
                }
              }
            }
            Spacer(modifier = Modifier.height(10.dp))
            MetricKeyValueRow("Active Media", state.config.videoFileName ?: File(localPath).name)
          } else {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.elevatedSurface.copy(alpha = 0.5f))
                .border(1.dp, colors.glassBorder, RoundedCornerShape(12.dp)),
              contentAlignment = Alignment.Center,
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp),
              ) {
                Icon(Icons.Default.Collections, contentDescription = null, tint = colors.accent)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Select Video, Photo, or GIF", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text(
                  "Import local media via Android Photo Picker or file browser with zero extra storage permissions.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = colors.textSecondary,
                )
              }
            }
          }
        }
      }

      item {
        NexusGlassCard(testTag = "wallpaper_import_card") {
          SectionHeader(title = "Import Media", icon = Icons.Default.VideoLibrary)
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = {
                runCatching {
                  mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                }.onFailure {
                  runCatching { getContentFallback.launch("video/*") }.onFailure { err ->
                    viewModel.setStatusMessage("Unable to open video picker: ${err.message}")
                  }
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
              modifier = Modifier
                .weight(1f)
                .testTag("select_wallpaper_video_button"),
            ) {
              Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Video")
            }
            OutlinedButton(
              onClick = {
                runCatching {
                  mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }.onFailure {
                  runCatching { getContentFallback.launch("image/*") }.onFailure { err ->
                    viewModel.setStatusMessage("Unable to open photo picker: ${err.message}")
                  }
                }
              },
              modifier = Modifier
                .weight(1f)
                .testTag("select_wallpaper_image_button"),
            ) {
              Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Photo")
            }
            OutlinedButton(
              onClick = {
                runCatching {
                  gifOrFilePicker.launch(arrayOf("image/gif", "video/*", "image/*"))
                }.onFailure {
                  runCatching { getContentFallback.launch("*/*") }.onFailure { err ->
                    viewModel.setStatusMessage("Unable to open file browser: ${err.message}")
                  }
                }
              },
              modifier = Modifier
                .weight(1f)
                .testTag("select_wallpaper_gif_button"),
            ) {
              Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("GIF/File")
            }
          }

          state.statusMessage?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = colors.nominal)
          }
        }
      }

      item {
        NexusGlassCard(testTag = "wallpaper_controls_card") {
          SectionHeader(title = "Playback & Framing", icon = Icons.Default.Speed)
          Spacer(modifier = Modifier.height(8.dp))

          WallpaperSwitchRow(
            title = "Crop to Fill Screen",
            subtitle = "Scale cleanly across aspect ratios without letterboxing",
            checked = state.config.scaleCrop,
            onCheckedChange = { viewModel.setScaleCrop(it) },
            testTag = "wallpaper_scale_switch",
          )
          WallpaperSwitchRow(
            title = "Mute Audio",
            subtitle = "Keep Home/Lock screen wallpaper silent",
            checked = state.config.muteAudio,
            onCheckedChange = { viewModel.setMuteAudio(it) },
            testTag = "wallpaper_mute_switch",
          )
          WallpaperSwitchRow(
            title = "Loop Playback",
            subtitle = "Continuously loop Video & Animated GIF wallpapers",
            checked = state.config.loopPlayback,
            onCheckedChange = { viewModel.setLoopPlayback(it) },
          )
          WallpaperSwitchRow(
            title = "Pause on Battery Saver",
            subtitle = "Auto-pause animation when system Power Saver is enabled",
            checked = state.config.batterySaverPause,
            onCheckedChange = { viewModel.setBatterySaverPause(it) },
          )

          if (state.config.mediaType != WallpaperMediaType.IMAGE) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Playback Speed: ${String.format(Locale.US, "%.2fx", state.config.playbackSpeed)}",
              style = MaterialTheme.typography.labelLarge,
              color = colors.textPrimary,
            )
            Slider(
              value = state.config.playbackSpeed,
              onValueChange = { viewModel.setPlaybackSpeed(it) },
              valueRange = 0.5f..2.0f,
              steps = 5,
              colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
            )
          } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Zoom: ${String.format(Locale.US, "%.2fx", state.config.zoomScale)} • Pan (${(state.config.offsetXPercent * 100).toInt()}%, ${(state.config.offsetYPercent * 100).toInt()}%)",
              style = MaterialTheme.typography.labelLarge,
              color = colors.textPrimary,
            )
            Slider(
              value = state.config.zoomScale,
              onValueChange = { viewModel.setTransform(state.config.offsetXPercent, state.config.offsetYPercent, it) },
              valueRange = 1.0f..2.2f,
              colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
            )
            Slider(
              value = state.config.offsetXPercent,
              onValueChange = { viewModel.setTransform(it, state.config.offsetYPercent, state.config.zoomScale) },
              valueRange = -0.35f..0.35f,
              colors = SliderDefaults.colors(thumbColor = colors.accentSecondary, activeTrackColor = colors.accentSecondary),
            )
            Slider(
              value = state.config.offsetYPercent,
              onValueChange = { viewModel.setTransform(state.config.offsetXPercent, it, state.config.zoomScale) },
              valueRange = -0.35f..0.35f,
              colors = SliderDefaults.colors(thumbColor = colors.accentSecondary, activeTrackColor = colors.accentSecondary),
            )
          }

          Spacer(modifier = Modifier.height(10.dp))
          Button(
            onClick = {
              val candidates = viewModel.buildSetWallpaperIntents()
              var launched = false
              for (candidate in candidates) {
                val ok = runCatching {
                  wallpaperLauncher.launch(candidate)
                  true
                }.getOrDefault(false)
                if (ok) {
                  launched = true
                  break
                }
              }
              if (!launched) {
                viewModel.setStatusMessage(
                  "This device/launcher does not expose an external Live Wallpaper chooser. Try Home/Lock static photo mode for images."
                )
              }
            },
            enabled = state.config.hasLocalVideo,
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("set_live_wallpaper_button"),
          ) {
            Icon(Icons.Default.Wallpaper, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Set Live Wallpaper (Home / Lock Screen)")
          }

          if (state.config.mediaType == WallpaperMediaType.IMAGE && state.config.hasLocalVideo) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              "Or apply static image directly via WallpaperManager:",
              style = MaterialTheme.typography.labelMedium,
              color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedButton(
                onClick = { viewModel.applyStaticImage(WallpaperManager.FLAG_SYSTEM) },
                modifier = Modifier.weight(1f),
              ) { Text("Home") }
              OutlinedButton(
                onClick = { viewModel.applyStaticImage(WallpaperManager.FLAG_LOCK) },
                modifier = Modifier.weight(1f),
              ) { Text("Lock") }
              OutlinedButton(
                onClick = { viewModel.applyStaticImage(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK) },
                modifier = Modifier.weight(1f),
              ) { Text("Both") }
            }
          }
        }
      }

      if (state.library.isNotEmpty()) {
        item {
          SectionHeader(title = "Local Wallpaper Library", icon = Icons.Default.Collections, trailingText = "${state.library.size}")
        }
        items(state.library, key = { it.id }) { item ->
          val isSelected = item.id == state.config.activeWallpaperId
          NexusGlassCard(
            accentHighlight = isSelected,
            onClick = { viewModel.selectLibraryItem(item) },
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = item.title,
                  style = MaterialTheme.typography.titleMedium,
                  color = colors.textPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "${item.mediaType} • Tap to select",
                  style = MaterialTheme.typography.labelSmall,
                  color = colors.textSecondary,
                )
              }
              if (isSelected) {
                StatusPill(text = "Active", color = colors.nominal)
                Spacer(modifier = Modifier.width(6.dp))
              }
              IconButton(onClick = { viewModel.deleteLibraryItem(item) }) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete ${item.title}", tint = colors.critical)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun WallpaperSwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String? = null,
) {
  val colors = NexusThemeExtras.colors
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
      Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier,
    )
  }
}

@Composable
private fun WallpaperGifPreview(
  gifPath: String,
  scaleCrop: Boolean,
  zoomScale: Float,
  offsetXPercent: Float,
  offsetYPercent: Float,
  modifier: Modifier = Modifier,
) {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    key(gifPath, scaleCrop) {
      AndroidView(
        modifier = modifier.graphicsLayer {
          scaleX = zoomScale
          scaleY = zoomScale
          translationX = offsetXPercent * size.width
          translationY = offsetYPercent * size.height
        },
        factory = { ctx ->
          ImageView(ctx).apply {
            scaleType = if (scaleCrop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            runCatching {
              val src = ImageDecoder.createSource(File(gifPath))
              val drawable = ImageDecoder.decodeDrawable(src)
              setImageDrawable(drawable)
              if (drawable is AnimatedImageDrawable) drawable.start()
            }
          }
        },
      )
    }
  } else {
    AsyncImage(
      model = File(gifPath),
      contentDescription = "GIF Wallpaper Preview",
      contentScale = if (scaleCrop) ContentScale.Crop else ContentScale.Fit,
      modifier = modifier.graphicsLayer {
        scaleX = zoomScale
        scaleY = zoomScale
        translationX = offsetXPercent * size.width
        translationY = offsetYPercent * size.height
      },
    )
  }
}

@Composable
private fun WallpaperVideoPreview(
  videoPath: String,
  muteAudio: Boolean,
  scaleCrop: Boolean,
  loopPlayback: Boolean,
  playbackSpeed: Float,
  modifier: Modifier = Modifier,
) {
  val playerHolder = remember(videoPath, scaleCrop, loopPlayback) { arrayOfNulls<MediaPlayer>(1) }

  DisposableEffect(videoPath, scaleCrop, loopPlayback) {
    onDispose {
      playerHolder[0]?.let { mp ->
        runCatching { if (mp.isPlaying) mp.stop() }
        runCatching { mp.reset() }
        runCatching { mp.release() }
      }
      playerHolder[0] = null
    }
  }

  LaunchedEffect(muteAudio) {
    val vol = if (muteAudio) 0f else 1f
    runCatching { playerHolder[0]?.setVolume(vol, vol) }
  }

  LaunchedEffect(playbackSpeed) {
    runCatching {
      playerHolder[0]?.playbackParams = PlaybackParams().setSpeed(playbackSpeed.coerceIn(0.25f, 2f))
    }
  }

  AndroidView(
    modifier = modifier,
    factory = { ctx ->
      TextureView(ctx).apply {
        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
          override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
            val surface = android.view.Surface(st)
            runCatching {
              val mp = MediaPlayer().apply {
                setDataSource(videoPath)
                setSurface(surface)
                isLooping = loopPlayback
                setVideoScalingMode(
                  if (scaleCrop) MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                  else MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT
                )
                val vol = if (muteAudio) 0f else 1f
                setVolume(vol, vol)
                setOnPreparedListener {
                  runCatching {
                    if (playbackSpeed != 1f) it.playbackParams = PlaybackParams().setSpeed(playbackSpeed)
                  }
                  it.start()
                }
                prepareAsync()
              }
              playerHolder[0] = mp
            }
          }

          override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) = Unit

          override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
            playerHolder[0]?.let { mp ->
              runCatching { if (mp.isPlaying) mp.stop() }
              runCatching { mp.reset() }
              runCatching { mp.release() }
            }
            playerHolder[0] = null
            return true
          }

          override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) = Unit
        }
      }
    },
  )
}
