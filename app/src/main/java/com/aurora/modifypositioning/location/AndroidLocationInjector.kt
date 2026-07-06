package com.aurora.modifypositioning.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import com.aurora.modifypositioning.model.InjectionReport
import com.aurora.modifypositioning.model.TargetLocation
import com.aurora.modifypositioning.simulation.LocationSample
import com.aurora.modifypositioning.simulation.toGpsLocation
import com.aurora.modifypositioning.simulation.toNetworkLocation
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Suppress("DEPRECATION")
class AndroidLocationInjector(
    context: Context,
    updateIntervalMs: Long,
    private val onError: (String) -> Unit,
    private val onInjected: (InjectionReport) -> Unit,
) : SampledLocationInjector {

    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    @Volatile
    private var currentTarget: TargetLocation? = null
    private var providersReady = false
    private var lastProviderRebuildTimeMillis: Long? = null
    private var state = InjectorState.IDLE
    private var lastSuccessAtMillis: Long? = null
    private var lastFailureAtMillis: Long? = null
    private var lastErrorCode: InjectorErrorCode? = null
    private var lastErrorMessage: String? = null
    private var lastInjectedSample: LocationSample? = null

    override fun start(target: TargetLocation) {
        updateTarget(target)
        if (currentTarget == null) {
            return
        }
        state = InjectorState.STARTING
        rebuildProviders()
        state = if (providersReady) InjectorState.RUNNING else InjectorState.FAILED
    }

    override fun updateTarget(target: TargetLocation) {
        if (!target.isValid()) {
            onError("目标坐标无效")
            return
        }
        currentTarget = target
    }

    override fun pause() {
        if (state == InjectorState.RUNNING) {
            state = InjectorState.DEGRADED
        }
    }

    override fun stop() {
        currentTarget = null
        removeProviders()
        state = InjectorState.STOPPED
    }

    private fun rebuildProviders() {
        val manager = locationManager ?: run {
            onError("定位服务不可用")
            return
        }
        providersReady = false
        runCatching {
            addTestProvider(LocationManager.GPS_PROVIDER)
            addTestProvider(LocationManager.NETWORK_PROVIDER)
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            manager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
            providersReady = true
            lastProviderRebuildTimeMillis = System.currentTimeMillis()
            lastErrorCode = null
            lastErrorMessage = null
        }.onFailure {
            removeProviders()
            publishFailure(InjectorErrorCode.TEST_PROVIDER_ADD_FAILED, "mock provider 重建失败: ${it.message ?: "未知错误"}")
        }
    }

    @SuppressLint("MissingPermission")
    override fun inject(sample: LocationSample) {
        val manager = locationManager ?: run {
            publishFailure(InjectorErrorCode.LOCATION_SERVICE_DISABLED, "定位服务不可用")
            return
        }
        if (!providersReady || !isProviderReady(manager, LocationManager.GPS_PROVIDER) || !isProviderReady(manager, LocationManager.NETWORK_PROVIDER)) {
            rebuildProviders()
        }
        if (!providersReady) {
            publishFailure(InjectorErrorCode.PROVIDER_NOT_READY, "mock provider 未就绪或已被移除")
            return
        }

        val gpsLocation = sample.toGpsLocation()
        val networkLocation = sample.toNetworkLocation()

        runCatching {
            setProviderLocations(manager, gpsLocation, networkLocation)
            val verification = verifyLastKnownLocations(
                manager = manager,
                target = TargetLocation(sample.sourceLabel, sample.latitude, sample.longitude),
                injectionTimeMillis = sample.timestampMillis,
            )
            val recovery = verification.firstOrNull { it.shouldRecover }
            var reportVerification = verification
            if (recovery != null) {
                rebuildProviders()
                if (!providersReady) {
                    error("mock provider 重建失败")
                }
                setProviderLocations(manager, gpsLocation, networkLocation)
                reportVerification = verifyLastKnownLocations(
                    manager = manager,
                    target = TargetLocation(sample.sourceLabel, sample.latitude, sample.longitude),
                    injectionTimeMillis = sample.timestampMillis,
                )
            }
            state = InjectorState.RUNNING
            lastSuccessAtMillis = sample.timestampMillis
            lastErrorCode = null
            lastErrorMessage = null
            lastInjectedSample = sample
            onInjected(
                InjectionReport(
                    provider = LocationManager.GPS_PROVIDER,
                    latitude = gpsLocation.latitude,
                    longitude = gpsLocation.longitude,
                    accuracyMeters = gpsLocation.accuracy,
                    timeMillis = gpsLocation.time,
                    providerRebuildTimeMillis = lastProviderRebuildTimeMillis,
                    verificationMockStatus = reportVerification.toMockStatusText(),
                    verificationDistanceMeters = reportVerification.verificationDistanceMeters(recovery?.provider),
                    recoveryStatus = recovery?.recoveryReason,
                ),
            )
        }.onFailure {
            rebuildProviders()
            publishFailure(InjectorErrorCode.TEST_PROVIDER_SET_LOCATION_FAILED, "注入失败: ${it.message ?: "未知错误"}")
        }
    }

    private fun setProviderLocations(
        manager: LocationManager,
        gpsLocation: Location,
        networkLocation: Location,
    ) {
        manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, gpsLocation)
        manager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, networkLocation)
    }

    @SuppressLint("MissingPermission")
    private fun verifyLastKnownLocations(
        manager: LocationManager,
        target: TargetLocation,
        injectionTimeMillis: Long,
    ): List<LastKnownVerification> {
        val now = System.currentTimeMillis()
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).map { provider ->
            val observation = runCatching {
                manager.getLastKnownLocation(provider)?.toVerificationObservation(provider)
            }.getOrNull()
            evaluateLastKnownVerification(
                provider = provider,
                targetLatitude = target.latitude,
                targetLongitude = target.longitude,
                injectionTimeMillis = injectionTimeMillis,
                nowMillis = now,
                observation = observation,
            )
        }
    }

    private fun isProviderReady(manager: LocationManager, provider: String): Boolean {
        return runCatching {
            manager.allProviders.contains(provider) && manager.isProviderEnabled(provider)
        }.getOrDefault(false)
    }

    private fun addTestProvider(provider: String) {
        val manager = locationManager ?: return
        val profile = testProviderProfile(provider)
        runCatching { manager.removeTestProvider(provider) }
        manager.addTestProvider(
            provider,
            profile.requiresNetwork,
            profile.requiresSatellite,
            profile.requiresCell,
            false,
            true,
            true,
            true,
            profile.powerRequirement,
            profile.accuracy,
        )
    }

    private fun removeProviders() {
        val manager = locationManager ?: return
        runCatching { manager.removeTestProvider(LocationManager.GPS_PROVIDER) }
        runCatching { manager.removeTestProvider(LocationManager.NETWORK_PROVIDER) }
        providersReady = false
    }

    override fun cleanup() {
        stop()
    }

    override fun status(): InjectorStatus {
        return InjectorStatus(
            id = "android-location",
            displayName = "GPS / Network",
            state = state,
            lastSuccessAtMillis = lastSuccessAtMillis,
            lastFailureAtMillis = lastFailureAtMillis,
            lastErrorCode = lastErrorCode,
            lastErrorMessage = lastErrorMessage,
            lastInjectedSample = lastInjectedSample,
        )
    }

    private fun publishFailure(code: InjectorErrorCode, message: String) {
        state = InjectorState.FAILED
        lastFailureAtMillis = System.currentTimeMillis()
        lastErrorCode = code
        lastErrorMessage = message
        onError(message)
    }

    private fun Location.toVerificationObservation(provider: String): LastKnownObservation {
        return LastKnownObservation(
            provider = provider,
            latitude = latitude,
            longitude = longitude,
            timeMillis = time,
            isMock = isMockCompat(),
        )
    }

    private fun Location.isMockCompat(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            isMock
        } else {
            @Suppress("DEPRECATION")
            isFromMockProvider
        }
    }

    @Suppress("unused")
    private val retainedUpdateIntervalMs = updateIntervalMs
}

