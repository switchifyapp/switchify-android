package com.enaboapps.switchify.service.gestures.patterns.store

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GesturePatternDao {
    @Transaction
    @Query("SELECT * FROM gesture_patterns")
    fun getAllPatterns(): Flow<List<GesturePatternWithGestures>>

    @Transaction
    @Query("SELECT * FROM gesture_patterns WHERE id = :id")
    suspend fun getPatternById(id: String): GesturePatternWithGestures?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPattern(pattern: GesturePatternEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGestures(gestures: List<GestureDataEntity>)

    @Delete
    suspend fun deletePattern(pattern: GesturePatternEntity)

    @Query("DELETE FROM gesture_patterns WHERE id = :id")
    suspend fun deletePatternById(id: String)

    @Query("DELETE FROM gesture_data WHERE patternId = :patternId")
    suspend fun deleteGesturesForPattern(patternId: String)
} 