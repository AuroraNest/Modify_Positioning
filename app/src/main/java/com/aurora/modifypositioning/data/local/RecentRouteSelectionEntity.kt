package com.aurora.modifypositioning.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_route_selections")
data class RecentRouteSelectionEntity(
    @PrimaryKey
    @ColumnInfo(name = "route_id")
    val routeId: String,
    @ColumnInfo(name = "route_name")
    val routeName: String,
    @ColumnInfo(name = "selected_at")
    val selectedAt: Long,
)

