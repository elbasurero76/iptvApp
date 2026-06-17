package com.marcosrava.iptvplayer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
    val lastUpdated: Long = 0L,
    val channelCount: Int = 0,
    val epgUrl: String? = null,
    val userAgent: String? = null,
    val autoRefresh: Boolean = false,
    val refreshIntervalHours: Int = 24,
    val source: PlaylistSource = PlaylistSource.URL
)

enum class PlaylistSource {
    URL,        // URL directa
    LOCAL,      // Archivo local
    UBUNTU      // Descargado del servidor Ubuntu
}
