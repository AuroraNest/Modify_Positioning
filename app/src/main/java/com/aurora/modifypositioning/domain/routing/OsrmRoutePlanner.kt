package com.aurora.modifypositioning.domain.routing

import com.aurora.modifypositioning.model.PlannedRoute
import com.aurora.modifypositioning.model.RoutePoint
import com.aurora.modifypositioning.model.RouteSource
import com.aurora.modifypositioning.model.TargetLocation
import com.aurora.modifypositioning.model.TravelMode
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class OsrmRoutePlanner(
    private val remote: OsrmRemote = HttpOsrmRemote(),
    private val routeIdProvider: () -> String = { UUID.randomUUID().toString() },
) : RoutePlanner {

    override suspend fun planPointToPoint(
        start: TargetLocation,
        end: TargetLocation,
        mode: TravelMode,
    ): PlannedRoute {
        val points = listOf(
            RoutePoint(start.latitude, start.longitude, System.currentTimeMillis()),
            RoutePoint(end.latitude, end.longitude, System.currentTimeMillis()),
        )
        return plan(points = points, mode = mode)
    }

    override suspend fun planViaWaypoints(points: List<RoutePoint>, mode: TravelMode): PlannedRoute {
        return plan(points = points, mode = mode)
    }

    override suspend fun snapHandDrawn(points: List<RoutePoint>, mode: TravelMode): PlannedRoute {
        return plan(points = points, mode = mode)
    }

    private suspend fun plan(points: List<RoutePoint>, mode: TravelMode): PlannedRoute {
        if (points.size < 2) {
            throw IllegalStateException("路线点不足，请至少选择两个点")
        }
        val profile = mode.toOsrmProfile()

        val response = runCatching {
            remote.route(profile = profile, points = points)
        }.recoverCatching {
            if (it is SocketTimeoutException || it is UnknownHostException) {
                remote.route(profile = profile, points = points)
            } else {
                throw it
            }
        }.getOrElse { error ->
            throw IllegalStateException(error.toFriendlyMessage())
        }

        if (response.code != "Ok") {
            val message = response.message.orEmpty().ifBlank { "未找到可行路线" }
            throw IllegalStateException(message)
        }
        val firstRoute = response.routes.firstOrNull() ?: throw IllegalStateException("未找到可行路线")
        val distance = firstRoute.distanceMeters
        val duration = firstRoute.durationSeconds

        val now = System.currentTimeMillis()
        val parsedPoints = firstRoute.points.map { point ->
            point.copy(ts = now)
        }

        if (parsedPoints.size < 2) {
            throw IllegalStateException("路线解析失败，请稍后重试")
        }

        return PlannedRoute(
            id = routeIdProvider(),
            points = parsedPoints,
            distanceMeters = distance,
            durationSeconds = duration,
            source = RouteSource.OSRM,
            mode = mode,
        )
    }
}

interface OsrmRemote {
    suspend fun route(profile: String, points: List<RoutePoint>): OsrmRouteResponse
}

data class OsrmRouteResponse(
    val code: String,
    val message: String? = null,
    val routes: List<OsrmRouteData> = emptyList(),
)

data class OsrmRouteData(
    val distanceMeters: Double,
    val durationSeconds: Double,
    val points: List<RoutePoint>,
)

class HttpOsrmRemote(
    private val baseUrl: String = "https://router.project-osrm.org",
) : OsrmRemote {

    override suspend fun route(profile: String, points: List<RoutePoint>): OsrmRouteResponse = withContext(Dispatchers.IO) {
        val coordinatePairs = points.joinToString(";") { point ->
            "${point.lng},${point.lat}"
        }
        val url = "$baseUrl/route/v1/$profile/$coordinatePairs?overview=full&geometries=geojson&steps=false"
        val connection = (java.net.URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "ModifyPositioning/1.0 (Android)")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("OSRM 服务异常: HTTP $code")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseResponse(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(body: String): OsrmRouteResponse {
        val root = JSONObject(body)
        val routesArray = root.optJSONArray("routes") ?: JSONArray()
        val routes = buildList {
            for (index in 0 until routesArray.length()) {
                val item = routesArray.optJSONObject(index) ?: continue
                val distance = item.optDouble("distance", 0.0)
                val duration = item.optDouble("duration", 0.0)
                val geometry = item.optJSONObject("geometry")
                val coordinates = geometry?.optJSONArray("coordinates") ?: JSONArray()
                val points = buildList {
                    for (i in 0 until coordinates.length()) {
                        val pair = coordinates.optJSONArray(i) ?: continue
                        if (pair.length() < 2) {
                            continue
                        }
                        val lng = pair.optDouble(0, Double.NaN)
                        val lat = pair.optDouble(1, Double.NaN)
                        if (!lat.isNaN() && !lng.isNaN()) {
                            add(RoutePoint(lat = lat, lng = lng, ts = 0L))
                        }
                    }
                }
                add(
                    OsrmRouteData(
                        distanceMeters = distance,
                        durationSeconds = duration,
                        points = points,
                    ),
                )
            }
        }
        return OsrmRouteResponse(
            code = root.optString("code"),
            message = root.optString("message"),
            routes = routes,
        )
    }
}

private fun TravelMode.toOsrmProfile(): String {
    return when (this) {
        TravelMode.WALK -> "foot"
        TravelMode.BIKE -> "bike"
        TravelMode.CAR -> "driving"
    }
}

private fun Throwable.toFriendlyMessage(): String {
    return when (this) {
        is SocketTimeoutException -> "路线服务超时，请稍后重试"
        is UnknownHostException -> "网络不可用，请检查网络后重试"
        is IllegalStateException -> message ?: "未找到可行路线"
        else -> message ?: "路线规划失败，请稍后重试"
    }
}
