package com.aurora.modifypositioning

import com.aurora.modifypositioning.domain.RandomWalkEngine
import com.aurora.modifypositioning.domain.StepResult
import com.aurora.modifypositioning.model.MovementPoint
import com.aurora.modifypositioning.model.RandomWalkConfig
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomWalkEngineTest {

    @Test
    fun nextStep_returnsMovedWithinConfiguredRange() {
        val engine = RandomWalkEngine(random = Random(7))
        val center = MovementPoint(lat = 40.7580, lng = -73.9855, timestampMs = 0L)
        val current = center.copy(timestampMs = 1_000L)
        val config = RandomWalkConfig(
            radiusMeters = 300.0,
            minSpeedMps = 0.8,
            maxSpeedMps = 1.6,
            stepIntervalMs = 800L,
        )

        val result = engine.nextStep(
            center = center,
            current = current,
            previousHeadingDeg = null,
            config = config,
            nowMs = 2_000L,
        )

        assertTrue(result is StepResult.Moved)
        val moved = result as StepResult.Moved
        assertTrue(moved.speedMps in 0.8..1.6)
        assertTrue(moved.distanceFromCenterMeters in 0.0..300.0)
        assertEquals(2_000L, moved.point.timestampMs)
    }

    @Test
    fun nextStep_returnsReachedBoundaryAndClampsAtEdge() {
        val engine = RandomWalkEngine(random = Random(11))
        val center = MovementPoint(lat = 40.7580, lng = -73.9855, timestampMs = 0L)
        val current = MovementPoint(
            lat = center.lat + (29.0 / 111_320.0),
            lng = center.lng,
            timestampMs = 1_000L,
        )
        val config = RandomWalkConfig(
            radiusMeters = 30.0,
            minSpeedMps = 6.0,
            maxSpeedMps = 6.0,
            stepIntervalMs = 2_000L,
        )

        val result = engine.nextStep(
            center = center,
            current = current,
            previousHeadingDeg = 0.0,
            config = config,
            nowMs = 2_000L,
        )

        assertTrue(result is StepResult.ReachedBoundary)
        val reached = result as StepResult.ReachedBoundary
        val distance = haversineMeters(center, reached.point)

        assertEquals(0.0, reached.speedMps, 0.0)
        assertEquals(30.0, reached.distanceFromCenterMeters, 0.0001)
        assertTrue(distance in 29.5..30.5)
    }

    private fun haversineMeters(a: MovementPoint, b: MovementPoint): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)

        val sinLat = sin(dLat / 2.0)
        val sinLng = sin(dLng / 2.0)
        val h = sinLat * sinLat + cos(lat1) * cos(lat2) * sinLng * sinLng
        return 2.0 * earthRadiusMeters * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
