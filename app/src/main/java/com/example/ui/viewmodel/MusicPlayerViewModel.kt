package com.example.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.local.MusicFavoriteEntity
import com.example.data.local.MusicHistoryEntity
import com.example.data.local.MusicPlaylistEntity
import com.example.data.local.MusicPlaylistTrackEntity
import com.example.data.model.LocalAudioTrack
import com.example.di.NexusContainer
import com.example.media.NexusMusicService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class MusicBrowseTab(val label: String) {
  SONGS("Songs"),
  FOLDERS("Folders"),
  ALBUMS("Albums"),
  ARTISTS("Artists"),
  PLAYLISTS("Playlists"),
  FAVORITES("Favorites"),
  RECENT("Recent"),
}

data class MusicPlayerUiState(
  val hasAudioPermission: Boolean = false,
  val isLoading: Boolean = false,
  val tracks: List<LocalAudioTrack> = emptyList(),
  val searchQuery: String = "",
  val activeTab: MusicBrowseTab = MusicBrowseTab.SONGS,
  val currentTrack: LocalAudioTrack? = null,
  val isPlaying: Boolean = false,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
  val playbackSpeed: Float = 1.0f,
  val shuffleEnabled: Boolean = false,
  val repeatMode: Int = Player.REPEAT_MODE_OFF,
  val albumArtBitmap: Bitmap? = null,
  val lyricsText: String? = null,
  val playlists: List<MusicPlaylistEntity> = emptyList(),
  val favorites: Set<String> = emptySet(),
  val history: List<MusicHistoryEntity> = emptyList(),
  val statusMessage: String? = null,
)

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {
  private val container = NexusContainer.get(application)
  private val dao = container.database.musicLibraryDao()

  private var controllerFuture: ListenableFuture<MediaController>? = null
  private var controller: MediaController? = null

  private val _hasPermission = MutableStateFlow(container.permissionManager.isAudioMediaGranted())
  private val _isLoading = MutableStateFlow(false)
  private val _tracks = MutableStateFlow<List<LocalAudioTrack>>(emptyList())
  private val _searchQuery = MutableStateFlow("")
  private val _activeTab = MutableStateFlow(MusicBrowseTab.SONGS)
  private val _currentTrack = MutableStateFlow<LocalAudioTrack?>(null)
  private val _isPlaying = MutableStateFlow(false)
  private val _positionMs = MutableStateFlow(0L)
  private val _durationMs = MutableStateFlow(0L)
  private val _speed = MutableStateFlow(1.0f)
  private val _shuffle = MutableStateFlow(false)
  private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
  private val _albumArt = MutableStateFlow<Bitmap?>(null)
  private val _lyrics = MutableStateFlow<String?>(null)
  private val _status = MutableStateFlow<String?>(null)

  val uiState: StateFlow<MusicPlayerUiState> = combine(
    _hasPermission,
    _isLoading,
    _tracks,
    _searchQuery,
    _activeTab,
    _currentTrack,
    _isPlaying,
    _positionMs,
    _durationMs,
    _speed,
    _shuffle,
    _repeatMode,
    _albumArt,
    _lyrics,
    dao.observePlaylists(),
    dao.observeFavorites(),
    dao.observeHistory(),
    _status,
  ) { arr ->
    @Suppress("UNCHECKED_CAST")
    val allTracks = arr[2] as List<LocalAudioTrack>
    val q = (arr[3] as String).trim().lowercase()
    val filtered = if (q.isEmpty()) allTracks else {
      allTracks.filter {
        it.title.lowercase().contains(q) ||
          it.artist.lowercase().contains(q) ||
          it.album.lowercase().contains(q)
      }
    }
    @Suppress("UNCHECKED_CAST")
    val playlists = arr[14] as List<MusicPlaylistEntity>
    @Suppress("UNCHECKED_CAST")
    val favList = arr[15] as List<MusicFavoriteEntity>
    @Suppress("UNCHECKED_CAST")
    val history = arr[16] as List<MusicHistoryEntity>

    MusicPlayerUiState(
      hasAudioPermission = arr[0] as Boolean,
      isLoading = arr[1] as Boolean,
      tracks = filtered,
      searchQuery = arr[3] as String,
      activeTab = arr[4] as MusicBrowseTab,
      currentTrack = arr[5] as LocalAudioTrack?,
      isPlaying = arr[6] as Boolean,
      positionMs = arr[7] as Long,
      durationMs = arr[8] as Long,
      playbackSpeed = arr[9] as Float,
      shuffleEnabled = arr[10] as Boolean,
      repeatMode = arr[11] as Int,
      albumArtBitmap = arr[12] as Bitmap?,
      lyricsText = arr[13] as String?,
      playlists = playlists,
      favorites = favList.map { it.trackUri }.toSet(),
      history = history,
      statusMessage = arr[17] as String?,
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = MusicPlayerUiState(),
  )

  init {
    connectMediaController()
    if (_hasPermission.value) {
      scanLibrary()
    }
    viewModelScope.launch {
      while (isActive) {
        controller?.let { c ->
          _isPlaying.value = c.isPlaying
          _positionMs.value = c.currentPosition.coerceAtLeast(0L)
          _durationMs.value = c.duration.coerceAtLeast(0L)
        }
        delay(500L)
      }
    }
  }

  private fun connectMediaController() {
    val ctx = getApplication<Application>()
    val token = SessionToken(ctx, ComponentName(ctx, NexusMusicService::class.java))
    val future = MediaController.Builder(ctx, token).buildAsync()
    controllerFuture = future
    future.addListener({
      runCatching {
        val c = future.get()
        controller = c
        c.addListener(object : Player.Listener {
          override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
          }

          override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val uri = mediaItem?.localConfiguration?.uri?.toString()
            val matched = _tracks.value.firstOrNull { it.uriString == uri }
            if (matched != null) onTrackBecameActive(matched)
          }
        })
      }
    }, ContextCompat.getMainExecutor(ctx))
  }

  fun onPermissionUpdated() {
    val granted = container.permissionManager.isAudioMediaGranted()
    _hasPermission.value = granted
    if (granted) scanLibrary()
  }

  fun audioPermissionName(): String = container.permissionManager.audioMediaPermissionName()

  fun scanLibrary() {
    viewModelScope.launch {
      _isLoading.value = true
      _tracks.value = container.musicLibraryDataSource.scanLocalAudio()
      _isLoading.value = false
    }
  }

  fun setSearchQuery(q: String) { _searchQuery.value = q }
  fun setActiveTab(tab: MusicBrowseTab) { _activeTab.value = tab }

  fun playQueueFrom(queue: List<LocalAudioTrack>, startIndex: Int) {
    val c = controller ?: return
    if (queue.isEmpty() || startIndex !in queue.indices) return
    val mediaItems = queue.map { track ->
      MediaItem.Builder()
        .setUri(Uri.parse(track.uriString))
        .setMediaId(track.uriString)
        .setMediaMetadata(
          MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .build()
        )
        .build()
    }
    c.setMediaItems(mediaItems, startIndex, 0L)
    c.prepare()
    c.play()
    onTrackBecameActive(queue[startIndex])
  }

  fun playPickedAudio(uri: Uri) {
    viewModelScope.launch {
      val picked = container.musicLibraryDataSource.trackFromPickedUri(uri)
      _tracks.value = (listOf(picked) + _tracks.value).distinctBy { it.uriString }
      playQueueFrom(listOf(picked), 0)
    }
  }

  private fun onTrackBecameActive(track: LocalAudioTrack) {
    _currentTrack.value = track
    viewModelScope.launch {
      dao.upsertHistory(
        MusicHistoryEntity(
          trackUri = track.uriString,
          title = track.title,
          artist = track.artist,
          album = track.album,
          durationMs = track.durationMs,
        )
      )
      val artBytes = container.musicLibraryDataSource.loadAlbumArtBytes(track.uriString)
      _albumArt.value = artBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
      _lyrics.value = container.musicLibraryDataSource.loadLyricsForTrack(track)
    }
  }

  fun togglePlayPause() {
    controller?.let { if (it.isPlaying) it.pause() else it.play() }
  }

  fun seekTo(ms: Long) {
    controller?.seekTo(ms.coerceAtLeast(0L))
    _positionMs.value = ms.coerceAtLeast(0L)
  }

  fun skipNext() {
    controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
  }

  fun skipPrev() {
    controller?.let { if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekTo(0L) }
  }

  fun setSpeed(speed: Float) {
    val clean = speed.coerceIn(0.5f, 2.0f)
    _speed.value = clean
    controller?.setPlaybackSpeed(clean)
  }

  fun toggleShuffle() {
    val next = !_shuffle.value
    _shuffle.value = next
    controller?.shuffleModeEnabled = next
  }

  fun cycleRepeatMode() {
    val next = when (_repeatMode.value) {
      Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
      Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
      else -> Player.REPEAT_MODE_OFF
    }
    _repeatMode.value = next
    controller?.repeatMode = next
  }

  fun toggleFavorite(track: LocalAudioTrack) {
    viewModelScope.launch {
      if (track.uriString in uiState.value.favorites) {
        dao.removeFavorite(track.uriString)
      } else {
        dao.insertFavorite(
          MusicFavoriteEntity(trackUri = track.uriString, title = track.title, artist = track.artist)
        )
      }
    }
  }

  fun createPlaylist(name: String) {
    val clean = name.trim()
    if (clean.isEmpty()) return
    viewModelScope.launch {
      dao.insertPlaylist(MusicPlaylistEntity(name = clean))
      _status.value = "Created playlist '$clean'."
    }
  }

  fun addToPlaylist(playlistId: Long, track: LocalAudioTrack) {
    viewModelScope.launch {
      dao.addTrackToPlaylist(
        MusicPlaylistTrackEntity(
          playlistId = playlistId,
          trackUri = track.uriString,
          title = track.title,
          artist = track.artist,
          album = track.album,
          durationMs = track.durationMs,
        )
      )
      _status.value = "Added '${track.title}' to playlist."
    }
  }

  fun deletePlaylist(id: Long) {
    viewModelScope.launch { dao.deletePlaylist(id) }
  }

  fun observePlaylistTracks(playlistId: Long) = dao.observePlaylistTracks(playlistId)

  override fun onCleared() {
    controllerFuture?.let { MediaController.releaseFuture(it) }
    super.onCleared()
  }
}
