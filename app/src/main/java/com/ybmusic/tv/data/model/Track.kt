package com.ybmusic.tv.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ─── Domain models ────────────────────────────────────────────────────────────

data class Track(
    val id: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    val durationSeconds: Long = 0L,
)

data class Playlist(
    val id: String,
    val name: String,
)

// ─── UI state ─────────────────────────────────────────────────────────────────

sealed class UiState<out T> {
    data object Idle    : UiState<Nothing>()
    data object Loading : UiState<Nothing>()
    data class  Success<T>(val data: T)      : UiState<T>()
    data class  Error(val message: String)   : UiState<Nothing>()
}

data class PlayerState(
    val currentTrack : Track?    = null,
    val queue        : List<Track> = emptyList(),
    val queueIndex   : Int       = 0,
    val isPlaying    : Boolean   = false,
    val positionMs   : Long      = 0L,
    val durationMs   : Long      = 0L,
    val playMode     : PlayMode  = PlayMode.SEQUENTIAL,
    val isBuffering  : Boolean   = false,
    val error        : String?   = null,
)

enum class PlayMode { SEQUENTIAL, SHUFFLE, REPEAT_ONE }

// ─── Room entities ────────────────────────────────────────────────────────────

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "playlist_tracks")
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val playlistId: String,
    val trackId: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val position: Int,
)
