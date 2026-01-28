package com.example.myapplication

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataManager(
    private val context: Context,
    private val testResultRepository: com.example.myapplication.data.TestResultRepository,
    private val userRepository: com.example.myapplication.data.UserRepository
) {
    private val localStorage by lazy { LocalStorage(context) }

    // ПОЛУЧЕНИЕ истории - основная версия Long
    fun getTestHistoryFromRoom(userId: Long): Flow<List<TestResult>> {
        return flow {
            try {
                testResultRepository.getTestHistory(userId).collect { history ->
                    emit(history)
                }
            } catch (e: Exception) {
                println("❌ Ошибка при загрузке истории: ${e.message}")
                emit(emptyList())
            }
        }
    }

    // ВЕРСИЯ для Int (для совместимости)
    fun getTestHistoryFromRoom(userId: Int): Flow<List<TestResult>> {
        return getTestHistoryFromRoom(userId.toLong())
    }

    // Сохранение теста - УПРОЩЕННАЯ версия (без SyncUtils пока)
    suspend fun saveTestResult(
        userId: Long,
        studentName: String,
        score: Int,
        date: String,
        answers: List<Int>,
        recommendations: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Сохраняем в Room
                testResultRepository.saveTestResult(
                    userId = userId,
                    studentName = studentName,
                    score = score,
                    date = date,
                    answers = answers,
                    recommendations = recommendations
                )
                println("✅ Тест сохранен в Room: $score баллов")

                // 2. УПРОЩЕННАЯ синхронизация - сначала сделаем без SyncUtils
                try {
                    // Пробуем получить приложение для синхронизации
                    val app = context.applicationContext as? PsyHelperApplication
                    app?.firebaseRepository?.syncTestResult(
                        userId = userId,
                        studentName = studentName,
                        score = score,
                        date = date,
                        answers = answers,
                        recommendations = recommendations
                    )
                    println("☁️ Тест отправлен в Firebase")
                } catch (e: Exception) {
                    println("⚠️ Не удалось синхронизировать тест: ${e.message}")
                    // Продолжаем без синхронизации
                }

                // 3. Для совместимости с LocalStorage
                val testResult = TestResult(
                    id = date.hashCode(),
                    studentId = userId.toInt(),
                    studentName = studentName,
                    score = score,
                    date = date,
                    answers = answers,
                    recommendations = recommendations
                )
                localStorage.saveTestResult(testResult)

            } catch (e: Exception) {
                println("❌ Ошибка сохранения: ${e.message}")
            }
        }
    }

    // Остальные методы без изменений
    suspend fun getTestHistoryFromLocalStorage(): List<TestResult> {
        return withContext(Dispatchers.IO) {
            try {
                localStorage.loadTestHistory()
            } catch (e: Exception) {
                println("❌ Ошибка загрузки из LocalStorage: ${e.message}")
                emptyList()
            }
        }
    }

    suspend fun saveUserData(userData: UserData, role: Int) {
        withContext(Dispatchers.IO) {
            try {
                localStorage.saveUserData(userData, role)
                println("✅ Пользователь сохранен: ${userData.fullName}, роль: $role")
            } catch (e: Exception) {
                println("❌ Ошибка сохранения пользователя: ${e.message}")
            }
        }
    }

    suspend fun loadUserData(): Pair<UserData?, Int> {
        return withContext(Dispatchers.IO) {
            try {
                localStorage.loadUserData()
            } catch (e: Exception) {
                println("❌ Ошибка загрузки пользователя: ${e.message}")
                Pair(null, 0)
            }
        }
    }

    suspend fun clearUserData() {
        withContext(Dispatchers.IO) {
            try {
                localStorage.clearUserData()
                println("🧹 Данные пользователя очищены")
            } catch (e: Exception) {
                println("❌ Ошибка очистки данных: ${e.message}")
            }
        }
    }

    // МИГРАЦИЯ с Long
    suspend fun migrateOldData(userId: Long) {
        withContext(Dispatchers.IO) {
            try {
                val oldHistory = localStorage.loadTestHistory()
                println("🔄 Найдено ${oldHistory.size} старых тестов для миграции")

                if (oldHistory.isNotEmpty()) {
                    var migratedCount = 0
                    oldHistory.forEach { testResult ->
                        val existing = testResultRepository.getLastTestResult(userId)
                        if (existing == null || existing.date != testResult.date) {
                            testResultRepository.saveTestResult(
                                userId = userId,
                                studentName = testResult.studentName,
                                score = testResult.score,
                                date = testResult.date,
                                answers = testResult.answers,
                                recommendations = testResult.recommendations
                            )
                            migratedCount++
                        }
                    }
                    println("✅ Успешно мигрировано $migratedCount тестов в Room")
                }
            } catch (e: Exception) {
                println("⚠️ Миграция не удалась: ${e.message}")
            }
        }
    }
}