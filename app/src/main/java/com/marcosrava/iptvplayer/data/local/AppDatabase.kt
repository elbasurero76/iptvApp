package com.marcosrava.iptvplayer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.marcosrava.iptvplayer.data.model.Channel
import com.marcosrava.iptvplayer.data.model.EpgProgram
import com.marcosrava.iptvplayer.data.model.Playlist
import com.marcosrava.iptvplayer.data.model.PlaylistSource

@Database(
    entities = [Playlist::class, Channel::class, EpgProgram::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun channelDao(): ChannelDao
    abstract fun epgDao(): EpgDao
}

class Converters {
    @TypeConverter
    fun fromPlaylistSource(value: PlaylistSource): String = value.name

    @TypeConverter
    fun toPlaylistSource(value: String): PlaylistSource =
        PlaylistSource.valueOf(value)
}
