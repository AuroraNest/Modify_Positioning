package com.aurora.modifypositioning.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "saved_route_points",
    primaryKeys = ["route_id", "seq"],
)
data class SavedRoutePointEntity(
    @ColumnInfo(name = "route_id")
    val routeId: String,
    val seq: Int,
    val lat: Double,
    val lng: Double,
    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,
)

