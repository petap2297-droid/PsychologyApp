package com.example.myapplication.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(
    entities = [
        User::class,
        TestResultEntity::class,
        MessageEntity::class
    ],
    version = 1, // ← НАЧНЕМ СНОВА С ВЕРСИИ 1
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun testResultDao(): TestResultDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "psy_helper_db"
                )
                    .fallbackToDestructiveMigration() // ← УДАЛИТЬ ВСЕ СТАРЫЕ ДАННЫЕ
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}