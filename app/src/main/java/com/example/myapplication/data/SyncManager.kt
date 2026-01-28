package com.example.myapplication.data

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import com.google.firebase.firestore.ListenerRegistration

class SyncManager(
    private val context: Context,
    private val firebaseRepo: FirebaseRepository,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository
) {
    // Для хранения активных listeners
    private var conversationListener: ListenerRegistration? = null
    private var userMessagesListener: ListenerRegistration? = null

    // Проверка подключения к интернету
    fun isOnline(): Boolean {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        } catch (e: Exception) {
            return false
        }
    }

    // === ЗАПУСТИТЬ REAL-TIME ДЛЯ ДИАЛОГА ===
    fun startConversationRealtime(
        userId1: Long,
        userId2: Long,
        currentUserId: Long, // КТО СЕЙЧАС ИСПОЛЬЗУЕТ ТЕЛЕФОН
        onNewMessage: (MessageEntity) -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        if (!isOnline()) {
            println("🌐 [SyncManager] Нет интернета для real-time")
            return
        }

        println("⚡ [SyncManager] Real-time для диалога $userId1 ↔ $userId2")
        println("   Текущий пользователь на этом устройстве: $currentUserId")

        // Останавливаем старый listener
        conversationListener?.remove()

        conversationListener = firebaseRepo.addConversationRealtimeListener(
            userId1 = userId1,
            userId2 = userId2,
            onNewMessage = { message ->
                println("📩 [SyncManager] Получено real-time сообщение:")
                println("   От: ${message.senderId} (${message.senderName})")
                println("   Кому: ${message.receiverId}")
                println("   Текст: ${message.message.take(30)}...")

                // ВАЖНО: Проверяем, не наше ли это сообщение?
                if (message.senderId == currentUserId) {
                    println("   ⚡ ИГНОРИРУЕМ: Это наше же сообщение (отправитель $currentUserId)")
                    return@addConversationRealtimeListener
                }

                println("   ✅ Сохраняем: Это сообщение от другого пользователя")

                // Сохраняем в Room
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        messageRepository.saveMessagesFromFirebase(listOf(message))
                        onNewMessage(message) // Уведомляем UI
                    } catch (e: Exception) {
                        println("❌ [SyncManager] Ошибка сохранения: ${e.message}")
                    }
                }
            },
            onError = { error ->
                println("❌ [SyncManager] Ошибка real-time: ${error.message}")
                onError(error)
            }
        )
    }

    // === ЗАПУСТИТЬ REAL-TIME ДЛЯ ВСЕХ СООБЩЕНИЙ ПОЛЬЗОВАТЕЛЯ ===
    fun startUserMessagesRealtime(
        userId: Long,
        onNewMessage: (MessageEntity) -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        if (!isOnline()) {
            println("🌐 [SyncManager] Нет интернета для real-time")
            return
        }

        println("⚡ [SyncManager] Real-time для всех сообщений пользователя $userId")

        // Останавливаем старый listener
        userMessagesListener?.remove()

        userMessagesListener = firebaseRepo.addUserMessagesRealtimeListener(
            userId = userId,
            onNewMessage = { message ->
                println("⚡ [SyncManager] Новое сообщение для пользователя")

                // Сохраняем в Room
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        messageRepository.saveMessagesFromFirebase(listOf(message))
                        onNewMessage(message)
                    } catch (e: Exception) {
                        println("❌ [SyncManager] Ошибка сохранения: ${e.message}")
                    }
                }
            },
            onError = { error ->
                println("❌ [SyncManager] Ошибка real-time: ${error.message}")
                onError(error)
            }
        )
    }

    // === ОСТАНОВИТЬ ВСЕ REAL-TIME LISTENERS ===
    fun stopAllRealtime() {
        conversationListener?.remove()
        userMessagesListener?.remove()
        conversationListener = null
        userMessagesListener = null
        println("⏹️ [SyncManager] Все real-time listeners остановлены")
    }

    // === ОСТАНОВИТЬ ТОЛЬКО ДИАЛОГ ===
    fun stopConversationRealtime() {
        conversationListener?.remove()
        conversationListener = null
        println("⏹️ [SyncManager] Conversation real-time остановлен")
    }

    // ОСНОВНАЯ СИНХРОНИЗАЦИЯ
    suspend fun syncAllData() {
        if (!isOnline()) {
            println("🌐 [SyncManager] Нет интернета")
            return
        }

        try {
            println("🔄 [SyncManager] Начинаем синхронизацию...")

            // 1. Загружаем пользователей ИЗ Firebase в Room
            syncUsersFromFirebase()

            // 2. Отправляем локальных пользователей В Firebase
            syncUsersToFirebase()

            println("✅ [SyncManager] Синхронизация завершена")
        } catch (e: Exception) {
            println("❌ [SyncManager] Ошибка: ${e.message}")
        }
    }

    // СИНХРОНИЗАЦИЯ СООБЩЕНИЙ ДЛЯ ПОЛЬЗОВАТЕЛЯ
    suspend fun syncMessagesForUser(userId: Long) {
        if (!isOnline()) {
            println("🌐 [SyncManager] Нет интернета для синхронизации сообщений")
            return
        }

        try {
            println("🔄 [SyncManager] Синхронизация сообщений для пользователя: $userId")

            // 1. Загружаем сообщения из Firebase
            val firebaseMessages = firebaseRepo.loadAllMessagesForUser(userId)
            println("📥 Загружено ${firebaseMessages.size} сообщений из Firebase")

            // 2. Сохраняем в Room
            if (firebaseMessages.isNotEmpty()) {
                messageRepository.saveMessagesFromFirebase(firebaseMessages)
            } else {
                println("📭 Нет сообщений в Firebase для пользователя $userId")
            }

        } catch (e: Exception) {
            println("❌ [SyncManager] Ошибка синхронизации сообщений: ${e.message}")
        }
    }

    // ОТПРАВКА пользователей: Room → Firebase
    private suspend fun syncUsersToFirebase() {
        try {
            println("👥 [SyncManager] Отправка пользователей В Firebase...")

            val usersFlow = userRepository.getAllUsers()
            val users = usersFlow.firstOrNull() ?: emptyList()

            println("👥 [SyncManager] Найдено ${users.size} пользователей для отправки")

            for (user in users) {
                firebaseRepo.syncUser(user)
            }

            println("✅ [SyncManager] Пользователи отправлены в Firebase")
        } catch (e: Exception) {
            println("❌ [SyncManager] Ошибка отправки пользователей: ${e.message}")
        }
    }

    // ЗАГРУЗКА пользователей: Firebase → Room (С УДАЛЕНИЕМ УСТАРЕВШИХ)
    private suspend fun syncUsersFromFirebase() {
        try {
            println("👥 [SyncManager] Загрузка пользователей ИЗ Firebase...")

            // Получаем список из Firebase
            val firebaseUsers = firebaseRepo.loadUsersFromFirebase()
            println("👥 [SyncManager] В Firebase найдено ${firebaseUsers.size} пользователей")

            // Получаем локальных пользователей
            val localUsers = userRepository.getAllUsersSyncAlternative()

            // 1. ДОБАВЛЯЕМ НОВЫХ
            var addedCount = 0
            for (firebaseUser in firebaseUsers) {
                val existingUser = userRepository.getUserById(firebaseUser.id)
                if (existingUser == null) {
                    userRepository.createUserFromFirebase(firebaseUser)
                    addedCount++
                }
            }

            // 2. УДАЛЯЕМ ТЕХ, КОГО НЕТ В FIREBASE (ВОТ ЭТО ВАЖНО!)
            var deletedCount = 0
            for (localUser in localUsers) {
                // Если локального юзера нет в списке из Firebase -> удаляем его
                val existsInCloud = firebaseUsers.any { it.id == localUser.id }
                if (!existsInCloud) {
                    userRepository.deleteUserLocally(localUser.id) // Только локально!
                    deletedCount++
                    println("❌ [SyncManager] Удален локальный призрак: ${localUser.username}")
                }
            }

            println("✅ [SyncManager] Синхронизация: +$addedCount новых, -$deletedCount удаленных")

        } catch (e: Exception) {
            println("❌ [SyncManager] Ошибка: ${e.message}")
        }
    }


    // Синхронизация при регистрации
    suspend fun syncOnUserRegistration(user: User) {
        if (isOnline()) {
            firebaseRepo.syncUser(user)
        }
    }

    // Синхронизация при сохранении теста
    suspend fun syncOnTestSave(
        userId: Long,
        studentName: String,
        score: Int,
        date: String,
        answers: List<Int>,
        recommendations: String
    ) {
        if (isOnline()) {
            firebaseRepo.syncTestResult(
                userId = userId,
                studentName = studentName,
                score = score,
                date = date,
                answers = answers,
                recommendations = recommendations
            )
        }
    }

    // Синхронизация сообщения
    suspend fun syncOnMessageSend(
        senderId: Long,
        receiverId: Long,
        senderName: String,
        message: String
    ) {
        if (isOnline()) {
            println("📨 [SyncManager] Синхронизация сообщения в Firebase...")
            firebaseRepo.syncMessage(
                senderId = senderId,
                receiverId = receiverId,
                senderName = senderName,
                message = message
            )
        } else {
            println("🌐 [SyncManager] Нет интернета, сообщение сохранено локально")
        }
    }
}