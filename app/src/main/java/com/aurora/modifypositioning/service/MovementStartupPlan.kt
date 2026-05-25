package com.aurora.modifypositioning.service

import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.MovementPoint
import com.aurora.modifypositioning.model.MovementState
import com.aurora.modifypositioning.model.PlannedRoute
import com.aurora.modifypositioning.model.RouteProgress
import com.aurora.modifypositioning.model.TargetLocation

internal sealed class MovementStartupPlanResult {
    data class Ready(val plan: MovementStartupPlan) : MovementStartupPlanResult()
    data class Failure(val message: String) : MovementStartupPlanResult()
}

internal data class MovementStartupPlan(
    val movementMode: MovementMode,
    val plannedRoute: PlannedRoute?,
    val resumingRandomWalk: Boolean,
    val resumingRoute: Boolean,
    val movementCenterTarget: TargetLocation,
    val movementStartTarget: TargetLocation,
)

internal fun buildMovementStartupPlan(
    movementMode: MovementMode,
    movementState: MovementState,
    movementTrace: List<MovementPoint>,
    movementCenter: TargetLocation,
    currentTarget: TargetLocation,
    selectedTarget: TargetLocation,
    plannedRoute: PlannedRoute?,
    routeProgress: RouteProgress?,
): MovementStartupPlanResult {
    val routeMode = movementMode == MovementMode.POINT_TO_POINT_NAV ||
        movementMode == MovementMode.CUSTOM_ROUTE
    val resumingRandomWalk = movementMode == MovementMode.RANDOM_WALK &&
        movementState == MovementState.Paused &&
        movementTrace.isNotEmpty()
    val resumingRoute = routeMode &&
        movementState == MovementState.Paused &&
        routeProgress != null &&
        plannedRoute != null

    if (routeMode && (plannedRoute == null || plannedRoute.points.size < 2)) {
        return MovementStartupPlanResult.Failure("请先规划并确认路线")
    }

    return MovementStartupPlanResult.Ready(
        MovementStartupPlan(
            movementMode = movementMode,
            plannedRoute = plannedRoute,
            resumingRandomWalk = resumingRandomWalk,
            resumingRoute = resumingRoute,
            movementCenterTarget = if (resumingRandomWalk) movementCenter else selectedTarget,
            movementStartTarget = if (resumingRandomWalk || resumingRoute) currentTarget else selectedTarget,
        ),
    )
}
