package com.ybmusic.tv.data.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import com.ybmusic.tv.data.model.PlaylistEntity
import com.ybmusic.tv.data.model.PlaylistTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :id ORDER BY position ASC")
    fun observeTracks(id: String): Flow<List<PlaylistTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(entity: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: String, trackId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :id")
    suspend fun clearTracks(id: String)

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :id")
    suspend fun trackCount(id: String): Int
}

@Database(
    entities  = [PlaylistEntity::class, PlaylistTrackEntity::class],
    version   = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
}
