package com.example.myapplication

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Videocam
import kotlinx.coroutines.launch
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.serialization.Serializable
import android.util.Log
import android.app.Application
import com.example.myapplication.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.text.style.TextOverflow
import com.example.myapplication.data.*
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Refresh // или .filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.* // ВАЖНО: звездочка импортирует ВСЕ иконки
import java.text.SimpleDateFormat
import java.util.*
import com.example.myapplication.ui.screens.CallScreen
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.flow.firstOrNull


// Вспомогательный класс для передачи данных звонка
data class CallScreenData(
    val remoteUserId: String,
    val remoteName: String,
    val isIncoming: Boolean,
    val isVideo: Boolean // <--- НОВОЕ ПОЛЕ
)
// PsyHelperApplication.kt - ОБНОВЛЕННАЯ ВЕРСИЯ
class PsyHelperApplication : Application() {

    companion object {
        private var instance: PsyHelperApplication? = null

        fun getInstance(): PsyHelperApplication {
            return instance ?: throw IllegalStateException("PsyHelperApplication еще не инициализирован")
        }
    }

    // ============ ИНИЦИАЛИЗАЦИЯ КОМПОНЕНТОВ ============

    // 1. БАЗА ДАННЫХ Room
    val database by lazy {
        AppDatabase.getDatabase(this).also {
            println("✅ База данных Room инициализирована")
        }
    }

    // 2. FIREBASE РЕПОЗИТОРИЙ
    val firebaseRepository by lazy {
        FirebaseRepository().also {
            println("✅ FirebaseRepository инициализирован")
        }
    }

    // 3. РЕПОЗИТОРИИ Room
    val userRepository by lazy {
        UserRepository(
            database.userDao(),
            firebaseRepository
        ).also {
            println("✅ UserRepository инициализирован")
        }
    }

    val testResultRepository by lazy {
        TestResultRepository(database.testResultDao()).also {
            println("✅ TestResultRepository инициализирован")
        }
    }

    val messageRepository by lazy {
        MessageRepository(database.messageDao()).also {
            println("✅ MessageRepository инициализирован")
        }
    }

    // 4. SYNC MANAGER (ИСПРАВЛЕНО!)
    val syncManager by lazy {
        SyncManager(
            context = this,
            firebaseRepo = firebaseRepository,
            userRepository = userRepository,
            messageRepository = messageRepository // ← ВАЖНО!
        ).also {
            println("✅ SyncManager инициализирован с MessageRepository")
        }
    }

    // 5. LOCAL STORAGE (DataStore)
    val localStorage by lazy {
        LocalStorage(this).also {
            println("✅ LocalStorage инициализирован")
        }
    }

    // 6. DATA MANAGER
    val dataManager by lazy {
        DataManager(
            context = this,
            testResultRepository = testResultRepository,
            userRepository = userRepository
        ).also {
            println("✅ DataManager инициализирован")
        }
    }

    // ============ ЖИЗНЕННЫЙ ЦИКЛ ============

    override fun onCreate() {
        super.onCreate()
        instance = this
        println("🚀 PsyHelperApplication.onCreate() запущен")

        // Инициализируем SyncUtils
        SyncUtils.initialize(this)
        println("✅ SyncUtils инициализирован")

        // Асинхронные задачи при запуске
        CoroutineScope(Dispatchers.IO).launch {
            // 1. Тест Firebase подключения
            testFirebaseConnection()

            // 2. Создание тестовых пользователей
            createTestUsers()

            // 3. Автоматическая синхронизация
            autoSyncData()

            // 4. ЗАПУСКАЕМ REAL-TIME (опционально)
            // startGlobalRealtimeSync()
        }
    }

    // ============ ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ============

    private suspend fun testFirebaseConnection() {
        delay(3000)

        println("🔥 Тестируем Firebase подключение...")
        try {
            val connected = firebaseRepository.testConnection()
            if (connected) {
                println("✅ Firebase подключен успешно")
            } else {
                println("⚠️ Firebase не отвечает")
            }
        } catch (e: Exception) {
            println("❌ Ошибка Firebase: ${e.message}")
        }
    }

    private suspend fun createTestUsers() {
        try {
            println("🔄 Проверяем тестовых пользователей...")

            // Тестовый ученик
            val testStudent = userRepository.authenticate("test.user", "123456")
            if (testStudent == null) {
                val userId = userRepository.registerUser(
                    username = "test.user",
                    password = "123456",
                    firstName = "Тест",
                    lastName = "Ученик",
                    role = "ученик"
                )
                println("✅ Создан тестовый ученик: test.user (ID: $userId)")
            } else {
                println("✅ Тестовый ученик уже существует: test.user")
            }

            // Тестовый учитель
            val testTeacher = userRepository.authenticate("teacher.test", "123456")
            if (testTeacher == null) {
                val userId = userRepository.registerUser(
                    username = "teacher.test",
                    password = "123456",
                    firstName = "Тест",
                    lastName = "Учитель",
                    role = "учитель"
                )
                println("✅ Создан тестовый учитель: teacher.test (ID: $userId)")
            } else {
                println("✅ Тестовый учитель уже существует: teacher.test")
            }

            // Тестовый администратор
            val testAdmin = userRepository.authenticate("admin.test", "123456")
            if (testAdmin == null) {
                val userId = userRepository.registerUser(
                    username = "admin.test",
                    password = "123456",
                    firstName = "Тест",
                    lastName = "Администратор",
                    role = "администратор"
                )
                println("✅ Создан тестовый администратор: admin.test (ID: $userId)")
            } else {
                println("✅ Тестовый администратор уже существует: admin.test")
            }

            println("✅ Все тестовые пользователи проверены/созданы")

        } catch (e: Exception) {
            println("❌ Ошибка создания тестовых пользователей: ${e.message}")
        }
    }

    private suspend fun autoSyncData() {
        delay(5000)

        println("🔄 Автоматическая синхронизация при запуске...")
        try {
            syncManager.syncAllData()
            println("✅ Автоматическая синхронизация завершена")
        } catch (e: Exception) {
            println("⚠️ Ошибка автоматической синхронизации: ${e.message}")
        }
    }

    // Метод для ручной синхронизации
    suspend fun syncAllData() {
        withContext(Dispatchers.IO) {
            try {
                println("🔄 Ручная синхронизация всех данных...")
                syncManager.syncAllData()
                println("✅ Ручная синхронизация завершена")
            } catch (e: Exception) {
                println("❌ Ошибка ручной синхронизации: ${e.message}")
                throw e
            }
        }
    }

    // Метод для синхронизации только пользователей
    suspend fun syncUsers() {
        withContext(Dispatchers.IO) {
            try {
                println("👥 Синхронизация пользователей...")

                // 1. Отправляем локальных пользователей в Firebase
                val users = userRepository.getAllUsersSyncAlternative()
                println("📤 Отправка ${users.size} пользователей в Firebase...")

                users.forEach { user ->
                    firebaseRepository.syncUser(user)
                }

                // 2. Загружаем пользователей из Firebase
                val firebaseUsers = firebaseRepository.loadUsersFromFirebase()
                println("📥 Загрузка ${firebaseUsers.size} пользователей из Firebase...")

                for (fbUser in firebaseUsers) {
                    val existing = userRepository.getUserById(fbUser.id)
                    if (existing == null) {
                        userRepository.createUserFromFirebase(fbUser)
                    }
                }

                println("✅ Синхронизация пользователей завершена")

            } catch (e: Exception) {
                println("❌ Ошибка синхронизации пользователей: ${e.message}")
            }
        }
    }
}
@Composable
fun DebugScreen(
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    var debugInfo by remember { mutableStateOf("Начало диагностики...\n") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                // 1. Проверка assets
                debugInfo += "1. Проверка папки assets:\n"
                val assetsFiles = context.assets.list("")?.joinToString(", ") ?: "Нет файлов"
                debugInfo += "   Файлы: $assetsFiles\n"

                // 2. Проверка questions.json
                debugInfo += "\n2. Проверка questions.json:\n"
                try {
                    val jsonContent = context.assets.open("questions.json")
                        .bufferedReader()
                        .use { it.readText() }

                    debugInfo += "   Размер файла: ${jsonContent.length} символов\n"

                    // Простая проверка - считаем вопросы по фигурным скобкам
                    val questionCount = jsonContent.count { it == '{' }
                    debugInfo += "   ✅ Вопросов найдено: $questionCount\n"

                    // Если вопросов 0, покажем больше информации
                    if (questionCount == 0) {
                        debugInfo += "   ⚠️ Возможно пустой файл или неправильный формат\n"
                        debugInfo += "   Начало файла: ${jsonContent.take(200)}...\n"
                    }
                } catch (e: Exception) {
                    debugInfo += "   ❌ Ошибка: ${e.message}\n"
                }

                // 3. Проверка SharedPreferences
                debugInfo += "\n3. Проверка SharedPreferences:\n"
                val prefs = context.getSharedPreferences("psychology_app", Context.MODE_PRIVATE)
                val hasUser = prefs.contains("user_first_name")
                debugInfo += "   Пользователь сохранен: ${if (hasUser) "Да" else "Нет"}\n"

                // 4. Проверка версии Android
                debugInfo += "\n4. Системная информация:\n"
                debugInfo += "   Android SDK: ${android.os.Build.VERSION.SDK_INT}\n"
                debugInfo += "   Устройство: ${android.os.Build.MODEL}\n"

                debugInfo += "\n✅ Диагностика завершена\n"

            } catch (e: Exception) {
                debugInfo += "\n❌ Критическая ошибка: ${e.message}\n"
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "🔧 Диагностика приложения",
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFF6A5AE0)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
        ) {
            Text(
                text = debugInfo,
                modifier = Modifier.padding(16.dp),
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6A5AE0)
            )
        ) {
            Text("Продолжить в приложение")
        }

        Button(
            onClick = {
                // Очистка данных
                val prefs = context.getSharedPreferences("psychology_app", Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
                debugInfo += "\n🧹 Данные очищены\n"
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE53935)
            )
        ) {
            Text("Очистить все данные")
        }
    }
}

// Создаем расширение для Context
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "psychology_app_data")

class LocalStorage(private val context: Context) {

    // Ключи для хранения
    private object Keys {
        val USER_FIRST_NAME = stringPreferencesKey("user_first_name")
        val USER_LAST_NAME = stringPreferencesKey("user_last_name")
        val USER_FULL_NAME = stringPreferencesKey("user_full_name")
        val USER_ROLE = intPreferencesKey("user_role")
        val TEST_HISTORY = stringSetPreferencesKey("test_history")
        val LAST_TEST_SCORE = intPreferencesKey("last_test_score")
        val LAST_TEST_DATE = stringPreferencesKey("last_test_date")
    }

    // === СОХРАНЕНИЕ ПОЛЬЗОВАТЕЛЯ ===
    suspend fun saveUserData(userData: UserData, role: Int) {
        context.dataStore.edit { preferences ->
            preferences[Keys.USER_FIRST_NAME] = userData.firstName
            preferences[Keys.USER_LAST_NAME] = userData.lastName
            preferences[Keys.USER_FULL_NAME] = userData.fullName
            preferences[Keys.USER_ROLE] = role
        }
    }

    // === ЗАГРУЗКА ПОЛЬЗОВАТЕЛЯ ===
    suspend fun loadUserData(): Pair<UserData?, Int> {
        val preferences = context.dataStore.data.first()
        val firstName = preferences[Keys.USER_FIRST_NAME]
        val lastName = preferences[Keys.USER_LAST_NAME]
        val fullName = preferences[Keys.USER_FULL_NAME]
        val role = preferences[Keys.USER_ROLE] ?: 0

        return if (firstName != null && lastName != null && fullName != null) {
            Pair(UserData(firstName, lastName, fullName), role)
        } else {
            Pair(null, role)
        }
    }

    // === СОХРАНЕНИЕ РЕЗУЛЬТАТА ТЕСТА ===
    suspend fun saveTestResult(testResult: TestResult) {
        // Сохраняем последний результат
        context.dataStore.edit { preferences ->
            preferences[Keys.LAST_TEST_SCORE] = testResult.score
            preferences[Keys.LAST_TEST_DATE] = testResult.date
        }

        // Добавляем в историю
        val historyEntry = "${testResult.date}|${testResult.score}|${testResult.recommendations.take(50)}"
        context.dataStore.edit { preferences ->
            val currentHistory = preferences[Keys.TEST_HISTORY] ?: emptySet()
            preferences[Keys.TEST_HISTORY] = currentHistory + historyEntry
        }
    }

