package com.aurora.modifypositioning.simulation

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

data class DriftPoint(
    val latitude: Double,
    val longitude: Double,
    val speedMps: Float,
    val bearingDegrees: Float?,
)

class StationaryDriftModel(
    private val random: Random = Random.Default,
) {
    fun next(
        anchorLatitude: Double,
        anchorLongitude: Double,
        environment: EnvironmentProfile,
        previous: LocationSample?,
        nowMillis: Long,
        elapsedRealtimeNanos: Long,
    ): DriftPoint {
        val radiusMeters = environment.driftRadiusMeters()
        val previousOffset = previous?.let {
            meterOffset(
                anchorLatitude = anchorLatitude,
                anchorLongitude = anchorLongitude,
                latitude = it.latitude,
                longitude = it.longitude,
            )
        } ?: (0.0 to 0.0)
        val pullX = -previousOffset.first * 0.18
        val pullY = -previousOffset.second * 0.18
        val noiseScale = (radiusMeters * 0.09).coerceIn(0.08, 1.8)
        var x = previousOffset.first + pullX + random.nextDouble(-noiseScale, noiseScale)
        var y = previousOffset.second + pullY + random.nextDouble(-noiseScale, noiseScale)
        val distanceFromAnchor = hypot(x, y)
        if (distanceFromAnchor > radiusMeters) {
            val ratio = radiusMeters / distanceFromAnchor
            x *= ratio
            y *= ratio
        }

        val point = offsetToCoordinate(anchorLatitude, anchorLongitude, x, y)
        val movedMeters = hypot(x - previousOffset.first, y - previousOffset.second)
        val dtSeconds = previous
            ?.let { ((nowMillis - it.timestampMillis) / 1_000.0).coerceAtLeast(0.2) }
            ?: 1.0
        val speed = (movedMeters / dtSeconds).coerceIn(0.0, 0.45).toFloat()
        val bearing = if (movedMeters < 0.05) {
            previous?.bearingDegrees
        } else {
            ((Math.toDegrees(atan2(x - previousOffset.first, y - previousOffset.second)) + 360.0) % 360.0).toFloat()
        }

        return DriftPoint(
            latitude = point.first,
            longitude = point.second,
            speedMps = speed,
            bearingDegrees = bearing,
        )
    }

    private fun EnvironmentProfile.driftRadiusMeters(): Double {
        return when (this) {
            EnvironmentProfile.OUTDOOR_OPEN -> 5.0
            EnvironmentProfile.URBAN_CANYON -> 18.0
            EnvironmentProfile.INDOOR_MALL -> 28.0
            EnvironmentProfile.AIRPORT -> 32.0
            EnvironmentProfile.HOTEL -> 24.0
            EnvironmentProfile.RESTAURANT -> 16.0
            EnvironmentProfile.TRANSIT_STATION -> 30.0
            EnvironmentProfile.MOVING_VEHICLE -> 6.0
        }
    }
}

internal fun distanceMeters(
    startLatitude: Double,
    startLongitude: Double,
    endLatitude: Double,
    endLongitude: Double,
): Double {
    val radiusMeters = 6_371_000.0
    val startLat = Math.toRadians(startLatitude)
    val endLat = Math.toRadians(endLatitude)
    val deltaLat = Math.toRadians(endLatitude - startLatitude)
    val deltaLon = Math.toRadians(endLongitude - startLongitude)
    val a = sin(deltaLat / 2.0) * sin(deltaLat / 2.0) +
        cos(startLat) * cos(endLat) * sin(deltaLon / 2.0) * sin(deltaLon / 2.0)
    val c = 2.0 * atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1.0 - a))
    return radiusMeters * c
}

internal fun meterOffset(
    anchorLatitude: Double,
    anchorLongitude: Double,
    latitude: Double,
    longitude: Double,
): Pair<Double, Double> {
    val latMeters = (latitude - anchorLatitude) * 111_320.0
    val lonMeters = (longitude - anchorLongitude) * 111_320.0 * cos(anchorLatitude * (PI / 180.0)).coerceAtLeast(0.2)
    return lonMeters to latMeters
}

internal fun offsetToCoordinate(
    anchorLatitude: Double,
    anchorLongitude: Double,
    eastMeters: Double,
    northMeters: Double,
): Pair<Double, Double> {
    val latitude = anchorLatitude + northMeters / 111_320.0
    val longitude = anchorLongitude + eastMeters / (111_320.0 * cos(anchorLatitude * (PI / 180.0)).coerceAtLeast(0.2))
    return latitude to longitude
}
