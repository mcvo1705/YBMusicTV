package com.ybmusic.tv.core.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.ybmusic.tv.data.model.PlayMode
import com.ybmusic.tv.data.model.PlayerState
import com.ybmusic.tv.data.model.Track
import com.ybmusic.tv.data.repository.MusicRepository
import com.ybmusic.tv.service.PlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PlayerController"

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: MusicRepository,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private var ctrl: MediaController? = null
    private var progressJob: Job? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun connect() {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener(
            {
                runCatching {
                    ctrl = future.get()
                    attachListener()
                    startProgress()
                }.onFailure { Log.e(TAG, "connect failed: ${it.message}") }
            },
            ContextCompat.getMainExecutor(ctx),
        )
    }

    fun disconnect() {
        progressJob?.cancel()
        ctrl?.release()
        ctrl = null
    }

    // ── Queue ─────────────────────────────────────────────────────────────────

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val idx = startIndex.coerceIn(0, tracks.lastIndex)
        _state.value = _state.value.copy(queue = tracks, queueIndex = idx)
        scope.launch { playAt(idx) }
    }

    fun playNext() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        val next = when (s.playMode) {
            PlayMode.SEQUENTIAL -> (s.queueIndex + 1).coerceAtMost(s.queue.lastIndex)
            PlayMode.SHUFFLE    -> s.queue.indices.random()
            PlayMode.REPEAT_ONE -> s.queueIndex
        }
        _state.value = s.copy(queueIndex = next)
        scope.launch { playAt(next) }
    }

    fun playPrevious() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        val prev = (s.queueIndex - 1).coerceAtLeast(0)
        _state.value = s.copy(queueIndex = prev)
        scope.launch { playAt(prev) }
    }

    // ── Controls ──────────────────────────────────────────────────────────────

    fun togglePlayPause() { ctrl?.let { if (it.isPlaying) it.pause() else it.play() } }

    fun seekTo(ms: Long) { ctrl?.seekTo(ms) }

    fun cyclePlayMode() {
        val next = PlayMode.entries[(_state.value.playMode.ordinal + 1) % PlayMode.entries.size]
        _state.value = _state.value.copy(playMode = next)
        ctrl?.repeatMode = if (next == PlayMode.REPEAT_ONE) Player.REPEAT_MODE_ONE
                           else Player.REPEAT_MODE_OFF
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private suspend fun playAt(index: Int) {
        val track = _state.value.queue.getOrNull(index) ?: return
        val c     = ctrl ?: return

        _state.value = _state.value.copy(
            currentTrack = track,
            isBuffering  = true,
            isPlaying    = false,
            error        = null,
        )

        runCatching {
            val url  = repo.streamUrl(track.id)
            val item = MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.author)
                        .setArtworkUri(Uri.parse(track.thumbnailUrl))
                        .build()
                )
                .build()

            c.setMediaItem(item)
            c.prepare()
            c.play()

            // Prefetch next track URL
            _state.value.queue.getOrNull(index + 1)?.let { next ->
                scope.launch(Dispatchers.IO) { runCatching { repo.streamUrl(next.id) } }
            }
        }.onFailure { e ->
            Log.e(TAG, "playAt($index): ${e.message}")
            _state.value = _state.value.copy(isBuffering = false, error = e.message)
        }
    }

    private fun attachListener() {
        ctrl?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _state.value = _state.value.copy(isPlaying = playing)
            }
            override fun onPlaybackStateChanged(state: Int) {
                _state.value = _state.value.copy(isBuffering = state == Player.STATE_BUFFERING)
                if (state == Player.STATE_ENDED) playNext()
            }
            override fun onPlayerError(error: PlaybackException) {
                // Lỗi phát (vd: 403 từ CDN, codec không hỗ trợ) — log rõ lý do và
                // hiện cho người dùng thay vì "đứng hình" im lặng.
                Log.e(TAG, "player error [${error.errorCodeName}]: ${error.message}", error)
                _state.value = _state.value.copy(
                    isBuffering = false,
                    isPlaying   = false,
                    error       = "Không phát được: ${error.errorCodeName}",
                )
            }
        })
    }

    private fun startProgress() {
        progressJob = scope.launch {
            while (true) {
                ctrl?.let { c ->
                    if (c.isPlaying) {
                        _state.value = _state.value.copy(
                            positionMs = c.currentPosition,
                            durationMs = c.duration.coerceAtLeast(0L),
                        )
                    }
                }
                delay(500L)
            }
        }
    }
}
