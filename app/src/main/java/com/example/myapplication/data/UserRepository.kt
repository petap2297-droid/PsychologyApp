package com.example.myapplication.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserRepository(
    private val userDao: UserDao,
    private val firebaseRepository: FirebaseRepository? = null
) {

    // РЕГИСТРАЦИЯ нового пользователя
    suspend fun registerUser(
        username: String,
        password: String,
        firstName: String,
        lastName: String,
        role: String
    ): Long {
        val user = User(
            id = 0,
            username = username,
            password = password,
            firstName = firstName,
            lastName = lastName,
            role = role,
            avatarColor = generateColorFromName(username),
            createdAt = System.currentTimeMillis()
        )

        val userId = userDao.insertUser(user)
        println("✅ Пользователь создан в Room: $username (ID: $userId)")

        // СИНХРОНИЗИРУЕМ С FIREBASE через SyncUtils
        SyncUtils.syncUser(user.copy(id = userId))

        return userId
    }

    // СОЗДАНИЕ пользователя (удаляем старый метод с UUID - больше не нужен)
    suspend fun createUser(
        username: String,
        role: String,
        avatarColor: Int? = null
    ): Long { // ← Возвращаем Long
        val user = User(
            id = 0, // Room сам сгенерирует
            username = username,
            password = "123456", // Дефолтный пароль
            firstName = username.split(".").firstOrNull()?.capitalize() ?: "User",
            lastName = username.split(".").lastOrNull()?.capitalize() ?: "User",
            role = role,
            avatarColor = avatarColor ?: generateColorFromName(username)
        )
        return userDao.insertUser(user) // Возвращает сгенерированный ID
    }

    // АУТЕНТИФИКАЦИЯ
    suspend fun authenticate(username: String, password: String): User? {
        return userDao.authenticate(username, password)
    }

    // ПОЛУЧЕНИЕ пользователя по ID (ВЕРСИЯ ДЛЯ LONG)
    suspend fun getUserById(userId: Long): User? {
        return userDao.getUserById(userId)
    }

    // ПОЛУЧЕНИЕ пользователя по username
    suspend fun getUserByUsername(username: String): User? {
        return userDao.getUserByUsername(username)
    }

    // ПОЛУЧЕНИЕ пользователей по роли
    fun getUsersByRole(role: String): Flow<List<User>> {
        return userDao.getUsersByRole(role)
    }

    fun getAllUsers(): Flow<List<User>> {
        return userDao.getAllUsers()
    }

    suspend fun isUsernameAvailable(username: String): Boolean {
        return userDao.checkUsernameExists(username) == 0
    }

    suspend fun updateAvatarColor(userId: Long, color: Int) {
        val user = getUserById(userId)
        user?.let {
            val updatedUser = it.copy(avatarColor = color)
            userDao.updateUser(updatedUser)
        }
    }

    suspend fun updatePassword(userId: Long, newPassword: String) {
        val user = getUserById(userId)
        user?.let {
            val updatedUser = it.copy(password = newPassword)
            userDao.updateUser(updatedUser)
        }
    }

    private fun generateColorFromName(name: String): Int {
        val colors = listOf(
            0xFFFF6B6B.toInt(),
            0xFF4ECDC4.toInt(),
            0xFFFFD166.toInt(),
            0xFF6A0572.toInt(),
            0xFF06D6A0.toInt(),
            0xFF118AB2.toInt()
        )
        val index = kotlin.math.abs(name.hashCode()) % colors.size
        return colors[index]
    }
    suspend fun getAllUsersSyncAlternative(): List<User> {
        return try {
            var result: List<User> = emptyList()
            userDao.getAllUsers().collect { users ->
                result = users
            }
            result
        } catch (e: Exception) {
            println("❌ Ошибка получения пользователей: ${e.message}")
            emptyList()
        }
    }
    suspend fun createUserFromFirebase(user: User): Long {
        return try {
            // Проверяем, нет ли уже пользователя с таким username
            val existing = getUserByUsername(user.username)
            if (existing != null) {
                println("⚠️ Пользователь ${user.username} уже существует")
                return existing.id
            }

            // Создаем пользователя
            val newUser = User(
                id = 0, // Room сгенерирует новый ID
                username = user.username,
                password = user.password,
                firstName = user.firstName,
                lastName = user.lastName,
                role = user.role,
                avatarColor = user.avatarColor,
                createdAt = user.createdAt
            )

            val userId = userDao.insertUser(newUser)
            println("✅ Создан пользователь из Firebase: ${user.username} (ID: $userId)")
            userId

        } catch (e: Exception) {
            println("❌ Ошибка создания пользователя из Firebase: ${e.message}")
            0L
        }
    }
    // Получить всех учеников
    fun getStudents(): Flow<List<User>> {
        return userDao.getAllUsers().map { users ->
            users.filter { it.role == "ученик" || it.role == "student" }
        }
    }

    // Получить всех учителей
    fun getTeachers(): Flow<List<User>> {
        return userDao.getAllUsers().map { users ->
            users.filter { it.role == "учитель" || it.role == "teacher" }
        }
    }
    suspend fun deleteUser(userId: Long) {
        // 1. Удаляем локально
        userDao.deleteUserById(userId)
        println("🗑️ [Room] Пользователь $userId удален локально")

        // 2. Удаляем из облака (ЭТО ВАЖНО)
        try {
            firebaseRepository?.deleteUser(userId)
        } catch (e: Exception) {
            println("❌ Не удалось удалить из облака: ${e.message}")
        }
    }
    suspend fun deleteUserLocally(userId: Long) {
        userDao.deleteUserById(userId)
    }


    // Поиск пользователей
    suspend fun searchUsers(query: String): List<User> {
        return userDao.searchUsers("%$query%")
    }
}