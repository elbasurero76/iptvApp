package com.marcosrava.iptvplayer.di

import android.content.Context
import androidx.room.Room
import com.marcosrava.iptvplayer.data.local.AppDatabase
import com.marcosrava.iptvplayer.data.local.ChannelDao
import com.marcosrava.iptvplayer.data.local.EpgDao
import com.marcosrava.iptvplayer.data.local.PlaylistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "iptv_player.db"
        ).build()
    }

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideChannelDao(db: AppDatabase): ChannelDao = db.channelDao()

    @Provides
    fun provideEpgDao(db: AppDatabase): EpgDao = db.epgDao()
}
