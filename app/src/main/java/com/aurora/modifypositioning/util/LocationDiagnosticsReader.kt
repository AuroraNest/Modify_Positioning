package com.aurora.modifypositioning.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import com.aurora.modifypositioning.data.MapPreferencesStore
import com.aurora.modifypositioning.location.distanceMeters
import com.aurora.modifypositioning.model.DiagnosticLocation
import com.aurora.modifypositioning.model.DiagnosticSnapshot
import com.aurora.modifypositioning.model.InjectionReport
import com.aurora.modifypositioning.model.MockState
import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.MovementPoint
import com.aurora.modifypositioning.model.MovementState
import com.aurora.modifypositioning.model.PlannedRoute
import com.aurora.modifypositioning.model.RouteProgress
import com.aurora.modifypositioning.model.TravelMode
import kotlinx.coroutines.runBlocking

object LocationDiagnosticsReader {

    fun read(
        context: Context,
        state: MockState,
        lastInjection: InjectionReport?,
        movementMode: MovementMode,
        movementState: MovementState,
        movementCurrentSpeedMps: Double,
        movementDistanceFromCenterMeters: Double,
        movementTrace: List<MovementPoint>,
        plannedRoute: PlannedRoute?,
        travelMode: TravelMode?,
        routeProgress: RouteProgress?,
        mapPreferencesStore: MapPreferencesStore,
    ): DiagnosticSnapshot {
        val missingPermissions = MockEnvironmentChecker.missingPermissions(context)
        val manager = context.getSystemService(LocationManager::class.java)

        val gpsEnabled = runCatching { manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false }
            .getOrDefault(false)
        val networkEnabled = runCatching { manager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?: false }
            .getOrDefault(false)

        val gps = if (missingPermissions.isEmpty()) {
            manager?.readProvider(LocationManager.GPS_PROVIDER, lastInjection)
        } else {
            null
        }
        val network = if (missingPermissions.isEmpty()) {
            manager?.readProvider(LocationManager.NETWORK_PROVIDER, lastInjection)
        } else {
            null
        }

        val calibrationMode = runBlocking { mapPreferencesStore.getCalibrationMode() }

        return DiagnosticSnapshot(
            generatedAtMillis = System.currentTimeMillis(),
            isMockAppSelected = MockEnvironmentChecker.isMockLocationAppSelected(context),
            missingPermissions = missingPermissions,
            gpsEnabled = gpsEnabled,
            networkEnabled = networkEnabled,
            gpsLastKnown = gps,
            networkLastKnown = network,
            appState = state,
            lastInjection = lastInjection,
            movementMode = movementMode,
            movementState = movementState,
            movementCurrentSpeedMps = movementCurrentSpeedMps,
            movementDistanceFromCenterMeters = movementDistanceFromCenterMeters,
            movementLastPointTimeMillis = movementTrace.lastOrNull()?.timestampMs,
            routeMode = if (
                movementMode == MovementMode.POINT_TO_POINT_NAV ||
                movementMode == MovementMode.CUSTOM_ROUTE
            ) movementMode else null,
            travelMode = travelMode,
            routeSource = plannedRoute?.source,
            remainingDistanceMeters = routeProgress?.remainingMeters,
            remainingDurationSeconds = routeProgress?.remainingSeconds,
            routeProgressPercent = routeProgress?.percent,
            calibrationMode = calibrationMode,
            searchRequestCount = AppSessionMetrics.searchRequests,
        )
    }

    @SuppressLint("MissingPermission")
    private fun LocationManager.readProvider(
        provider: String,
        lastInjection: InjectionReport?,
    ): DiagnosticLocation? {
        return runCatching {
            val location = getLastKnownLocation(provider) ?: return null
            location.toDiagnostic(provider, lastInjection)
        }.getOrNull()
    }

    private fun Location.toDiagnostic(
        provider: String,
        lastInjection: InjectionReport?,
    ): DiagnosticLocation {
        val mock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isMock
        } else {
            @Suppress("DEPRECATION")
            isFromMockProvider
        }

        return DiagnosticLocation(
            provider = provider,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = accuracy,
            timeMillis = time,
            isMock = mock,
            distanceToLastInjectionMeters = lastInjection?.let {
                distanceMeters(
                    startLatitude = latitude,
                    startLongitude = longitude,
                    endLatitude = it.latitude,
                    endLongitude = it.longitude,
                )
            },
        )
    }
}
