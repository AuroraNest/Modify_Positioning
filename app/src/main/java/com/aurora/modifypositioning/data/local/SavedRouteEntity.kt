package com.aurora.modifypositioning.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_routes")
data class SavedRouteEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @ColumnInfo(name = "travel_mode")
    val travelMode: String,
    @ColumnInfo(name = "input_mode")
    val inputMode: String,
    @ColumnInfo(name = "snap_to_road")
    val snapToRoad: Boolean,
    @ColumnInfo(name = "distance_meters")
    val distanceMeters: Double,
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Double,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

