package com.aurora.modifypositioning

import com.aurora.modifypositioning.domain.CustomRouteBuilder
import com.aurora.modifypositioning.model.RoutePoint
import com.aurora.modifypositioning.model.RouteSource
import com.aurora.modifypositioning.model.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomRouteBuilderTest {

    private val builder = CustomRouteBuilder(routeIdProvider = { "custom_route" })

    @Test
    fun normalize_filtersTooClosePoints() {
        val now = System.currentTimeMillis()
        val points = listOf(
            RoutePoint(31.2304, 121.4737, now),
            RoutePoint(31.2304005, 121.4737005, now),
            RoutePoint(31.2306, 121.4739, now),
        )

        val normalized = builder.normalize(points)

        assertEquals(2, normalized.size)
    }

    @Test
    fun buildManualRoute_createsRouteWithDistanceAndDuration() {
        val now = System.currentTimeMillis()
        val points = listOf(
            RoutePoint(31.2304, 121.4737, now),
            RoutePoint(31.2314, 121.4747, now),
            RoutePoint(31.2324, 121.4757, now),
        )

        val route = builder.buildManualRoute(points, TravelMode.BIKE)

        assertEquals("custom_route", route.id)
        assertEquals(RouteSource.MANUAL, route.source)
        assertTrue(route.points.size >= 2)
        assertTrue(route.distanceMeters > 0.0)
        assertTrue(route.durationSeconds > 0.0)
    }
}

