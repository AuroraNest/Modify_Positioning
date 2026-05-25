package com.aurora.modifypositioning

import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.MovementPoint
import com.aurora.modifypositioning.model.MovementState
import com.aurora.modifypositioning.model.PlannedRoute
import com.aurora.modifypositioning.model.RoutePoint
import com.aurora.modifypositioning.model.RouteProgress
import com.aurora.modifypositioning.model.RouteSource
import com.aurora.modifypositioning.model.TargetLocation
import com.aurora.modifypositioning.model.TravelMode
import com.aurora.modifypositioning.service.MovementStartupPlanResult
import com.aurora.modifypositioning.service.buildMovementStartupPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementStartupPlanTest {

    @Test
    fun routeStartWithoutPlannedRouteFailsBeforeInjectorStart() {
        val result = buildMovementStartupPlan(
            movementMode = MovementMode.POINT_TO_POINT_NAV,
            movementState = MovementState.Idle,
            movementTrace = emptyList(),
            movementCenter = CENTER,
            currentTarget = CURRENT,
            selectedTarget = SELECTED,
            plannedRoute = null,
            routeProgress = null,
        )

        assertTrue(result is MovementStartupPlanResult.Failure)
        assertEquals("请先规划并确认路线", (result as MovementStartupPlanResult.Failure).message)
    }

    @Test
    fun routeResumeKeepsCurrentTargetUntilRouteLoopPublishesNextPoint() {
        val progress = RouteProgress(
            traveledMeters = 10.0,
            remainingMeters = 90.0,
            remainingSeconds = 60.0,
            percent = 10.0,
        )

        val result = buildMovementStartupPlan(
            movementMode = MovementMode.CUSTOM_ROUTE,
            movementState = MovementState.Paused,
            movementTrace = listOf(MovementPoint(31.0, 121.0, 1_000L)),
            movementCenter = CENTER,
            currentTarget = CURRENT,
            selectedTarget = SELECTED,
            plannedRoute = ROUTE,
            routeProgress = progress,
        )

        val plan = (result as MovementStartupPlanResult.Ready).plan
        assertTrue(plan.resumingRoute)
        assertEquals(CURRENT, plan.movementStartTarget)
        assertEquals(ROUTE, plan.plannedRoute)
    }

    private companion object {
        val CENTER = TargetLocation("center", 31.0, 121.0)
        val CURRENT = TargetLocation("current", 31.1, 121.1)
        val SELECTED = TargetLocation("selected", 31.2, 121.2)
        val ROUTE = PlannedRoute(
            id = "route",
            points = listOf(
                RoutePoint(31.0, 121.0, 1_000L),
                RoutePoint(31.1, 121.1, 2_000L),
            ),
            distanceMeters = 100.0,
            durationSeconds = 80.0,
            source = RouteSource.MANUAL,
            mode = TravelMode.WALK,
        )
    }
}
