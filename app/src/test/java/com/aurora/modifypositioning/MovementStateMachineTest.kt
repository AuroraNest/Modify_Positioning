package com.aurora.modifypositioning

import com.aurora.modifypositioning.domain.MockController
import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.MovementPoint
import com.aurora.modifypositioning.model.MovementState
import com.aurora.modifypositioning.model.TargetLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementStateMachineTest {

    @Test
    fun movementState_transitionsFollowStartPauseResumeBoundaryStop() {
        val controller = MockController()
        val center = TargetLocation("中心", 31.2304, 121.4737)
        val startPoint = MovementPoint(lat = center.latitude, lng = center.longitude, timestampMs = 1_000L)
        val nextPoint = MovementPoint(lat = 31.2305, lng = 121.4738, timestampMs = 2_000L)

        controller.onMovementModeChanged(MovementMode.RANDOM_WALK)
        controller.onMovementStarted(center, startPoint)
        assertEquals(MovementState.Walking, controller.movementState.value)
        assertEquals(1, controller.movementTrace.value.size)

        controller.onMovementProgress(nextPoint, speedMps = 1.2, distanceFromCenterMeters = 18.6)
        assertEquals(MovementState.Walking, controller.movementState.value)
        assertEquals(2, controller.movementTrace.value.size)
        assertEquals(1.2, controller.movementCurrentSpeedMps.value, 0.0001)
        assertEquals(18.6, controller.movementDistanceFromCenterMeters.value, 0.0001)

        controller.onMovementPaused()
        assertEquals(MovementState.Paused, controller.movementState.value)
        assertEquals(0.0, controller.movementCurrentSpeedMps.value, 0.0)

        controller.onMovementResumed()
        assertEquals(MovementState.Walking, controller.movementState.value)

        controller.onMovementReachedBoundary(distanceFromCenterMeters = 300.0)
        assertEquals(MovementState.ReachedBoundary, controller.movementState.value)
        assertEquals(300.0, controller.movementDistanceFromCenterMeters.value, 0.0001)

        controller.onServiceStopped()
        assertEquals(MovementState.Idle, controller.movementState.value)
        assertTrue(controller.movementTrace.value.isEmpty())
    }

    @Test
    fun movementTrace_keepsOnlyLatest300Points() {
        val controller = MockController()
        val center = TargetLocation("中心", 31.2304, 121.4737)
        controller.onMovementStarted(
            centerTarget = center,
            startPoint = MovementPoint(center.latitude, center.longitude, 0L),
        )

        repeat(320) { index ->
            controller.onMovementProgress(
                point = MovementPoint(
                    lat = center.latitude + index * 0.000001,
                    lng = center.longitude,
                    timestampMs = index.toLong(),
                ),
                speedMps = 1.0,
                distanceFromCenterMeters = index.toDouble(),
            )
        }

        assertEquals(300, controller.movementTrace.value.size)
        assertEquals(319L, controller.movementTrace.value.last().timestampMs)
    }
}
