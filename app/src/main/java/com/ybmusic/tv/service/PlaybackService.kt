package com.ybmusic.tv.service

import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint

private const val TAG = "Playback/Service"

/** Tên đọc được cho các hằng số Player.STATE_* để log rõ ràng. */
private fun playbackStateName(state: Int): String = when (state) {
    Player.STATE_IDLE      -> "IDLE"
    Player.STATE_BUFFERING -> "BUFFERING"
    Player.STATE_READY     -> "READY"
    Player.STATE_ENDED     -> "ENDED"
    else                   -> "UNKNOWN($state)"
}

/**
 * Foreground service giữ ExoPlayer chạy khi màn hình tắt.
 * Media3 tự tạo notification với nút play/pause/next cho remote TV.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // URL audio của YouTube (googlevideo CDN) hay redirect (http↔https, đổi host)
        // và cần User-Agent giống trình duyệt. DefaultHttpDataSource mặc định của
        // ExoPlayer không cho cross-protocol redirect → request fail. Cấu hình
        // riêng data source để stream tải được ổn định trên Android TV.
        val httpDataSource = DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 11; Android TV) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            )
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSource))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Log ở tầng ExoPlayer thật (PlayerController chỉ nói chuyện qua một
        // MediaController proxy). Đây là nơi PlaybackException mang đầy đủ
        // nguyên nhân gốc (HTTP 403, codec, source error...) để biết chính xác
        // vì sao một stream không phát được.
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                Log.d(TAG, "ExoPlayer.onPlaybackStateChanged → ${playbackStateName(state)}")
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "ExoPlayer.onIsPlayingChanged → $isPlaying")
            }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                Log.d(TAG, "ExoPlayer.onMediaItemTransition → '${item?.mediaMetadata?.title}' " +
                    "uri=${item?.localConfiguration?.uri?.toString()?.take(100)} reason=$reason")
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer.onPlayerError [${error.errorCodeName}] (code=${error.errorCode}): " +
                    "${error.message}", error)
                error.cause?.let { Log.e(TAG, "  └─ caused by: ${it.javaClass.simpleName}: ${it.message}") }
            }
        })

        Log.d(TAG, "onCreate(): ExoPlayer + MediaSession created")
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(info: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
