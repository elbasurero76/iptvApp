package com.marcosrava.iptvplayer.data.local

import androidx.room.*
import com.marcosrava.iptvplayer.data.model.Playlist
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): Playlist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("UPDATE playlists SET channelCount = :count, lastUpdated = :timestamp WHERE id = :id")
    suspend fun updatePlaylistStats(id: Long, count: Int, timestamp: Long)

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun getPlaylistCount(): Int
}