    // === ЗАГРУЗКА ИСТОРИИ ТЕСТОВ ===
    suspend fun loadTestHistory(): List<TestResult> {
        val preferences = context.dataStore.data.first()
        val historySet = preferences[Keys.TEST_HISTORY] ?: emptySet()

        return historySet.mapNotNull { entry ->
            try {
                val parts = entry.split("|")
                if (parts.size >= 3) {
                    TestResult(
                        id = entry.hashCode(),
                        studentId = 0,
                        studentName = "Ученик",
                        score = parts[1].toIntOrNull() ?: 0,
                        date = parts[0],
                        answers = emptyList(),
                        recommendations = parts[2]
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.date }
    }

    // === ПОСЛЕДНИЙ РЕЗУЛЬТАТ ===
    suspend fun getLastTestResult(): TestResult? {
        val preferences = context.dataStore.data.first()
        val score = preferences[Keys.LAST_TEST_SCORE]
        val date = preferences[Keys.LAST_TEST_DATE]

        return if (score != null && date != null) {
            TestResult(
                id = date.hashCode(),
                studentId = 0,
                studentName = "Ученик",
                score = score,
                date = date,
                answers = emptyList(),
                recommendations = "Загружено из истории"
            )
        } else {
            null
        }
    }

    // === ОЧИСТКА ДАННЫХ ===
    suspend fun clearUserData() {
        context.dataStore.edit { preferences ->
            preferences.remove(Keys.USER_FIRST_NAME)
            preferences.remove(Keys.USER_LAST_NAME)
            preferences.remove(Keys.USER_FULL_NAME)
            preferences.remove(Keys.USER_ROLE)
            preferences.remove(Keys.TEST_HISTORY)
            preferences.remove(Keys.LAST_TEST_SCORE)
            preferences.remove(Keys.LAST_TEST_DATE)
        }
    }
}


object QuestionLoader {
    suspend fun loadQuestions(context: Context): List<Question> {
        Log.d("QUESTION_LOADER", "=== НАЧАЛО ЗАГРУЗКИ ===")

        val jsonString = try {
            withContext(Dispatchers.IO) {
                context.assets.open("questions.json").bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            Log.e("QUESTION_LOADER", "Файл не прочитан: ${e.message}")
            return getTestQuestions()
        }

        return try {
            val questions = parseJsonSafely(jsonString)
            if (questions.isEmpty()) getTestQuestions() else questions
        } catch (e: Exception) {
            getTestQuestions()
        }
    }
    // Теперь принимаем repository, чтобы скачать данные
    suspend fun loadQuestions(context: Context, firebaseRepository: FirebaseRepository? = null): List<Question> {
        Log.d("QUESTION_LOADER", "=== НАЧАЛО ЗАГРУЗКИ ===")

        // 1. ПОПЫТКА ЗАГРУЗИТЬ ИЗ FIREBASE (Приоритет)
        if (firebaseRepository != null) {
            val cloudQuestions = firebaseRepository.getQuestions()
            if (cloudQuestions.isNotEmpty()) {
                Log.d("QUESTION_LOADER", "🔥 Загружено из Firebase: ${cloudQuestions.size} вопросов")
                return cloudQuestions
            }
        }

        // 2. ЕСЛИ В FIREBASE ПУСТО ИЛИ НЕТ СЕТИ -> ГРУЗИМ JSON (Резерв)
        Log.d("QUESTION_LOADER", "📂 Firebase недоступен, грузим локальный JSON...")

        val jsonString = try {
            withContext(Dispatchers.IO) {
                context.assets.open("questions.json").bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            Log.e("QUESTION_LOADER", "Файл не прочитан: ${e.message}")
            return getTestQuestions()
        }

        return try {
            val questions = parseJsonSafely(jsonString)
            if (questions.isEmpty()) getTestQuestions() else questions
        } catch (e: Exception) {
            getTestQuestions()
        }
    }

    private fun parseJsonSafely(jsonString: String): List<Question> {
        val questions = mutableListOf<Question>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                try {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val text = jsonObject.getString("text")

                    // Пробуем получить категорию, если нет - "Общее"
                    val category = if (jsonObject.has("category")) jsonObject.getString("category") else "Общее"

                    // Генерируем ID на основе индекса (1, 2, 3...)
                    val id = i + 1

                    // Создаем вопрос с новыми полями
                    questions.add(Question(
                        id = id,
                        text = text,
                        category = category
                        // options подставятся по умолчанию ("Никогда", "Редко"...)
                    ))
                } catch (e: Exception) {
                    Log.w("QUESTION_LOADER", "Ошибка в вопросе $i: ${e.message}")
                }
            }
        } catch (e: Exception) {
            throw e
        }
        return questions
    }

    private fun getTestQuestions(): List<Question> {
        // ВОТ ТУТ БЫЛА ОШИБКА. Теперь мы явно указываем параметры.
        return listOf(
            Question(id = 1, text = "Вы чувствуете себя спокойно сегодня?", category = "настроение"),
            Question(id = 2, text = "У вас был хороший сон?", category = "сон"),
            Question(id = 3, text = "Вы готовы пройти тест?", category = "готовность"),
            Question(id = 4, text = "Вы легко сосредотачиваетесь?", category = "концентрация"),
            Question(id = 5, text = "Вы довольны своими результатами?", category = "удовлетворение")
        )
    }
}

data class Question(
    val id: Int = 0,                            // ID (по умолчанию 0)
    val text: String,                           // Текст вопроса
    val category: String = "Общее",             // Категория (по умолчанию "Общее")
    val options: List<String> = listOf("Никогда", "Редко", "Иногда", "Часто", "Всегда") // Варианты ответов
)
data class StudentAdmin(
    val name: String,
    val className: String,
    val status: String,
    val lastTest: String,
    val stressLevel: Int
)

data class TeacherAdmin(
    val name: String,
    val email: String,
    val studentsCount: String,
    val status: String
)
data class UserData(
    val firstName: String,
    val lastName: String,
    val fullName: String
)


data class TestResult(
    val id: Int,
    val studentId: Int,
    val studentName: String,
    val score: Int,
    val date: String,
    val answers: List<Int>,
    val recommendations: String
)
// Добавим в начало файла, рядом с data class Student
data class ChatMessage(
    val id: String,
    val senderId: Long,        // ← Должно быть Long
    val receiverId: Long,      // ← Должно быть Long
    val senderName: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean = false
)

data class Chat(
    val chatId: String,
    val studentId: Long, // ← ИЗМЕНЕНИЕ: Long вместо Int
    val studentName: String,
    val lastMessage: String,
    val lastMessageTime: Long,
    val unreadCount: Int = 0
)


// ДАННЫЕ ДЛЯ УПРАВЛЕНИЯ ТЕМОЙ
enum class ColorTheme { LIGHT, DARK }

@Composable
fun rememberAppThemeState() = remember {
    mutableStateOf(ColorTheme.LIGHT)
}
// МОДЕЛЬ ДАННЫХ УЧЕНИКА
data class Student(
    val id: Long, // ← ИЗМЕНЕНИЕ: Long вместо Int
    val firstName: String,
    val lastName: String,
    val testScore: Int? = null,
    val testHistory: List<TestResult> = emptyList(),
    val lastActive: String = "Сегодня",
    val hasUnreadMessages: Boolean = false
)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { //
            MaterialTheme { //// ↓ Применяем тему Material Design
                // Показываем главный экран приложения
                PsychologyApp()
            }
        }
    }
}
//primary = Color(0xFF2196F3)Яркий синий
//primary = Color(0xFF00BCD4) Бирюзовый
//primary = Color(0xFFFF9800)  оранжевый
//primary = Color(0xFF7E57C2)  фиолетовый
@Composable
fun MyAppTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        // ТЕМНАЯ ТЕМА
        darkColorScheme(
            primary = Color(0xFFBB86FC),
            onPrimary = Color(0xFF000000),
            primaryContainer = Color(0xFF3700B3),
            onPrimaryContainer = Color(0xFFEADDFF),
            secondary = Color(0xFF03DAC6),
            onSecondary = Color(0xFF000000),
            background = Color(0xFF121212),       // Тёмный фон
            onBackground = Color(0xFFFFFFFF),     // Белый текст на тёмном
            surface = Color(0xFF1E1E1E),          // Тёмные карточки
            onSurface = Color(0xFFFFFFFF),        // Белый текст на карточках
            surfaceVariant = Color(0xFF2D2D2D),
            onSurfaceVariant = Color(0xFFC8C8C8)
        )
    } else {
        // СВЕТЛАЯ ТЕМА
        lightColorScheme(
            primary = Color(0xFF6A5AE0),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE8E6FF),
            onPrimaryContainer = Color(0xFF1A0061),
            secondary = Color(0xFF625B71),
            onSecondary = Color(0xFFFFFFFF),
            background = Color(0xFFF5F7FF),       // Светлый фон
            onBackground = Color(0xFF1C1B1F),     // Тёмный текст на светлом
            surface = Color(0xFFFFFFFF),          // Белые карточки
            onSurface = Color(0xFF1C1B1F),        // Тёмный текст на карточках
            surfaceVariant = Color(0xFFE7E0EC),
            onSurfaceVariant = Color(0xFF49454F)
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
// Главное приложение, которое управляет экранами
@Composable
fun PsychologyApp() {
    // ============ ДИАГНОСТИКА ============
    var showDebug by remember { mutableStateOf(false) }

    if (showDebug) {
        // Если DebugScreen существует в вашем проекте
        // DebugScreen(onContinue = { showDebug = false })
        return
    }

    // ============ СОСТОЯНИЯ ============
    var showLoginScreen by remember { mutableStateOf(true) }
    var initialLoadCompleted by remember { mutableStateOf(false) }

    // ID текущего пользователя (Long для Room)
    var currentUserId by remember { mutableStateOf<Long?>(null) }

    // Вспомогательная функция для получения Long ID
    fun getCurrentUserIdAsLong(): Long = currentUserId ?: 0L

    var userRole by remember { mutableStateOf<String?>(null) }
    var currentUserData by remember {
        mutableStateOf(UserData(firstName = "", lastName = "", fullName = "Гость"))
    }

    // Состояние звонка (если не null -> показываем экран звонка)
    var callScreenData by remember { mutableStateOf<CallScreenData?>(null) }

    // Переменные навигации и UI
    val themeState = rememberAppThemeState()
    var currentScreen by remember { mutableStateOf("login") }
    var selectedUserRole by remember { mutableStateOf(0) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Данные для переходов
    var currentStudentForHistory by remember { mutableStateOf<Student?>(null) }
    var currentStudentChat by remember { mutableStateOf<Pair<Long, String>?>(null) } // ID ученика, Имя
    var currentTeacherChat by remember { mutableStateOf<Pair<Long, String>?>(null) } // ID учителя, Имя
    var userTestScore by remember { mutableStateOf(0) }

    // ==================== ИНИЦИАЛИЗАЦИЯ ====================
    val context = LocalContext.current
    val application = (context.applicationContext as PsyHelperApplication)
    val userRepository = application.userRepository
    val syncManager = application.syncManager

    val dataManager = remember {
        DataManager(
            context = context,
            testResultRepository = application.testResultRepository,
            userRepository = application.userRepository
        )
    }

    // ==================== СИНХРОНИЗАЦИЯ ====================
    LaunchedEffect(Unit) {
        delay(3000)
        syncManager.syncAllData()
    }

    // ==================== АВТО-ВХОД ====================
    LaunchedEffect(Unit) {
        if (!initialLoadCompleted) {
            try {
                val (savedUser, savedRole) = dataManager.loadUserData()
                if (savedUser != null) {
                    val username = "${savedUser.firstName.lowercase()}.${savedUser.lastName.lowercase()}"
                    val userInDb = userRepository.getUserByUsername(username)

                    if (userInDb != null) {
                        currentUserId = userInDb.id
                        currentUserData = savedUser
                        userRole = userInDb.role
                        selectedUserRole = savedRole
                        showLoginScreen = false

                        currentScreen = when (userInDb.role) {
                            "ученик", "student" -> "personal_advice"
                            "учитель", "teacher" -> "teacher"
                            "администратор", "admin" -> "admin"
                            else -> "personal_advice"
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                initialLoadCompleted = true
            }
        }
    }

    // ==================== СЛУШАТЕЛЬ ВХОДЯЩИХ ЗВОНКОВ ====================
    LaunchedEffect(currentUserId) {
        val myId = currentUserId
        if (myId != null) {
            val db = FirebaseFirestore.getInstance()
            val myIdStr = myId.toString()

            db.collection("calls")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) return@addSnapshotListener

                    if (snapshots != null) {
                        for (doc in snapshots.documents) {
                            val callId = doc.id
                            val type = doc.getString("type")
                            val senderId = doc.getString("senderId")
                            // ПРОВЕРКА ВРЕМЕНИ (не старше 1 минуты)
                            val timestamp = doc.getLong("timestamp") ?: 0L
                            val isFresh = (System.currentTimeMillis() - timestamp) < 60000

                            if (type == "OFFER" &&
                                senderId != myIdStr &&
                                callId.contains(myIdStr) &&
                                isFresh) { // <-- ВАЖНО: isFresh
                                val isVideo = doc.getBoolean("isVideo") ?: false
                                if (callScreenData == null) {
                                    callScreenData = CallScreenData(
                                        remoteUserId = senderId ?: "Unknown",
                                        remoteName = "Входящий ${if(isVideo) "видео" else "аудио"}звонок",
                                        isIncoming = true,
                                        isVideo = isVideo // <--- Передаем
                                    )
                                    currentScreen = "call"
                                }
                            }
                        }
                    }
                }
        }
    }



    // ==================== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ====================
    fun logout() {
        currentUserId = null
        currentUserData = UserData("", "", "Гость")
        userRole = null
        userTestScore = 0
        callScreenData = null
        scope.launch { dataManager.clearUserData() }
        currentScreen = "login"
        showLoginScreen = true
    }

    fun openDrawer() { scope.launch { drawerState.open() } }
    fun closeDrawer() { scope.launch { drawerState.close() } }

    // ==================== ЭКРАН ВХОДА ====================
    if (showLoginScreen) {
        LoginScreen(
            onLoginSuccess = { userId, userData, role ->
                currentUserId = userId
                currentUserData = userData
                userRole = role

                selectedUserRole = when (role.lowercase()) {
                    "ученик", "student" -> 0
                    "учитель", "teacher" -> 1
                    "администратор", "admin" -> 2
                    else -> 0
                }

                scope.launch { dataManager.saveUserData(userData, selectedUserRole) }

                showLoginScreen = false
                currentScreen = when (selectedUserRole) {
                    0 -> "personal_advice"
                    1 -> "teacher"
                    2 -> "admin"
                    else -> "personal_advice"
                }
            },
            onRegisterClick = {
                showLoginScreen = false
                currentScreen = "registration"
            }
        )
        return
    }

    // ==================== МЕНЮ (ОБНОВЛЕННОЕ) ====================
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                // Информация о текущем пользователе
                if (currentUserId != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "👤 ${currentUserData.fullName}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when (userRole) {
                                    "ученик", "student" -> "Ученик"
                                    "учитель", "teacher" -> "Учитель"
                                    "администратор", "admin" -> "Администратор"
                                    else -> "Пользователь"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                    Divider()
                }

                Text(
                    text = "Меню",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Divider()

                // Если пользователь вошел - показываем меню выхода
                if (currentUserId != null) {
                    NavigationDrawerItem(
                        label = { Text("🚪 Выйти") },
                        selected = false,
                        onClick = {
                            logout()
                            closeDrawer()
                        }
                    )
                }

                NavigationDrawerItem(
                    label = {
                        Text(
                            if (currentUserId != null) "👥 Сменить пользователя" else "👤 Войти"
                        )
                    },
                    selected = false,
                    onClick = {
                        if (currentUserId != null) {
                            logout()
                        } else {
                            currentScreen = "login"
                            showLoginScreen = true
                        }
                        closeDrawer()
                    }
                )

                NavigationDrawerItem(
                    label = {
                        Text(
                            if (themeState.value == ColorTheme.DARK) "☀️ Светлая тема" else "🌙 Тёмная тема"
                        )
                    },
                    selected = false,
                    onClick = {
                        themeState.value = if (themeState.value == ColorTheme.LIGHT) ColorTheme.DARK else ColorTheme.LIGHT
                        closeDrawer()
                    }
                )

                NavigationDrawerItem(
                    label = { Text("⚙️ Панель администратора") },
                    selected = false,
                    onClick = {
                        currentScreen = "admin_registration"
                        closeDrawer()
                    }
                )

                Spacer(modifier = Modifier.weight(1f))

                // Информация о версии
                Text(
                    text = "Психологический помощник v1.0",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ID пользователя для отладки
                if (currentUserId != null) {
                    Text(
                        // ИСПРАВЛЕНИЕ: отображаем Long ID
                        text = "ID: ${currentUserId}",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }
    ) {
        // ==================== ОСНОВНАЯ НАВИГАЦИЯ ====================
        when (currentScreen) {
            // Экран входа (если понадобится отдельный путь)
            "login" -> {
                // Этот блок не должен выполняться, т.к. мы уже обработали showLoginScreen выше
                // Но на всякий случай:
                LoginScreen(
                    onLoginSuccess = { userId, userData, role ->
                        currentUserId = userId
                        currentUserData = userData
                        userRole = role
                        showLoginScreen = false
                        currentScreen = "personal_advice"
                    },
                    onRegisterClick = {
                        currentScreen = "registration"
                    }
                )
            }

            "registration" -> RegistrationScreen(
                onStartTest = { firstName, lastName ->
                    currentUserData = UserData(
                        firstName = firstName,
                        lastName = lastName,
                        fullName = "$firstName $lastName"
                    )

                    scope.launch {
                        try {
                            val username = "${firstName.lowercase()}.${lastName.lowercase()}"

                            // ИЗМЕНЕНИЕ: userId теперь Long
                            val userId: Long = userRepository.registerUser(
                                username = username,
                                password = "123456",
                                firstName = firstName,
                                lastName = lastName,
                                role = when (selectedUserRole) {
                                    0 -> "ученик"
                                    1 -> "учитель"
                                    2 -> "администратор"
                                    else -> "ученик"
                                }
                            )

                            // ИЗМЕНЕНИЕ: сохраняем Long ID
                            currentUserId = userId

                            // Получаем пользователя для роли
                            val user = userRepository.getUserById(userId)
                            userRole = user?.role ?: "ученик"

                            // Сохраняем в LocalStorage
                            dataManager.saveUserData(currentUserData, selectedUserRole)

                            println("✅ Пользователь зарегистрирован: $username, ID: $userId")

                        } catch (e: Exception) {
                            println("❌ Ошибка регистрации: ${e.message}")
                        }
                    }

                    when (selectedUserRole) {
                        0 -> currentScreen = "test"
                        1 -> currentScreen = "teacher"
                        2 -> currentScreen = "admin_registration"
                        else -> currentScreen = "test"
                    }
                },
                onRoleSelected = { role -> selectedUserRole = role },
                onMenuClick = { openDrawer() }
            )

            "test" -> PsychologyTestScreen(
                onBackToMain = { currentScreen = "registration" },
                onMenuClick = { openDrawer() },
                onTestCompleted = { score ->
                    userTestScore = score

                    scope.launch {
                        dataManager.saveTestResult(
                            userId = getCurrentUserIdAsLong(), // ← ИСПРАВЛЕНИЕ: используем Long версию
                            studentName = currentUserData.fullName,
                            score = score,
                            date = getCurrentDateTime(),
                            answers = List(40) { 2 },
                            recommendations = getAdviceBasedOnScore(score)
                        )
                    }

                    currentScreen = "personal_advice"
                }
            )
            "teacher" -> TeacherScreen(
                onBackToMain = { currentScreen = "registration" },
                onMenuClick = { openDrawer() },
                onOpenChatList = { currentScreen = "teacher_chat_list" },
                onOpenChatWithStudent = { studentId, studentName ->
                    // studentId уже Long
                    currentStudentChat = Pair(studentId, studentName)
                    currentScreen = "chat"
                },
                onViewStudentHistory = { student ->
                    currentStudentForHistory = student
                    currentScreen = "student_history"
                }
            )

            "admin_registration" -> AdminRegistrationScreen(
                onBackToMain = { currentScreen = "registration" },
                onAdminRegistered = { currentScreen = "admin" }
            )

            "admin" -> AdminScreen(
                onBackToMain = { currentScreen = "registration" },
                onMenuClick = { openDrawer() }
            )

            "teacher_chat_list" -> TeacherChatListScreen(
                onBackToMain = { currentScreen = "registration" },
                onOpenChat = { studentId, studentName ->
                    currentStudentChat = Pair(studentId, studentName)
                    currentScreen = "chat"
                },
                onMenuClick = { openDrawer() }
            )
            "call" -> {
                val data = callScreenData
                if (data != null) {
                    CallScreen(
                        callerName = data.remoteName,
                        remoteUserId = data.remoteUserId,
                        currentUserId = getCurrentUserIdAsLong().toString(),
                        isIncomingCall = data.isIncoming,

                        // ПЕРЕДАЕМ ТИП ЗВОНКА
                        isVideoCall = data.isVideo,

                        onCallFinished = {
                            callScreenData = null
                            // Возврат назад
                            currentScreen = if (userRole == "учитель" || userRole == "teacher") "teacher" else "personal_advice"
                        }
                    )
                } else {
                    currentScreen = "personal_advice"
                }
            }
            // В ChatScreen:
            "chat" -> {
                val (studentId, studentName) = currentStudentChat ?: Pair(0L, "Ученик")
                ChatScreen(
                    studentId = studentId,
                    studentName = studentName,
                    teacherId = getCurrentUserIdAsLong(),
                    teacherName = currentUserData.fullName,
                    currentUserId = getCurrentUserIdAsLong(),
                    onBack = { currentScreen = "teacher_chat_list" },
                    onMenuClick = { openDrawer() },

                    // ИЗМЕНЕНИЕ: Теперь принимаем isVideo (true/false)
                    onStartCall = { isVideo ->
                        callScreenData = CallScreenData(
                            remoteUserId = studentId.toString(),
                            remoteName = studentName,
                            isIncoming = false,
                            isVideo = isVideo // <--- Передаем выбор пользователя
                        )
                        currentScreen = "call"
                    }
                )
            }


            "personal_advice" -> PersonalAdviceScreen(
                userName = currentUserData.fullName,
                userScore = userTestScore,
                onStartChat = { currentScreen = "student_chat_list" },
                onBackToMain = { currentScreen = "registration" },
                onViewHistory = { currentScreen = "my_history" },
                onRelaxation = { currentScreen = "relaxation" }
            )
            // В StudentChatListScreen:
            "student_chat_list" -> StudentChatListScreen(
                onBackToMain = { currentScreen = "personal_advice" },
                onOpenChat = { teacherId, teacherName ->
                    currentTeacherChat = Pair(teacherId, teacherName)
                    currentScreen = "student_chat"
                },
                onMenuClick = { openDrawer() }
            )

            "student_chat" -> {
                val (teacherId, teacherName) = currentTeacherChat ?: Pair(0L, "Учитель")
                StudentChatScreen(
                    teacherId = teacherId,
                    teacherName = teacherName,
                    studentId = getCurrentUserIdAsLong(),
                    studentName = currentUserData.fullName,
                    onBack = { currentScreen = "student_chat_list" },
                    onMenuClick = { openDrawer() },
                    onRetakeTest = { currentScreen = "test" },

                    // ИЗМЕНЕНИЕ: Принимаем isVideo
                    onStartCall = { isVideo ->
                        callScreenData = CallScreenData(
                            remoteUserId = teacherId.toString(),
                            remoteName = teacherName,
                            isIncoming = false,
                            isVideo = isVideo // <--- Передаем
                        )
                        currentScreen = "call"
                    }
                )
            }



            "student_history" -> {
                val student = currentStudentForHistory ?: Student(
                    id = 0,
                    firstName = "Неизвестный",
                    lastName = "Ученик",
                    testHistory = emptyList() // временно пустая история
                )
                StudentTestHistoryScreen(
                    studentName = "${student.firstName} ${student.lastName}",
                    testHistory = student.testHistory,
                    onBack = { currentScreen = "teacher" },
                    onMenuClick = { openDrawer() }
                )
            }

            "my_history" -> {
                var roomHistoryState by remember { mutableStateOf<List<TestResult>>(emptyList()) }
                var isLoadingHistory by remember { mutableStateOf(false) }

                LaunchedEffect(currentUserId) {
                    if (currentUserId != null) {
                        isLoadingHistory = true

                        // ИСПРАВЛЕНИЕ: используем Long версию
                        dataManager.getTestHistoryFromRoom(getCurrentUserIdAsLong()).collect { history ->
                            roomHistoryState = history
                            isLoadingHistory = false
                            println("📊 Загружено ${history.size} тестов из Room")
                        }
                    }
                }

                if (isLoadingHistory) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    StudentTestHistoryScreen(
                        studentName = currentUserData.fullName,
                        testHistory = roomHistoryState,
                        onBack = { currentScreen = "personal_advice" },
                        onMenuClick = { openDrawer() }
                    )
                }
            }
            "relaxation" -> RelaxationScreen(
                onBack = { currentScreen = "personal_advice" },
                onMenuClick = { openDrawer() },
                onStartBreathing = { currentScreen = "breathing_exercise" },
                onStartMuscleRelaxation = { currentScreen = "muscle_relaxation" },
                onStartMeditation = { currentScreen = "meditation" }
            )

            "breathing_exercise" -> BreathingExerciseScreen(
                onBack = { currentScreen = "relaxation" },
                onMenuClick = { openDrawer() }
            )

            "muscle_relaxation" -> MuscleRelaxationScreen(
                onBack = { currentScreen = "relaxation" },
                onMenuClick = { openDrawer() }
            )

            "meditation" -> MeditationScreen(
                onBack = { currentScreen = "relaxation" },
                onMenuClick = { openDrawer() }
            )
        }
    }
}
// ЭКРАН РЕГИСТРАЦИИ
@Composable
fun RegistrationScreen(
    onStartTest: (String, String) -> Unit, // ← ТЕПЕРЬ ПРИНИМАЕМ ИМЯ И ФАМИЛИЮ
    onRoleSelected: (Int) -> Unit = {},
    onMenuClick: () -> Unit
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(0) }

    LaunchedEffect(selectedRole) {
        onRoleSelected(selectedRole)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FF))
    ) {
        // ШАПКА С КНОПКОЙ МЕНЮ
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Кнопка меню (три полоски)
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Меню",
                    tint = Color(0xFF6A5AE0)
                )
            }

            Text(
                text = "Регистрация",
                style = MaterialTheme.typography.headlineSmall,
                color = Color(0xFF6A5AE0)
            )

            // ПУСТОЙ ЭЛЕМЕНТ ДЛЯ ВЫРАВНИВАНИЯ (вместо кнопки темы)
            Spacer(modifier = Modifier.size(48.dp))
        }

        // ОСНОВНОЙ КОНТЕНТ
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center, // ← ИСПОЛЬЗУЕМ Center вместо weight
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Заголовок
            Text(
                text = "👋 Добро пожаловать!",
                style = MaterialTheme.typography.headlineLarge,
                color = Color(0xFF6A5AE0),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Психологический помощник",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF6A5AE0).copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Карточка с формой
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Поля ввода
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("Имя") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color(0xFF6A5AE0),
                            unfocusedIndicatorColor = Color(0xFF6A5AE0).copy(alpha = 0.5f)
                        )
                    )

                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("Фамилия") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color(0xFF6A5AE0),
                            unfocusedIndicatorColor = Color(0xFF6A5AE0).copy(alpha = 0.5f)
                        )
                    )

                    // Выбор роли
                    Text(
                        text = "Выберите роль:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RoleButton(
                            text = "🎓 Ученик",
                            isSelected = selectedRole == 0,
                            onClick = { selectedRole = 0 }
                        )

                        RoleButton(
                            text = "👨‍🏫 Учитель",
                            isSelected = selectedRole == 1,
                            onClick = { selectedRole = 1 }
                        )
                    }
                    // Кнопка регистрации
                    Button(
                        onClick = {
                            if (firstName.isNotEmpty() && lastName.isNotEmpty()) {
                                // ПЕРЕДАЕМ ИМЯ И ФАМИЛИЮ
                                onStartTest(firstName, lastName)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6A5AE0)
                        ),
                        enabled = firstName.isNotEmpty() && lastName.isNotEmpty()
                    ) {
                        Text("Начать тест 🚀", color = Color.White)
                    }
                }
            }
        }
    }
}
@Composable
fun RoleButton(text: String, isSelected: Boolean, onClick: () -> Unit) { //Создаем переиспользуемый компонент кнопки роли
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent // проверяем выбрана ли кнопка если да то берем основной цвет темы .Если нет полностью прозрачный фон
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline//Если выбрана: Основной цвет темы . Если не выбрана: MaterialTheme.colorScheme.outline - стандартный цвет границы (серый)
    val textColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = if (isSelected) CardDefaults.cardElevation(4.dp) else CardDefaults.cardElevation(1.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp), // Отступы вокруг всей строки
            verticalAlignment = Alignment.CenterVertically // Выравнивание по центру по вертикали
        ) {
            RadioButton(
                selected = isSelected, // Состояние выбора (true/false)
                onClick = onClick,  // Обработчик клика
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary // Цвет выбранной кнопки
                )
            )
            Text(
                text = text, // Текст кнопки
                modifier = Modifier.padding(start = 8.dp),// Отступ слева от RadioButton
                color = textColor,  // Цвет текста (зависит от выбора)
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal  // Жирность текста
            )
        }
    }
}
@Composable
fun PsychologyTestScreen(
    onBackToMain: () -> Unit,
    onMenuClick: () -> Unit,
    onTestCompleted: (Int) -> Unit,
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // Получаем репозиторий
    val application = context.applicationContext as PsyHelperApplication
    val firebaseRepository = application.firebaseRepository

    val allQuestions = remember { mutableStateOf<List<Question>>(emptyList()) }

    // Загрузка
    LaunchedEffect(Unit) {
        try {
            val loaded = QuestionLoader.loadQuestions(context, firebaseRepository)
            if (loaded.isEmpty()) loadError = "Вопросы не найдены" else allQuestions.value = loaded
        } catch (e: Exception) {
            loadError = "Ошибка: ${e.message}"
        }
        isLoading = false
    }

    // Обработка загрузки и ошибок
    if (isLoading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (loadError != null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text(loadError!!) }
        return
    }

    // Выбираем вопросы (берем все или перемешиваем)
    val questions = remember(allQuestions.value) {
        if (allQuestions.value.size > 40) allQuestions.value.shuffled().take(40)
        else allQuestions.value
    }

    var currentQuestionIndex by remember { mutableStateOf(0) }
    // Храним баллы (Int) для каждого вопроса. -1 значит "не отвечено"
    var answers by remember { mutableStateOf(List(questions.size) { -1 }) }
    var showResult by remember { mutableStateOf(false) }

    if (showResult) {
        val totalScore = answers.sum()
        // Рассчитываем макс. балл динамически (кол-во вопросов * 4)
        // Предполагаем, что макс. ответ всегда стоит 4 балла
        val maxPossibleScore = questions.size * 4

        TestResultScreen(
            score = totalScore,
            maxScore = maxPossibleScore,
            onBackToMain = { onTestCompleted(totalScore) }
        )
        return
    }

    // UI ТЕСТА
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Шапка
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, "Меню", tint = Color(0xFF6A5AE0)) }
            Text("Тестирование", style = MaterialTheme.typography.titleLarge, color = Color(0xFF6A5AE0))
            IconButton(onClick = onBackToMain) { Icon(Icons.Default.ArrowBack, "Назад", tint = Color(0xFF6A5AE0)) }
        }

        // Прогресс
        LinearProgressIndicator(
            progress = (currentQuestionIndex + 1) / questions.size.toFloat(),
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF6A5AE0)
        )

        // Вопрос
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val question = questions[currentQuestionIndex]

            Text("Вопрос ${currentQuestionIndex + 1} из ${questions.size}", color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Text(question.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Категория: ${question.category}", fontSize = 12.sp, color = Color.Gray)

            Spacer(Modifier.height(24.dp))

            // ВАРИАНТЫ ОТВЕТОВ (ДИНАМИЧЕСКИЕ)
            question.options.forEachIndexed { index, optionText ->
                // Расчет балла: индекс (0, 1, 2...)
                // Если вариантов 2 ("Да", "Нет"), то Да=0, Нет=1.
                // Но обычно в тестах наоборот: Нет=0, Да=1.
                // Давай сделаем просто: индекс = балл.
                val scoreValue = index

                val isSelected = answers[currentQuestionIndex] == scoreValue

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(if (isSelected) Color(0xFFE8EAF6) else Color.Transparent)
                        .clickable {
                            val newAnswers = answers.toMutableList()
                            newAnswers[currentQuestionIndex] = scoreValue
                            answers = newAnswers
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = null, // Обработка в Row
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF6A5AE0))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(optionText, fontSize = 16.sp)
                }
            }
        }

        // Кнопки
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentQuestionIndex > 0) {
                OutlinedButton(onClick = { currentQuestionIndex-- }) { Text("Назад") }
            } else {
                Spacer(Modifier.width(10.dp))
            }

            Button(
                onClick = {
                    if (currentQuestionIndex < questions.size - 1) {
                        currentQuestionIndex++
                    } else {
                        showResult = true
                    }
                },
                enabled = answers[currentQuestionIndex] != -1,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A5AE0))
            ) {
                Text(if (currentQuestionIndex < questions.size - 1) "Далее" else "Завершить")
            }
        }
    }
}

