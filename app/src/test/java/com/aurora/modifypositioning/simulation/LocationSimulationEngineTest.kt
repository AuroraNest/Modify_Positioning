package com.aurora.modifypositioning.simulation

import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.TargetLocation
import org.junit.Assert.assertEquals
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
}
