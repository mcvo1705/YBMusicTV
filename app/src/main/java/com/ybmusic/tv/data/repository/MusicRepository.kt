package com.ybmusic.tv.data.repository

import com.ybmusic.tv.data.database.AppDatabase
import com.ybmusic.tv.data.model.Playlist
import com.ybmusic.tv.data.model.PlaylistEntity
import com.ybmusic.tv.data.model.PlaylistTrackEntity
import com.ybmusic.tv.data.model.Track
import com.ybmusic.tv.data.youtube.YouTubeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val yt : YouTubeSource,
    private val db : AppDatabase,
) {

    // ── YouTube ───────────────────────────────────────────────────────────────

    suspend fun search(query: String)      = yt.search(query)
    suspend fun streamUrl(id: String)      = yt.streamUrl(id)
    suspend fun videoInfo(id: String)      = yt.videoInfo(id)
    suspend fun ytPlaylist(url: String)    = yt.playlist(url)
    fun extractId(url: String)             = yt.extractId(url)

    fun isYtUrl(text: String) = text.contains("youtube.com") || text.contains("youtu.be")
    fun isYtPlaylist(url: String) = url.contains("list=")

    // ── Playlists (Room) ──────────────────────────────────────────────────────

    fun observePlaylists(): Flow<List<Playlist>> =
        db.playlistDao().observeAll().map { list ->
            list.map { Playlist(it.id, it.name) }
        }

    fun observeTracks(playlistId: String): Flow<List<Track>> =
        db.playlistDao().observeTracks(playlistId).map { list ->
            list.map { Track(it.trackId, it.title, it.author, it.thumbnailUrl, it.durationSeconds) }
        }

    suspend fun createPlaylist(name: String): String {
        val id = "pl_${System.currentTimeMillis()}"
        db.playlistDao().insert(PlaylistEntity(id, name))
        return id
    }

    suspend fun deletePlaylist(id: String) {
        db.playlistDao().delete(id)
        db.playlistDao().clearTracks(id)
    }

    suspend fun addTrack(playlistId: String, track: Track) {
        val pos = db.playlistDao().trackCount(playlistId)
        db.playlistDao().insertTrack(
            PlaylistTrackEntity(
                playlistId      = playlistId,
                trackId         = track.id,
                title           = track.title,
                author          = track.author,
                thumbnailUrl    = track.thumbnailUrl,
                durationSeconds = track.durationSeconds,
                position        = pos,
            )
        )
    }

    suspend fun removeTrack(playlistId: String, trackId: String) =
        db.playlistDao().removeTrack(playlistId, trackId)
}
