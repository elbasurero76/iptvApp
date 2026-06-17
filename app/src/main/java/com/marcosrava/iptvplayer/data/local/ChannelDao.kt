package com.marcosrava.iptvplayer.data.local

import androidx.room.*
import com.marcosrava.iptvplayer.data.model.Channel
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY `group` ASC, name ASC")
    fun getChannelsByPlaylist(playlistId: Long): Flow<List<Channel>>

    @Query("SELECT * FROM channels ORDER BY `group` ASC, name ASC")
    fun getAllChannels(): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteChannels(): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE lastWatched IS NOT NULL ORDER BY lastWatched DESC LIMIT 20")
    fun getRecentChannels(): Flow<List<Channel>>

    @Query("SELECT DISTINCT `group` FROM channels WHERE playlistId = :playlistId AND `group` != '' ORDER BY `group` ASC")
    fun getGroupsByPlaylist(playlistId: Long): Flow<List<String>>

    @Query("SELECT DISTINCT `group` FROM channels WHERE `group` != '' ORDER BY `group` ASC")
    fun getAllGroups(): Flow<List<String>>

    @Query("SELECT * FROM channels WHERE (name LIKE '%' || :query || '%' OR tvgName LIKE '%' || :query || '%') ORDER BY name ASC")
    fun searchChannels(query: String): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE `group` = :group ORDER BY name ASC")
    fun getChannelsByGroup(group: String): Flow<List<Channel>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<Channel>)

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE channels SET lastWatched = :timestamp WHERE id = :id")
    suspend fun updateLastWatched(id: Long, timestamp: Long)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteChannelsByPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId")
    suspend fun getChannelCountByPlaylist(playlistId: Long): Int
}
