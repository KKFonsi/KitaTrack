package com.example.kitatrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long = 0,
    val type: String = "",
    val categoryId: Long? = null,
    val description: String = "",
    val note: String? = null,
    val occurredAt: Long = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
