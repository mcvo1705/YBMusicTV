package com.ybmusic.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ybmusic.tv.core.player.PlayerController
import com.ybmusic.tv.data.model.PlayerState
import com.ybmusic.tv.data.model.Track
import com.ybmusic.tv.data.model.UiState
import com.ybmusic.tv.data.repository.MusicRepository
import com.ybmusic.tv.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repo    : MusicRepository,
    private val settings: SettingsRepository,
    val player          : PlayerController,
) : ViewModel() {

    // ── Player ────────────────────────────────────────────────────────────────
    val playerState: StateFlow<PlayerState> = player.state

    fun togglePlayPause() = player.togglePlayPause()
    fun playNext()        = player.playNext()
    fun playPrev()        = player.playPrevious()
    fun seekTo(ms: Long)  = player.seekTo(ms)
    fun cyclePlayMode()   = player.cyclePlayMode()

    fun playAll(tracks: List<Track>, startIndex: Int = 0) = player.playQueue(tracks, startIndex)
    fun playShuffle(tracks: List<Track>) = player.playQueue(tracks.shuffled(), 0)

    // ── Search ────────────────────────────────────────────────────────────────
    private val _query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _query.asStateFlow()

    private val _searchState = MutableStateFlow<UiState<List<Track>>>(UiState.Idle)
    val searchState: StateFlow<UiState<List<Track>>> = _searchState.asStateFlow()

    // Còn trang kết quả tiếp theo để tải khi cuộn xuống cuối danh sách.
    private val _canLoadMore = MutableStateFlow(false)
    val canLoadMore: StateFlow<Boolean> = _canLoadMore.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private var loadMoreJob: Job? = null

    fun onQueryChanged(q: String) { _query.value = q }

    fun search() {
        val q = _query.value.trim()
        if (q.isBlank()) return
        loadMoreJob?.cancel()
        _isLoadingMore.value = false
        viewModelScope.launch {
            _searchState.value = UiState.Loading
            _canLoadMore.value = false
            runCatching {
                when {
                    repo.isYtUrl(q) && repo.isYtPlaylist(q) ->
                        _searchState.value = UiState.Success(repo.ytPlaylist(q))
                    repo.isYtUrl(q) ->
                        _searchState.value = UiState.Success(listOf(repo.videoInfo(repo.extractId(q))))
                    else -> {
                        val page = repo.search(q)
                        _searchState.value = UiState.Success(page.tracks)
                        _canLoadMore.value = page.hasMore
                    }
                }
            }.onFailure {
                _searchState.value = UiState.Error(it.message ?: "Lỗi tìm kiếm")
            }
        }
    }

    // Tải trang kết quả tiếp theo và nối vào danh sách hiện có (phân trang vô hạn).
    fun loadMore() {
        if (_isLoadingMore.value || !_canLoadMore.value) return
        val current = (_searchState.value as? UiState.Success)?.data ?: return
        loadMoreJob = viewModelScope.launch {
            _isLoadingMore.value = true
            runCatching {
                val page   = repo.searchMore()
                val merged = (current + page.tracks).distinctBy { it.id }
                _searchState.value = UiState.Success(merged)
                _canLoadMore.value = page.hasMore && page.tracks.isNotEmpty()
            }.onFailure {
                _canLoadMore.value = false
            }
            _isLoadingMore.value = false
        }
    }

    // ── Library / Playlists ───────────────────────────────────────────────────
    val playlists = repo.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedPlaylistId: StateFlow<String?> = _selectedId.asStateFlow()

    val selectedTracks: StateFlow<List<Track>> = _selectedId
        .flatMapLatest { id -> if (id != null) repo.observeTracks(id) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun selectPlaylist(id: String) { _selectedId.value = id }

    fun createPlaylist(name: String) { viewModelScope.launch { repo.createPlaylist(name) } }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            repo.deletePlaylist(id)
            if (_selectedId.value == id) _selectedId.value = null
        }
    }

    fun addToPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch { repo.addTrack(playlistId, track) }
    }

    fun removeTrack(playlistId: String, trackId: String) {
        viewModelScope.launch { repo.removeTrack(playlistId, trackId) }
    }

    // ── Settings ──────────────────────────────────────────────────────────────
    val audioQuality = settings.audioQuality.stateIn(viewModelScope, SharingStarted.Lazily, "high")
    val cacheUrls    = settings.cacheUrls.stateIn(viewModelScope, SharingStarted.Lazily, true)

    fun setAudioQuality(v: String) { viewModelScope.launch { settings.setAudioQuality(v) } }
    fun setCacheUrls(v: Boolean)   { viewModelScope.launch { settings.setCacheUrls(v) } }
}
