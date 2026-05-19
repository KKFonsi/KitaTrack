package com.example.kitatrack.data.local.model

import com.example.kitatrack.data.local.entity.SubscriptionEntity

data class SubscriptionProgress(
    val subscription: SubscriptionEntity,
    val reservePercent: Int,
    val isOverdue: Boolean,
    val isUpcoming: Boolean,
    val isFunded: Boolean,
    val statusLabel: String,
    val dueLabel: String,
    val cycleLabel: String
)
