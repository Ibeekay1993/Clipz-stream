package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val sourceUrl: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val transcript: String,
    val createdAt: Long = System.currentTimeMillis()
)
