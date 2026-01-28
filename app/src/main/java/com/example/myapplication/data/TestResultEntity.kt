package com.example.myapplication.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "test_results")
data class TestResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "user_id")  // ← Обратите внимание: user_id, не userId
    val userId: Long,

    @ColumnInfo(name = "student_name")
    val studentName: String,

    val score: Int,
    val date: String,

    @ColumnInfo(name = "answers_json")
    val answersJson: String,

    val recommendations: String,

    @ColumnInfo(name = "category_scores")
    val categoryScores: String? = null
)