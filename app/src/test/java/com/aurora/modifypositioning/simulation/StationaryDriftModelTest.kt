package com.aurora.modifypositioning.simulation

import com.aurora.modifypositioning.model.MovementMode
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StationaryDriftModelTest {
    @Test
    fun outdoorDriftStaysNearAnchor() {
        val model = StationaryDriftModel(Random(3))
        var previous: LocationSample? = null
        repeat(80) { index ->
            val now = 10_000L + index * 1_000L
            val point = model.next(
                anchorLatitude = 40.7580,
                anchorLongitude = -73.9855,
                environment = EnvironmentProfile.OUTDOOR_OPEN,
                previous = previous,
                nowMillis = now,
                elapsedRealtimeNanos = now * 1_000L,
            )
            val distance = distanceMeters(40.7580, -73.9855, point.latitude, point.longitude)
            assertTrue(distance <= 5.1)
            assertTrue(point.speedMps <= 0.45f)
            previous = sample(point, now)
        }
    }

    private fun sample(point: DriftPoint, now: Long): LocationSample {
        return LocationSample(
            latitude = point.latitude,
            longitude = point.longitude,
            altitudeMeters = null,
            accuracyMeters = 5f,
            verticalAccuracyMeters = 8f,
            speedMps = point.speedMps,
            speedAccuracyMps = 0.2f,
            bearingDegrees = point.bearingDegrees,
            bearingAccuracyDegrees = 8f,
            timestampMillis = now,
            elapsedRealtimeNanos = now * 1_000L,
            movementMode = MovementMode.FIXED,
            environment = EnvironmentProfile.OUTDOOR_OPEN,
            sourceLabel = "test",
        )
    }
}
