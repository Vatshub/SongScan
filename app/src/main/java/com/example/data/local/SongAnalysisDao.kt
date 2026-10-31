package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongAnalysisDao {
    @Query("SELECT * FROM song_analyses ORDER BY analyzedTimestamp DESC")
    fun getAllAnalyses(): Flow<List<SongAnalysisEntity>>

    @Query("SELECT * FROM song_analyses WHERE id = :id LIMIT 1")
    suspend fun getAnalysisById(id: Long): SongAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysis(entity: SongAnalysisEntity): Long

    @Update
    suspend fun updateAnalysis(entity: SongAnalysisEntity)

    @Delete
    suspend fun deleteAnalysis(entity: SongAnalysisEntity)

    @Query("DELETE FROM song_analyses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM song_analyses WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR genre LIKE '%' || :query || '%' ORDER BY analyzedTimestamp DESC")
    fun searchAnalyses(query: String): Flow<List<SongAnalysisEntity>>
}
