package com.example.kitatrack.data.local.model

data class BudgetProgress(
    val budgetId: Long,
    val name: String,
    val budgetType: String,
    val limitAmount: Long,
    val originalLimitAmount: Long = limitAmount,
    val adjustedLimitAmount: Long = limitAmount,
    val usedAmount: Long,
    val remainingAmount: Long,
    val usagePercent: Int,
    val reserveImpactAmount: Long = 0,
    val debtReserveImpact: Long = 0,
    val piggyBankImpact: Long = 0,
    val subscriptionReserveImpact: Long = 0,
    val periodUsableIncome: Long = adjustedLimitAmount,
    val isNearLimit: Boolean,
    val isOverLimit: Boolean,
    val categoryName: String?,
    val periodLabel: String,
    val isActive: Boolean
)
