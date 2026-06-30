package com.ybmusic.tv.data.youtube

import android.util.Log
import com.ybmusic.tv.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG    = "YouTubeSource"
private const val TTL_MS = 2L * 60 * 60 * 1000   // 2 h cache

@Singleton
class YouTubeSource @Inject constructor() {

    // ── Stream URL cache ──────────────────────────────────────────────────────
    private data class Cached(val url: String, val expiresAt: Long)
    private val cache = HashMap<String, Cached>()

    // ── Search ────────────────────────────────────────────────────────────────
    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        runCatching {
            val handler = YouTube.searchQHFactory.fromQuery(
                query,
                listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
                "",
            )
            SearchInfo.getInfo(YouTube, handler)
                .relatedItems
                .filterIsInstance<StreamInfoItem>()
                .map { it.toTrack() }
        }.getOrElse { e ->
            Log.e(TAG, "search($query): ${e.message}")
            emptyList()
        }
    }

    // ── Audio stream URL ──────────────────────────────────────────────────────
    suspend fun streamUrl(videoId: String): String = withContext(Dispatchers.IO) {
        cache[videoId]?.let { if (it.expiresAt > System.currentTimeMillis()) return@withContext it.url }

        val info = StreamInfo.getInfo(YouTube, "https://www.youtube.com/watch?v=$videoId")

        // Ưu tiên opus (nhỏ, chất lượng tốt) → mp4a → bất kỳ
        val best = info.audioStreams.maxByOrNull { s ->
            val codec = when {
                s.codec?.contains("opus", true) == true -> 10_000
                s.codec?.contains("mp4a", true) == true ->  5_000
                else -> 0
            }
            codec + (if (s.averageBitrate > 0) s.averageBitrate else s.bitrate).coerceAtMost(160)
        } ?: error("No audio stream for $videoId")

        val url = best.content ?: error("Empty URL for $videoId")
        cache[videoId] = Cached(url, System.currentTimeMillis() + TTL_MS)
        url
    }

    // ── Single video info ─────────────────────────────────────────────────────
    suspend fun videoInfo(videoId: String): Track = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(YouTube, "https://www.youtube.com/watch?v=$videoId")
        Track(
            id             = videoId,
            title          = info.name,
            author         = info.uploaderName ?: "YouTube",
            thumbnailUrl   = info.thumbnails.firstOrNull()?.url ?: thumb(videoId),
            durationSeconds = info.duration,
        )
    }

    // ── Playlist ──────────────────────────────────────────────────────────────
    suspend fun playlist(url: String): List<Track> = withContext(Dispatchers.IO) {
        runCatching {
            PlaylistInfo.getInfo(YouTube, url)
                .relatedItems
                .filterIsInstance<StreamInfoItem>()
                .map { it.toTrack() }
        }.getOrElse { e ->
            Log.e(TAG, "playlist($url): ${e.message}")
            emptyList()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    fun extractId(url: String): String {
        listOf(
            Regex("[?&]v=([A-Za-z0-9_-]{11})"),
            Regex("youtu\\.be/([A-Za-z0-9_-]{11})"),
            Regex("/shorts/([A-Za-z0-9_-]{11})"),
            Regex("^([A-Za-z0-9_-]{11})\$"),
        ).forEach { p -> p.find(url)?.groupValues?.get(1)?.let { return it } }
        return url.takeLast(11)
    }

    private fun StreamInfoItem.toTrack(): Track {
        val id = extractId(url)
        return Track(
            id             = id,
            title          = name,
            author         = uploaderName ?: "YouTube",
            thumbnailUrl   = thumbnails.firstOrNull()?.url ?: thumb(id),
            durationSeconds = duration,
        )
    }

    private fun thumb(id: String) = "https://img.youtube.com/vi/$id/hqdefault.jpg"
}
