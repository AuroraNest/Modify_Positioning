package com.aurora.modifypositioning.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class SavedRouteWithPoints(
    @Embedded
    val route: SavedRouteEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "route_id",
    )
    val points: List<SavedRoutePointEntity>,
)