// === ЭКРАН РЕЗУЛЬТАТА  ===
@Composable
fun TestResultScreen(
    score: Int,
    maxScore: Int,
    onBackToMain: () -> Unit
) {
    // Рассчитываем процент успешности
    val percentage = (score.toFloat() / maxScore.toFloat()) * 100

    val resultText = when {
        percentage <= 40 -> "Низкий уровень. Стоит обратить внимание на отдых."
        percentage <= 70 -> "Средний уровень. Все в порядке, но есть зоны роста."
        else -> "Высокий уровень! Отличное эмоциональное состояние."
    }

    val resultColor = when {
        percentage <= 40 -> Color(0xFFE53935)
        percentage <= 70 -> Color(0xFFFB8C00)
        else -> Color(0xFF43A047)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Результат теста", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$score / $maxScore",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = resultColor
                )
                Text("баллов", color = Color.Gray)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(resultText, textAlign = TextAlign.Center, fontSize = 18.sp)
        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onBackToMain,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A5AE0))
        ) {
            Text("Завершить")
        }
    }
}
// ЭКРАН ЗАГРУЗКИ
@Composable
fun LoadingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = Color(0xFF6A5AE0)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Загружаем вопросы...",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF6A5AE0)
        )
    }
}

// ЭКРАН ОШИБКИ
@Composable
fun ErrorScreen(
    error: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "❌ Ошибка загрузки",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.Red
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFCDD2))
        ) {
            Text(
                text = error,
                modifier = Modifier.padding(16.dp),
                color = Color(0xFFB71C1C)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6A5AE0)
                )
            ) {
                Text("Повторить загрузку")
            }

            TextButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Вернуться назад")
            }
        }
    }
}
// ЭКРАН РЕЗУЛЬТАТОВ ТЕСТА (ОБНОВЛЁННАЯ)
@Composable
fun TestResultScreen(
    answers: List<Int>,
    onBackToMain: () -> Unit
) {
    val totalScore = answers.sum()
    val resultText = when {
        totalScore <= 15 -> "Низкий уровень эмоционального благополучия. Рекомендуется обратиться к психологу."
        totalScore <= 25 -> "Средний уровень. Есть над чем работать, но в целом стабильное состояние."
        else -> "Высокий уровень эмоционального благополучия. Продолжайте в том же духе!"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .background(Color.White),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Результаты теста",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Ваш результат:")
                Text(
                    text = "$totalScore/40 баллов",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFF6A5AE0)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = resultText,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ИЗМЕНЯЕМ КНОПКУ - она должна передавать результат, а не просто возвращать
        Button(
            onClick = {
                // ВЫЗЫВАЕМ onBackToMain КОТОРЫЙ ПЕРЕДАСТ РЕЗУЛЬТАТ
                onBackToMain()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6A5AE0)
            )
        ) {
            Text("Посмотреть персональные рекомендации", color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ДОБАВЛЯЕМ КНОПКУ ДЛЯ ВОЗВРАТА НА ГЛАВНУЮ (если нужно)
        TextButton(
            onClick = {
                // Здесь нужна дополнительная логика для возврата на главную
                // Пока оставим пустым или уберем
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Вернуться на главную", color = Color.Gray)
        }
    }
}
@Composable
fun TeacherScreen(
    onBackToMain: () -> Unit,
    onMenuClick: () -> Unit,
    onOpenChatList: () -> Unit,
    onOpenChatWithStudent: (Long, String) -> Unit,
    onViewStudentHistory: (Student) -> Unit
) {
    val context = LocalContext.current
    val application = (context.applicationContext as PsyHelperApplication)
    val userRepository = application.userRepository
    val testResultRepository = application.testResultRepository
    val scope = rememberCoroutineScope()

    // 1. Загружаем список пользователей (учеников)
    val usersFlow = userRepository.getStudents().collectAsState(initial = emptyList())

    // 2. Состояние для хранения учеников С РЕЗУЛЬТАТАМИ
    var studentsWithHistory by remember { mutableStateOf<List<Student>>(emptyList()) }

    // 3. Загружаем результаты тестов для каждого ученика
    LaunchedEffect(usersFlow.value) {
        val loadedStudents = mutableListOf<Student>()

        usersFlow.value.forEach { user ->
            // Загружаем историю тестов для конкретного ученика
            // Используем firstOrNull, чтобы получить текущее значение flow один раз
            val history = testResultRepository.getTestHistory(user.id).firstOrNull() ?: emptyList()

            // Находим последний результат (если есть)
            // Сортируем по дате, чтобы найти самый свежий, если база не сортирует
            val lastResult = history.maxByOrNull { it.date }

            loadedStudents.add(
                Student(
                    id = user.id,
                    firstName = user.firstName,
                    lastName = user.lastName,
                    testScore = lastResult?.score, // ✅ Реальный балл
                    testHistory = history,         // ✅ Реальная история
                    lastActive = "Недавно",
                    hasUnreadMessages = false
                )
            )
        }
        studentsWithHistory = loadedStudents
    }

    var searchText by remember { mutableStateOf("") }

    // Фильтрация
    val filteredStudents = studentsWithHistory.filter { student ->
        student.firstName.contains(searchText, ignoreCase = true) ||
                student.lastName.contains(searchText, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FF))
    ) {
        // ШАПКА
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, "Меню", tint = Color(0xFF6A5AE0))
            }
            Text("Панель учителя", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF6A5AE0))
            IconButton(onClick = onBackToMain) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = Color(0xFF6A5AE0))
            }
        }

        // ПОИСК И СТАТИСТИКА
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Учеников: ${studentsWithHistory.size}", color = Color.Gray)

                if (studentsWithHistory.isNotEmpty()) {
                    val withTests = studentsWithHistory.count { it.testScore != null }
                    // Подсчет среднего балла (только среди тех, кто сдал)
                    val avgScore = if (withTests > 0)
                        studentsWithHistory.mapNotNull { it.testScore }.average().toInt()
                    else 0

                    Text(
                        text = "Прошли тест: $withTests | Средний балл: $avgScore",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("🔍 Поиск ученика...") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // СПИСОК
        if (studentsWithHistory.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Пока нет учеников", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                    if (usersFlow.value.isNotEmpty()) {
                        CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                        Text("Загрузка результатов...", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredStudents) { student ->
                    StudentCard(
                        student = student,
                        onChatClick = { onOpenChatWithStudent(student.id, "${student.firstName} ${student.lastName}") },
                        onViewHistory = { onViewStudentHistory(student) }
                    )
                }
            }

            // КНОПКИ ВНИЗУ
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onOpenChatList,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A5AE0))
                ) { Text("💬 Перейти к чатам") }
            }
        }
    }
}

