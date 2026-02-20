package com.aurora.modifypositioning.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_selections")
data class RecentSelectionEntity(
    @PrimaryKey
    val id: String,
    val label: String,
    val lat: Double,
    val lng: Double,
    @ColumnInfo(name = "selected_at")
    val selectedAt: Long,
)
