package com.marcosrava.iptvplayer.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId")]
)
data class Channel(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long,
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val group: String = "",
    val tvgId: String? = null,
    val tvgName: String? = null,
    val isFavorite: Boolean = false,
    val lastWatched: Long? = null,
    val catchupSource: String? = null,
    val catchupDays: Int = 0,
    val userAgent: String? = null,
    val referrer: String? = null
)
