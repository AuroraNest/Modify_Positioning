package com.aurora.modifypositioning.simulation

import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.TargetLocation
import com.aurora.modifypositioning.location.distanceMeters
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
    fun fixedSamplesKeepDriftButPublishStationaryFields() {
        var wall = 1_000L
        var nanos = 10_000L
        val target = TargetLocation("fixed", 34.0, -118.0)
        val engine = LocationSimulationEngine(
            initialTarget = target,
            initialMovementMode = MovementMode.FIXED,
            clock = LocationSampleClock(
                wallTimeProvider = { wall++ },
                elapsedRealtimeNanosProvider = { nanos++ },
            ),
        )

        val samples = List(8) { engine.nextSample() }

        assertTrue(samples.any { it.latitude != target.latitude || it.longitude != target.longitude })
        samples.forEach { sample ->
            assertEquals(0.0f, sample.speedMps)
            assertNull(sample.bearingDegrees)
            assertNull(sample.bearingAccuracyDegrees)
            assertNull(sample.altitudeMeters)
        }
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

    @Test
    fun resetAfterLargeTargetJump_dropsOldSampleState() {
        var wall = 1_000L
        var nanos = 10_000L
        val oldTarget = TargetLocation("old", 30.0, 120.0)
        val newTarget = TargetLocation("new", 40.758, -73.9855)
        val engine = LocationSimulationEngine(
            initialTarget = oldTarget,
            initialMovementMode = MovementMode.FIXED,
            clock = LocationSampleClock(
                wallTimeProvider = { wall++ },
                elapsedRealtimeNanosProvider = { nanos++ },
            ),
        )
        repeat(5) { engine.nextSample() }

        engine.reset(
            target = newTarget,
            movementMode = MovementMode.FIXED,
            environment = EnvironmentProfile.OUTDOOR_OPEN,
        )
        val resetSample = engine.nextSample()

        assertTrue(
            distanceMeters(
                resetSample.latitude,
                resetSample.longitude,
                newTarget.latitude,
                newTarget.longitude,
            ) <= 10.0,
        )
        assertTrue(
            distanceMeters(
                resetSample.latitude,
                resetSample.longitude,
                oldTarget.latitude,
                oldTarget.longitude,
            ) > 2_000.0,
        )
        assertEquals(0.0f, resetSample.speedMps)
        assertNull(resetSample.bearingDegrees)
    }
}
