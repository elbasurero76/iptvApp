package com.marcosrava.iptvplayer.data.repository

import com.marcosrava.iptvplayer.data.local.ChannelDao
import com.marcosrava.iptvplayer.data.local.EpgDao
import com.marcosrava.iptvplayer.data.local.PlaylistDao
import com.marcosrava.iptvplayer.data.model.Channel
import com.marcosrava.iptvplayer.data.model.EpgProgram
import com.marcosrava.iptvplayer.data.model.Playlist
import com.marcosrava.iptvplayer.parser.M3UParser
import com.marcosrava.iptvplayer.parser.XmltvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val m3uParser: M3UParser,
    private val xmltvParser: XmltvParser,
    private val okHttpClient: OkHttpClient
) {
    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylists()
    val allChannels: Flow<List<Channel>> = channelDao.getAllChannels()
    val favoriteChannels: Flow<List<Channel>> = channelDao.getFavoriteChannels()
    val recentChannels: Flow<List<Channel>> = channelDao.getRecentChannels()
    val allGroups: Flow<List<String>> = channelDao.getAllGroups()

    fun getChannelsByPlaylist(playlistId: Long) = channelDao.getChannelsByPlaylist(playlistId)
    fun getGroupsByPlaylist(playlistId: Long) = channelDao.getGroupsByPlaylist(playlistId)
    fun searchChannels(query: String) = channelDao.searchChannels(query)
    fun getChannelsByGroup(group: String) = channelDao.getChannelsByGroup(group)
    fun getProgramsForChannel(tvgId: String) = epgDao.getProgramsForChannel(tvgId)

    suspend fun addPlaylist(playlist: Playlist): Long = playlistDao.insertPlaylist(playlist)

    suspend fun deletePlaylist(playlist: Playlist) {
        playlistDao.deletePlaylist(playlist)
    }

    suspend fun updatePlaylist(playlist: Playlist) = playlistDao.updatePlaylist(playlist)

    suspend fun setFavorite(channelId: Long, isFavorite: Boolean) {
        channelDao.setFavorite(channelId, isFavorite)
    }

    suspend fun updateLastWatched(channelId: Long) {
        channelDao.updateLastWatched(channelId, System.currentTimeMillis())
    }

    suspend fun refreshPlaylist(playlist: Playlist): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val content = downloadContent(playlist.url, playlist.userAgent)
            val channels = m3uParser.parse(content, playlist.id)
            channelDao.deleteChannelsByPlaylist(playlist.id)
            channelDao.insertChannels(channels)
            playlistDao.updatePlaylistStats(
                playlist.id,
                channels.size,
                System.currentTimeMillis()
            )
            // Refresh EPG if configured
            playlist.epgUrl?.let { epgUrl ->
                runCatching { refreshEpg(epgUrl) }
            }
            Result.success(channels.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshEpg(epgUrl: String) = withContext(Dispatchers.IO) {
        val content = downloadContent(epgUrl)
        val programs = xmltvParser.parse(content)
        epgDao.deleteOldPrograms(System.currentTimeMillis() - 3_600_000) // Clean 1h old
        epgDao.insertPrograms(programs)
    }

    private fun downloadContent(url: String, userAgent: String? = null): String {
        val request = Request.Builder()
            .url(url)
            .apply {
                userAgent?.let { header("User-Agent", it) }
                    ?: header("User-Agent", "IPTVPlayer/1.0 Android")
            }
            .build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
            response.body?.string() ?: throw Exception("Empty response")
        }
    }

    suspend fun getCurrentProgram(tvgId: String): EpgProgram? = epgDao.getCurrentProgram(tvgId)
    suspend fun getNextProgram(tvgId: String): EpgProgram? = epgDao.getNextProgram(tvgId)
}
