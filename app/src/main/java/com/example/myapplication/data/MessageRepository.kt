package com.example.myapplication.data

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.map

class MessageRepository(
    private val messageDao: MessageDao
) {

    // StateFlow для принудительного обновления UI
    private val _refreshTrigger = MutableStateFlow(0)

    // ОСНОВНАЯ версия - с возможностью передачи syncManager
    suspend fun sendMessage(
        senderId: Long,
        receiverId: Long,
        senderName: String,
        message: String,
        syncManager: SyncManager? = null
    ) {
        println("📤 [MessageRepository] Отправка сообщения:")
        println("   От: $senderId ($senderName)")
        println("   Кому: $receiverId")
        println("   Текст: $message")

        val entity = MessageEntity(
            senderId = senderId,
            receiverId = receiverId,
            senderName = senderName,
            message = message,
            timestamp = System.currentTimeMillis(),
            isRead = false
        )

        try {
            // 1. Сохраняем в Room
            val insertedId = messageDao.insertMessage(entity)
            println("✅ [MessageRepository] Сообщение сохранено в БД, ID: $insertedId")

            // 2. СИНХРОНИЗИРУЕМ С FIREBASE (если передан syncManager)
            syncManager?.let {
                it.syncOnMessageSend(
                    senderId = senderId,
                    receiverId = receiverId,
                    senderName = senderName,
                    message = message
                )
                println("☁️ [MessageRepository] Сообщение отправлено в Firebase")
            }

            // 3. Альтернативно через SyncUtils (если реализован)
            try {
                // SyncUtils может быть не реализован, поэтому в try-catch
                // SyncUtils.syncMessage(senderId, receiverId, senderName, message)
            } catch (e: Exception) {
                // Игнорируем если SyncUtils нет
            }

            // 4. Триггерим обновление UI
            _refreshTrigger.value++
            println("🔄 [MessageRepository] Триггер обновления: ${_refreshTrigger.value}")

        } catch (e: Exception) {
            println("❌ [MessageRepository] Ошибка: ${e.message}")
            throw e
        }
    }

    // ВЕРСИЯ для String (для совместимости)
    suspend fun sendMessage(
        senderId: String,
        receiverId: String,
        senderName: String,
        message: String,
        syncManager: SyncManager? = null
    ) {
        val senderIdLong = senderId.toLongOrNull() ?: 0L
        val receiverIdLong = receiverId.toLongOrNull() ?: 0L
        sendMessage(senderIdLong, receiverIdLong, senderName, message, syncManager)
    }
    // ДИАЛОГ с автоматическим обновлением
    fun getConversation(userId1: Long, userId2: Long): Flow<List<com.example.myapplication.ChatMessage>> {
        return _refreshTrigger.flatMapLatest { trigger ->
            println("🔄 [MessageRepository] Загрузка диалога (триггер: $trigger)")
            messageDao.getConversation(userId1, userId2)
        }.map { entities ->
            println("📨 [MessageRepository] Загружено ${entities.size} сообщений")
            entities.map { it.toDomainModel() }
        }
    }

    // ВЕРСИЯ для String
    fun getConversation(userId1: String, userId2: String): Flow<List<com.example.myapplication.ChatMessage>> {
        val userId1Long = userId1.toLongOrNull() ?: 0L
        val userId2Long = userId2.toLongOrNull() ?: 0L
        return getConversation(userId1Long, userId2Long)
    }

    // ПОМЕТКА как прочитанные - Long
    suspend fun markAsRead(userId: Long, senderId: Long) {
        println("👁️ [MessageRepository] Пометить как прочитанные: $senderId → $userId")
        messageDao.markConversationAsRead(senderId, userId)
        _refreshTrigger.value++ // Обновляем UI
    }

    // ВЕРСИЯ для String
    suspend fun markAsRead(userId: String, senderId: String) {
        val userIdLong = userId.toLongOrNull() ?: 0L
        val senderIdLong = senderId.toLongOrNull() ?: 0L
        markAsRead(userIdLong, senderIdLong)
    }

    // НЕПРОЧИТАННЫЕ - Long
    suspend fun getUnreadCount(userId: Long): Int {
        return messageDao.getUnreadCount(userId).also { count ->
            println("🔔 [MessageRepository] Непрочитанных для $userId: $count")
        }
    }

    // ВЕРСИЯ для String
    suspend fun getUnreadCount(userId: String): Int {
        val userIdLong = userId.toLongOrNull() ?: 0L
        return getUnreadCount(userIdLong)
    }

    // ВСЕ сообщения пользователя с обновлением
    fun getAllUserMessages(userId: Long): Flow<List<com.example.myapplication.ChatMessage>> {
        return _refreshTrigger.flatMapLatest {
            messageDao.getAllUserMessages(userId)
        }.map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    // ВЕРСИЯ для String
    fun getAllUserMessages(userId: String): Flow<List<com.example.myapplication.ChatMessage>> {
        val userIdLong = userId.toLongOrNull() ?: 0L
        return getAllUserMessages(userIdLong)
    }

    // РУЧНОЕ ОБНОВЛЕНИЕ (можно вызвать из UI)
    fun refresh() {
        _refreshTrigger.value++
        println("🔄 [MessageRepository] Ручное обновление")
    }

    // ПОЛУЧИТЬ ПОСЛЕДНИЕ СООБЩЕНИЯ (для тестирования)
    suspend fun getLatestMessages(limit: Int = 10): List<com.example.myapplication.ChatMessage> {
        return messageDao.getAllUserMessages(0).first().take(limit).map { it.toDomainModel() }
    }

    // Конвертация Entity → Domain Model
    private fun MessageEntity.toDomainModel(): com.example.myapplication.ChatMessage {
        return com.example.myapplication.ChatMessage(
            id = id.toString(),
            senderId = senderId,
            receiverId = receiverId,
            senderName = senderName,
            message = message,
            timestamp = timestamp,
            isRead = isRead
        )
    }
    suspend fun saveMessagesFromFirebase(messages: List<MessageEntity>) {
        if (messages.isEmpty()) {
            println("📭 Нет сообщений для сохранения из Firebase")
            return
        }

        println("💾 Сохраняем ${messages.size} сообщений из Firebase в Room...")

        var savedCount = 0
        messages.forEach { firebaseMessage ->
            try {
                // Просто вставляем - Room сам обработает конфликты по primary key
                messageDao.insertMessage(firebaseMessage)
                savedCount++
                println("   💾 Сохранено: '${firebaseMessage.message.take(20)}...'")
            } catch (e: Exception) {
                // Игнорируем дубликаты
                println("   ⚠️ Сообщение уже есть: ${e.message}")
            }
        }

        if (savedCount > 0) {
            _refreshTrigger.value++ // Обновляем UI
            println("🔔 [MessageRepository] UI обновлен ($savedCount новых сообщений)")
        }
    }
}