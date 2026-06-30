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
    // videoId đã thử "cứu" URL hết hạn (resolve lại) — chỉ cho phép MỘT lần mỗi
    // bài để KHÔNG tạo vòng lặp reload vô hạn khi lỗi là vĩnh viễn (vd anti-bot).
    private var recoveredTrackId: String? = null
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
        // Bài mới ⇒ cho phép cứu URL hết hạn một lần nữa cho bài này.
        recoveredTrackId = null
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
            val item = buildItem(track, url)

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
                Log.e(TAG, "Listener.onPlayerError [${error.errorCodeName}] (code=${error.errorCode}): " +
                    "${error.message}", error)
                // Lỗi nhóm 2xxx = lỗi nguồn/IO (vd HTTP 403 do URL googlevideo hết
                // hạn hoặc bị ràng buộc IP). Đây là nguyên nhân THẬT của "video tự
                // dừng / cần tải lại" giữa chừng. Cách đúng: resolve lại URL MỚI và
                // phát tiếp từ vị trí cũ — KHÔNG phải tải lại cả màn hình. Giới hạn
                // một lần/bài để tránh vòng lặp khi lỗi vĩnh viễn.
                val isSourceIoError = error.errorCode / 1000 == 2
                if (isSourceIoError) recoverFromSourceError(error)
                else surfacePlaybackError(error)
            }
        })
    }

    /** Hiện lỗi phát cho người dùng (không tự cứu được). */
    private fun surfacePlaybackError(error: PlaybackException) {
        _state.value = _state.value.copy(
            isBuffering = false,
            isPlaying   = false,
            error       = "Không phát được: ${error.errorCodeName}",
        )
    }

    /**
     * Cứu lỗi nguồn (URL hết hạn) đúng cách: xoá cache → lấy URL mới → phát tiếp
     * từ vị trí đang nghe. Mỗi bài chỉ thử MỘT lần (recoveredTrackId) để nếu lỗi
     * là vĩnh viễn (anti-bot, video gỡ) thì dừng và báo lỗi, không reload lặp vô tận.
     */
    private fun recoverFromSourceError(error: PlaybackException) {
        val track = _state.value.currentTrack ?: return surfacePlaybackError(error)
        if (recoveredTrackId == track.id) {
            Log.e(TAG, "onPlayerError: already re-resolved '${track.id}' once — surfacing error, " +
                "NOT reloading again (loop guard)")
            return surfacePlaybackError(error)
        }
        recoveredTrackId = track.id
        val resumeAt = ctrl?.currentPosition ?: 0L
        Log.w(TAG, "onPlayerError [${error.errorCodeName}] → stream URL likely expired; re-resolving " +
            "FRESH url for '${track.id}' and resuming at ${resumeAt}ms (one-shot recovery)")
        _state.value = _state.value.copy(isBuffering = true, error = null)
        scope.launch {
            runCatching {
                repo.invalidateStream(track.id)
                val url = repo.streamUrl(track.id)
                val c   = ctrl ?: return@launch
                c.setMediaItem(buildItem(track, url))
                c.prepare()
                c.seekTo(resumeAt)
                c.play()
                Log.d(TAG, "recovery: re-prepared '${track.id}' with fresh url, seek=${resumeAt}ms")
            }.onFailure { e ->
                Log.e(TAG, "recovery FAILED for '${track.id}': ${e.message}", e)
                surfacePlaybackError(error)
            }
        }
    }

    private fun buildItem(track: Track, url: String): MediaItem =
        MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.author)
                    .setArtworkUri(Uri.parse(track.thumbnailUrl))
                    .build()
            )
            .build()

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
