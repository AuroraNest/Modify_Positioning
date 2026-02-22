package com.aurora.modifypositioning

import com.aurora.modifypositioning.domain.MockController
import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.MovementPoint
import com.aurora.modifypositioning.model.MovementState
import com.aurora.modifypositioning.model.PlannedRoute
import com.aurora.modifypositioning.model.RoutePoint
import com.aurora.modifypositioning.model.RouteProgress
import com.aurora.modifypositioning.model.RouteSource
import com.aurora.modifypositioning.model.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementStateMachineRouteTest {

    @Test
    fun routeMovement_transitionsStartPauseReachStop() {
        val controller = MockController()
        val route = PlannedRoute(
            id = "r1",
            points = listOf(
                RoutePoint(31.2304, 121.4737, 1_000L),
                RoutePoint(31.2314, 121.4747, 2_000L),
            ),
            distanceMeters = 100.0,
            durationSeconds = 80.0,
            source = RouteSource.OSRM,
            mode = TravelMode.WALK,
        )
        val start = MovementPoint(31.2304, 121.4737, 1_000L)
        val progress = RouteProgress(10.0, 90.0, 70.0, 10.0)

        controller.onMovementModeChanged(MovementMode.POINT_TO_POINT_NAV)
        controller.onRouteSimulationStarted(route, start, progress)
        assertEquals(MovementState.Walking, controller.movementState.value)
        assertEquals(route, controller.plannedRoute.value)

        controller.onMovementPaused()
        assertEquals(MovementState.Paused, controller.movementState.value)

        controller.onMovementResumed()
        assertEquals(MovementState.Walking, controller.movementState.value)

        controller.onRouteSimulationReachedDestination(
            point = MovementPoint(31.2314, 121.4747, 2_000L),
            progress = RouteProgress(100.0, 0.0, 0.0, 100.0),
        )
        assertEquals(MovementState.ReachedDestination, controller.movementState.value)
        assertTrue(controller.routeProgress.value?.remainingMeters == 0.0)

        controller.onServiceStopped()
        assertEquals(MovementState.Idle, controller.movementState.value)
    }
}

