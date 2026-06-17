package com.marcosrava.iptvplayer.data.local

import androidx.room.*
import com.marcosrava.iptvplayer.data.model.EpgProgram
import kotlinx.coroutines.flow.Flow

@Dao
interface EpgDao {
    @Query("SELECT * FROM epg_programs WHERE channelTvgId = :tvgId AND endTime > :now ORDER BY startTime ASC")
    fun getProgramsForChannel(tvgId: String, now: Long = System.currentTimeMillis()): Flow<List<EpgProgram>>

    @Query("SELECT * FROM epg_programs WHERE channelTvgId = :tvgId AND startTime <= :now AND endTime >= :now LIMIT 1")
    suspend fun getCurrentProgram(tvgId: String, now: Long = System.currentTimeMillis()): EpgProgram?

    @Query("SELECT * FROM epg_programs WHERE channelTvgId = :tvgId AND startTime > :now ORDER BY startTime ASC LIMIT 1")
    suspend fun getNextProgram(tvgId: String, now: Long = System.currentTimeMillis()): EpgProgram?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<EpgProgram>)

    @Query("DELETE FROM epg_programs WHERE endTime < :before")
    suspend fun deleteOldPrograms(before: Long)

    @Query("DELETE FROM epg_programs")
    suspend fun clearAll()
}
