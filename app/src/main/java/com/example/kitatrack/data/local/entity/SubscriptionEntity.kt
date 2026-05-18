package com.example.kitatrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val amount: Long = 0,
    val billingCycle: String = "",
    val nextDueDate: Long? = null,
    val reserveEnabled: Boolean = false,
    val isActive: Boolean = true
)
