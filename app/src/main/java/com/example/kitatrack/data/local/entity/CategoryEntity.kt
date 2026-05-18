package com.example.kitatrack.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [Index(value = ["name", "type"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val type: String = "",
    val iconName: String? = null,
    val colorHex: String? = null,
    val isArchived: Boolean = false,
    val isDefault: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
