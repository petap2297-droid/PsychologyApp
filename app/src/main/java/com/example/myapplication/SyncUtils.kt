// SyncUtils.kt - РАБОЧАЯ ВЕРСИЯ
package com.example.myapplication.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SyncUtils {

    // Ссылка на Application контекст (будет установлена при запуске)
    private var appContext: Context? = null

    // Инициализация при запуске приложения
    fun initialize(context: Context) {
        appContext = context.applicationContext
        println("✅ [SyncUtils] Инициализирован с контекстом приложения")
    }

    // Получение экземпляра PsyHelperApplication
    private fun getApplication(): com.example.myapplication.PsyHelperApplication? {
        return try {
            appContext as? com.example.myapplication.PsyHelperApplication
        } catch (e: Exception) {
            println("⚠️ [SyncUtils] Не удалось получить PsyHelperApplication: ${e.message}")
            null
        }
    }

    // Синхронизация теста
    suspend fun syncTestResult(
        userId: Long,
        studentName: String,
        score: Int,
        date: String,
        answers: List<Int>,
        recommendations: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                println("📊 [SyncUtils] Синхронизация теста: $studentName - $score баллов")

                val app = getApplication()
                if (app != null) {
                    // Используем FirebaseRepository напрямую
                    app.firebaseRepository.syncTestResult(
                        userId = userId,
                        studentName = studentName,
                        score = score,
                        date = date,
                        answers = answers,
                        recommendations = recommendations
                    )
                    println("✅ [SyncUtils] Тест синхронизирован с Firebase")
                } else {
                    println("⚠️ [SyncUtils] Приложение не доступно, откладываем синхронизацию")
                }

            } catch (e: Exception) {
                println("❌ [SyncUtils] Ошибка синхронизации теста: ${e.message}")
            }
        }
    }

    // Синхронизация пользователя
    suspend fun syncUser(user: User) {
        withContext(Dispatchers.IO) {
            try {
                println("👤 [SyncUtils] Синхронизация пользователя: ${user.username}")

                val app = getApplication()
                if (app != null) {
                    app.firebaseRepository.syncUser(user)
                    println("✅ [SyncUtils] Пользователь синхронизирован с Firebase")
                } else {
                    println("⚠️ [SyncUtils] Приложение не доступно")
                }

            } catch (e: Exception) {
                println("❌ [SyncUtils] Ошибка синхронизации пользователя: ${e.message}")
            }
        }
    }

    // Синхронизация сообщения
    suspend fun syncMessage(
        senderId: Long,
        receiverId: Long,
        senderName: String,
        message: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                println("📨 [SyncUtils] Синхронизация сообщения: $senderName → $receiverId")

                val app = getApplication()
                if (app != null) {
                    app.firebaseRepository.syncMessage(
                        senderId = senderId,
                        receiverId = receiverId,
                        senderName = senderName,
                        message = message
                    )
                    println("✅ [SyncUtils] Сообщение синхронизировано с Firebase")
                } else {
                    println("⚠️ [SyncUtils] Приложение не доступно")
                }

            } catch (e: Exception) {
                println("❌ [SyncUtils] Ошибка синхронизации сообщения: ${e.message}")
            }
        }
    }

    // Полная синхронизация всех данных
    suspend fun syncAllData() {
        withContext(Dispatchers.IO) {
            try {
                println("🔄 [SyncUtils] Полная синхронизация всех данных...")

                val app = getApplication()
                if (app != null) {
                    app.syncManager.syncAllData()
                    println("✅ [SyncUtils] Полная синхронизация завершена")
                } else {
                    println("⚠️ [SyncUtils] Приложение не доступно для синхронизации")
                }

            } catch (e: Exception) {
                println("❌ [SyncUtils] Ошибка полной синхронизации: ${e.message}")
            }
        }
    }
}