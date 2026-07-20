package com.aurora.modifypositioning.simulation

import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.TargetLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSimulationEngineTest {
    @Test
    fun samplesShareMonotonicClockAndSelectedMode() {
        var wall = 1_000L
        var nanos = 10_000L
        val engine = LocationSimulationEngine(
            initialTarget = TargetLocation("target", 34.0, -118.0),
            initialMovementMode = MovementMode.FIXED,
            clock = LocationSampleClock(
                wallTimeProvider = { wall.also { wall -= 1L } },
                elapsedRealtimeNanosProvider = { nanos.also { nanos -= 1L } },
            ),
        )

        val first = engine.nextSample()
        val second = engine.nextSample()

        assertEquals(MovementMode.FIXED, first.movementMode)
        assertTrue(second.timestampMillis > first.timestampMillis)
        assertTrue(second.elapsedRealtimeNanos > first.elapsedRealtimeNanos)
    }

    @Test
    fun movingSample_usesTargetSpeedAndBearing_withoutStationaryCap() {
        var wall = 1_000L
        var nanos = 10_000L
        val engine = LocationSimulationEngine(
            initialTarget = TargetLocation("start", 34.0, -118.0),
            initialMovementMode = MovementMode.RANDOM_WALK,
            clock = LocationSampleClock(
                wallTimeProvider = { wall++ },
                elapsedRealtimeNanosProvider = { nanos++ },
            ),
        )
        val target = TargetLocation("moving", 34.001, -117.999)

        engine.updateTarget(
            target = target,
            movementMode = MovementMode.RANDOM_WALK,
            speedMps = 12.5,
            bearingDegrees = 91.0,
        )
        val moving = engine.nextSample()
        engine.stopMotion()
        val stopped = engine.nextSample()

        assertEquals(target.latitude, moving.latitude, 0.0)
        assertEquals(target.longitude, moving.longitude, 0.0)
        assertEquals(12.5f, moving.speedMps)
        assertEquals(91.0f, moving.bearingDegrees)
        assertEquals(0.0f, stopped.speedMps)
        assertNull(stopped.bearingDegrees)
    }
}
