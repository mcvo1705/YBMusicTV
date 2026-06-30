package com.ybmusic.tv.data.youtube

import android.util.Log
import com.ybmusic.tv.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "YouTubeSource"
private const val STREAM_TAG = "Playback/Stream"

/** Một trang kết quả tìm kiếm + cờ cho biết còn trang sau (continuation) hay không. */
data class SearchPage(val tracks: List<Track>, val hasMore: Boolean)

private const val TTL_MS = 2L * 60 * 60 * 1000   // 2 h cache

@Singleton
class YouTubeSource @Inject constructor() {

    // ── Stream URL cache ──────────────────────────────────────────────────────
    private data class Cached(val url: String, val expiresAt: Long)
    private val cache = HashMap<String, Cached>()

    // ── Search pagination state ─────────────────────────────────────────────────
    // Giữ lại query handler + con trỏ trang kế tiếp để load thêm kết quả khi cuộn.
    // YouTube trả về ~20 item mỗi trang; muốn lấy hết phải đi theo continuation
    // token (Page) qua SearchInfo.getMoreItems.
    private var searchHandler: SearchQueryHandler? = null
    private var nextPage: Page? = null

    // ── Search ────────────────────────────────────────────────────────────────
    // KHÔNG bắt exception ở đây. Trước đây dùng runCatching {}.getOrElse {
    // emptyList() } khiến mọi lỗi thật (mất mạng, NewPipe bị YouTube chặn,
    // parser lỗi do YouTube đổi định dạng...) bị nuốt mất và UI luôn hiện
    // "không tìm thấy kết quả" — sai hoàn toàn với lý do thật. Để exception
    // bay lên MainViewModel.search(), nơi đã có UiState.Error xử lý đúng và
    // hiển thị message cụ thể cho người dùng.
    suspend fun search(query: String): SearchPage = withContext(Dispatchers.IO) {
        try {
            val handler = YouTube.searchQHFactory.fromQuery(
                query,
                listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
                "",
            )
            val info = SearchInfo.getInfo(YouTube, handler)
            logExtractorErrors("search('$query')", info.errors)
            searchHandler = handler
            nextPage = info.nextPage
            SearchPage(
                tracks  = info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toTrack() },
                hasMore = nextPage != null,
            )
        } catch (e: Exception) {
            Log.e(TAG, "search('$query') FAILED: ${e.message}", e)
            throw e
        }
    }

    // Lấy trang kết quả tiếp theo của lần search() gần nhất. Trả về trang rỗng,
    // hasMore=false khi không còn continuation.
    suspend fun searchMore(): SearchPage = withContext(Dispatchers.IO) {
        val handler = searchHandler ?: return@withContext SearchPage(emptyList(), false)
        val page    = nextPage ?: return@withContext SearchPage(emptyList(), false)
        try {
            val more = SearchInfo.getMoreItems(YouTube, handler, page)
            logExtractorErrors("searchMore", more.errors)
            nextPage = more.nextPage
            SearchPage(
                tracks  = more.items.filterIsInstance<StreamInfoItem>().map { it.toTrack() },
                hasMore = more.hasNextPage() && nextPage != null,
            )
        } catch (e: Exception) {
            Log.e(TAG, "searchMore FAILED: ${e.message}", e)
            throw e
        }
    }

    // ── Audio stream URL ──────────────────────────────────────────────────────
    suspend fun streamUrl(videoId: String): String = withContext(Dispatchers.IO) {
        cache[videoId]?.let {
            if (it.expiresAt > System.currentTimeMillis()) {
                Log.d(STREAM_TAG, "streamUrl($videoId): CACHE HIT")
                return@withContext it.url
            }
        }

        try {
            Log.d(STREAM_TAG, "streamUrl($videoId): fetching StreamInfo from NewPipe…")
            val info = StreamInfo.getInfo(YouTube, "https://www.youtube.com/watch?v=$videoId")
            logExtractorErrors("streamUrl($videoId)", info.errors)
            Log.d(STREAM_TAG, "streamUrl($videoId): '${info.name}' — ${info.audioStreams.size} audio stream(s)")

            // ExoPlayer (progressive MediaItem) chỉ phát được URL tải trực tiếp, KHÔNG
            // phát được manifest DASH/HLS. Vì vậy chỉ chọn trong các audio stream có
            // deliveryMethod PROGRESSIVE_HTTP và có content URL thật.
            val progressive = info.audioStreams.filter {
                it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && !it.content.isNullOrEmpty()
            }

            val url: String
            if (progressive.isNotEmpty()) {
                // Ưu tiên opus (nhỏ, chất lượng tốt) → mp4a → bất kỳ
                val best = progressive.maxByOrNull { s ->
                    val codec = when {
                        s.codec?.contains("opus", true) == true -> 10_000
                        s.codec?.contains("mp4a", true) == true ->  5_000
                        else -> 0
                    }
                    codec + (if (s.averageBitrate > 0) s.averageBitrate else s.bitrate).coerceAtMost(160)
                }!!
                url = best.content!!
                Log.d(STREAM_TAG, "streamUrl($videoId): selected ${best.format?.name} " +
                    "${best.averageBitrate}kbps progressive audio codec=${best.codec}")
            } else {
                // YouTube đang siết SABR (server-side adaptive bitrate): nhiều video chỉ
                // còn trả về 1 file MP4 progressive đã muxed sẵn audio+video (thường
                // 360p), không còn audio-only stream riêng. ExoPlayer vẫn phát được file
                // này, chỉ tốn băng thông hơn vì tải kèm video không dùng tới. Lấy
                // resolution thấp nhất để giảm lãng phí.
                val videoProgressive = info.videoStreams.filter {
                    it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && !it.content.isNullOrEmpty()
                }
                if (videoProgressive.isEmpty()) {
                    val kinds = (info.audioStreams + info.videoStreams)
                        .joinToString { "${it.format?.name}/${it.deliveryMethod}" }
                    error("No progressive audio or video+audio stream for $videoId (available: [$kinds])")
                }
                val fallback = videoProgressive.minByOrNull {
                    it.resolution?.filter(Char::isDigit)?.toIntOrNull() ?: Int.MAX_VALUE
                }!!
                url = fallback.content!!
                Log.w(STREAM_TAG, "streamUrl($videoId): NO audio-only stream (SABR?), " +
                    "falling back to muxed video '${fallback.resolution}' ${fallback.format?.name}")
            }

            Log.d(STREAM_TAG, "streamUrl($videoId): URL (len=${url.length}) ${url.take(100)}…")
            cache[videoId] = Cached(url, System.currentTimeMillis() + TTL_MS)
            url
        } catch (e: Exception) {
            // Yêu cầu: khi trích xuất stream lỗi, log exception + videoId. Phản hồi
            // gốc của extractor (HTTP/body) đã được NewPipe/Downloader log riêng.
            Log.e(STREAM_TAG, "streamUrl FAILED videoId=$videoId: ${e.message}", e)
            throw e
        }
    }

    // ── Single video info ─────────────────────────────────────────────────────
    suspend fun videoInfo(videoId: String): Track = withContext(Dispatchers.IO) {
        try {
            val info = StreamInfo.getInfo(YouTube, "https://www.youtube.com/watch?v=$videoId")
            logExtractorErrors("videoInfo($videoId)", info.errors)
            Track(
                id             = videoId,
                title          = info.name,
                author         = info.uploaderName ?: "YouTube",
                thumbnailUrl   = info.thumbnails.firstOrNull()?.url ?: thumb(videoId),
                durationSeconds = info.duration,
            )
        } catch (e: Exception) {
            Log.e(TAG, "videoInfo FAILED videoId=$videoId: ${e.message}", e)
            throw e
        }
    }

    // ── Playlist ──────────────────────────────────────────────────────────────
    // Cùng lý do với search(): không nuốt lỗi ở đây nữa.
    suspend fun playlist(url: String): List<Track> = withContext(Dispatchers.IO) {
        try {
            val info = PlaylistInfo.getInfo(YouTube, url)
            logExtractorErrors("playlist($url)", info.errors)
            info.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .map { it.toTrack() }
        } catch (e: Exception) {
            Log.e(TAG, "playlist FAILED url=$url: ${e.message}", e)
            throw e
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // NewPipe gom các lỗi không chí mạng (một phần dữ liệu parse hỏng nhưng vẫn
    // trích xuất được phần còn lại) vào Info.errors. Log lại để biết khi YouTube
    // đổi định dạng làm rụng một số field, dù request không ném exception.
    private fun logExtractorErrors(ctx: String, errors: List<Throwable>) {
        if (errors.isEmpty()) return
        Log.w(TAG, "$ctx: ${errors.size} non-fatal extractor error(s): " +
            errors.joinToString(" | ") { "${it.javaClass.simpleName}: ${it.message}" })
    }

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
