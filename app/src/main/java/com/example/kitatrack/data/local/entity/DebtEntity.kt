package com.example.kitatrack.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val totalAmount: Long = 0,
    val remainingAmount: Long = 0,
    val dueDate: Long? = null,
    val isActive: Boolean = true
)