@Composable
fun StudentCard(student: Student, onChatClick: () -> Unit, onViewHistory: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onChatClick() },
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ИНФО
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${student.firstName} ${student.lastName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (student.testScore != null) {
                        val scoreColor = when {
                            student.testScore <= 15 -> Color(0xFFE53935) // Плохо
                            student.testScore <= 25 -> Color(0xFFFB8C00) // Средне
                            else -> Color(0xFF43A047)                    // Хорошо
                        }
                        Text("Последний тест: ${student.testScore}/40", color = scoreColor, fontWeight = FontWeight.Medium)
                    } else {
                        Text("Тест не пройден", color = Color.Gray)
                    }

                    if (student.testHistory.isNotEmpty()) {
                        Text("Всего попыток: ${student.testHistory.size}", fontSize = 12.sp, color = Color.Gray)
                    }
                }

                // ДЕЙСТВИЯ
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onChatClick,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A5AE0))
                    ) { Text("Чат", fontSize = 12.sp) }

                    OutlinedButton(
                        onClick = onViewHistory,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) { Text("История", fontSize = 12.sp) }
                }
            }
        }
    }
}


@Composable
fun AdminScreen(
    onBackToMain: () -> Unit,
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current
    val application = (context.applicationContext as PsyHelperApplication)
    val userRepository = application.userRepository
    val syncManager = application.syncManager
    val firebaseRepository = application.firebaseRepository

    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    // 5 вкладок
    val tabs = listOf("👥 Ученики", "👨‍🏫 Учителя", "📊 Аналитика", "⚙️ Настройки", "📝 Тесты")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FF))
    ) {
        // ШАПКА
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, "Меню", tint = Color(0xFF6A5AE0))
            }

            Text(
                text = "Панель администратора",
                style = MaterialTheme.typography.headlineSmall,
                color = Color(0xFF6A5AE0)
            )

            Row {
                IconButton(
                    onClick = {
                        scope.launch { syncManager.syncAllData() }
                    }
                ) {
                    Icon(Icons.Default.Refresh, "Синхронизировать", tint = Color(0xFF6A5AE0))
                }

                IconButton(onClick = onBackToMain) {
                    Icon(Icons.Default.ArrowBack, "Назад", tint = Color(0xFF6A5AE0))
                }
            }
        }

        // ПЕРЕКЛЮЧАТЕЛЬ ВКЛАДОК
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth(),
            containerColor = Color.Transparent,
            contentColor = Color(0xFF6A5AE0),
            edgePadding = 0.dp
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    text = { Text(title) },
                    selected = selectedTab == index,
                    onClick = { selectedTab = index }
                )
            }
        }

        // КОНТЕНТ ВКЛАДОК
        when (selectedTab) {
            0 -> StudentsAdminContent(userRepository)
            1 -> TeachersAdminContent(userRepository)
            2 -> AnalyticsAdminContent(userRepository)
            3 -> SettingsAdminContent(syncManager, userRepository)
            4 -> TestsAdminContent(firebaseRepository)
        }
    }
}

// === 1. УЧЕНИКИ ===
@Composable
fun StudentsAdminContent(userRepository: UserRepository) {
    val students = userRepository.getStudents().collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск учеников...") },
                modifier = Modifier.weight(1f),
                leadingIcon = { Icon(Icons.Default.Search, "Поиск") }
            )
            Button(onClick = { showAddDialog = true }, modifier = Modifier.padding(start = 8.dp)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Text("Добавить", modifier = Modifier.padding(start = 4.dp))
            }
        }
        Spacer(Modifier.height(16.dp))

        if (students.value.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("📚 Нет учеников", color = Color.Gray) }
        } else {
            val filtered = students.value.filter { it.firstName.contains(searchQuery, true) || it.lastName.contains(searchQuery, true) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered) { student ->
                    StudentAdminItem(student, onDelete = { scope.launch { userRepository.deleteUser(student.id) } })
                }
            }
        }
    }
    if (showAddDialog) AddUserDialog("Добавить ученика", "ученик", { showAddDialog = false }, { f, l, u, p -> scope.launch { userRepository.registerUser(u, p, f, l, "ученик"); showAddDialog = false } })
}

// === 2. УЧИТЕЛЯ ===
@Composable
fun TeachersAdminContent(userRepository: UserRepository) {
    val teachers = userRepository.getTeachers().collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, placeholder = { Text("Поиск...") }, modifier = Modifier.weight(1f))
            Button(onClick = { showAddDialog = true }, modifier = Modifier.padding(start = 8.dp)) { Text("Добавить") }
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(teachers.value.filter { it.firstName.contains(searchQuery, true) }) { teacher ->
                TeacherAdminItem(teacher, onDelete = { scope.launch { userRepository.deleteUser(teacher.id) } })
            }
        }
    }
    if (showAddDialog) AddUserDialog("Добавить учителя", "учитель", { showAddDialog = false }, { f, l, u, p -> scope.launch { userRepository.registerUser(u, p, f, l, "учитель"); showAddDialog = false } })
}

// === 3. АНАЛИТИКА ===
@Composable
fun AnalyticsAdminContent(userRepository: UserRepository) {
    val students = userRepository.getStudents().collectAsState(initial = emptyList())
    val teachers = userRepository.getTeachers().collectAsState(initial = emptyList())
    val total = students.value.size + teachers.value.size

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("📊 Аналитика", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF6A5AE0))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatCard("Всего", "$total", "👥", Color(0xFF2196F3))
            StatCard("Учеников", "${students.value.size}", "🎓", Color(0xFF4CAF50))
            StatCard("Учителей", "${teachers.value.size}", "👨‍🏫", Color(0xFFFF9800))
        }
    }
}

// === 4. НАСТРОЙКИ ===
@Composable
fun SettingsAdminContent(syncManager: SyncManager, userRepository: UserRepository) {
    val allUsers = userRepository.getAllUsers().collectAsState(initial = emptyList())
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("⚙️ Настройки", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF6A5AE0))
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFF5F5F5))) {
            Column(Modifier.padding(16.dp)) {
                Text("Система", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                SystemInfoRow("Пользователей", "${allUsers.value.size}")
                SystemInfoRow("Версия", "1.0.0")
            }
        }
    }
}

