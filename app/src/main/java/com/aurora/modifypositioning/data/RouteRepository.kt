package com.aurora.modifypositioning.data

import com.aurora.modifypositioning.data.local.LocationDao
import com.aurora.modifypositioning.data.local.RecentRouteSelectionEntity
import com.aurora.modifypositioning.data.local.SavedRouteEntity
import com.aurora.modifypositioning.data.local.SavedRoutePointEntity
import com.aurora.modifypositioning.model.PlannedRoute
import com.aurora.modifypositioning.model.RecentRouteSelection
import com.aurora.modifypositioning.model.RouteInputMode
import com.aurora.modifypositioning.model.RoutePoint
import com.aurora.modifypositioning.model.RouteSource
import com.aurora.modifypositioning.model.SavedRouteDetail
import com.aurora.modifypositioning.model.SavedRouteSummary
import com.aurora.modifypositioning.model.TravelMode
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RouteRepository(
    private val dao: LocationDao,
) {

    fun observeSavedRoutes(): Flow<List<SavedRouteSummary>> {
        return dao.observeSavedRoutes().map { list ->
            list.map { entity -> entity.toSummary() }
        }
    }

    fun observeRecentRoutes(): Flow<List<RecentRouteSelection>> {
        return dao.observeRecentRoutes().map { list ->
            list.map {
                RecentRouteSelection(
                    routeId = it.routeId,
                    routeName = it.routeName,
                    selectedAt = it.selectedAt,
                )
            }
        }
    }

    suspend fun saveRoute(
        name: String,
        route: PlannedRoute,
        inputMode: RouteInputMode,
        snapToRoad: Boolean,
    ): String {
        val routeId = route.id.ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        dao.upsertSavedRoute(
            SavedRouteEntity(
                id = routeId,
                name = name,
                travelMode = route.mode.name,
                inputMode = inputMode.name,
                snapToRoad = snapToRoad,
                distanceMeters = route.distanceMeters,
                durationSeconds = route.durationSeconds,
                createdAt = now,
                updatedAt = now,
            ),
        )
        dao.deleteSavedRoutePoints(routeId)
        dao.upsertSavedRoutePoints(
            entities = route.points.mapIndexed { index, point ->
                SavedRoutePointEntity(
                    routeId = routeId,
                    seq = index,
                    lat = point.lat,
                    lng = point.lng,
                    timestampMs = point.ts,
                )
            },
        )
        recordRecent(routeId = routeId, routeName = name)
        return routeId
    }

    suspend fun loadRoute(routeId: String): SavedRouteDetail? {
        val loaded = dao.findSavedRoute(routeId) ?: return null
        val route = loaded.route
        val points = loaded.points.sortedBy { it.seq }.map {
            RoutePoint(
                lat = it.lat,
                lng = it.lng,
                ts = it.timestampMs,
            )
        }
        return SavedRouteDetail(
            summary = route.toSummary(),
            points = points,
        )
    }

    suspend fun deleteRoute(routeId: String) {
        dao.deleteSavedRoutePoints(routeId)
        dao.deleteSavedRoute(routeId)
    }

    suspend fun recordRecent(routeId: String, routeName: String) {
        dao.upsertRecentRoute(
            RecentRouteSelectionEntity(
                routeId = routeId,
                routeName = routeName,
                selectedAt = System.currentTimeMillis(),
            ),
        )
        dao.trimRecentRoutesTo20()
    }

    private fun SavedRouteEntity.toSummary(): SavedRouteSummary {
        return SavedRouteSummary(
            id = id,
            name = name,
            travelMode = runCatching { TravelMode.valueOf(travelMode) }.getOrDefault(TravelMode.WALK),
            inputMode = runCatching { RouteInputMode.valueOf(inputMode) }.getOrDefault(RouteInputMode.HAND_DRAW),
            snapToRoad = snapToRoad,
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            updatedAt = updatedAt,
        )
    }
}

fun SavedRouteDetail.toPlannedRoute(): PlannedRoute {
    return PlannedRoute(
        id = summary.id,
        points = points,
        distanceMeters = summary.distanceMeters,
        durationSeconds = summary.durationSeconds,
        source = if (summary.snapToRoad) RouteSource.OSRM else RouteSource.MANUAL,
        mode = summary.travelMode,
    )
}

