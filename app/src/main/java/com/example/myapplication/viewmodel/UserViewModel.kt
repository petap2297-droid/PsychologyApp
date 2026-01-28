package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.asLiveData
import com.example.myapplication.data.UserRepository
import kotlinx.coroutines.launch

class UserViewModel(private val userRepository: UserRepository) : ViewModel() {

    // УДАЛИТЕ проблемный метод или закомментируйте:
    /*
    fun getCurrentUser(userId: Long) =
        userRepository.getCurrentUserFlow(userId).asLiveData()
    */

    // Оставьте только рабочие методы:

    // РЕГИСТРАЦИЯ нового пользователя
    fun registerUser(username: String, role: String) {
        viewModelScope.launch {
            val userId: Long = userRepository.createUser(username, role)
            // Сохраняем ID пользователя в DataStore
            saveUserIdToPreferences(userId)
        }
    }

    // ПОЛУЧЕНИЕ всех учеников
    fun getStudents() = userRepository.getStudents().asLiveData()

    // ПОЛУЧЕНИЕ всех учителей
    fun getTeachers() = userRepository.getTeachers().asLiveData()

    // ПРОВЕРКА доступности имени пользователя
    suspend fun checkUsernameAvailability(username: String): Boolean {
        return userRepository.isUsernameAvailable(username)
    }

    private suspend fun saveUserIdToPreferences(userId: Long) {
        // Реализация сохранения
    }

    suspend fun getUserIdFromPreferences(): Long? {
        return null
    }
}