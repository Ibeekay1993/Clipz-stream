package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WordTimestamp(
    val word: String,
    val startMs: Long,
    val endMs: Long
)

@Entity(
    tableName = "clips",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class Clip(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectId: Int,
    val title: String,
    val startSec: Int,
    val endSec: Int,
    val viralScore: Int,
    val viralReason: String,
    val aspectRatio: String = "9:16", // "9:16", "1:1", "16:9"
    val captionStyle: String = "Kinetic Yellow", // "Kinetic Yellow", "Cyber Glow", "Minimal Bold"
    val panOffset: Float = 0.5f, // center crop position
    val captionsJson: String = "", // JSON string of List<WordTimestamp>
    val isExported: Boolean = false,
    val exportedFilePath: String? = null
)
