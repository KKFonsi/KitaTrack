package com.example.kitatrack.data.local.model

import com.example.kitatrack.data.local.entity.DebtEntity

data class DebtProgress(
    val debt: DebtEntity,
    val progressPercent: Int,
    val reservePercent: Int,
    val isOverdue: Boolean,
    val isUpcoming: Boolean,
    val statusLabel: String,
    val dueLabel: String
)
