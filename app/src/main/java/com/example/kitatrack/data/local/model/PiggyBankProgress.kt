package com.example.kitatrack.data.local.model

data class PiggyBankProgress(
    val id: Long,
    val name: String,
    val targetAmount: Long,
    val currentAmount: Long,
    val remainingAmount: Long,
    val progressPercent: Int,
    val weeklyIncomePrediction: Long,
    val selectedAllocationPercent: Int,
    val minAllocationPercent: Int,
    val maxAllocationPercent: Int,
    val isGoalPossible: Boolean,
    val targetDate: Long?,
    val daysRemaining: Long?,
    val requiredDailySaving: Long?,
    val requiredWeeklySaving: Long?,
    val estimatedMonthlyAllocation: Long?,
    val estimatedWeeklySavingAmount: Long?,
    val isOnTrack: Boolean?,
    val statusLabel: String,
    val unresolvedMissedCount: Int,
    val unresolvedMissedAmount: Long,
    val isActive: Boolean,
    val notes: String?
)
