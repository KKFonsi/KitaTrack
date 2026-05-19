package com.example.kitatrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debt_transactions")
data class DebtTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val amount: Long,
    val transactionType: String,
    val sourceTransactionId: Long? = null,
    val date: Long,
    val notes: String? = null,
    val createdAt: Long
)
