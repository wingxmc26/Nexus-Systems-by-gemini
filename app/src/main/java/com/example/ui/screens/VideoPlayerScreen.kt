package com.example.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.VideoAspectMode
import com.example.data.model.VideoDecoderMode
import com.example.di.NexusContainer
import com.example.ui.components.EmptyOrErrorState
import com.example.ui.components.LoadingTelemetryState
import com.example.ui.components.MetricKeyValueRow
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.VideoPlayerViewModel
import com.example.util.Formatters

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
  viewModel: VideoPlayerViewModel,
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
  isInPipMode: Boolean = false,
  onActiveVideoForPipChanged: (Boolean) -> Unit = {},
  onEnterPipRequest: (() -> Unit)? = null,
) {
  val colors = NexusThemeExtras.colors
  val context = LocalContext.current
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  val permLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { viewModel.onPermissionUpdated() }

  val videoPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri -> if (uri != null) viewModel.openVideoUri(uri) }

  val subPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri -> if (uri != null) viewModel.attachExternalSubtitle(uri) }

  val activeVideo = state.activeVideo
  DisposableEffect(activeVideo != null) {
    onActiveVideoForPipChanged(activeVideo != null)
    onDispose {
      onActiveVideoForPipChanged(false)
    }
  }

  BackHandler(enabled = activeVideo != null && !isInPipMode) {
    viewModel.closePlayer()
  }

  if (activeVideo != null) {
    ActiveVideoPlayerPane(
      title = activeVideo.title,
      videoUriString = activeVideo.uriString,
      resumeMs = state.resumePositionMs,
      aspectMode = state.aspectMode,
      decoderMode = state.decoderMode,
      speed = state.playbackSpeed,
      subtitleUri = state.externalSubtitleUri,
      isInPipMode = isInPipMode,
      onClose = { pos, dur ->
        viewModel.saveProgress(pos, dur)
        viewModel.closePlayer()
      },
      onPickSubtitle = { subPicker.launch(arrayOf("application/x-subrip", "text/vtt", "text/plain", "*/*")) },
      onCycleAspect = {
        val next = VideoAspectMode.entries[(state.aspectMode.ordinal + 1) % VideoAspectMode.entries.size]
        viewModel.setAspectMode(next)
      },
      onToggleDecoder = {
        val next = if (state.decoderMode == VideoDecoderMode.AUTO_HW) VideoDecoderMode.PREFER_SW else VideoDecoderMode.AUTO_HW
        viewModel.setDecoderMode(next)
      },
      onSetSpeed = viewModel::setSpeed,
      onShiftSub = viewModel::shiftSubtitleOffset,
      onEnterPip = {
        if (onEnterPipRequest != null) {
          onEnterPipRequest()
        } else {
          val act = context as? Activity
          if (act != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
              .setAspectRatio(Rational(16, 9))
              .build()
            runCatching { act.enterPictureInPictureMode(params) }
          }
        }
      },
    )
    return
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Video Player",
      subtitle = "Offline ExoPlayer, Subtitles, Gestures & PiP",
      onBack = onBack,
      trailingContent = {
        IconButton(onClick = { videoPicker.launch(arrayOf("video/*")) }, modifier = Modifier.size(48.dp)) {
          Icon(Icons.Default.FolderOpen, contentDescription = "Pick video", tint = colors.accent)
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
      state.hdrSummary?.let { hdr ->
        item {
          NexusGlassCard(accentHighlight = true) {
            SectionHeader(title = "Device HDR & Video Codec Support", icon = Icons.Default.Hd)
            Spacer(modifier = Modifier.height(6.dp))
            MetricKeyValueRow(
              "Display HDR Support",
              if (hdr.isHdrDisplaySupported) hdr.displayHdrTypes.joinToString(", ") else "SDR / Not reported",
            )
            MetricKeyValueRow(
              "Hardware Decoders",
              if (hdr.decoderCodecsHdr.isNotEmpty()) "${hdr.decoderCodecsHdr.size} HEVC/VP9/AV1 decoders" else "Standard codecs",
            )
          }
        }
      }

      if (!state.hasVideoPermission) {
        item {
          NexusGlassCard {
            Text("Scan Device Videos", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              "Allow video library access to list folders and files, or open any video directly using the folder picker.",
              style = MaterialTheme.typography.bodyMedium,
              color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(
                onClick = { permLauncher.launch(viewModel.videoPermissionName()) },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                modifier = Modifier.weight(1f),
              ) { Text("Allow Video Access") }
              OutlinedButton(
                onClick = { videoPicker.launch(arrayOf("video/*")) },
                modifier = Modifier.weight(1f),
              ) { Text("Pick Video") }
            }
          }
        }
      }

      item {
        OutlinedTextField(
          value = state.searchQuery,
          onValueChange = viewModel::setSearchQuery,
          placeholder = { Text("Search videos or folders...") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
        )
      }

      if (state.recentProgress.isNotEmpty()) {
        item {
          SectionHeader(title = "Recently Played", trailingText = "Resume")
          Spacer(modifier = Modifier.height(6.dp))
          Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            state.recentProgress.take(8).forEach { rec ->
              FilterChip(
                selected = false,
                onClick = { viewModel.openVideoUri(Uri.parse(rec.uriString), rec.title) },
                label = { Text("${rec.title.take(18)} (${Formatters.formatTimer(rec.positionMs)})") },
              )
            }
          }
        }
      }

      if (state.isLoading) {
        item { LoadingTelemetryState("Scanning local video library...") }
      } else if (state.videos.isEmpty()) {
        item {
          EmptyOrErrorState(
            icon = Icons.Default.VideoLibrary,
            title = "No Videos Found",
            description = "Tap the folder button in the top bar to play any local MP4, MKV, or WebM video.",
          )
        }
      } else {
        items(state.videos, key = { it.uriString }) { video ->
          NexusGlassCard(onClick = { viewModel.openVideo(video) }) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = colors.accent)
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(video.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                  "${video.folderName} • ${Formatters.formatTimer(video.durationMs)} • ${Formatters.formatBytes(video.sizeBytes)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = colors.textSecondary,
                )
              }
              if (video.isHdrLikely) {
                StatusPill(text = "HDR", color = colors.accentSecondary)
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(UnstableApi::class)
@Composable
private fun ActiveVideoPlayerPane(
  title: String,
  videoUriString: String,
  resumeMs: Long,
  aspectMode: VideoAspectMode,
  decoderMode: VideoDecoderMode,
  speed: Float,
  subtitleUri: Uri?,
  isInPipMode: Boolean,
  onClose: (Long, Long) -> Unit,
  onPickSubtitle: () -> Unit,
  onCycleAspect: () -> Unit,
  onToggleDecoder: () -> Unit,
  onSetSpeed: (Float) -> Unit,
  onShiftSub: (Long) -> Unit,
  onEnterPip: () -> Unit,
) {
  val colors = NexusThemeExtras.colors
  val context = LocalContext.current
  val container = remember(context) { NexusContainer.get(context) }
  var screenLocked by remember { mutableStateOf(false) }
  var brightnessFactor by remember { mutableFloatStateOf(0.7f) }

  val exoPlayer = remember(videoUriString, decoderMode, subtitleUri) {
    val rf = DefaultRenderersFactory(context).apply {
      setExtensionRendererMode(
        if (decoderMode == VideoDecoderMode.PREFER_SW) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
      )
      setEnableDecoderFallback(true)
    }
    ExoPlayer.Builder(context, rf).build().apply {
      val builder = MediaItem.Builder().setUri(Uri.parse(videoUriString))
      if (subtitleUri != null) {
        val mime = if (subtitleUri.toString().lowercase().contains("vtt")) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
        builder.setSubtitleConfigurations(
          listOf(
            MediaItem.SubtitleConfiguration.Builder(subtitleUri)
              .setMimeType(mime)
              .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
              .build()
          )
        )
      }
      setMediaItem(builder.build())
      if (resumeMs > 1000L) seekTo(resumeMs)
      prepare()
      playWhenReady = true
    }
  }

  LaunchedEffect(exoPlayer) {
    container.soundBoosterEngine.attachToSession(exoPlayer.audioSessionId)
  }

  LaunchedEffect(speed) {
    exoPlayer.setPlaybackSpeed(speed)
  }

  DisposableEffect(exoPlayer) {
    onDispose {
      onClose(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0L))
      exoPlayer.release()
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black),
  ) {
    if (!isInPipMode) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = { onClose(exoPlayer.currentPosition, exoPlayer.duration.coerceAtLeast(0L)) }) {
          Icon(Icons.Default.Close, contentDescription = "Close player", tint = Color.White)
        }
        Text(
          title,
          color = Color.White,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { screenLocked = !screenLocked }) {
          Icon(if (screenLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = "Lock screen", tint = colors.accent)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          IconButton(onClick = onEnterPip) {
            Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Picture in Picture", tint = Color.White)
          }
        }
      }
    }

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
        .pointerInput(screenLocked, isInPipMode) {
          if (screenLocked || isInPipMode) return@pointerInput
          detectVerticalDragGestures { change, dragAmount ->
            val leftHalf = change.position.x < size.width / 2f
            if (leftHalf) {
              brightnessFactor = (brightnessFactor - dragAmount / 600f).coerceIn(0.1f, 1.0f)
              val act = context as? Activity
              act?.window?.attributes = act?.window?.attributes?.apply {
                screenBrightness = brightnessFactor
              }
            } else {
              val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
              if (dragAmount < -18f) am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
              else if (dragAmount > 18f) am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
            }
          }
        },
    ) {
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
          PlayerView(ctx).apply {
            player = exoPlayer
            useController = !screenLocked && !isInPipMode
          }
        },
        update = { pv ->
          pv.player = exoPlayer
          val showCtrl = !screenLocked && !isInPipMode
          pv.useController = showCtrl
          if (!showCtrl) pv.hideController()
          pv.resizeMode = if (isInPipMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT
          } else {
            when (aspectMode) {
              VideoAspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
              VideoAspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
              VideoAspectMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
              VideoAspectMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
            }
          }
        },
      )
    }

    if (!isInPipMode && !screenLocked) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .horizontalScroll(rememberScrollState())
          .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = false,
          onClick = onCycleAspect,
          leadingIcon = { Icon(Icons.Default.AspectRatio, contentDescription = null, modifier = Modifier.size(16.dp)) },
          label = { Text("Aspect: ${aspectMode.label}") },
        )
        FilterChip(
          selected = decoderMode == VideoDecoderMode.PREFER_SW,
          onClick = onToggleDecoder,
          label = { Text(decoderMode.label) },
        )
        FilterChip(
          selected = subtitleUri != null,
          onClick = onPickSubtitle,
          leadingIcon = { Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(16.dp)) },
          label = { Text(if (subtitleUri != null) "Subtitles Loaded" else "Load .SRT/.VTT") },
        )
        FilterChip(selected = false, onClick = { onShiftSub(-500) }, label = { Text("Sub -0.5s") })
        FilterChip(selected = false, onClick = { onShiftSub(500) }, label = { Text("Sub +0.5s") })
        listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { s ->
          FilterChip(
            selected = kotlin.math.abs(speed - s) < 0.05f,
            onClick = { onSetSpeed(s) },
            label = { Text("${s}x") },
          )
        }
      }
    }
  }
}
