package com.example.kitatrack.data.local.model

data class BudgetProgress(
    val budgetId: Long,
    val name: String,
    val budgetType: String,
    val limitAmount: Long,
    val usedAmount: Long,
    val remainingAmount: Long,
    val usagePercent: Int,
    val isNearLimit: Boolean,
    val isOverLimit: Boolean,
    val categoryName: String?,
    val periodLabel: String,
    val isActive: Boolean
)
