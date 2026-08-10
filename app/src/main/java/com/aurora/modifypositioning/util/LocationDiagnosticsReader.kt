package com.aurora.modifypositioning.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import com.aurora.modifypositioning.data.MapPreferencesStore
import com.aurora.modifypositioning.domain.calibration.MainlandCoordinateCalibrator
import com.aurora.modifypositioning.fixed.FixedPointReadinessEvaluator
import com.aurora.modifypositioning.fixed.FixedPointReadinessInput
import com.aurora.modifypositioning.fixed.FixedPointSessionStore
import com.aurora.modifypositioning.location.CompositeInjectorStatus
import com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore
import com.aurora.modifypositioning.location.distanceMeters
import com.aurora.modifypositioning.model.DiagnosticInjectorStatus
import com.aurora.modifypositioning.model.DiagnosticLocation
import com.aurora.modifypositioning.model.DiagnosticSnapshot
import com.aurora.modifypositioning.model.DEFAULT_TARGET
import com.aurora.modifypositioning.model.InjectionReport
import com.aurora.modifypositioning.model.MockState
import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.MovementPoint
import com.aurora.modifypositioning.model.MovementState
import com.aurora.modifypositioning.model.PlannedRoute
import com.aurora.modifypositioning.model.RouteProgress
import com.aurora.modifypositioning.model.RestorationState
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
        injectorStatus: CompositeInjectorStatus,
        injectorWarning: String?,
        restorationState: RestorationState,
    ): DiagnosticSnapshot {
        val missingPermissions = MockEnvironmentChecker.missingLocationPermissions(context)
        val nowElapsedRealtimeMillis = SystemClock.elapsedRealtime()
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
        val selectedTarget = runBlocking { mapPreferencesStore.getTargetOrNull() } ?: DEFAULT_TARGET
        val fused = FusedLocationDiagnosticsStore.state.value
        val session = FixedPointSessionStore.snapshot()
        val rawTarget = session.rawTarget ?: selectedTarget
        val injectedTarget = session.injectedTarget ?: MainlandCoordinateCalibrator()
            .toInjectCoordinate(rawTarget.latitude, rawTarget.longitude, calibrationMode)
            .let { (latitude, longitude) ->
                rawTarget.copy(latitude = latitude, longitude = longitude)
            }
        val fixedPointReadiness = FixedPointReadinessEvaluator.evaluate(
            FixedPointReadinessInput(
                nowElapsedRealtimeMillis = nowElapsedRealtimeMillis,
                appState = state,
                movementMode = movementMode,
                rawTarget = rawTarget,
                injectedTarget = injectedTarget,
                session = session,
                isMockAppSelected = MockEnvironmentChecker.isMockLocationAppSelected(context),
                missingPermissions = missingPermissions,
                systemLocationEnabled = MockEnvironmentChecker.isSystemLocationEnabled(context),
                batteryIgnoringOptimizations = MockEnvironmentChecker.isIgnoringBatteryOptimizations(context),
                gpsEnabled = gpsEnabled,
                networkEnabled = networkEnabled,
                gpsLastKnown = gps,
                networkLastKnown = network,
                fusedAvailable = fused.available,
                fusedMockModeEnabled = fused.mockModeEnabled,
                fusedMockModePending = fused.mockModePending,
                fusedLastInjectionPending = fused.lastInjectionPending,
                fusedLastInjectionElapsedRealtimeMillis = fused.lastInjectionElapsedRealtimeMillis,
                fusedLastSuccessfulLatitude = fused.lastSuccessfulLatitude,
                fusedLastSuccessfulLongitude = fused.lastSuccessfulLongitude,
                fusedLastSuccessfulAccuracyMeters = fused.lastSuccessfulAccuracyMeters,
                injectorStatus = injectorStatus,
                lastInjection = lastInjection,
            ),
        )
        fixedPointReadiness?.let { readiness ->
            FixedPointSessionStore.applyEvaluation(
                expectedGeneration = session.generation,
                stableSinceElapsedRealtimeMillis = readiness.stableSinceElapsedRealtimeMillis,
            )
        }
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()

        return DiagnosticSnapshot(
            generatedAtMillis = System.currentTimeMillis(),
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            androidApiLevel = Build.VERSION.SDK_INT,
            appVersionName = packageInfo?.versionName ?: "unknown",
            appVersionCode = packageInfo?.longVersionCode ?: 0L,
            isMockAppSelected = MockEnvironmentChecker.isMockLocationAppSelected(context),
            missingPermissions = missingPermissions,
            gpsEnabled = gpsEnabled,
            networkEnabled = networkEnabled,
            gpsLastKnown = gps,
            networkLastKnown = network,
            fusedAvailable = fused.available,
            fusedMockModeEnabled = fused.mockModeEnabled,
            fusedMockModePending = fused.mockModePending,
            fusedLastInjectionPending = fused.lastInjectionPending,
            fusedLastInjectionTimeMillis = fused.lastInjectionTimeMillis,
            fusedLastSuccessfulLatitude = fused.lastSuccessfulLatitude,
            fusedLastSuccessfulLongitude = fused.lastSuccessfulLongitude,
            fusedLastError = fused.lastError,
            injectorOverallState = injectorStatus.overallState,
            injectorActiveCount = injectorStatus.activeCount,
            injectorFailedCount = injectorStatus.failedCount,
            injectorStatuses = injectorStatus.statuses.map { status ->
                DiagnosticInjectorStatus(
                    id = status.id,
                    displayName = status.displayName,
                    state = status.state,
                    lastSuccessAtMillis = status.lastSuccessAtMillis,
                    lastFailureAtMillis = status.lastFailureAtMillis,
                    lastErrorCode = status.lastErrorCode?.name,
                )
            },
            injectorWarning = injectorWarning,
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
            restorationState = restorationState,
            fixedPointReadiness = fixedPointReadiness,
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
            elapsedRealtimeNanos = elapsedRealtimeNanos,
        )
    }
}
