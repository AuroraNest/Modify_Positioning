package com.aurora.modifypositioning.domain

import com.aurora.modifypositioning.model.MovementPoint
import com.aurora.modifypositioning.model.RandomWalkConfig
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class RandomWalkEngine(
    private val random: Random = Random.Default,
) {

    fun nextStep(
        center: MovementPoint,
        current: MovementPoint,
        previousHeadingDeg: Double?,
        config: RandomWalkConfig,
        nowMs: Long,
    ): StepResult {
        val normalizedConfig = config.normalized()
        val dtSeconds = (normalizedConfig.stepIntervalMs / 1_000.0).coerceAtLeast(0.1)
        val speed = random.nextDouble(normalizedConfig.minSpeedMps, normalizedConfig.maxSpeedMps)
        val heading = computeHeading(previousHeadingDeg)
        val distanceMeters = speed * dtSeconds
        val next = move(current, heading, distanceMeters, nowMs)
        val distanceFromCenter = haversineMeters(center, next)

        return if (distanceFromCenter > normalizedConfig.radiusMeters) {
            val boundaryPoint = placeOnBoundary(
                center = center,
                toward = next,
                radiusMeters = normalizedConfig.radiusMeters,
                timestampMs = nowMs,
            )
            StepResult.ReachedBoundary(
                point = boundaryPoint,
                headingDeg = heading,
                speedMps = 0.0,
                distanceFromCenterMeters = normalizedConfig.radiusMeters,
            )
        } else {
            StepResult.Moved(
                point = next,
                headingDeg = heading,
                speedMps = speed,
                distanceFromCenterMeters = distanceFromCenter,
            )
        }
    }

    private fun computeHeading(previousHeadingDeg: Double?): Double {
        if (previousHeadingDeg == null) {
            return random.nextDouble(0.0, 360.0)
        }
        val jitter = random.nextDouble(-18.0, 18.0)
        val next = previousHeadingDeg + jitter
        return ((next % 360.0) + 360.0) % 360.0
    }

    private fun move(
        point: MovementPoint,
        headingDeg: Double,
        distanceMeters: Double,
        timestampMs: Long,
    ): MovementPoint {
        val earthRadiusMeters = 6_371_000.0
        val heading = Math.toRadians(headingDeg)
        val lat1 = Math.toRadians(point.lat)
        val lng1 = Math.toRadians(point.lng)
        val angularDistance = distanceMeters / earthRadiusMeters

        val lat2 = asin(
            sin(lat1) * cos(angularDistance) +
                cos(lat1) * sin(angularDistance) * cos(heading),
        )
        val lng2 = lng1 + atan2(
            sin(heading) * sin(angularDistance) * cos(lat1),
            cos(angularDistance) - sin(lat1) * sin(lat2),
        )

        return MovementPoint(
            lat = Math.toDegrees(lat2),
            lng = normalizeLongitude(Math.toDegrees(lng2)),
            timestampMs = timestampMs,
        )
    }

    private fun placeOnBoundary(
        center: MovementPoint,
        toward: MovementPoint,
        radiusMeters: Double,
        timestampMs: Long,
    ): MovementPoint {
        val heading = bearingDegrees(center, toward)
        return move(
            point = center,
            headingDeg = heading,
            distanceMeters = radiusMeters,
            timestampMs = timestampMs,
        )
    }

    private fun bearingDegrees(from: MovementPoint, to: MovementPoint): Double {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val dLng = Math.toRadians(to.lng - from.lng)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        val brng = Math.toDegrees(atan2(y, x))
        return ((brng % 360.0) + 360.0) % 360.0
    }

    private fun normalizeLongitude(value: Double): Double {
        return ((value + 540.0) % 360.0) - 180.0
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

sealed class StepResult {
    data class Moved(
        val point: MovementPoint,
        val headingDeg: Double,
        val speedMps: Double,
        val distanceFromCenterMeters: Double,
    ) : StepResult()

    data class ReachedBoundary(
        val point: MovementPoint,
        val headingDeg: Double,
        val speedMps: Double,
        val distanceFromCenterMeters: Double,
    ) : StepResult()
}
