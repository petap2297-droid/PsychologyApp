// Создайте новый файл DateUtils.kt:
package com.example.myapplication.utils

import java.text.SimpleDateFormat
import java.util.*

object  DateUtils {
    fun formatDate(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            "Н/Д"
        }
    }

    fun formatDateTime(timestamp: Long): String {
        return try {
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        } catch (e: Exception) {
            "Н/Д"
        }
    }
}

// Импортируйте в файлы:
// import com.example.myapplication.utils.DateUtils.formatDate