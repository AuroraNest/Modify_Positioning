package com.aurora.modifypositioning.domain

import com.aurora.modifypositioning.model.PlannedRoute
import com.aurora.modifypositioning.model.RoutePoint
import com.aurora.modifypositioning.model.RouteSource
import com.aurora.modifypositioning.model.SpeedProfile
import com.aurora.modifypositioning.model.TravelMode
import java.util.UUID
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class CustomRouteBuilder(
    private val routeIdProvider: () -> String = { UUID.randomUUID().toString() },
) {

    fun normalize(points: List<RoutePoint>): List<RoutePoint> {
        if (points.isEmpty()) {
            return emptyList()
        }
        val filtered = mutableListOf<RoutePoint>()
        points.forEach { point ->
            val last = filtered.lastOrNull()
            if (last == null || haversineMeters(last, point) >= MIN_POINT_GAP_METERS) {
                filtered += point
            }
        }
        if (filtered.size <= 2) {
            return filtered
        }

        val smoothed = filtered.toMutableList()
        for (index in 1 until smoothed.lastIndex) {
            val prev = filtered[index - 1]
            val current = filtered[index]
            val next = filtered[index + 1]
            smoothed[index] = current.copy(
                lat = (prev.lat + current.lat + next.lat) / 3.0,
                lng = (prev.lng + current.lng + next.lng) / 3.0,
            )
        }
        return smoothed
    }

    fun buildManualRoute(points: List<RoutePoint>, mode: TravelMode): PlannedRoute {
        val normalized = normalize(points)
        if (normalized.size < 2) {
            throw IllegalStateException("手绘路线至少需要两个点")
        }
        val totalDistance = normalized.zipWithNext().sumOf { (a, b) -> haversineMeters(a, b) }
        val avgSpeed = SpeedProfile.from(mode).avgSpeedMps.coerceAtLeast(0.5)
        return PlannedRoute(
            id = routeIdProvider(),
            points = normalized,
            distanceMeters = totalDistance,
            durationSeconds = totalDistance / avgSpeed,
            source = RouteSource.MANUAL,
            mode = mode,
        )
    }

    companion object {
        private const val MIN_POINT_GAP_METERS = 2.0
    }
}

internal fun haversineMeters(a: RoutePoint, b: RoutePoint): Double {
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