// === 5. ТЕСТЫ (УПРАВЛЕНИЕ ВОПРОСАМИ) ===
@Composable
fun TestsAdminContent(firebaseRepository: FirebaseRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var questionToEdit by remember { mutableStateOf<Question?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        questions = QuestionLoader.loadQuestions(context, firebaseRepository)
        isLoading = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Управление вопросами", style = MaterialTheme.typography.titleLarge, color = Color(0xFF6A5AE0))
            Button(
                onClick = {
                    val newId = (questions.maxOfOrNull { it.id } ?: 0) + 1
                    questionToEdit = Question(newId, "", "Общее", listOf("Да", "Нет"))
                    showDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Text("Добавить", Modifier.padding(start = 4.dp)) }
        }
        Spacer(Modifier.height(16.dp))

        if (isLoading) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(questions) { question ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("№${question.id}", fontWeight = FontWeight.Bold, color = Color.Gray)
                            Text(question.category, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6A5AE0))
                        }
                        Text(question.text, fontSize = 16.sp)
                        Text("Ответы: ${question.options.joinToString(", ")}", fontSize = 12.sp, color = Color.Gray)
                        Row(Modifier.align(Alignment.End)) {
                            TextButton(onClick = { scope.launch { firebaseRepository.deleteQuestion(question.id); questions = questions.filter { it.id != question.id } } }) { Text("Удалить", color = Color.Red) }
                            TextButton(onClick = { questionToEdit = question; showDialog = true }) { Text("Редактировать") }
                        }
                    }
                }
            }
        }
    }

    if (showDialog && questionToEdit != null) {
        val currentQ = questionToEdit!!
        var text by remember(currentQ) { mutableStateOf(currentQ.text) }
        var category by remember(currentQ) { mutableStateOf(currentQ.category) }
        var optionsString by remember(currentQ) { mutableStateOf(currentQ.options.joinToString(", ")) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (currentQ.text.isEmpty()) "Новый вопрос" else "Редактировать") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Вопрос") })
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Категория") })
                    OutlinedTextField(value = optionsString, onValueChange = { optionsString = it }, label = { Text("Ответы (через запятую)") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newOpts = optionsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val newQ = currentQ.copy(text = text, category = category, options = if (newOpts.isNotEmpty()) newOpts else listOf("Да", "Нет"))
                    scope.launch {
                        firebaseRepository.saveQuestion(newQ)
                        val newList = questions.toMutableList()
                        val idx = newList.indexOfFirst { it.id == newQ.id }
                        if (idx != -1) newList[idx] = newQ else newList.add(newQ)
                        questions = newList
                        showDialog = false
                    }
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Отмена") } }
        )
    }
}

// === ВСПОМОГАТЕЛЬНЫЕ КОМПОНЕНТЫ ===

@Composable
fun AddUserDialog(
    title: String,
    role: String,
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String) -> Unit
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("123456") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("Имя") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Фамилия") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Логин") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (firstName.isNotBlank() && username.isNotBlank()) {
                        onAdd(firstName, lastName, username, password)
                    }
                },
                enabled = firstName.isNotBlank()
            ) {
                Text("Добавить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun StudentAdminItem(student: User, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(student.avatarColor)),
                contentAlignment = Alignment.Center
            ) {
                Text("👤", fontSize = 16.sp)
            }

            Column(
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            ) {
                Text("${student.firstName} ${student.lastName}", fontWeight = FontWeight.Bold)
                Text("@${student.username}", color = Color.Gray, fontSize = 12.sp)
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color.Red)
            }
        }
    }
}

@Composable
fun TeacherAdminItem(teacher: User, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(50.dp).clip(CircleShape).background(Color(0xFF4CAF50)),
                contentAlignment = Alignment.Center
            ) {
                Text("👨‍🏫", fontSize = 20.sp)
            }

            Column(
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            ) {
                Text("${teacher.firstName} ${teacher.lastName}", fontWeight = FontWeight.Bold)
                Text("Логин: ${teacher.username}", color = Color.Gray, fontSize = 12.sp)
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color.Red)
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, icon: String, color: Color) {
    Card(
        modifier = Modifier.width(110.dp).height(90.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = icon, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(text = title, fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
fun SystemInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray)
        Text(text = value, fontWeight = FontWeight.Medium)
    }
}


@Composable
fun StudentsAdminContent() {
    val students = remember {
        listOf(
            StudentAdmin("Алексей Петров", "8Б", "Нормальное", "12.12.2024", 65),
            StudentAdmin("Мария Сидорова", "9А", "Повышенный стресс", "11.12.2024", 82),
            StudentAdmin("Иван Козлов", "10В", "Отличное", "10.12.2024", 42),
            StudentAdmin("Елена Новикова", "7А", "Тревожное", "09.12.2024", 78),
            StudentAdmin("Дмитрий Волков", "11Б", "Критическое", "08.12.2024", 95)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // СТАТИСТИКА
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Всего", "127", Color(0xFF6A5AE0))
                StatItem("Высокий риск", "8", Color(0xFFE53935))
                StatItem("Норма", "89", Color(0xFF4CAF50))
                StatItem("Не тестировались", "15", Color(0xFF9E9E9E))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // СПИСОК УЧЕНИКОВ
        Text(
            text = "Список учеников",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn {
            items(students) { student ->
                StudentAdminItem(student)
            }
        }
    }
}

@Composable
fun TeachersAdminContent() {
    val teachers = remember {
        listOf(
            TeacherAdmin("Ольга Иванова", "olga@school.ru", "45 учеников", "Активен"),
            TeacherAdmin("Сергей Комаров", "sergey@school.ru", "38 учеников", "Активен"),
            TeacherAdmin("Анна Смирнова", "anna@school.ru", "52 ученика", "Неактивен"),
            TeacherAdmin("Михаил Орлов", "mikhail@school.ru", "29 учеников", "Активен")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // КНОПКИ УПРАВЛЕНИЯ
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { /* Добавить учителя */ },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("➕ Добавить")
            }
            Button(
                onClick = { /* Массовая рассылка */ },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
            ) {
                Text("📧 Рассылка")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // СПИСОК УЧИТЕЛЕЙ
        Text(
            text = "Преподаватели",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn {
            items(teachers) { teacher ->
                TeacherAdminItem(teacher)
            }
        }
    }
}

@Composable
fun StudentAdminItem(student: StudentAdmin) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // АВАТАР
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(
                        color = when {
                            student.stressLevel > 80 -> Color(0xFFFFCDD2)
                            student.stressLevel > 60 -> Color(0xFFFFE0B2)
                            else -> Color(0xFFC8E6C9)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = student.name.split(" ").map { it.first() }.joinToString(""),
                    color = Color(0xFF6A5AE0),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // ИНФОРМАЦИЯ
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${student.className} • ${student.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    text = "Последний тест: ${student.lastTest}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            // УРОВЕНЬ СТРЕССА
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = when {
                            student.stressLevel > 80 -> Color(0xFFE53935)
                            student.stressLevel > 60 -> Color(0xFFFF9800)
                            else -> Color(0xFF4CAF50)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${student.stressLevel}%",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TeacherAdminItem(teacher: TeacherAdmin) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(Color(0xFFE3F2FD), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("👨‍🏫", fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = teacher.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = teacher.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    text = "${teacher.studentsCount} • ${teacher.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            IconButton(onClick = { /* Действия */ }) {
                Icon(Icons.Default.MoreVert, "Действия")
            }
        }
    }
}

@Composable
fun StatItem(title: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}

@Composable
fun AdminRegistrationScreen(
    onBackToMain: () -> Unit,
    onAdminRegistered: () -> Unit
) {
    val context = LocalContext.current
    val application = (context.applicationContext as PsyHelperApplication)
    val userRepository = application.userRepository
    val scope = rememberCoroutineScope()

    var adminName by remember { mutableStateOf("") }
    var adminEmail by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }
    var adminCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FF))
    ) {
        // ШАПКА
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackToMain) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = Color(0xFF6A5AE0))
            }

            Text(
                text = "Регистрация администратора",
                style = MaterialTheme.typography.headlineSmall,
                color = Color(0xFF6A5AE0)
            )

            Spacer(modifier = Modifier.size(48.dp))
        }

        // ОСНОВНОЙ КОНТЕНТ
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ОШИБКА
            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("❌", fontSize = 20.sp)
                        Text(
                            text = errorMessage!!,
                            modifier = Modifier.padding(start = 8.dp),
                            color = Color.Red
                        )
                    }
                }
            }

            // КАРТОЧКА С ФОРМОЙ
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = adminName,
                        onValueChange = { adminName = it },
                        label = { Text("ФИО администратора") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = adminEmail,
                        onValueChange = { adminEmail = it },
                        label = { Text("Служебный email") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = adminPassword,
                        onValueChange = { adminPassword = it },
                        label = { Text("Пароль (мин. 6 символов)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = adminCode,
                        onValueChange = { adminCode = it },
                        label = { Text("Секретный код доступа") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Red,
                            unfocusedIndicatorColor = Color.Red.copy(alpha = 0.5f)
                        )
                    )

                    Button(
                        onClick = {
                            if (adminCode != "1234") {
                                errorMessage = "Неверный секретный код"
                                return@Button
                            }

                            if (adminPassword.length < 6) {
                                errorMessage = "Пароль должен быть не менее 6 символов"
                                return@Button
                            }

                            scope.launch {
                                try {
                                    // Регистрируем администратора
                                    val adminId = userRepository.registerUser(
                                        username = adminEmail.split("@").first(),
                                        password = adminPassword,
                                        firstName = adminName.split(" ").firstOrNull() ?: "Admin",
                                        lastName = adminName.split(" ").lastOrNull() ?: "User",
                                        role = "администратор"
                                    )

                                    println("✅ Администратор зарегистрирован: $adminName (ID: $adminId)")
                                    onAdminRegistered()

                                } catch (e: Exception) {
                                    errorMessage = "Ошибка регистрации: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = adminName.isNotEmpty() && adminEmail.isNotEmpty() &&
                                adminPassword.isNotEmpty() && adminCode.isNotEmpty()
                    ) {
                        Text("🔐 Зарегистрировать администратора")
                    }
                }
            }
        }
    }
}

@Composable
fun TeacherChatListScreen(
    onBackToMain: () -> Unit,
    onOpenChat: (Long, String) -> Unit,
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current
    val application = (context.applicationContext as PsyHelperApplication)
    val userRepository = application.userRepository
    val syncManager = application.syncManager

    // ЗАГРУЖАЕМ РЕАЛЬНЫХ УЧЕНИКОВ ИЗ БАЗЫ
    val students = userRepository.getStudents().collectAsState(initial = emptyList())

    // State для обновления UI
    var refreshTrigger by remember { mutableStateOf(0) }

    // COROUTINE SCOPE - ОДИН РАЗ В НАЧАЛЕ!
    val scope = rememberCoroutineScope()

    // ПРЕОБРАЗУЕМ УЧЕНИКОВ В ЧАТЫ
    val chats = remember(students.value, refreshTrigger) {
        students.value.map { student ->
            Chat(
                chatId = "chat_${student.id}",
                studentId = student.id,
                studentName = "${student.firstName} ${student.lastName}",
                lastMessage = "Начните диалог с учеником",
                lastMessageTime = System.currentTimeMillis(),
                unreadCount = 0
            )
        }
    }

    // REAL-TIME СИНХРОНИЗАЦИЯ ДЛЯ УЧИТЕЛЯ
    // Получаем ID учителя из тестовых данных
    val teacherId = remember { 2L } // ID тестового учителя "teacher.test"

    LaunchedEffect(Unit) {
        println("🎯 [TeacherList] ID учителя: $teacherId")

        // Запускаем периодическую синхронизацию
        while (true) {
            delay(20000) // Каждые 20 секунд
            if (syncManager.isOnline()) {
                println("🔄 [TeacherList] Фоновая синхронизация")
                syncManager.syncMessagesForUser(teacherId)
                refreshTrigger++ // Обновляем UI
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FF))
    ) {
        // ШАПКА
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, "Меню", tint = Color(0xFF6A5AE0))
            }

            Text(
                text = "Чаты с учениками (${chats.size})",
                style = MaterialTheme.typography.headlineSmall,
                color = Color(0xFF6A5AE0)
            )

            Row {
                // КНОПКА СИНХРОНИЗАЦИИ
                IconButton(
                    onClick = {
                        scope.launch {
                            println("🔄 [TeacherList] Ручная синхронизация")
                            syncManager.syncMessagesForUser(teacherId)
                            refreshTrigger++ // Обновляем UI
                        }
                    }
                ) {
                    // ИСПРАВЛЕНО: Используем Refresh иконку
                    Icon(Icons.Default.Refresh, "Синхронизировать", tint = Color(0xFF6A5AE0))
                }

                IconButton(onClick = onBackToMain) {
                    Icon(Icons.Default.ArrowBack, "Назад", tint = Color(0xFF6A5AE0))
                }
            }
        }

        // ЕСЛИ НЕТ УЧЕНИКОВ
        if (chats.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Пока нет учеников",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray
                )
                Text(
                    text = "Ученики появятся здесь после регистрации",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // КНОПКА СИНХРОНИЗАЦИИ ДАННЫХ
                Button(
                    onClick = {
                        scope.launch {
                            println("☁️ Синхронизируем пользователей...")
                            syncManager.syncAllData()
                            refreshTrigger++
                        }
                    },
                    modifier = Modifier.padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6A5AE0)
                    )
                ) {
                    Text("🔄 Синхронизировать из облака")
                }
            }
        } else {
            // СПИСОК ЧАТОВ
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chats) { chat ->
                    ChatListItem(
                        chat = chat,
                        onClick = {
                            onOpenChat(chat.studentId, chat.studentName)
                        }
                    )
                }
            }
        }
    }
}
@Composable
fun ChatListItem(
    chat: Chat,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF6A5AE0), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = chat.studentName.firstOrNull()?.toString() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            ) {
                Text(
                    text = chat.studentName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = chat.lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = formatTime(chat.lastMessageTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )

                if (chat.unreadCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color.Red, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chat.unreadCount.toString(),
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

// Вспомогательная функция для форматирования времени
private fun formatTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60 * 1000 -> "только что"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} мин."
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} ч."
        else -> "${diff / (24 * 60 * 60 * 1000)} дн."
    }
}
@Composable
fun ChatScreen(
    studentId: Long,
    studentName: String,
    teacherId: Long,
    teacherName: String,
    currentUserId: Long,
    onBack: () -> Unit,
    onMenuClick: () -> Unit,
    onStartCall: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val application = (context.applicationContext as PsyHelperApplication)

    // ПОЛУЧАЕМ ВСЕ НУЖНЫЕ РЕПОЗИТОРИИ
    val messageRepository = application.messageRepository
    val syncManager = application.syncManager
    val firebaseRepository = application.firebaseRepository

    var messageText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // STATE ДЛЯ СООБЩЕНИЙ
    var conversation by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }

    // COROUTINE SCOPE ДЛЯ КОМПОЗАБЛ
    val scope = rememberCoroutineScope()

    // ЗАГРУЗКА СООБЩЕНИЙ - УПРОЩЕННАЯ ВЕРСИЯ
    LaunchedEffect(teacherId, studentId) {
        isLoading = true

        try {
            // 1. ПРОСТО загружаем из Room (Firebase загрузку добавим позже)
            messageRepository.getConversation(teacherId, studentId).collect { messages ->
                conversation = messages
                isLoading = false
                println("✅ [ChatScreen] Загружено ${messages.size} сообщений из Room")
            }
        } catch (e: Exception) {
            errorMessage = "Ошибка загрузки: ${e.message}"
            isLoading = false
            println("❌ [ChatScreen] Ошибка: ${e.message}")
        }
    }

    DisposableEffect(teacherId, studentId) {
        println("🎯 [ChatScreen] Запускаем real-time (учитель $teacherId ↔ ученик $studentId)")

        // УЧИТЕЛЬ - текущий пользователь
        syncManager.startConversationRealtime(
            userId1 = teacherId,
            userId2 = studentId,
            currentUserId = teacherId, // ← ВАЖНО: учитель сейчас использует приложение
            onNewMessage = { message ->
                println("💫 [ChatScreen] Получено сообщение от ученика")
            }
        )

        onDispose {
            syncManager.stopConversationRealtime()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FF))
    ) {
        // ШАПКА
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // КНОПКА НАЗАД
            IconButton(onClick = onBack) {
                Text("←", fontSize = 24.sp, color = Color(0xFF6A5AE0))
            }

            Text(
                text = studentName,
                style = MaterialTheme.typography.headlineSmall,
                color = Color(0xFF6A5AE0),
                modifier = Modifier.weight(1f)
            )


            // 1. КНОПКА ВИДЕО
            IconButton(
                onClick = { onStartCall(true) }, // Передаем TRUE
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Icon(Icons.Default.Videocam, "Видео", tint = Color(0xFF6A5AE0))
            }

            // 2. КНОПКА АУДИО
            IconButton(
                onClick = { onStartCall(false) }, // Передаем FALSE
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(Icons.Default.Call, "Аудио", tint = Color(0xFF6A5AE0))
            }
            // КНОПКА ОБНОВЛЕНИЯ
            IconButton(
                onClick = {
                    scope.launch {
                        println("🔄 [ChatScreen] Ручная синхронизация...")
                        // Пока только обновляем UI
                        messageRepository.refresh()
                    }
                },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text("🔄", fontSize = 20.sp, color = Color(0xFF6A5AE0))
            }

            // КНОПКА МЕНЮ
            IconButton(onClick = onMenuClick) {
                Text("☰", fontSize = 24.sp, color = Color(0xFF6A5AE0))
            }
        }

        // ОШИБКА
        if (errorMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("❌", fontSize = 20.sp, color = Color.Red)
                    Text(
                        text = errorMessage!!,
                        modifier = Modifier.padding(start = 8.dp),
                        color = Color.Red,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // ИНДИКАТОР ЗАГРУЗКИ
        if (isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF6A5AE0))
                    Text(
                        text = "Загрузка сообщений...",
                        modifier = Modifier.padding(top = 12.dp),
                        color = Color.Gray
                    )
                }
            }
        }
        // ЕСЛИ НЕТ СООБЩЕНИЙ
        else if (conversation.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("💬", fontSize = 64.sp, color = Color.Gray)
                Text(
                    text = "Начните диалог с учеником",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = "Напишите первое сообщение",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            // СПИСОК СООБЩЕНИЙ
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(conversation.reversed()) { message ->
                    MessageBubble(
                        message = message,
                        isTeacher = message.senderId == teacherId
                    )
                }
            }
        }

        // ПОЛЕ ВВОДА
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                placeholder = { Text("Введите сообщение...") },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color(0xFF6A5AE0),
                    unfocusedIndicatorColor = Color(0xFF6A5AE0).copy(alpha = 0.5f)
                )
            )

            Button(
                onClick = {
                    if (messageText.isNotBlank()) {
                        val textToSend = messageText
                        messageText = ""
                        errorMessage = null

                        scope.launch {
                            try {
                                // ОТПРАВКА С СИНХРОНИЗАЦИЕЙ!
                                messageRepository.sendMessage(
                                    senderId = teacherId,
                                    receiverId = studentId,
                                    senderName = teacherName,
                                    message = textToSend,
                                    syncManager = syncManager // ← передаем syncManager
                                )

                                println("✅ [ChatScreen] Сообщение отправлено")

                            } catch (e: Exception) {
                                errorMessage = "Ошибка отправки: ${e.message}"
                                messageText = textToSend
                                println("❌ [ChatScreen] Ошибка отправки: ${e.message}")
                            }
                        }
                    }
                },
                enabled = messageText.isNotBlank() && !isLoading,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Отпр.")
            }
        }
    }
}

