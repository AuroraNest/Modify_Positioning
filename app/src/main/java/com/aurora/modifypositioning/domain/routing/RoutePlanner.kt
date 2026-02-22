package com.aurora.modifypositioning.domain.routing

import com.aurora.modifypositioning.model.PlannedRoute
import com.aurora.modifypositioning.model.RoutePoint
import com.aurora.modifypositioning.model.TargetLocation
import com.aurora.modifypositioning.model.TravelMode

interface RoutePlanner {
    suspend fun planPointToPoint(
        start: TargetLocation,
        end: TargetLocation,
        mode: TravelMode,
    ): PlannedRoute

    suspend fun planViaWaypoints(
        points: List<RoutePoint>,
        mode: TravelMode,
    ): PlannedRoute

    suspend fun snapHandDrawn(
        points: List<RoutePoint>,
        mode: TravelMode,
    ): PlannedRoute
}

