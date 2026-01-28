package com.example.myapplication

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(
    // ИЗМЕНЕНИЕ: userId теперь Long
    onLoginSuccess: (Long, UserData, String) -> Unit,
    onRegisterClick: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as PsyHelperApplication
    val userRepository = application.userRepository
    val scope = rememberCoroutineScope()

    // Состояния
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Логотип
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(Color(0xFF6A5AE0)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🧠",
                fontSize = 36.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Заголовок
        Text(
            text = "Психологический помощник",
            style = MaterialTheme.typography.headlineMedium,
            color = Color(0xFF6A5AE0)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Вход в систему",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Поле логина
        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                errorMessage = null
            },
            label = { Text("Логин") },
            placeholder = { Text("test.user") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = errorMessage != null
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Поле пароля
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            label = { Text("Пароль") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = errorMessage != null
        )

        // Ошибка
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Кнопка входа
        Button(
            onClick = {
                if (username.isBlank() || password.isBlank()) {
                    errorMessage = "Заполните все поля"
                    return@Button
                }

                isLoading = true
                errorMessage = null

                scope.launch {
                    try {
                        val user = userRepository.authenticate(username, password)

                        if (user != null) {
                            // ИЗМЕНЕНИЕ: передаем user.id как Long (уже Long)
                            onLoginSuccess(
                                user.id, // Это теперь Long
                                UserData(
                                    firstName = user.firstName,
                                    lastName = user.lastName,
                                    fullName = "${user.firstName} ${user.lastName}"
                                ),
                                user.role
                            )
                        } else {
                            errorMessage = "Неверный логин или пароль"
                        }
                    } catch (e: Exception) {
                        errorMessage = "Ошибка: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White
                )
            } else {
                Text("Войти")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Ссылка на регистрацию
        TextButton(
            onClick = onRegisterClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Создать новый аккаунт")
        }

        // Тестовые данные
        Spacer(modifier = Modifier.height(32.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF5F5F5)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Тестовые данные:",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "test.user / 123456",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}