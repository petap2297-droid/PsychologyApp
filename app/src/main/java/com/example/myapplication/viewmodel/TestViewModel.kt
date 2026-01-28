package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asLiveData
import com.example.myapplication.data.TestResultRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TestViewModel(private val testResultRepository: TestResultRepository) : ViewModel() {

    // State для истории тестов
    private val _testHistory = MutableStateFlow<List<com.example.myapplication.TestResult>>(emptyList())
    val testHistory: StateFlow<List<com.example.myapplication.TestResult>> = _testHistory.asStateFlow()

    // State для последнего теста
    private val _lastTestResult = MutableStateFlow<com.example.myapplication.TestResult?>(null)
    val lastTestResult: StateFlow<com.example.myapplication.TestResult?> = _lastTestResult.asStateFlow()

    init {
        // Загружаем данные при создании
        loadTestHistory(1L) // временно, потом заменим на реальный ID
    }

    // СОХРАНЕНИЕ результата теста (ВЕРСИЯ ДЛЯ LONG)
    fun saveTestResult(
        userId: Long, // ← Изменяем на Long
        studentName: String,
        score: Int,
        date: String,
        answers: List<Int>,
        recommendations: String
    ) {
        viewModelScope.launch {
            testResultRepository.saveTestResult(
                userId = userId,
                studentName = studentName,
                score = score,
                date = date,
                answers = answers,
                recommendations = recommendations
            )
            loadTestHistory(userId)
        }
    }

    // ВЕРСИЯ ДЛЯ String (для совместимости со старым кодом)
    fun saveTestResult(
        userId: String,
        studentName: String,
        score: Int,
        date: String,
        answers: List<Int>,
        recommendations: String
    ) {
        val userIdLong = userId.toLongOrNull() ?: 0L
        saveTestResult(userIdLong, studentName, score, date, answers, recommendations)
    }

    // ЗАГРУЗКА истории тестов (ВЕРСИЯ ДЛЯ LONG)
    private fun loadTestHistory(userId: Long) {
        viewModelScope.launch {
            testResultRepository.getTestHistory(userId).collect { history ->
                _testHistory.value = history
                _lastTestResult.value = history.firstOrNull()
            }
        }
    }

    // ВЕРСИЯ ДЛЯ String
    fun loadTestHistory(userId: String) {
        val userIdLong = userId.toLongOrNull() ?: 0L
        loadTestHistory(userIdLong)
    }

    // СРЕДНИЙ балл (ВЕРСИЯ ДЛЯ LONG)
    suspend fun getAverageScore(userId: Long): Float? {
        return testResultRepository.getAverageScore(userId)
    }

    // ВЕРСИЯ ДЛЯ String
    suspend fun getAverageScore(userId: String): Float? {
        val userIdLong = userId.toLongOrNull() ?: 0L
        return getAverageScore(userIdLong)
    }
}