package com.example.kitatrack.data.local.model

import androidx.room.Embedded

data class TransactionWithCategory(
    @Embedded val transaction: com.example.kitatrack.data.local.entity.TransactionEntity,
    val categoryName: String?
)