internal data class TestProviderProfile(
    val provider: String,
    val requiresNetwork: Boolean,
    val requiresSatellite: Boolean,
    val requiresCell: Boolean,
    val powerRequirement: Int,
    val accuracy: Int,
)

internal fun testProviderProfile(provider: String): TestProviderProfile {
    return when (provider) {
        LocationManager.GPS_PROVIDER -> TestProviderProfile(
            provider = provider,
            requiresNetwork = false,
            requiresSatellite = true,
            requiresCell = false,
            powerRequirement = Criteria.POWER_HIGH,
            accuracy = Criteria.ACCURACY_FINE,
        )

        LocationManager.NETWORK_PROVIDER -> TestProviderProfile(
            provider = provider,
            requiresNetwork = true,
            requiresSatellite = false,
            requiresCell = true,
            powerRequirement = Criteria.POWER_LOW,
            accuracy = Criteria.ACCURACY_COARSE,
        )

        else -> TestProviderProfile(
            provider = provider,
            requiresNetwork = false,
            requiresSatellite = false,
            requiresCell = false,
            powerRequirement = Criteria.POWER_LOW,
            accuracy = Criteria.ACCURACY_COARSE,
        )
    }
}

internal data class LastKnownObservation(
    val provider: String,
    val latitude: Double,
    val longitude: Double,
    val timeMillis: Long,
    val isMock: Boolean,
)