// ФУНКЦИЯ ДЛЯ ПРОВЕРКИ FIREBASE (вынести из Composable)
private suspend fun checkFirebaseForMessages() {
    println("🔍 Проверяем Firebase на наличие сообщений...")
    // Здесь можно добавить логику проверки
    // Пока просто логируем
    println("⚠️ Проверка Firebase еще не реализована")
}

@Composable
fun MessageBubble(message: ChatMessage, isTeacher: Boolean) {
    val horizontalAlignment = if (isTeacher) Alignment.End else Alignment.Start
    val backgroundColor = if (isTeacher) Color(0xFF6A5AE0) else Color(0xFFE8E6FF)

    // ИСПРАВЛЕННЫЙ Box - используем fillMaxWidth и отдельно выравнивание
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .wrapContentWidth()
                .align(if (isTeacher) Alignment.TopEnd else Alignment.TopStart),
            horizontalAlignment = horizontalAlignment
        ) {
            if (!isTeacher) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            Card(
                modifier = Modifier.padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = backgroundColor),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Text(
                    text = message.message,
                    modifier = Modifier.padding(12.dp),
                    color = if (isTeacher) Color.White else Color.Black
                )
            }

            Text(
                text = formatTime(message.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}
/*
// ==================== БАЗА ДАННЫХ (УПРОЩЕННАЯ) ====================
//@Entity(tableName = "users")
//data class UserEntity(
   // @PrimaryKey val id: Int,
   // @ColumnInfo(name = "first_name") val firstName: String,
   // @ColumnInfo(name = "last_name") val lastName: String,
   // @ColumnInfo(name = "role") val role: Int
//)

//@Dao
//interface UserDao {
   // @Query("SELECT * FROM users WHERE role = 0")
    //fun getStudents(): List<UserEntity>

   // @Insert
    //fun insertUser(user: UserEntity)
//}

//@Database(
    //entities = [UserEntity::class],
    //version = 1,
    //exportSchema = false
//)
//abstract class PsychologyDatabase : RoomDatabase() {
    //abstract fun userDao(): UserDao

   // companion object {
    //    @Volatile
     //   private var INSTANCE: PsychologyDatabase? = null

      //  fun getInstance(context: Context): PsychologyDatabase {
         //   return INSTANCE ?: synchronized(this) {
         //       val instance = Room.databaseBuilder(
          //          context.applicationContext,
          //          PsychologyDatabase::class.java,
          //          "psychology_db"
          //      ).build()
          //      INSTANCE = instance
           //     instance
          //  }
       // }
   // }
//}

// ПРОСТОЙ РЕПОЗИТОРИЙ
//class AppRepository(private val context: Context) {
    //private val database = PsychologyDatabase.getInstance(context)

   // fun getStudents(): List<UserEntity> {
      //  return try {
           // database.userDao().getStudents()
       // } catch (e: Exception) {
           // emptyList() // если ошибка - возвращаем пустой список
      //  }
  //  }

    //fun addUser(user: UserEntity) {
      //  try {
      //      database.userDao().insertUser(user)
      //  } catch (e: Exception) {
            // игнорируем ошибки при добавлении
      //  }
  //  }
//}
/*
fun getTestStudents(): List<Student> {
    return listOf(
        Student(1, "Анна", "Иванова", 28, "Сегодня", true),
        Student(2, "Максим", "Петров", 32, "Вчера", false),
        Student(3, "София", "Сидорова", null, "2 дня назад", true),
        Student(4, "Дмитрий", "Кузнецов", 19, "Неделю назад", false),
        Student(5, "Елена", "Смирнова", 35, "Сегодня", false)
    )
}*/*/
// ==================== ЭКРАН ПЕРСОНАЛЬНЫХ РЕКОМЕНДАЦИЙ ====================

@Composable
fun PersonalAdviceScreen(
    userName: String,
    userScore: Int,
    onStartChat: () -> Unit,
    onBackToMain: () -> Unit,
    onViewHistory: () -> Unit,
    onRelaxation: () -> Unit // ← ДОБАВЬТЕ ЭТОТ ПАРАМЕТР
) {
    val showWarning = userScore <= 15 // Предупреждение для низких баллов

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .background(Color(0xFFF5F7FF)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // КАРТОЧКА С ДАННЫМИ ПОЛЬЗОВАТЕЛЯ (ПО ЦЕНТРУ)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // АВАТАР ПОЛЬЗОВАТЕЛЯ
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFF6A5AE0), CircleShape),
                    contentAlignment = Alignment.Center
                ){
                    Text(
                        // ИСПРАВЛЕННАЯ СТРОКА:
                        text = if (userName.isNotBlank()) {
                            userName.split(" ")
                                .filter { it.isNotEmpty() } // убираем пустые строки
                                .mapNotNull { it.firstOrNull() } // безопасное получение первой буквы
                                .joinToString("")
                        } else {
                            "Г" // или "У" для "Ученик"
                        },
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // ИМЯ И ФАМИЛИЯ ПО ЦЕНТРУ
                Text(
                    text = userName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color(0xFF6A5AE0),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // РЕЗУЛЬТАТ ТЕСТА
                Text(
                    text = "Результат теста: $userScore/40 баллов",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // СТАТУС РЕЗУЛЬТАТА
                val (statusText, statusColor) = when {
                    userScore <= 15 -> "Требуется внимание" to Color(0xFFE53935)
                    userScore <= 25 -> "Стабильное состояние" to Color(0xFFFB8C00)
                    else -> "Отличный результат" to Color(0xFF43A047)
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ПРЕДУПРЕЖДЕНИЕ ДЛЯ ВЫСОКОГО РИСКА
        if (showWarning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚠️",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Text(
                        text = "Рекомендуем обратиться к школьному психологу для консультации",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFE65100)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onStartChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A5AE0))
            ) {
                Text("💬 Начать общение с психологом")
            }

            Button(
                onClick = onViewHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("📊 Моя история тестов")
            }

            // ДОБАВЛЯЕМ ЭТУ КНОПКУ:
            Button(
                onClick = onRelaxation, // ← ИСПОЛЬЗУЕМ НОВЫЙ ПАРАМЕТР
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
            ) {
                Text("🧘‍♂️ Техники релаксации")
            }

            Button(
                onClick = onBackToMain,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)
            ) {
                Text("Вернуться на главную", color = Color.Black)
            }
        }

        }

        // ДОПОЛНИТЕЛЬНАЯ ИНФОРМАЦИЯ
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Ваши данные сохранены анонимно (Ага-ага",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray.copy(alpha = 0.6f)
        )
}
// ФУНКЦИЯ ДЛЯ ОПРЕДЕЛЕНИЯ РЕКОМЕНДАЦИЙ
fun getAdviceBasedOnScore(score: Int): String {
    return when {
        score <= 15 -> "На основе ваших результатов рекомендуется срочно обратиться к профессиональному психологу для консультации. Не откладывайте заботу о своем психическом здоровье."
        score <= 25 -> "Ваши результаты указывают на некоторые эмоциональные трудности. Рекомендуется регулярно практиковать техники релаксации и рассмотреть возможность консультации со специалистом."
        score <= 35 -> "Ваше эмоциональное состояние в целом стабильно. Продолжайте практиковать здоровые привычки и саморефлексию для поддержания баланса."
        else -> "Отличные результаты! Вы демонстрируете высокий уровень эмоционального благополучия. Продолжайте в том же духе и делитесь своими стратегиями с другими."
    }
}
// Добавим эту функцию в основной код
@Composable
fun StudentChatListScreen(
    onBackToMain: () -> Unit,
    onOpenChat: (Long, String) -> Unit,
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current
    val application = (context.applicationContext as PsyHelperApplication)
    val userRepository = application.userRepository
    val syncManager = application.syncManager

    // ЗАГРУЖАЕМ РЕАЛЬНЫХ УЧИТЕЛЕЙ ИЗ БАЗЫ
    val teachers = userRepository.getTeachers().collectAsState(initial = emptyList())

    // State для обновления UI
    var refreshTrigger by remember { mutableStateOf(0) }

    // COROUTINE SCOPE - ОДИН РАЗ В НАЧАЛЕ!
    val scope = rememberCoroutineScope()

    // ПРЕОБРАЗУЕМ УЧИТЕЛЕЙ В ЧАТЫ
    val chats = remember(teachers.value, refreshTrigger) {
        teachers.value.map { teacher ->
            Chat(
                chatId = "teacher_${teacher.id}",
                studentId = teacher.id,
                studentName = "${teacher.firstName} ${teacher.lastName}",
                lastMessage = "Школьный психолог",
                lastMessageTime = System.currentTimeMillis(),
                unreadCount = 0
            )
        }
    }

    // REAL-TIME СИНХРОНИЗАЦИЯ ДЛЯ УЧЕНИКА
    // Используем тестового ученика
    val studentId = remember { 1L } // ID тестового ученика "test.user"

    LaunchedEffect(Unit) {
        println("🎯 [StudentList] ID ученика: $studentId")

        // Запускаем периодическую синхронизацию
        while (true) {
            delay(20000) // Каждые 20 секунд
            if (syncManager.isOnline()) {
                println("🔄 [StudentList] Фоновая синхронизация")
                syncManager.syncMessagesForUser(studentId)
                refreshTrigger++ // Обновляем UI
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FF))
    ) {
        // ШАПКА
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, "Меню", tint = Color(0xFF6A5AE0))
            }

            Text(
                text = "Выберите учителя (${chats.size})",
                style = MaterialTheme.typography.headlineSmall,
                color = Color(0xFF6A5AE0)
            )

            Row {
                // КНОПКА СИНХРОНИЗАЦИИ
                IconButton(
                    onClick = {
                        scope.launch {
                            println("🔄 [StudentList] Ручная синхронизация")
                            syncManager.syncMessagesForUser(studentId)
                            refreshTrigger++ // Обновляем UI
                        }
                    }
                ) {
                    // ИСПРАВЛЕНО: Используем Refresh иконку
                    Icon(Icons.Default.Refresh, "Синхронизировать", tint = Color(0xFF6A5AE0))
                }

                IconButton(onClick = onBackToMain) {
                    Icon(Icons.Default.ArrowBack, "Назад", tint = Color(0xFF6A5AE0))
                }
            }
        }

        // ЕСЛИ НЕТ УЧИТЕЛЕЙ
        if (chats.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Пока нет учителей",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray
                )
                Text(
                    text = "Учителя появятся здесь после регистрации",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // КНОПКА СИНХРОНИЗАЦИИ
                Button(
                    onClick = {
                        scope.launch {
                            println("☁️ Синхронизируем пользователей...")
                            syncManager.syncAllData()
                            refreshTrigger++
                        }
                    },
                    modifier = Modifier.padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6A5AE0)
                    )
                ) {
                    Text("🔄 Синхронизировать из облака")
                }
            }
        } else {
            // СПИСОК УЧИТЕЛЕЙ
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chats) { chat ->
                    TeacherChatListItem(
                        teacher = chat,
                        onClick = {
                            onOpenChat(chat.studentId, chat.studentName)
                        }
                    )
                }
            }
        }
    }
}

