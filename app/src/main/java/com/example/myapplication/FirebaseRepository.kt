// FirebaseRepository.kt - исправленная версия
package com.example.myapplication.data

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.util.Date
import com.example.myapplication.Question
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.DocumentChange

class FirebaseRepository {
    private val db = Firebase.firestore

    // === ОПРЕДЕЛЯЕМ Question ДЛЯ Firebase ===
    data class FirebaseQuestion(
        val id: String = "",
        val text: String = "",
        val category: String = "общее",
        val order: Int = 0,
        val isActive: Boolean = true
    )
    // === REALTIME LISTENER ДЛЯ СООБЩЕНИЙ МЕЖДУ ДВУМЯ ПОЛЬЗОВАТЕЛЯМИ ===
    fun addConversationRealtimeListener(
        userId1: Long,
        userId2: Long,
        onNewMessage: (MessageEntity) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        println("🎯 [Firebase] Real-time listener для диалога $userId1 ↔ $userId2")

        return db.collection("messages")
            .whereIn("senderId", listOf(userId1.toString(), userId2.toString()))
            .whereIn("receiverId", listOf(userId1.toString(), userId2.toString()))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    println("❌ [Firebase] Ошибка real-time: ${error.message}")
                    onError(error)
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == DocumentChange.Type.ADDED) {
                        val message = convertToMessageEntity(change.document)
                        if (message != null) {
                            println("📩 [Firebase] Новое сообщение в real-time!")
                            onNewMessage(message)
                        }
                    }
                }
            }
    }

    // === REALTIME LISTENER ДЛЯ ВСЕХ СООБЩЕНИЙ ПОЛЬЗОВАТЕЛЯ ===
    fun addUserMessagesRealtimeListener(
        userId: Long,
        onNewMessage: (MessageEntity) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        println("🎯 [Firebase] Real-time listener для всех сообщений пользователя $userId")

        // Слушаем сообщения где пользователь получатель
        return db.collection("messages")
            .whereEqualTo("receiverId", userId.toString())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    println("❌ [Firebase] Ошибка real-time: ${error.message}")
                    onError(error)
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == DocumentChange.Type.ADDED) {
                        val message = convertToMessageEntity(change.document)
                        if (message != null) {
                            println("📩 [Firebase] Новое сообщение для пользователя $userId")
                            onNewMessage(message)
                        }
                    }
                }
            }
    }
    // === СИНХРОНИЗАЦИЯ ПОЛЬЗОВАТЕЛЕЙ ===
    suspend fun syncUser(user: User) {
        try {
            println("🔄 [Firebase] Синхронизация пользователя: ${user.username}")

            val userData = hashMapOf(
                "id" to user.id.toString(),
                "username" to user.username,
                "password" to user.password, // Добавляем пароль
                "firstName" to user.firstName,
                "lastName" to user.lastName,
                "role" to user.role,
                "avatarColor" to user.avatarColor,
                "createdAt" to user.createdAt,
                "syncedAt" to Date().time
            )

            db.collection("users")
                .document(user.id.toString())
                .set(userData)
                .await()

            println("✅ [Firebase] Пользователь синхронизирован: ${user.username}")
        } catch (e: Exception) {
            println("❌ [Firebase] Ошибка синхронизации пользователя: ${e.message}")
        }
    }

    // === СИНХРОНИЗАЦИЯ РЕЗУЛЬТАТОВ ТЕСТОВ ===
    suspend fun syncTestResult(
        userId: Long,
        studentName: String,
        score: Int,
        date: String,
        answers: List<Int>, // Добавляем ответы
        recommendations: String
    ) {
        try {
            println("🔄 [Firebase] Синхронизация теста: $studentName - $score баллов")

            val testData = hashMapOf(
                "userId" to userId.toString(),
                "studentName" to studentName,
                "score" to score,
                "date" to date,
                "answers" to answers.toString(), // Сохраняем как строку
                "recommendations" to recommendations,
                "syncedAt" to Date().time
            )

            val testId = "${userId}_${date.hashCode()}" // Более стабильный ID

            db.collection("testResults")
                .document(testId)
                .set(testData)
                .await()

            println("✅ [Firebase] Тест синхронизирован: $score баллов")
        } catch (e: Exception) {
            println("❌ [Firebase] Ошибка синхронизации теста: ${e.message}")
        }
    }

    // === СИНХРОНИЗАЦИЯ СООБЩЕНИЙ ===
    suspend fun syncMessage(
        senderId: Long,
        receiverId: Long,
        senderName: String,
        message: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        try {
            println("🔄 [Firebase] Синхронизация сообщения: $senderName → $receiverId")

            val messageData = hashMapOf(
                "senderId" to senderId.toString(),
                "receiverId" to receiverId.toString(),
                "senderName" to senderName,
                "message" to message,
                "timestamp" to timestamp,
                "isRead" to false,
                "syncedAt" to Date().time
            )

            val messageId = "${senderId}_${receiverId}_${timestamp}"

            db.collection("messages")
                .document(messageId)
                .set(messageData)
                .await()

            println("✅ [Firebase] Сообщение синхронизировано")
        } catch (e: Exception) {
            println("❌ [Firebase] Ошибка синхронизации сообщения: ${e.message}")
        }
    }

    // === ЗАГРУЗКА ВОПРОСОВ С СЕРВЕРА ===
    suspend fun loadQuestionsFromFirebase(): List<Question> {
        return try {
            println("🔄 [Firebase] Загрузка вопросов с сервера...")

            val snapshot = db.collection("questions")
                .whereEqualTo("isActive", true)
                .orderBy("order")
                .get()
                .await()

            val questions = snapshot.documents.mapNotNull { doc ->
                val data = doc.data
                if (data != null) {
                    Question(
                        text = data["text"] as? String ?: "",
                        category = data["category"] as? String ?: "общее"
                    )
                } else {
                    null
                }
            }

            println("✅ [Firebase] Загружено ${questions.size} вопросов")
            questions
        } catch (e: Exception) {
            println("❌ [Firebase] Ошибка загрузки вопросов: ${e.message}")
            // Если нет интернета, возвращаем пустой список
            emptyList()
        }
    }

    // === ЗАГРУЗКА ПОЛЬЗОВАТЕЛЕЙ С СЕРВЕРА ===
    suspend fun loadUsersFromFirebase(role: String? = null): List<User> {
        return try {
            println("🔄 [Firebase] Загрузка пользователей с сервера...")

            val query = if (role != null) {
                db.collection("users").whereEqualTo("role", role)
            } else {
                db.collection("users")
            }

            val snapshot = query.get().await()

            val users = snapshot.documents.mapNotNull { doc ->
                val data = doc.data
                if (data != null) {
                    User(
                        id = (data["id"] as? String)?.toLongOrNull() ?: 0L,
                        username = data["username"] as? String ?: "",
                        password = data["password"] as? String ?: "",
                        firstName = data["firstName"] as? String ?: "",
                        lastName = data["lastName"] as? String ?: "",
                        role = data["role"] as? String ?: "ученик",
                        avatarColor = (data["avatarColor"] as? Long)?.toInt() ?: 0,
                        createdAt = (data["createdAt"] as? Long) ?: System.currentTimeMillis()
                    )
                } else {
                    null
                }
            }

            println("✅ [Firebase] Загружено ${users.size} пользователей")
            users
        } catch (e: Exception) {
            println("❌ [Firebase] Ошибка загрузки пользователей: ${e.message}")
            emptyList()
        }
    }

    // === ПРОВЕРКА ПОДКЛЮЧЕНИЯ К FIREBASE ===
    suspend fun testConnection(): Boolean {
        return try {
            db.collection("test").document("connection").get().await()
            println("✅ [Firebase] Подключение к Firebase успешно")
            true
        } catch (e: Exception) {
            println("❌ [Firebase] Нет подключения к Firebase: ${e.message}")
            false
        }
    }
    // 1. Загрузка сообщений между двумя пользователями
    suspend fun loadMessagesBetweenUsers(userId1: Long, userId2: Long): List<MessageEntity> {
        return try {
            println("🔄 [Firebase] Загрузка сообщений $userId1 ↔ $userId2")

            val snapshot = db.collection("messages")
                .whereIn("senderId", listOf(userId1.toString(), userId2.toString()))
                .whereIn("receiverId", listOf(userId1.toString(), userId2.toString()))
                .orderBy("timestamp")
                .get()
                .await()

            snapshot.documents.mapNotNull { doc -> convertToMessageEntity(doc) }

        } catch (e: Exception) {
            println("❌ Ошибка загрузки сообщений: ${e.message}")
            emptyList()
        }
    }

    // 2. Загрузка всех сообщений пользователя
    suspend fun loadAllMessagesForUser(userId: Long): List<MessageEntity> {
        return try {
            println("🔄 [Firebase] Загрузка всех сообщений пользователя $userId")

            // Получаем сообщения где пользователь отправитель ИЛИ получатель
            val snapshot = db.collection("messages")
                .whereEqualTo("senderId", userId.toString())
                .get()
                .await()

            val sentMessages = snapshot.documents.mapNotNull { convertToMessageEntity(it) }

            val receivedSnapshot = db.collection("messages")
                .whereEqualTo("receiverId", userId.toString())
                .get()
                .await()

            val receivedMessages = receivedSnapshot.documents.mapNotNull { convertToMessageEntity(it) }

            (sentMessages + receivedMessages).sortedBy { it.timestamp }

        } catch (e: Exception) {
            println("❌ Ошибка загрузки сообщений пользователя: ${e.message}")
            emptyList()
        }
    }

    // 3. Вспомогательный метод для конвертации
    private fun convertToMessageEntity(doc: DocumentSnapshot): MessageEntity? {
        val data = doc.data ?: return null

        return MessageEntity(
            id = 0, // Room сам сгенерирует
            senderId = (data["senderId"] as? String)?.toLongOrNull() ?: 0L,
            receiverId = (data["receiverId"] as? String)?.toLongOrNull() ?: 0L,
            senderName = data["senderName"] as? String ?: "",
            message = data["message"] as? String ?: "",
            timestamp = (data["timestamp"] as? Long) ?: System.currentTimeMillis(),
            isRead = data["isRead"] as? Boolean ?: false
        )
    }
    // Удаление пользователя из Firestore (УСИЛЕННАЯ ВЕРСИЯ)
    suspend fun deleteUser(userId: Long) {
        println("🔥 [Firebase] Попытка удалить пользователя с ID: $userId")

        try {
            val usersCollection = db.collection("users")

            // 1. Ищем по числовому ID (как Long)
            val queryLong = usersCollection.whereEqualTo("id", userId).get().await()

            // 2. Ищем по строковому ID (на всякий случай, если в базе записано "1" вместо 1)
            // (Это частая проблема при импорте/экспорте)
            val queryString = usersCollection.whereEqualTo("id", userId.toString()).get().await()

            // Объединяем результаты (убираем дубликаты)
            val documents = (queryLong.documents + queryString.documents).distinctBy { it.id }

            if (documents.isEmpty()) {
                println("⚠️ [Firebase] Пользователь с ID $userId НЕ НАЙДЕН в облаке!")
                return
            }

            println("🔥 [Firebase] Найдено документов для удаления: ${documents.size}")

            val batch = db.batch() // Используем Batch для надежности

            for (document in documents) {
                println("🔥 [Firebase] Удаляю документ: ${document.id} (username: ${document.getString("username")})")
                batch.delete(document.reference)
            }

            batch.commit().await()
            println("✅ [Firebase] Удаление завершено успешно")

        } catch (e: Exception) {
            println("❌ [Firebase] Ошибка при удалении: ${e.message}")
            e.printStackTrace()
        }
    }
    // === УПРАВЛЕНИЕ ВОПРОСАМИ ===

    // 1. Получить вопросы из облака
    suspend fun getQuestions(): List<Question> {
        return try {
            val snapshot = db.collection("questions")
                .orderBy("id") // Сортируем по порядку
                .get()
                .await()

            snapshot.documents.map { doc ->
                Question(
                    id = doc.getLong("id")?.toInt() ?: 0,
                    text = doc.getString("text") ?: "",
                    category = doc.getString("category") ?: "Общее",
                    options = doc.get("options") as? List<String> ?: listOf("Никогда", "Всегда")
                )
            }
        } catch (e: Exception) {
            println("⚠️ [Firebase] Ошибка загрузки вопросов: ${e.message}")
            emptyList() // Если ошибка или нет инета - вернем пустой список
        }
    }

    // 2. Сохранить вопрос (Для Админки)
    suspend fun saveQuestion(question: Question) {
        val data = hashMapOf(
            "id" to question.id,
            "text" to question.text,
            "category" to question.category,
            "options" to question.options
        )
        // Используем ID вопроса как ID документа, чтобы легко обновлять
        db.collection("questions").document(question.id.toString())
            .set(data)
            .await()
    }

    // 3. Удалить вопрос
    suspend fun deleteQuestion(questionId: Int) {
        db.collection("questions").document(questionId.toString())
            .delete()
            .await()
    }




}
