package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.example.data.model.LocalAudioTrack
import com.example.ui.components.EmptyOrErrorState
import com.example.ui.components.LoadingTelemetryState
import com.example.ui.components.NexusGlassCard
import com.example.ui.components.NexusModuleHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.NexusThemeExtras
import com.example.ui.viewmodel.MusicBrowseTab
import com.example.ui.viewmodel.MusicPlayerViewModel
import com.example.util.Formatters
import java.util.Locale

@Composable
fun MusicPlayerScreen(
  viewModel: MusicPlayerViewModel,
  onOpenSoundBooster: () -> Unit,
  onBack: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(),
) {
  val colors = NexusThemeExtras.colors
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var showLyricsDialog by remember { mutableStateOf(false) }
  var playlistDialogTrack by remember { mutableStateOf<LocalAudioTrack?>(null) }
  var newPlaylistName by remember { mutableStateOf("") }
  var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }

  val permLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) {
    viewModel.onPermissionUpdated()
  }

  val audioFilePicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) viewModel.playPickedAudio(uri)
  }

  if (showLyricsDialog) {
    AlertDialog(
      onDismissRequest = { showLyricsDialog = false },
      containerColor = colors.cardSurface,
      title = { Text(state.currentTrack?.title ?: "Lyrics", color = colors.textPrimary) },
      text = {
        Text(
          text = state.lyricsText ?: "No matching local .lrc or .txt lyrics file found in this track's folder.",
          style = MaterialTheme.typography.bodyMedium,
          color = colors.textSecondary,
        )
      },
      confirmButton = {
        TextButton(onClick = { showLyricsDialog = false }) { Text("Close") }
      },
    )
  }

  playlistDialogTrack?.let { tr ->
    AlertDialog(
      onDismissRequest = { playlistDialogTrack = null },
      containerColor = colors.cardSurface,
      title = { Text("Add to Playlist", color = colors.textPrimary) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (state.playlists.isEmpty()) {
            Text("Create a playlist first inside the Playlists tab.", color = colors.textSecondary)
          } else {
            state.playlists.forEach { pl ->
              OutlinedButton(
                onClick = {
                  viewModel.addToPlaylist(pl.id, tr)
                  playlistDialogTrack = null
                },
                modifier = Modifier.fillMaxWidth(),
              ) { Text(pl.name) }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { playlistDialogTrack = null }) { Text("Cancel") }
      },
    )
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
  ) {
    NexusModuleHeader(
      title = "Music Player",
      subtitle = "Offline Media3 Background Audio",
      onBack = onBack,
      trailingContent = {
        IconButton(onClick = onOpenSoundBooster, modifier = Modifier.size(48.dp)) {
          Icon(Icons.Default.Equalizer, contentDescription = "Open Sound Booster EQ", tint = colors.accent)
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
      state.currentTrack?.let { track ->
        item {
          NexusGlassCard(accentHighlight = true, testTag = "music_now_playing_card") {
            Row(verticalAlignment = Alignment.CenterVertically) {
              val art = state.albumArtBitmap
              Box(
                modifier = Modifier
                  .size(64.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .background(colors.accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
              ) {
                if (art != null) {
                  Image(
                    bitmap = art.asImageBitmap(),
                    contentDescription = "Album art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                  )
                } else {
                  Icon(Icons.Default.MusicNote, contentDescription = null, tint = colors.accent, modifier = Modifier.size(32.dp))
                }
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${track.artist} • ${track.album}", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
              IconButton(onClick = { showLyricsDialog = true }) {
                Icon(Icons.Default.Lyrics, contentDescription = "Show Lyrics", tint = colors.accentSecondary)
              }
              IconButton(onClick = { viewModel.toggleFavorite(track) }) {
                val fav = track.uriString in state.favorites
                Icon(
                  imageVector = if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                  contentDescription = "Favorite",
                  tint = if (fav) colors.accent else colors.textSecondary,
                )
              }
            }

            Spacer(modifier = Modifier.height(6.dp))
            val maxDur = state.durationMs.coerceAtLeast(track.durationMs).coerceAtLeast(1L)
            Slider(
              value = state.positionMs.coerceIn(0L, maxDur).toFloat(),
              onValueChange = { viewModel.seekTo(it.toLong()) },
              valueRange = 0f..maxDur.toFloat(),
              colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text(Formatters.formatTimer(state.positionMs), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
              Text(Formatters.formatTimer(maxDur), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceEvenly,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              IconButton(onClick = { viewModel.toggleShuffle() }) {
                Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = if (state.shuffleEnabled) colors.accent else colors.textSecondary)
              }
              IconButton(onClick = { viewModel.skipPrev() }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = colors.textPrimary)
              }
              Button(
                onClick = { viewModel.togglePlayPause() },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                modifier = Modifier.testTag("music_play_pause_button"),
              ) {
                Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause")
              }
              IconButton(onClick = { viewModel.skipNext() }) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = colors.textPrimary)
              }
              IconButton(onClick = { viewModel.cycleRepeatMode() }) {
                Icon(
                  imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                  contentDescription = "Repeat",
                  tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) colors.accent else colors.textSecondary,
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { spd ->
                  FilterChip(
                    selected = kotlin.math.abs(state.playbackSpeed - spd) < 0.05f,
                    onClick = { viewModel.setSpeed(spd) },
                    label = { Text("${String.format(Locale.US, "%.2g", spd)}x") },
                  )
                }
              }
              OutlinedButton(onClick = onOpenSoundBooster) {
                Icon(Icons.Default.Equalizer, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("EQ")
              }
            }
          }
        }
      }

      if (!state.hasAudioPermission) {
        item {
          NexusGlassCard(accentHighlight = true) {
            Text("Scan Local Audio Library", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              "Grant audio library access to browse all songs, folders, albums, and artists, or pick any audio file directly without permission.",
              style = MaterialTheme.typography.bodyMedium,
              color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(
                onClick = { permLauncher.launch(viewModel.audioPermissionName()) },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                modifier = Modifier.weight(1f),
              ) { Text("Allow Audio Access") }
              OutlinedButton(
                onClick = { audioFilePicker.launch(arrayOf("audio/*")) },
                modifier = Modifier.weight(1f),
              ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pick File")
              }
            }
          }
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedTextField(
            value = state.searchQuery,
            onValueChange = viewModel::setSearchQuery,
            placeholder = { Text("Search tracks, artists, albums...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f),
          )
          OutlinedButton(onClick = { audioFilePicker.launch(arrayOf("audio/*")) }) {
            Icon(Icons.Default.FolderOpen, contentDescription = "Pick Audio File")
          }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          MusicBrowseTab.entries.forEach { tab ->
            FilterChip(
              selected = state.activeTab == tab,
              onClick = { viewModel.setActiveTab(tab) },
              label = { Text(tab.label) },
            )
          }
        }
      }

      if (state.isLoading) {
        item { LoadingTelemetryState("Scanning local audio library...") }
      } else {
        when (state.activeTab) {
          MusicBrowseTab.SONGS -> {
            if (state.tracks.isEmpty()) {
              item {
                EmptyOrErrorState(
                  icon = Icons.Default.MusicNote,
                  title = "No Audio Tracks Found",
                  description = "Grant audio permission or tap the folder icon above to pick a track directly.",
                )
              }
            } else {
              itemsIndexed(state.tracks, key = { _, t -> t.uriString }) { idx, track ->
                TrackRowCard(
                  track = track,
                  isActive = state.currentTrack?.uriString == track.uriString,
                  onPlay = { viewModel.playQueueFrom(state.tracks, idx) },
                  onAddToPlaylist = { playlistDialogTrack = track },
                )
              }
            }
          }
          MusicBrowseTab.FOLDERS -> {
            val grouped = state.tracks.groupBy { it.folderName }
            items(grouped.keys.sorted().toList()) { folder ->
              val list = grouped[folder].orEmpty()
              NexusGlassCard(onClick = { viewModel.playQueueFrom(list, 0) }) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(folder, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                  StatusPill(text = "${list.size} tracks", color = colors.accent)
                }
              }
            }
          }
          MusicBrowseTab.ALBUMS -> {
            val grouped = state.tracks.groupBy { it.album }
            items(grouped.keys.sorted().toList()) { album ->
              val list = grouped[album].orEmpty()
              NexusGlassCard(onClick = { viewModel.playQueueFrom(list, 0) }) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(album, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                  StatusPill(text = "${list.size} tracks", color = colors.accentSecondary)
                }
              }
            }
          }
          MusicBrowseTab.ARTISTS -> {
            val grouped = state.tracks.groupBy { it.artist }
            items(grouped.keys.sorted().toList()) { artist ->
              val list = grouped[artist].orEmpty()
              NexusGlassCard(onClick = { viewModel.playQueueFrom(list, 0) }) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(artist, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                  StatusPill(text = "${list.size} tracks", color = colors.info)
                }
              }
            }
          }
          MusicBrowseTab.PLAYLISTS -> {
            item {
              NexusGlassCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                  OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("New playlist name...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                  )
                  Button(
                    onClick = {
                      viewModel.createPlaylist(newPlaylistName)
                      newPlaylistName = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                  ) { Icon(Icons.Default.Add, contentDescription = "Create playlist") }
                }
              }
            }
            items(state.playlists, key = { it.id }) { pl ->
              val plTracks by viewModel.observePlaylistTracks(pl.id).collectAsStateWithLifecycle(initialValue = emptyList())
              NexusGlassCard(onClick = { selectedPlaylistId = if (selectedPlaylistId == pl.id) null else pl.id }) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.QueueMusic, contentDescription = null, tint = colors.accent)
                  Spacer(modifier = Modifier.width(10.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(pl.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text("${plTracks.size} tracks", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                  }
                  if (plTracks.isNotEmpty()) {
                    OutlinedButton(onClick = {
                      val items = plTracks.map { e ->
                        LocalAudioTrack(e.id, e.trackUri, e.title, e.artist, e.album, 0L, e.durationMs, "Playlist", "", "audio/*", 0L)
                      }
                      viewModel.playQueueFrom(items, 0)
                    }) { Text("Play") }
                  }
                  IconButton(onClick = { viewModel.deletePlaylist(pl.id) }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete playlist", tint = colors.critical)
                  }
                }
              }
            }
          }
          MusicBrowseTab.FAVORITES -> {
            val favTracks = state.tracks.filter { it.uriString in state.favorites }
            itemsIndexed(favTracks, key = { _, t -> t.uriString }) { idx, track ->
              TrackRowCard(track, state.currentTrack?.uriString == track.uriString, { viewModel.playQueueFrom(favTracks, idx) }, { playlistDialogTrack = track })
            }
          }
          MusicBrowseTab.RECENT -> {
            items(state.history, key = { it.trackUri }) { h ->
              NexusGlassCard(onClick = {
                val track = LocalAudioTrack(h.trackUri.hashCode().toLong(), h.trackUri, h.title, h.artist, h.album, 0L, h.durationMs, "Recent", "", "audio/*", 0L)
                viewModel.playQueueFrom(listOf(track), 0)
              }) {
                Text(h.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text("${h.artist} • ${h.album}", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TrackRowCard(
  track: LocalAudioTrack,
  isActive: Boolean,
  onPlay: () -> Unit,
  onAddToPlaylist: () -> Unit,
) {
  val colors = NexusThemeExtras.colors
  NexusGlassCard(accentHighlight = isActive, onClick = onPlay) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Default.MusicNote, contentDescription = null, tint = if (isActive) colors.accent else colors.textSecondary)
      Spacer(modifier = Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(track.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
          "${track.artist} • ${Formatters.formatTimer(track.durationMs)}",
          style = MaterialTheme.typography.labelSmall,
          color = colors.textSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(onClick = onAddToPlaylist) {
        Icon(Icons.Default.Add, contentDescription = "Add to playlist", tint = colors.textSecondary)
      }
    }
  }
}