// ВСПОМОГАТЕЛЬНЫЙ МЕТОД ДЛЯ UserRepository (если нет)
// suspend fun getStudentsSync(): List<User> {
//     return getAllUsers().first().filter { it.role == "ученик" || it.role == "student" }
// }

@Composable
fun TeacherChatListItem(teacher: Chat, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // АВАТАР УЧИТЕЛЯ
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(Color(0xFF4CAF50), CircleShape), // Зеленый для учителей
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "👨‍🏫",
                    fontSize = 20.sp
                )
            }

            // ИНФОРМАЦИЯ ОБ УЧИТЕЛЕ
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = teacher.studentName, // Здесь имя учителя
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = teacher.lastMessage, // Должность/специализация
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }

            // СЧЕТЧИК НЕПРОЧИТАННЫХ
            if (teacher.unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color.Red, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = teacher.unreadCount.toString(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
@Composable
fun StudentChatScreen(
    teacherId: Long,
    teacherName: String,
    studentId: Long,
    studentName: String,
    onBack: () -> Unit,
    onMenuClick: () -> Unit,
    onRetakeTest: () -> Unit,
    onStartCall: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val application = (context.applicationContext as PsyHelperApplication)

    // ПОЛУЧАЕМ РЕПОЗИТОРИИ
    val messageRepository = application.messageRepository
    val syncManager = application.syncManager // ← ДОБАВЛЯЕМ SyncManager

    var messageText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // STATE ДЛЯ СООБЩЕНИЙ
    var conversation by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }

    // COROUTINE SCOPE
    val scope = rememberCoroutineScope()

    // ЗАГРУЗКА СООБЩЕНИЙ
    LaunchedEffect(teacherId, studentId) {
        isLoading = true
        errorMessage = null

        try {
            messageRepository.getConversation(studentId, teacherId).collect { messages ->
                conversation = messages
                isLoading = false
                println("✅ [StudentChat] Обновлено сообщений: ${messages.size}")
            }
        } catch (e: Exception) {
            errorMessage = "Ошибка загрузки: ${e.message}"
            isLoading = false
            println("❌ [StudentChat] Ошибка: ${e.message}")
        }
    }
    DisposableEffect(teacherId, studentId) {
        println("🎯 [StudentChat] Запускаем real-time (ученик $studentId ↔ учитель $teacherId)")

        // УЧЕНИК - текущий пользователь
        syncManager.startConversationRealtime(
            userId1 = studentId,
            userId2 = teacherId,
            currentUserId = studentId, // ← ВАЖНО: ученик сейчас использует приложение
            onNewMessage = { message ->
                println("💫 [StudentChat] Получено сообщение от учителя")
            }
        )

        onDispose {
            syncManager.stopConversationRealtime()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FF))
    ) {
        // ШАПКА (ОПТИМИЗИРОВАННАЯ)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // КНОПКА НАЗАД
            IconButton(onClick = onBack) {
                Text("←", fontSize = 24.sp, color = Color(0xFF6A5AE0))
            }

            // АВАТАР И ИМЯ
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50)),
                contentAlignment = Alignment.Center
            ) {
                Text("👨‍🏫", fontSize = 16.sp)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp) // Отступы по бокам
            ) {
                Text(
                    text = teacherName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6A5AE0),
                    maxLines = 1 // Чтобы не налезало
                )
                Text(
                    text = "Учитель",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            // === ВАЖНЫЕ КНОПКИ (Остались на виду) ===

            // Видео
            IconButton(onClick = { onStartCall(true) }) {
                Icon(Icons.Default.Videocam, "Видео", tint = Color(0xFF6A5AE0))
            }

            // Аудио
            IconButton(onClick = { onStartCall(false) }) {
                Icon(Icons.Default.Call, "Аудио", tint = Color(0xFF6A5AE0))
            }

            // === ВЫПАДАЮЩЕЕ МЕНЮ (Все остальное спрятано здесь) ===
            Box {
                var showMenu by remember { mutableStateOf(false) }

                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Меню", tint = Color(0xFF6A5AE0))
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("📊 Пройти тест") },
                        onClick = {
                            showMenu = false
                            onRetakeTest()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🔄 Обновить чат") },
                        onClick = {
                            showMenu = false
                            scope.launch {
                                syncManager.syncAllData()
                                messageRepository.refresh()
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("☰ Главное меню") },
                        onClick = {
                            showMenu = false
                            onMenuClick()
                        }
                    )
                }
            }
        }


        // ОШИБКА
        if (errorMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("❌", fontSize = 20.sp, color = Color.Red)
                    Text(
                        text = errorMessage!!,
                        modifier = Modifier.padding(start = 8.dp),
                        color = Color.Red,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // БАННЕР С СОВЕТОМ
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("💡", fontSize = 20.sp)
                Text(
                    text = "Можете пройти тест заново, чтобы обсудить изменения",
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1976D2)
                )
            }
        }

        // ИНДИКАТОР ЗАГРУЗКИ
        if (isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF6A5AE0))
                    Text(
                        text = "Загрузка сообщений...",
                        modifier = Modifier.padding(top = 12.dp),
                        color = Color.Gray
                    )
                }
            }
        }
        // ЕСЛИ НЕТ СООБЩЕНИЙ
        else if (conversation.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // ЭМОДЗИ ДЛЯ ЧАТА
                Text("💬", fontSize = 64.sp, color = Color.Gray)
                Text(
                    text = "Начните диалог с учителем",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = "Задайте вопрос или поделитесь мыслями",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // КНОПКА СИНХРОНИЗАЦИИ
                Button(
                    onClick = {
                        scope.launch {
                            syncManager.syncAllData()
                        }
                    },
                    modifier = Modifier.padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text("☁️ Загрузить из облака")
                }
            }
        } else {
            // СПИСОК СООБЩЕНИЙ
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(conversation.reversed()) { message ->
                    MessageBubble(
                        message = message,
                        isTeacher = message.senderId == teacherId
                    )
                }
            }
        }

        // ПОЛЕ ВВОДА С СИНХРОНИЗАЦИЕЙ
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                placeholder = { Text("Напишите сообщение...") },
                modifier = Modifier.weight(1f),
                isError = errorMessage != null,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color(0xFF6A5AE0),
                    unfocusedIndicatorColor = Color(0xFF6A5AE0).copy(alpha = 0.5f)
                )
            )

            Button(
                onClick = {
                    if (messageText.isNotBlank()) {
                        val textToSend = messageText
                        messageText = ""
                        errorMessage = null

                        scope.launch {
                            try {
                                // ОТПРАВКА С СИНХРОНИЗАЦИЕЙ
                                messageRepository.sendMessage(
                                    senderId = studentId,
                                    receiverId = teacherId,
                                    senderName = studentName,
                                    message = textToSend,
                                    syncManager = syncManager // ← ДОБАВЛЯЕМ синхронизацию!
                                )
                                println("✅ [StudentChat] Сообщение отправлено и синхронизировано")

                            } catch (e: Exception) {
                                errorMessage = "Ошибка отправки: ${e.message}"
                                messageText = textToSend
                                println("❌ [StudentChat] Ошибка отправки: ${e.message}")
                            }
                        }
                    }
                },
                enabled = messageText.isNotBlank() && !isLoading,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Отпр.")
                }
            }

            // ТЕСТОВАЯ КНОПКА (С СИНХРОНИЗАЦИЕЙ)
            Button(
                onClick = {
                    scope.launch {
                        try {
                            // Отправляем тестовое сообщение от учителя
                            messageRepository.sendMessage(
                                senderId = teacherId,
                                receiverId = studentId,
                                senderName = teacherName,
                                message = "Это тестовый ответ от учителя",
                                syncManager = syncManager // ← СИНХРОНИЗАЦИЯ!
                            )
                            println("🧪 [StudentChat] Тестовое сообщение отправлено")
                        } catch (e: Exception) {
                            errorMessage = "Тестовая ошибка: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.padding(start = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Text("Тест")
            }
        }
    }
}
@Composable
fun StudentTestHistoryScreen(
    studentName: String,
    testHistory: List<TestResult>,
    onBack: () -> Unit,
    onMenuClick: () -> Unit
) {
    // ДЛЯ ОТЛАДКИ: выведем размер истории
    LaunchedEffect(testHistory) {
        println("🎯 StudentTestHistoryScreen получил историю: ${testHistory.size} тестов")
        testHistory.forEachIndexed { index, test ->
            println("   Тест $index: ${test.score} баллов, ${test.date}")
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FF))
    ) {
        // ШАПКА
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = Color(0xFF6A5AE0))
            }

            Text(
                text = "История тестов",
                style = MaterialTheme.typography.headlineSmall,
                color = Color(0xFF6A5AE0)
            )

            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, "Меню", tint = Color(0xFF6A5AE0))
            }
        }

        if (testHistory.isEmpty()) {
            // ЕСЛИ ИСТОРИИ НЕТ
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "📊",
                    fontSize = 64.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "История тестов пуста",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Пройдите психологический тест, чтобы увидеть здесь результаты",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            // ГРАФИК ПРОГРЕССА (простая версия)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "📈 Динамика результатов",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // ПРОСТОЙ ГРАФИК ИЗ ТЕКСТА
                    testHistory.sortedBy { it.id }.forEachIndexed { index, result ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Тест ${index + 1}:")
                            Text("${result.score}/40 баллов",
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    result.score <= 15 -> Color(0xFFE53935)
                                    result.score <= 25 -> Color(0xFFFB8C00)
                                    else -> Color(0xFF43A047)
                                }
                            )
                        }
                    }
                }
            }

            // СПИСОК ТЕСТОВ
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(testHistory.sortedByDescending { it.id }) { testResult ->
                    TestHistoryItem(testResult = testResult)
                }
            }
        }
    }
}

