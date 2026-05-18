package com.example.kitatrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "piggy_banks")
data class PiggyBankEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val targetAmount: Long = 0,
    val currentAmount: Long = 0,
    val weeklyIncomePrediction: Long = 0,
    val selectedAllocationPercent: Int = 0,
    val minAllocationPercent: Int = 0,
    val maxAllocationPercent: Int = 100,
    val isGoalPossible: Boolean = false,
    val targetDate: Long? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val isActive: Boolean = true,
    val isArchived: Boolean = false,
    val notes: String? = null,
    val allowOverSaving: Boolean = false,
    val completedAt: Long? = null
)
