package com.example.kitatrack.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "monthly_ai_summaries",
    indices = [Index(value = ["year", "month"], unique = true)]
)
data class MonthlyAiSummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val year: Int,
    val month: Int,
    val summaryText: String,
    val generatedAt: Long,
    val inputDataHash: String? = null,
    val modelName: String? = null,
    val promptVersion: String = PROMPT_VERSION,
    val status: String = STATUS_GENERATED,
    val errorMessage: String? = null,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        const val STATUS_NOT_GENERATED = "NOT_GENERATED"
        const val STATUS_GENERATING = "GENERATING"
        const val STATUS_GENERATED = "GENERATED"
        const val STATUS_FAILED = "FAILED"
        const val PROMPT_VERSION = "kitatrack-monthly-v1"
    }
}