@Composable
fun TestHistoryItem(testResult: TestResult) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Тест от ${testResult.date}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                // БАЛЛ С ЦВЕТОМ
                Text(
                    text = "${testResult.score}/40",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        testResult.score <= 15 -> Color(0xFFE53935)
                        testResult.score <= 25 -> Color(0xFFFB8C00)
                        else -> Color(0xFF43A047)
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // СТАТУС
            val statusText = when {
                testResult.score <= 15 -> "Требуется внимание"
                testResult.score <= 25 -> "Стабильное состояние"
                else -> "Отличный результат"
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    testResult.score <= 15 -> Color(0xFFE53935)
                    testResult.score <= 25 -> Color(0xFFFB8C00)
                    else -> Color(0xFF43A047)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // РЕКОМЕНДАЦИИ (сокращенные)
            Text(
                text = testResult.recommendations.take(100) + "...",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}
fun getCurrentDateTime(): String {
    val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
    return dateFormat.format(java.util.Date())
}

@Composable
fun RelaxationScreen(
    onBack: () -> Unit,
    onMenuClick: () -> Unit,
    onStartBreathing: () -> Unit,
    onStartMuscleRelaxation: () -> Unit,
    onStartMeditation: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FF))
    ) {
        // ШАПКА (оставляем)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = Color(0xFF6A5AE0))
            }

            Text(
                text = "Релаксация",
                style = MaterialTheme.typography.headlineSmall,
                color = Color(0xFF6A5AE0)
            )

            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, "Меню", tint = Color(0xFF6A5AE0))
            }
        }

        // ОСНОВНОЙ КОНТЕНТ с простыми карточками
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🧘‍♂️ Зона релаксации",
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFF6A5AE0),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Выберите технику для расслабления",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ПРОСТЫЕ КАРТОЧКИ БЕЗ ИКОНОК
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onStartBreathing()
                    },
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "🌬️ Дыхательное упражнение",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6A5AE0)
                    )
                    Text(
                        text = "4-7-8 техника дыхания для успокоения",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Text(
                        text = "⏱️ 2 минуты",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onStartMeditation() // Добавляем этот параметр
                    },
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "🎵 Медитация",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6A5AE0)
                    )
                    Text(
                        text = "Медитация осознанности с таймером",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Text(
                        text = "⏱️ 1-10 минут",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50)
                    )
                }
            }

            // ИСПРАВЛЕННАЯ КАРТОЧКА - убрал лишний вложенный Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStartMuscleRelaxation() },
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "💆 Мышечная релаксация",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6A5AE0)
                    )
                    Text(
                        text = "Прогрессивное расслабление мышц",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Text(
                        text = "⏱️ 5 минут",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}
@Composable
fun BreathingExerciseScreen(
    onBack: () -> Unit,
    onMenuClick: () -> Unit
) {
    var currentStep by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }

    // Адаптивные параметры
    val configuration = LocalConfiguration.current
    val isSmallScreen = configuration.screenHeightDp < 600
    val horizontalPadding = if (isSmallScreen) 16.dp else 24.dp
    val verticalSpacing = if (isSmallScreen) 16.dp else 24.dp
    val circleContainerSize = if (isSmallScreen) 180.dp else 250.dp

    // АНИМИРОВАННЫЙ РАЗМЕР КРУГА
    val circleSize by animateDpAsState(
        targetValue = when (currentStep) {
            0 -> if (isSmallScreen) 120.dp else 160.dp
            1 -> if (isSmallScreen) 120.dp else 160.dp
            else -> if (isSmallScreen) 80.dp else 100.dp
        },
        animationSpec = tween(
            durationMillis = when (currentStep) {
                0 -> 4000
                2 -> 8000
                else -> 1000
            }
        ),
        label = "circle_animation"
    )

    // Таймер для автоматической смены шагов
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                currentStep = 0
                kotlinx.coroutines.delay(4000)
                if (!isPlaying) break

                currentStep = 1
                kotlinx.coroutines.delay(7000)
                if (!isPlaying) break

                currentStep = 2
                kotlinx.coroutines.delay(8000)
                if (!isPlaying) break
                currentStep = 0
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FF))
    ) {
        // КОМПАКТНАЯ ШАПКА
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontalPadding, 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = Color(0xFF6A5AE0))
            }

            Text(
                text = "Дыхание",
                style = if (isSmallScreen) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.headlineSmall,
                color = Color(0xFF6A5AE0)
            )

            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Menu, "Меню", tint = Color(0xFF6A5AE0))
            }
        }

        // ОСНОВНОЙ КОНТЕНТ С ПРОКРУТКОЙ
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontalPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            // ЗАГОЛОВОК
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🌬️ Дыхание 4-7-8",
                    style = if (isSmallScreen) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.headlineMedium,
                    color = Color(0xFF6A5AE0),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Техника для успокоения",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }

            // АНИМИРОВАННЫЙ КРУГ
            Box(
                modifier = Modifier
                    .size(circleContainerSize),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(circleSize)
                        .background(
                            color = when (currentStep) {
                                0 -> Color(0xFF4CAF50)
                                1 -> Color(0xFFFF9800)
                                else -> Color(0xFF2196F3)
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (currentStep) {
                            0 -> "ВДОХ\n4 сек"
                            1 -> "ПАУЗА\n7 сек"
                            else -> "ВЫДОХ\n8 сек"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isSmallScreen) 14.sp else 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // СТАТУС И ИНСТРУКЦИЯ
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isPlaying) "▶️ Выполняется" else "⏸️ Остановлено",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isPlaying) Color(0xFF4CAF50) else Color(0xFFE53935)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when (currentStep) {
                            0 -> "Глубокий вдох через нос"
                            1 -> "Задержите дыхание"
                            else -> "Медленный выдох через рот"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // КНОПКА УПРАВЛЕНИЯ
            Button(
                onClick = {
                    isPlaying = !isPlaying
                    if (!isPlaying) {
                        currentStep = 0
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPlaying) Color(0xFFE53935) else Color(0xFF4CAF50)
                )
            ) {
                Text(
                    text = if (isPlaying) "⏸️ Стоп" else "▶️ Старт",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isSmallScreen) 14.sp else 16.sp
                )
            }

            // КОМПАКТНАЯ ИНСТРУКЦИЯ
            if (!isSmallScreen) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "📋 Методика:",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6A5AE0)
                        )
                        Text(
                            text = "• Вдох: 4 секунды\n• Пауза: 7 секунд\n• Выдох: 8 секунд\n• Повторить 4-5 раз",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MuscleRelaxationScreen(
    onBack: () -> Unit,
    onMenuClick: () -> Unit
) {
    var currentStep by remember { mutableStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableStateOf(0) }

    // Адаптивные параметры
    val configuration = LocalConfiguration.current
    val isSmallScreen = configuration.screenHeightDp < 600
    val horizontalPadding = if (isSmallScreen) 16.dp else 24.dp
    val verticalSpacing = if (isSmallScreen) 12.dp else 16.dp

    val muscleGroups = listOf(
        "Кисти" to "✊ Сожмите кулаки",
        "Бицепсы" to "💪 Напрягите бицепсы",
        "Плечи" to "⬆️ Поднимите плечи",
        "Лоб" to "😠 Нахмурьте лоб",
        "Глаза" to "😑 Зажмурьтесь",
        "Губы" to "😗 Сожмите губы",
        "Челюсть" to "🦷 Сожмите челюсть",
        "Шея" to "👆 Наклоните голову",
        "Грудь" to "📏 Сведите лопатки",
        "Пресс" to "🎯 Напрягите пресс",
        "Бедра" to "🦵 Напрягите бедра",
        "Икры" to "👣 Встаньте на носки",
        "Стопы" to "👞 Согните стопы"
    )

    // Таймер для каждого шага
    LaunchedEffect(isPlaying, currentStep) {
        if (isPlaying && currentStep < muscleGroups.size) {
            timeLeft = 5
            while (timeLeft > 0 && isPlaying) {
                kotlinx.coroutines.delay(1000)
                timeLeft--
            }
            if (!isPlaying) return@LaunchedEffect

            timeLeft = 10
            while (timeLeft > 0 && isPlaying) {
                kotlinx.coroutines.delay(1000)
                timeLeft--
            }
            if (!isPlaying) return@LaunchedEffect

            if (currentStep < muscleGroups.size - 1) {
                currentStep++
            } else {
                isPlaying = false
                currentStep = 0
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FF))
    ) {
        // КОМПАКТНАЯ ШАПКА
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontalPadding, 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = Color(0xFF6A5AE0))
            }

            Text(
                text = "Мышечная релаксация",
                style = if (isSmallScreen) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.headlineSmall,
                color = Color(0xFF6A5AE0)
            )

            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Menu, "Меню", tint = Color(0xFF6A5AE0))
            }
        }

        // ОСНОВНОЙ КОНТЕНТ С ПРОКРУТКОЙ
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontalPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            // ЗАГОЛОВОК
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "💆 Мышечная релаксация",
                    style = if (isSmallScreen) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.headlineMedium,
                    color = Color(0xFF6A5AE0),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Напрягайте и расслабляйте мышцы",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }

            // ПРОГРЕСС-БАР
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = if (muscleGroups.isNotEmpty()) (currentStep + 1) / muscleGroups.size.toFloat() else 0f,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF6A5AE0)
                )

                Text(
                    text = "${currentStep + 1}/${muscleGroups.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // ТЕКУЩАЯ ГРУППА МЫШЦ
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(6.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = muscleGroups.getOrNull(currentStep)?.first ?: "Завершено",
                        style = if (isSmallScreen) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6A5AE0),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = muscleGroups.getOrNull(currentStep)?.second
                            ?: "🎉 Упражнение завершено!",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isPlaying) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "⏱️ $timeLeft сек",
                                style = if (isSmallScreen) MaterialTheme.typography.titleLarge
                                else MaterialTheme.typography.headlineMedium,
                                color = if (timeLeft > 5) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = if (timeLeft > 5) "НАПРЯГАЙТЕ" else "РАССЛАБЛЯЙТЕ",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (timeLeft > 5) Color(0xFFE53935) else Color(0xFF4CAF50)
                            )
                        }
                    }
                }
            }

            // КНОПКИ УПРАВЛЕНИЯ
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ОСНОВНАЯ КНОПКА
                Button(
                    onClick = {
                        isPlaying = !isPlaying
                        if (!isPlaying && currentStep >= muscleGroups.size - 1) {
                            currentStep = 0
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying) Color(0xFFE53935) else Color(0xFF4CAF50)
                    )
                ) {
                    Text(
                        text = when {
                            currentStep >= muscleGroups.size - 1 -> "🔄 Заново"
                            isPlaying -> "⏸️ Пауза"
                            else -> "▶️ Начать"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isSmallScreen) 14.sp else 16.sp
                    )
                }

                // КНОПКИ НАВИГАЦИИ (только если не играет)
                if (!isPlaying) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { currentStep-- },
                            modifier = Modifier.weight(1f),
                            enabled = currentStep > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF9E9E9E)
                            )
                        ) {
                            Text("⬅️ Назад")
                        }

                        Button(
                            onClick = { currentStep++ },
                            modifier = Modifier.weight(1f),
                            enabled = currentStep < muscleGroups.size - 1,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF9E9E9E)
                            )
                        ) {
                            Text("Далее ➡️")
                        }
                    }
                }
            }

            // КОМПАКТНАЯ ИНСТРУКЦИЯ
            if (!isSmallScreen) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "📋 Методика:",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6A5AE0)
                        )
                        Text(
                            text = "1. Напрягите на 5 сек\n2. Расслабьте на 10 сек\n3. Прочувствуйте разницу",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun MeditationScreen(
    onBack: () -> Unit,
    onMenuClick: () -> Unit
) {
    var meditationTime by remember { mutableStateOf(300) }
    var timeLeft by remember { mutableStateOf(meditationTime) }
    var isPlaying by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }
    var isSoundOn by remember { mutableStateOf(true) } // По умолчанию включен

    // Адаптивные параметры
    val configuration = LocalConfiguration.current
    val isSmallScreen = configuration.screenHeightDp < 600
    val horizontalPadding = if (isSmallScreen) 16.dp else 24.dp
    val verticalSpacing = if (isSmallScreen) 16.dp else 24.dp
    val circleSize = if (isSmallScreen) 200.dp else 280.dp

    // ⭐⭐⭐ ИСПРАВЛЕННЫЙ MEDIA PLAYER КОД ⭐⭐⭐
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlayerReady by remember { mutableStateOf(false) }

    // Инициализация MediaPlayer
    LaunchedEffect(Unit) {
        try {
            val resourceId = context.resources.getIdentifier(
                "nature_forest",
                "raw",
                context.packageName
            )

            if (resourceId == 0) {
                println("❌ Файл nature_forest не найден в res/raw/")
                return@LaunchedEffect
            }

            mediaPlayer = MediaPlayer.create(context, resourceId).apply {
                isLooping = true
                setVolume(1.0f, 1.0f) // ⭐ УВЕЛИЧИЛИ ГРОМКОСТЬ НА МАКСИМУМ
                setOnPreparedListener {
                    isPlayerReady = true
                    println("🎵 MediaPlayer готов, громкость установлена на максимум") // ИСПРАВЛЕНО
                }
            }

        } catch (e: Exception) {
            println("❌ Ошибка создания MediaPlayer: ${e.message}")
        }
    }

    // ⭐⭐⭐ ИСПРАВЛЕННЫЙ ТАЙМЕР ⭐⭐⭐
    LaunchedEffect(isPlaying) {
        while (isPlaying && timeLeft > 0) {
            delay(1000)
            timeLeft--
            println("⏰ Время осталось: $timeLeft")
        }

        if (timeLeft == 0 && isPlaying) {
            isPlaying = false
            isFinished = true
            mediaPlayer?.pause() // Останавливаем звук при завершении
        }
    }

    // ⭐⭐⭐ ИСПРАВЛЕННОЕ УПРАВЛЕНИЕ ЗВУКОМ ⭐⭐⭐
    LaunchedEffect(isSoundOn, isPlayerReady) {
        if (!isPlayerReady) return@LaunchedEffect

        if (isSoundOn && isPlaying) {
            println("🎵 Включаем звук")
            if (!mediaPlayer!!.isPlaying) {
                mediaPlayer!!.start()
            }
        } else {
            println("🔇 Выключаем звук")
            if (mediaPlayer!!.isPlaying) {
                mediaPlayer!!.pause()
            }
        }
    }

    // Управление звуком при старте/паузе медитации
    LaunchedEffect(isPlaying, isPlayerReady) {
        if (!isPlayerReady) return@LaunchedEffect

        if (isPlaying && isSoundOn) {
            println("🎵 Запуск медитации со звуком")
            if (!mediaPlayer!!.isPlaying) {
                mediaPlayer!!.start()
            }
        } else if (!isPlaying) {
            println("⏸️ Пауза медитации")
            if (mediaPlayer!!.isPlaying) {
                mediaPlayer!!.pause()
            }
        }
    }

    // Очистка при выходе
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF6A5AE0))
    ) {
        // ШАПКА
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White)
            }

            Text(
                text = "Медитация",
                style = if (isSmallScreen) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.headlineSmall,
                color = Color.White
            )

            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, "Меню", tint = Color.White)
            }
        }

        // ОСНОВНОЙ КОНТЕНТ
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            // ЗАГОЛОВОК
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🧘‍♀️ Медитация",
                    style = if (isSmallScreen) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Сосредоточьтесь на дыхании",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFE0E0FF),
                    textAlign = TextAlign.Center
                )
            }

            // КРУГОВОЙ ТАЙМЕР
            Box(
                modifier = Modifier
                    .size(circleSize)
                    .background(
                        color = Color(0x40FFFFFF),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = if (meditationTime > 0) 1 - (timeLeft.toFloat() / meditationTime.toFloat()) else 0f,
                    modifier = Modifier.size(circleSize - 20.dp),
                    color = Color.White,
                    strokeWidth = 6.dp
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isFinished) {
                        Text(
                            text = "🎉 Готово!",
                            style = if (isSmallScreen) MaterialTheme.typography.titleSmall
                            else MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "${timeLeft / 60}:${String.format("%02d", timeLeft % 60)}",
                            style = if (isSmallScreen) MaterialTheme.typography.headlineSmall
                            else MaterialTheme.typography.headlineLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = when {
                            isFinished -> "Завершено"
                            isPlaying -> "Медитируйте..."
                            else -> "Готов к началу"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE0E0FF)
                    )
                }
            }

            // КНОПКА ЗВУКА С УВЕДОМЛЕНИЕМ О ГРОМКОСТИ
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isSoundOn) "🔊 Звуки природы" else "🔇 Звуки природы",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF6A5AE0)
                        )

                        Switch(
                            checked = isSoundOn,
                            onCheckedChange = { isSoundOn = it }
                        )
                    }
                    // ⭐ УВЕДОМЛЕНИЕ О ГРОМКОСТИ
                    if (isSoundOn) {
                        Text(
                            text = "💡 Громкость регулируется на устройстве",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF888888),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // ВЫБОР ВРЕМЕНИ
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "⏱️ Время:",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6A5AE0)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(1, 3, 5, 10).forEach { minutes ->
                            val seconds = minutes * 60
                            val isSelected = meditationTime == seconds

                            TextButton(
                                onClick = {
                                    meditationTime = seconds
                                    timeLeft = seconds
                                    isFinished = false
                                    isPlaying = false
                                    // Сбрасываем звук при смене времени
                                    mediaPlayer?.pause()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.textButtonColors(
                                    containerColor = if (isSelected) Color(0xFF6A5AE0) else Color.Transparent,
                                    contentColor = if (isSelected) Color.White else Color(0xFF6A5AE0)
                                )
                            ) {
                                Text("$minutes мин")
                            }
                        }
                    }
                }
            }

            // ГЛАВНАЯ КНОПКА
            Button(
                onClick = {
                    if (isFinished) {
                        // Сброс
                        timeLeft = meditationTime
                        isFinished = false
                        isPlaying = true
                    } else {
                        // Старт/пауза
                        isPlaying = !isPlaying
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isFinished -> Color(0xFF6A5AE0)
                        isPlaying -> Color(0xFFE53935)
                        else -> Color(0xFF4CAF50)
                    }
                )
            ) {
                Text(
                    text = when {
                        isFinished -> "🔄 Заново"
                        isPlaying -> "⏸️ Пауза"
                        else -> "▶️ Начать"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
@Composable
fun TestJsonScreen(
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    var loadedQuestions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loadedQuestions = QuestionLoader.loadQuestions(context)
        isLoading = false

        if (loadedQuestions.isEmpty()) {
            errorMessage = "Не удалось загрузить вопросы"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Тест загрузки JSON",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = Color(0xFF6A5AE0)
            )
            Text(
                text = "Загружаем вопросы...",
                modifier = Modifier.padding(top = 16.dp)
            )
        } else if (errorMessage != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFCDD2))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("❌ Ошибка", color = Color.Red, fontWeight = FontWeight.Bold)
                    Text(errorMessage ?: "Неизвестная ошибка")
                }
            }

            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        loadedQuestions = QuestionLoader.loadQuestions(context)
                        isLoading = false
                    }
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Попробовать снова")
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFC8E6C9))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("✅ Успешно!", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    Text("Загружено вопросов: ${loadedQuestions.size}")

                    Spacer(modifier = Modifier.height(8.dp))

                    if (loadedQuestions.size >= 3) {
                        Text("Примеры вопросов:", fontWeight = FontWeight.Medium)
                        loadedQuestions.take(3).forEachIndexed { index, question ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "${index + 1}. ${question.text}",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Категория: ${question.category}",
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6A5AE0)
                )
            ) {
                Text("Всё работает! Продолжить")
            }
        }
    }
}