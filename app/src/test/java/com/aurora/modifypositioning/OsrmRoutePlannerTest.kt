package com.aurora.modifypositioning

import com.aurora.modifypositioning.domain.routing.OsrmRemote
import com.aurora.modifypositioning.domain.routing.OsrmRouteData
import com.aurora.modifypositioning.domain.routing.OsrmRoutePlanner
import com.aurora.modifypositioning.domain.routing.OsrmRouteResponse
import com.aurora.modifypositioning.model.RoutePoint
import com.aurora.modifypositioning.model.TargetLocation
import com.aurora.modifypositioning.model.TravelMode
import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OsrmRoutePlannerTest {

    @Test
    fun planner_parsesDistanceDurationAndCoordinates() = runBlocking {
        val remote = object : OsrmRemote {
            override suspend fun route(profile: String, points: List<RoutePoint>): OsrmRouteResponse {
                assertEquals("foot", profile)
                return OsrmRouteResponse(
                    code = "Ok",
                    routes = listOf(
                        OsrmRouteData(
                            distanceMeters = 1234.5,
                            durationSeconds = 678.9,
                            points = listOf(
                                RoutePoint(31.2304, 121.4737, 0L),
                                RoutePoint(31.2310, 121.4740, 0L),
                            ),
                        ),
                    ),
                )
            }
        }

        val planner = OsrmRoutePlanner(remote = remote, routeIdProvider = { "route_mock" })
        val route = planner.planPointToPoint(
            start = TargetLocation("A", 31.2304, 121.4737),
            end = TargetLocation("B", 31.2310, 121.4740),
            mode = TravelMode.WALK,
        )

        assertEquals("route_mock", route.id)
        assertEquals(1234.5, route.distanceMeters, 0.001)
        assertEquals(678.9, route.durationSeconds, 0.001)
        assertEquals(2, route.points.size)
        assertEquals(31.2304, route.points.first().lat, 0.000001)
    }

    @Test
    fun planner_returnsFriendlyMessage_onTimeout() = runBlocking {
        val remote = object : OsrmRemote {
            override suspend fun route(profile: String, points: List<RoutePoint>): OsrmRouteResponse {
                throw SocketTimeoutException("timeout")
            }
        }
        val planner = OsrmRoutePlanner(remote = remote)

        val error = runCatching {
            planner.planViaWaypoints(
                points = listOf(
                    RoutePoint(31.2304, 121.4737, 0L),
                    RoutePoint(31.2310, 121.4740, 0L),
                ),
                mode = TravelMode.CAR,
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message?.contains("超时") == true)
    }
}
