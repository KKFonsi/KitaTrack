package com.example.kitatrack.data.local.model

data class PiggyBankAllocationPlan(
    val isValid: Boolean,
    val isPossible: Boolean,
    val remainingAmount: Long,
    val daysRemaining: Long,
    val weeksRemaining: Double,
    val requiredWeeklySaving: Long,
    val minPercent: Int,
    val maxPercent: Int,
    val defaultSelectedPercent: Int,
    val estimatedWeeklySaving: Long,
    val warning: String?
)
