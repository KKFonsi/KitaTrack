package com.example.kitatrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "piggy_bank_transactions")
data class PiggyBankTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val piggyBankId: Long,
    val amount: Long,
    val transactionType: String,
    val sourceTransactionId: Long? = null,
    val date: Long,
    val notes: String? = null,
    val createdAt: Long
)
