package com.example.myapplication.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TestResultDao {
    @Insert
    suspend fun insertTestResult(result: TestResultEntity)

    @Query("SELECT * FROM test_results WHERE user_id = :userId ORDER BY date DESC")
    fun getTestHistoryByUser(userId: Long): Flow<List<TestResultEntity>>

    @Query("SELECT * FROM test_results WHERE user_id = :userId ORDER BY date DESC LIMIT 1")
    suspend fun getLastTestResult(userId: Long): TestResultEntity?

    @Query("DELETE FROM test_results WHERE id = :id")
    suspend fun deleteTestResult(id: Long)

    @Query("SELECT AVG(score) FROM test_results WHERE user_id = :userId")
    suspend fun getAverageScore(userId: Long): Float?
}