package com.aurora.modifypositioning.domain

import com.aurora.modifypositioning.model.PlannedRoute
import com.aurora.modifypositioning.model.RoutePoint
import com.aurora.modifypositioning.model.RouteProgress
import com.aurora.modifypositioning.model.SpeedProfile
import com.aurora.modifypositioning.model.TravelMode
import kotlin.math.max
import kotlin.random.Random

class RouteSimulationEngine(
    private val random: Random = Random.Default,
) {

    fun createSession(
        route: PlannedRoute,
        mode: TravelMode,
        startTimeMs: Long,
        initialTraveledMeters: Double = 0.0,
    ): Session {
        return Session(
            route = route,
            profile = SpeedProfile.from(mode),
            random = random,
            startTimeMs = startTimeMs,
            initialTraveledMeters = initialTraveledMeters,
        )
    }

    class Session internal constructor(
        private val route: PlannedRoute,
        private val profile: SpeedProfile,
        private val random: Random,
        startTimeMs: Long,
        initialTraveledMeters: Double,
    ) {
        private val cumulativeDistances = route.points.toCumulativeDistances()
        private val totalDistance = cumulativeDistances.lastOrNull() ?: 0.0
        private var lastTickMs = startTimeMs
        private var traveledMeters = initialTraveledMeters.coerceIn(0.0, totalDistance)
        private var stopUntilMs = 0L
        private var nextStopTriggerMs = startTimeMs + random.nextInt(
            profile.minPauseIntervalSeconds,
            profile.maxPauseIntervalSeconds + 1,
        ) * 1_000L

        fun currentProgress(nowMs: Long): SimulationTick {
            val point = interpolatePoint(route.points, cumulativeDistances, traveledMeters)
            val remainingMeters = (totalDistance - traveledMeters).coerceAtLeast(0.0)
            val remainingSeconds = remainingMeters / profile.avgSpeedMps.coerceAtLeast(0.5)
            return SimulationTick(
                point = point.copy(ts = nowMs),
                speedMps = 0.0,
                bearingDegrees = null,
                progress = RouteProgress(
                    traveledMeters = traveledMeters,
                    remainingMeters = remainingMeters,
                    remainingSeconds = remainingSeconds,
                    percent = if (totalDistance <= 0.0) 100.0 else traveledMeters / totalDistance * 100.0,
                ),
                reachedDestination = traveledMeters >= totalDistance,
            )
        }

        fun advance(nowMs: Long): SimulationTick {
            val dtSeconds = ((nowMs - lastTickMs) / 1_000.0).coerceIn(0.1, 5.0)
            lastTickMs = nowMs

            val previousPoint = interpolatePoint(route.points, cumulativeDistances, traveledMeters)
            val requestedSpeed = computeSpeed(nowMs).coerceAtLeast(0.0)
            traveledMeters = (traveledMeters + requestedSpeed * dtSeconds).coerceAtMost(totalDistance)
            val reached = traveledMeters >= max(totalDistance - 0.5, 0.0)
            if (reached) {
                traveledMeters = totalDistance
            }
            val remainingMeters = (totalDistance - traveledMeters).coerceAtLeast(0.0)
            val point = interpolatePoint(route.points, cumulativeDistances, traveledMeters)
            val movedMeters = haversineMeters(previousPoint, point)
            val speed = if (reached) 0.0 else requestedSpeed
            val bearing = if (speed > 0.0 && movedMeters > 0.001) {
                bearingDegrees(previousPoint, point)
            } else {
                null
            }
            val remainingSeconds = if (speed <= 0.0) {
                remainingMeters / profile.avgSpeedMps.coerceAtLeast(0.5)
            } else {
                remainingMeters / speed
            }

            return SimulationTick(
                point = point.copy(ts = nowMs),
                speedMps = speed,
                bearingDegrees = bearing,
                progress = RouteProgress(
                    traveledMeters = traveledMeters,
                    remainingMeters = remainingMeters,
                    remainingSeconds = remainingSeconds,
                    percent = if (totalDistance <= 0.0) 100.0 else traveledMeters / totalDistance * 100.0,
                ),
                reachedDestination = reached,
            )
        }

        private fun computeSpeed(nowMs: Long): Double {
            if (nowMs < stopUntilMs) {
                return 0.0
            }
            if (nowMs >= nextStopTriggerMs) {
                val pauseSeconds = random.nextInt(
                    profile.minPauseDurationSeconds,
                    profile.maxPauseDurationSeconds + 1,
                )
                stopUntilMs = nowMs + pauseSeconds * 1_000L
                nextStopTriggerMs = stopUntilMs + random.nextInt(
                    profile.minPauseIntervalSeconds,
                    profile.maxPauseIntervalSeconds + 1,
                ) * 1_000L
                return 0.0
            }

            val base = random.nextDouble(profile.minSpeedMps, profile.maxSpeedMps)
            val jitter = random.nextDouble(-0.25, 0.25)
            return (base + jitter).coerceIn(profile.minSpeedMps, profile.maxSpeedMps)
        }
    }
}

data class SimulationTick(
    val point: RoutePoint,
    val speedMps: Double,
    val bearingDegrees: Double?,
    val progress: RouteProgress,
    val reachedDestination: Boolean,
)

private fun List<RoutePoint>.toCumulativeDistances(): List<Double> {
    if (isEmpty()) {
        return emptyList()
    }
    val cumulative = MutableList(size) { 0.0 }
    var total = 0.0
    for (index in 1 until size) {
        total += haversineMeters(this[index - 1], this[index])
        cumulative[index] = total
    }
    return cumulative
}

private fun interpolatePoint(
    points: List<RoutePoint>,
    cumulativeDistances: List<Double>,
    distance: Double,
): RoutePoint {
    if (points.isEmpty()) {
        return RoutePoint(0.0, 0.0, 0L)
    }
    if (points.size == 1) {
        return points.first()
    }
    if (distance <= 0.0) {
        return points.first()
    }

    val total = cumulativeDistances.lastOrNull() ?: 0.0
    if (distance >= total) {
        return points.last()
    }

    var segmentIndex = 1
    while (segmentIndex < cumulativeDistances.size && cumulativeDistances[segmentIndex] < distance) {
        segmentIndex += 1
    }
    val segmentStartDistance = cumulativeDistances[segmentIndex - 1]
    val segmentEndDistance = cumulativeDistances[segmentIndex]
    val segmentLength = (segmentEndDistance - segmentStartDistance).coerceAtLeast(0.0001)
    val ratio = ((distance - segmentStartDistance) / segmentLength).coerceIn(0.0, 1.0)
    val start = points[segmentIndex - 1]
    val end = points[segmentIndex]
    return RoutePoint(
        lat = start.lat + (end.lat - start.lat) * ratio,
        lng = start.lng + (end.lng - start.lng) * ratio,
        ts = end.ts,
    )
}

private fun bearingDegrees(from: RoutePoint, to: RoutePoint): Double {
    val fromLatitude = Math.toRadians(from.lat)
    val toLatitude = Math.toRadians(to.lat)
    val longitudeDelta = Math.toRadians(to.lng - from.lng)
    val y = kotlin.math.sin(longitudeDelta) * kotlin.math.cos(toLatitude)
    val x = kotlin.math.cos(fromLatitude) * kotlin.math.sin(toLatitude) -
        kotlin.math.sin(fromLatitude) * kotlin.math.cos(toLatitude) * kotlin.math.cos(longitudeDelta)
    return ((Math.toDegrees(kotlin.math.atan2(y, x)) % 360.0) + 360.0) % 360.0
}
