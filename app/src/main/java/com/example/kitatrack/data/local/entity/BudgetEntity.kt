package com.example.kitatrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long? = null,
    val name: String = "",
    val amountLimit: Long = 0,
    val budgetType: String = "",
    val startDate: Long? = null,
    val endDate: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
