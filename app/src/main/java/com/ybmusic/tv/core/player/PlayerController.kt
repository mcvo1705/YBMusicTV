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

private const val TAG = "Playback/Controller"

/** Tên đọc được cho các hằng số Player.STATE_* để log rõ ràng. */
private fun playbackStateName(state: Int): String = when (state) {
    Player.STATE_IDLE      -> "IDLE"
    Player.STATE_BUFFERING -> "BUFFERING"
    Player.STATE_READY     -> "READY"
    Player.STATE_ENDED     -> "ENDED"
    else                   -> "UNKNOWN($state)"
}

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
    // Đánh dấu đang trong quá trình buildAsync() để chặn gọi connect() chồng nhau
    // (vd: Activity tạo lại nhanh) tạo ra HAI MediaController cho cùng một session.
    private var connecting = false

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Bind một MediaController DUY NHẤT tới PlaybackService. PlayerController là
     * @Singleton và ExoPlayer thật nằm trong service (sống qua việc Activity bị
     * tạo lại), nên connect() phải idempotent: nếu đã có controller hoặc đang
     * bind dở thì BỎ QUA — tránh "khởi tạo lại" player gây cảm giác reload.
     */
    fun connect() {
        if (ctrl != null) {
            Log.d(TAG, "connect(): MediaController already connected — skip re-init")
            return
        }
        if (connecting) {
            Log.d(TAG, "connect(): bind already in progress — skip duplicate connect()")
            return
        }
        connecting = true
        // Log kèm stack trace để biết CHÍNH XÁC nơi nào gọi khởi tạo player —
        // yêu cầu "log exactly what triggered any reload / caller method".
        Log.d(TAG, "connect(): initializing NEW MediaController — caller trace below",
            Throwable("connect() call site"))
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener(
            {
                runCatching {
                    ctrl = future.get()
                    attachListener()
                    startProgress()
                    Log.d(TAG, "connect(): MediaController connected")
                }.onFailure { Log.e(TAG, "connect(): failed to bind MediaController: ${it.message}", it) }
                connecting = false
            },
            ContextCompat.getMainExecutor(ctx),
        )
    }

    fun disconnect() {
        Log.d(TAG, "disconnect(): releasing MediaController (playback continues in service if active)")
        progressJob?.cancel()
        progressJob = null
        ctrl?.release()
        ctrl = null
        connecting = false
    }

    // ── Queue ─────────────────────────────────────────────────────────────────

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) {
            Log.w(TAG, "playQueue(): empty track list, ignoring")
            return
        }
        val idx = startIndex.coerceIn(0, tracks.lastIndex)
        Log.d(TAG, "playQueue(size=${tracks.size}, startIndex=$startIndex -> $idx)")
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
        val track = _state.value.queue.getOrNull(index) ?: run {
            Log.w(TAG, "playAt($index): no track at index (queue=${_state.value.queue.size})")
            return
        }
        val c = ctrl ?: run {
            // MediaController chưa connect xong — đây là lý do hay gặp khi
            // "click TrackCard nhưng không phát". Log rõ để phân biệt.
            Log.e(TAG, "playAt($index): MediaController NOT connected; cannot play " +
                "videoId=${track.id} '${track.title}'")
            _state.value = _state.value.copy(isBuffering = false, error = "Player chưa sẵn sàng")
            return
        }

        Log.d(TAG, "playAt(index=$index) START videoId=${track.id} title='${track.title}'")
        _state.value = _state.value.copy(
            currentTrack = track,
            isBuffering  = true,
            isPlaying    = false,
            error        = null,
        )

        runCatching {
            Log.d(TAG, "  [1/4] resolving stream URL for videoId=${track.id}")
            val url  = repo.streamUrl(track.id)
            Log.d(TAG, "  [2/4] stream URL ok (len=${url.length}): ${url.take(100)}…")
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

            Log.d(TAG, "  [3/4] setMediaItem() + prepare()")
            c.setMediaItem(item)
            c.prepare()
            Log.d(TAG, "  [4/4] play()")
            c.play()
            Log.d(TAG, "playAt(index=$index) commands issued for videoId=${track.id}")

            // Prefetch next track URL
            _state.value.queue.getOrNull(index + 1)?.let { next ->
                scope.launch(Dispatchers.IO) {
                    runCatching { repo.streamUrl(next.id) }
                        .onFailure { Log.w(TAG, "prefetch next (${next.id}) failed: ${it.message}") }
                }
            }
        }.onFailure { e ->
            Log.e(TAG, "playAt($index) FAILED for videoId=${track.id}: ${e.message}", e)
            _state.value = _state.value.copy(isBuffering = false, error = e.message)
        }
    }

    private fun attachListener() {
        ctrl?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                Log.d(TAG, "Listener.onIsPlayingChanged → $playing")
                _state.value = _state.value.copy(isPlaying = playing)
            }
            override fun onPlaybackStateChanged(state: Int) {
                Log.d(TAG, "Listener.onPlaybackStateChanged → ${playbackStateName(state)}")
                _state.value = _state.value.copy(isBuffering = state == Player.STATE_BUFFERING)
                if (state == Player.STATE_ENDED) playNext()
            }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                Log.d(TAG, "Listener.onMediaItemTransition → '${item?.mediaMetadata?.title}' reason=$reason")
            }
            override fun onPlayerError(error: PlaybackException) {
                // Lỗi phát (vd: 403 từ CDN, codec không hỗ trợ) — log rõ lý do và
                // hiện cho người dùng thay vì "đứng hình" im lặng.
                Log.e(TAG, "Listener.onPlayerError [${error.errorCodeName}] (code=${error.errorCode}): " +
                    "${error.message}", error)
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
