package com.example.myapplication

import android.util.Log
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.Date

object TestFirebase {
    fun testConnection() {
        try {
            // Получаем экземпляр Firestore
            val db = Firebase.firestore
            Log.d("FIREBASE_TEST", "✅ Firebase Firestore успешно инициализирован")
            println("✅ [TestFirebase] Firebase подключен")

            // Простой тест
            val testData = hashMapOf(
                "app_name" to "Психологический помощник",
                "timestamp" to Date().time, // Используем Date вместо System.currentTimeMillis()
                "status" to "connected"
            )

            db.collection("test_connections")
                .document("app_test")
                .set(testData)
                .addOnSuccessListener {
                    Log.d("FIREBASE_TEST", "✅ Тестовый документ записан в Firestore")
                    println("✅ [TestFirebase] Тестовые данные записаны")
                }
                .addOnFailureListener { e ->
                    Log.e("FIREBASE_TEST", "❌ Ошибка записи: ${e.message}")
                    println("❌ [TestFirebase] Ошибка: ${e?.message ?: "неизвестная ошибка"}")
                }

        } catch (e: Exception) {
            Log.e("FIREBASE_TEST", "❌ Ошибка инициализации: ${e.message}")
            println("❌ [TestFirebase] Критическая ошибка: ${e.message}")
        }
    }

    // Тест чтения
    fun testRead() {
        try {
            val db = Firebase.firestore
            db.collection("test_connections")
                .document("app_test")
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val data = document.data
                        Log.d("FIREBASE_TEST", "✅ Документ прочитан: $data")
                        println("✅ [TestFirebase] Данные из Firestore: $data")
                    } else {
                        Log.d("FIREBASE_TEST", "⚠️ Документ не найден")
                        println("⚠️ [TestFirebase] Документ не найден")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("FIREBASE_TEST", "❌ Ошибка чтения: ${e.message}")
                    println("❌ [TestFirebase] Ошибка чтения: ${e?.message ?: "неизвестная ошибка"}")
                }
        } catch (e: Exception) {
            println("❌ [TestFirebase] Ошибка: ${e.message}")
        }
    }
}