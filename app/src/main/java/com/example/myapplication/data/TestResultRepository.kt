package com.example.myapplication.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TestResultRepository(private val testResultDao: TestResultDao) {

    // ОСНОВНАЯ версия - использует Long
    suspend fun saveTestResult(
        userId: Long, // ← Меняем на Long
        studentName: String,
        score: Int,
        date: String,
        answers: List<Int>,
        recommendations: String,
        categoryScores: Map<String, Int>? = null
    ) {
        val entity = TestResultEntity(
            userId = userId, // ← Теперь Long
            studentName = studentName,
            score = score,
            date = date,
            answersJson = answers.joinToString(","),
            recommendations = recommendations,
            categoryScores = categoryScores?.let {
                it.entries.joinToString(";") { (key, value) -> "$key:$value" }
            } ?: ""
        )
        testResultDao.insertTestResult(entity)
    }

    // ВЕРСИЯ ДЛЯ Int (для совместимости со старым кодом)
    suspend fun saveTestResult(
        userId: Int,
        studentName: String,
        score: Int,
        date: String,
        answers: List<Int>,
        recommendations: String
    ) {
        saveTestResult(
            userId = userId.toLong(), // Конвертируем Int → Long
            studentName = studentName,
            score = score,
            date = date,
            answers = answers,
            recommendations = recommendations
        )
    }

    // ИСТОРИЯ тестов - основная версия Long
    fun getTestHistory(userId: Long): Flow<List<com.example.myapplication.TestResult>> {
        return testResultDao.getTestHistoryByUser(userId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    // ВЕРСИЯ ДЛЯ Int (для совместимости)
    fun getTestHistory(userId: Int): Flow<List<com.example.myapplication.TestResult>> {
        return getTestHistory(userId.toLong())
    }

    // ПОСЛЕДНИЙ тест - основная версия Long
    suspend fun getLastTestResult(userId: Long): com.example.myapplication.TestResult? {
        return testResultDao.getLastTestResult(userId)?.toDomainModel()
    }

    // ПОСЛЕДНИЙ тест - версия для Int
    suspend fun getLastTestResult(userId: Int): com.example.myapplication.TestResult? {
        return getLastTestResult(userId.toLong())
    }

    // СРЕДНИЙ балл - основная версия Long
    suspend fun getAverageScore(userId: Long): Float? {
        return testResultDao.getAverageScore(userId)
    }

    // СРЕДНИЙ балл - версия для Int
    suspend fun getAverageScore(userId: Int): Float? {
        return getAverageScore(userId.toLong())
    }

    // Конвертация Entity → Domain Model
    private fun TestResultEntity.toDomainModel(): com.example.myapplication.TestResult {
        val answers = try {
            answersJson.split(",").mapNotNull { it.toIntOrNull() }
        } catch (e: Exception) {
            emptyList()
        }

        return com.example.myapplication.TestResult(
            id = id.toInt(),
            studentId = this.userId.toInt(), // userId теперь Long, конвертируем в Int для TestResult
            studentName = studentName,
            score = score,
            date = date,
            answers = answers,
            recommendations = recommendations
        )
    }
}