internal enum class LastKnownVerificationStatus {
    MissingCache,
    StaleCache,
    MockNearTarget,
    MockFarFromTarget,
    NonMockOverwrite,
}

internal data class LastKnownVerification(
    val provider: String,
    val status: LastKnownVerificationStatus,
    val isMock: Boolean?,
    val distanceMeters: Double?,
    val shouldRecover: Boolean,
    val recoveryReason: String?,
)

internal fun evaluateLastKnownVerification(
    provider: String,
    targetLatitude: Double,
    targetLongitude: Double,
    injectionTimeMillis: Long,
    nowMillis: Long,
    observation: LastKnownObservation?,
): LastKnownVerification {
    if (observation == null) {
        return LastKnownVerification(
            provider = provider,
            status = LastKnownVerificationStatus.MissingCache,
            isMock = null,
            distanceMeters = null,
            shouldRecover = false,
            recoveryReason = null,
        )
    }

    val distance = distanceMeters(
        startLatitude = observation.latitude,
        startLongitude = observation.longitude,
        endLatitude = targetLatitude,
        endLongitude = targetLongitude,
    )
    val isFresh = observation.timeMillis >= injectionTimeMillis - LAST_KNOWN_TIME_SLOP_MS &&
        nowMillis - observation.timeMillis <= LAST_KNOWN_FRESH_WINDOW_MS
    if (!isFresh) {
        return LastKnownVerification(
            provider = provider,
            status = LastKnownVerificationStatus.StaleCache,
            isMock = observation.isMock,
            distanceMeters = distance,
            shouldRecover = false,
            recoveryReason = null,
        )
    }

    if (!observation.isMock) {
        return LastKnownVerification(
            provider = provider,
            status = LastKnownVerificationStatus.NonMockOverwrite,
            isMock = false,
            distanceMeters = distance,
            shouldRecover = true,
            recoveryReason = "疑似真实定位覆盖: $provider mock=false",
        )
    }

    if (distance > LAST_KNOWN_DISTANCE_RECOVERY_METERS) {
        return LastKnownVerification(
            provider = provider,
            status = LastKnownVerificationStatus.MockFarFromTarget,
            isMock = true,
            distanceMeters = distance,
            shouldRecover = true,
            recoveryReason = "疑似真实定位覆盖: $provider 距目标 ${"%.1f".format(distance)}m",
        )
    }

    return LastKnownVerification(
        provider = provider,
        status = LastKnownVerificationStatus.MockNearTarget,
        isMock = true,
        distanceMeters = distance,
        shouldRecover = false,
        recoveryReason = null,
    )
}

internal fun distanceMeters(
    startLatitude: Double,
    startLongitude: Double,
    endLatitude: Double,
    endLongitude: Double,
): Double {
    val radiusMeters = 6_371_000.0
    val startLat = startLatitude * (PI / 180.0)
    val endLat = endLatitude * (PI / 180.0)
    val deltaLat = (endLatitude - startLatitude) * (PI / 180.0)
    val deltaLon = (endLongitude - startLongitude) * (PI / 180.0)
    val a = sin(deltaLat / 2.0) * sin(deltaLat / 2.0) +
        cos(startLat) * cos(endLat) * sin(deltaLon / 2.0) * sin(deltaLon / 2.0)
    val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    return radiusMeters * c
}

private fun List<LastKnownVerification>.toMockStatusText(): String {
    return joinToString(separator = ", ") { verification ->
        val mock = verification.isMock?.toString() ?: "未知"
        "${verification.provider} mock=$mock ${verification.status}"
    }
}

internal fun List<LastKnownVerification>.verificationDistanceMeters(recoveryProvider: String?): Double? {
    if (recoveryProvider != null) {
        return firstOrNull { it.provider == recoveryProvider }?.distanceMeters
    }
    return mapNotNull { it.distanceMeters }.maxOrNull()
}

private const val LAST_KNOWN_FRESH_WINDOW_MS = 5_000L
private const val LAST_KNOWN_TIME_SLOP_MS = 1_000L
private const val LAST_KNOWN_DISTANCE_RECOVERY_METERS = 75.0
