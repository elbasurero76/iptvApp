package com.marcosrava.iptvplayer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "epg_programs")
data class EpgProgram(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val channelTvgId: String,
    val title: String,
    val description: String = "",
    val startTime: Long,
    val endTime: Long,
    val category: String = "",
    val iconUrl: String? = null,
    val rating: String? = null
) {
    val isLive: Boolean
        get() {
            val now = System.currentTimeMillis()
            return now in startTime..endTime
        }

    val progressPercent: Float
        get() {
            val now = System.currentTimeMillis()
            if (now < startTime) return 0f
            if (now > endTime) return 1f
            return (now - startTime).toFloat() / (endTime - startTime).toFloat()
        }
}

data class RemoteFile(
    val name: String,
    val url: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: String = ""
)
