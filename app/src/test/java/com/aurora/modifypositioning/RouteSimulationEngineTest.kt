package com.aurora.modifypositioning

import com.aurora.modifypositioning.domain.RouteSimulationEngine
import com.aurora.modifypositioning.model.PlannedRoute
import com.aurora.modifypositioning.model.RoutePoint
import com.aurora.modifypositioning.model.RouteSource
import com.aurora.modifypositioning.model.TravelMode
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSimulationEngineTest {

    @Test
    fun speeds_areWithinExpectedRanges_forAllTravelModes() {
        val route = simpleRoute(distanceMeters = 600.0)
        val engine = RouteSimulationEngine(random = Random(12))
        val modes = listOf(TravelMode.WALK, TravelMode.BIKE, TravelMode.CAR)
        val bounds = mapOf(
            TravelMode.WALK to (1.0..1.8),
            TravelMode.BIKE to (3.0..6.5),
            TravelMode.CAR to (8.0..18.0),
        )

        modes.forEach { mode ->
            val session = engine.createSession(
                route = route.copy(mode = mode),
                mode = mode,
                startTimeMs = 1_000L,
            )
            val speeds = mutableListOf<Double>()
            for (index in 1..10) {
                val tick = session.advance(1_000L + index * 1_000L)
                speeds += tick.speedMps
            }
            val range = bounds.getValue(mode)
            speeds.forEach { speed ->
                assertTrue(speed == 0.0 || speed in range)
            }
        }
    }

    @Test
    fun walk_speed_hasRandomVariation_andRouteEventuallyReachesDestination() {
        val route = simpleRoute(distanceMeters = 80.0)
        val engine = RouteSimulationEngine(random = Random(7))
        val session = engine.createSession(
            route = route.copy(mode = TravelMode.WALK),
            mode = TravelMode.WALK,
            startTimeMs = 0L,
        )
        val nonZeroSpeeds = mutableSetOf<Double>()
        var reached = false

        for (index in 1..300) {
            val tick = session.advance(index * 1_000L)
            if (tick.speedMps > 0.0) {
                nonZeroSpeeds += (tick.speedMps * 100.0).toInt() / 100.0
            }
            if (tick.reachedDestination) {
                reached = true
                break
            }
        }

        assertTrue(nonZeroSpeeds.size >= 3)
        assertTrue(reached)
    }

    private fun simpleRoute(distanceMeters: Double): PlannedRoute {
        val latDelta = distanceMeters / 111_320.0
        return PlannedRoute(
            id = "route_test",
            points = listOf(
                RoutePoint(lat = 31.2304, lng = 121.4737, ts = 0L),
                RoutePoint(lat = 31.2304 + latDelta, lng = 121.4737, ts = 0L),
            ),
            distanceMeters = distanceMeters,
            durationSeconds = distanceMeters / 1.2,
            source = RouteSource.MANUAL,
            mode = TravelMode.WALK,
        )
    }
}

