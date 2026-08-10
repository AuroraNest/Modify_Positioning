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
    private val readyProviders = linkedSetOf<String>()
    private var lastProviderRebuildTimeMillis: Long? = null
    private var lastProviderRebuildAttemptTimeMillis: Long? = null
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
        ensureProviders(bypassCooldown = true)
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

    private fun ensureProviders(bypassCooldown: Boolean = false) {
        val manager = locationManager ?: run {
            publishFailure(InjectorErrorCode.LOCATION_SERVICE_DISABLED, "定位服务不可用")
            return
        }
        readyProviders.removeAll { provider -> !isProviderReady(manager, provider) }
        val missingProviders = MOCK_LOCATION_PROVIDERS.filterNot(readyProviders::contains)
        if (missingProviders.isEmpty()) {
            state = InjectorState.RUNNING
            lastErrorCode = null
            lastErrorMessage = null
            return
        }
        val rebuildResult = rebuildProviders(manager, missingProviders, bypassCooldown)
        if (rebuildResult == null && readyProviders.size < MOCK_LOCATION_PROVIDERS.size) {
            publishProviderAvailability(
                code = InjectorErrorCode.TEST_PROVIDER_ADD_FAILED,
                failureDetail = "未就绪: ${missingProviderNames()}",
            )
        }
    }

    private fun rebuildProviders(
        manager: LocationManager,
        providers: List<String>,
        bypassCooldown: Boolean = false,
    ): ProviderAttemptResult? {
        val nowMillis = System.currentTimeMillis()
        if (!bypassCooldown && !shouldAttemptProviderRebuild(lastProviderRebuildAttemptTimeMillis, nowMillis)) {
            return null
        }
        // Cooldown is based on every attempt so a denied provider cannot be rebuilt on every injection tick.
        lastProviderRebuildAttemptTimeMillis = nowMillis
        readyProviders.removeAll(providers.toSet())
        val result = attemptLocationProviders(providers) { provider ->
            addTestProvider(provider)
            manager.setTestProviderEnabled(provider, true)
        }
        lastProviderRebuildTimeMillis = providerRebuildSuccessTimeMillis(
            previousSuccessTimeMillis = lastProviderRebuildTimeMillis,
            attemptTimeMillis = nowMillis,
            successfulProviders = result.successfulProviders,
        )
        readyProviders.addAll(result.successfulProviders)
        publishProviderAvailability(
            code = InjectorErrorCode.TEST_PROVIDER_ADD_FAILED,
            failureDetail = result.failureMessage().takeIf(String::isNotBlank),
        )
        return result
    }

    @SuppressLint("MissingPermission")
    override fun inject(sample: LocationSample) {
        val manager = locationManager ?: run {
            publishFailure(InjectorErrorCode.LOCATION_SERVICE_DISABLED, "定位服务不可用")
            return
        }
        ensureProviders()
        if (readyProviders.isEmpty()) {
            return
        }

        val gpsLocation = sample.toGpsLocation()
        val networkLocation = sample.toNetworkLocation()

        var providerResult = setProviderLocations(
            manager = manager,
            gpsLocation = gpsLocation,
            networkLocation = networkLocation,
            providers = readyProviders.toList(),
        )
        readyProviders.removeAll(providerResult.failures.keys)
        if (providerResult.successfulProviders.isEmpty()) {
            publishProviderAvailability(
                code = InjectorErrorCode.TEST_PROVIDER_SET_LOCATION_FAILED,
                failureDetail = "GPS / Network provider 均注入失败: ${providerResult.failureMessage()}",
            )
            return
        }
        val verification = verifyLastKnownLocations(
            manager = manager,
            target = TargetLocation(sample.sourceLabel, sample.latitude, sample.longitude),
            injectionTimeMillis = sample.timestampMillis,
            providers = providerResult.successfulProviders,
        )
        val recovery = verification.firstOrNull { it.shouldRecover }
        var reportVerification = verification
        if (recovery != null) {
            val rebuildResult = rebuildProviders(manager, listOf(recovery.provider))
            if (rebuildResult != null) {
                providerResult = setProviderLocations(
                    manager = manager,
                    gpsLocation = gpsLocation,
                    networkLocation = networkLocation,
                    providers = readyProviders.toList(),
                )
                readyProviders.removeAll(providerResult.failures.keys)
                if (providerResult.successfulProviders.isEmpty()) {
                    publishProviderAvailability(
                        code = InjectorErrorCode.TEST_PROVIDER_SET_LOCATION_FAILED,
                        failureDetail = "GPS / Network provider 重建后均注入失败: ${providerResult.failureMessage()}",
                    )
                    return
                }
                reportVerification = verifyLastKnownLocations(
                    manager = manager,
                    target = TargetLocation(sample.sourceLabel, sample.latitude, sample.longitude),
                    injectionTimeMillis = sample.timestampMillis,
                    providers = providerResult.successfulProviders,
                )
            }
        }
        val reportProvider = providerResult.successfulProviders.first()
        val reportLocation = if (reportProvider == LocationManager.GPS_PROVIDER) gpsLocation else networkLocation
        lastSuccessAtMillis = sample.timestampMillis
        lastInjectedSample = sample
        if (providerResult.failures.isNotEmpty()) {
            publishProviderAvailability(
                code = InjectorErrorCode.TEST_PROVIDER_SET_LOCATION_FAILED,
                failureDetail = providerResult.failureMessage(),
            )
        }
        onInjected(
            InjectionReport(
                provider = reportProvider,
                latitude = reportLocation.latitude,
                longitude = reportLocation.longitude,
                accuracyMeters = reportLocation.accuracy,
                timeMillis = reportLocation.time,
                providerRebuildTimeMillis = lastProviderRebuildTimeMillis,
                verificationMockStatus = reportVerification.toMockStatusText(),
                verificationDistanceMeters = reportVerification.verificationDistanceMeters(recovery?.provider),
                recoveryStatus = recovery?.recoveryReason,
                elapsedRealtimeNanos = sample.elapsedRealtimeNanos,
            ),
        )
    }

    private fun setProviderLocations(
        manager: LocationManager,
        gpsLocation: Location,
        networkLocation: Location,
        providers: List<String>,
    ): ProviderAttemptResult {
        return attemptLocationProviders(providers) { provider ->
            val location = if (provider == LocationManager.GPS_PROVIDER) gpsLocation else networkLocation
            manager.setTestProviderLocation(provider, location)
        }
    }

    @SuppressLint("MissingPermission")
    private fun verifyLastKnownLocations(
        manager: LocationManager,
        target: TargetLocation,
        injectionTimeMillis: Long,
        providers: List<String>,
    ): List<LastKnownVerification> {
        val now = System.currentTimeMillis()
        return providers.map { provider ->
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
        attemptLocationProviders { provider -> manager.removeTestProvider(provider) }
        readyProviders.clear()
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
        if (state == InjectorState.FAILED && lastErrorCode == code && lastErrorMessage == message) {
            return
        }
        state = InjectorState.FAILED
        lastFailureAtMillis = System.currentTimeMillis()
        lastErrorCode = code
        lastErrorMessage = message
        onError(message)
    }

    private fun publishDegraded(code: InjectorErrorCode, message: String) {
        if (state == InjectorState.DEGRADED && lastErrorCode == code && lastErrorMessage == message) {
            return
        }
        state = InjectorState.DEGRADED
        lastFailureAtMillis = System.currentTimeMillis()
        lastErrorCode = code
        lastErrorMessage = message
        onError(message)
    }

    private fun publishProviderAvailability(
        code: InjectorErrorCode,
        failureDetail: String?,
    ) {
        when (providerAvailabilityState(readyProviders)) {
            InjectorState.RUNNING -> {
                state = InjectorState.RUNNING
                lastErrorCode = null
                lastErrorMessage = null
            }

            InjectorState.DEGRADED -> publishDegraded(
                code,
                buildString {
                    append("部分 mock provider 不可用; 已保留 ${readyProviders.joinToString()}")
                    failureDetail?.let { append(": $it") }
                },
            )

            else -> publishFailure(
                code,
                buildString {
                    append("GPS / Network mock provider 均不可用")
                    failureDetail?.let { append(": $it") }
                },
            )
        }
    }

    private fun missingProviderNames(): String {
        return MOCK_LOCATION_PROVIDERS.filterNot(readyProviders::contains).joinToString()
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

internal data class ProviderAttemptResult(
    val successfulProviders: List<String>,
    val failures: Map<String, Throwable>,
) {
    fun failureMessage(): String {
        return failures.entries.joinToString { (provider, error) ->
            "$provider: ${error.message ?: "未知错误"}"
        }
    }
}

internal fun attemptLocationProviders(operation: (String) -> Unit): ProviderAttemptResult {
    return attemptLocationProviders(MOCK_LOCATION_PROVIDERS, operation)
}

internal fun attemptLocationProviders(
    providers: List<String>,
    operation: (String) -> Unit,
): ProviderAttemptResult {
    val successfulProviders = mutableListOf<String>()
    val failures = linkedMapOf<String, Throwable>()
    providers.forEach { provider ->
        runCatching { operation(provider) }
            .onSuccess { successfulProviders += provider }
            .onFailure { failures[provider] = it }
    }
    return ProviderAttemptResult(successfulProviders, failures)
}

internal fun providerAvailabilityState(readyProviders: Collection<String>): InjectorState {
    val readyCount = MOCK_LOCATION_PROVIDERS.count(readyProviders::contains)
    return when (readyCount) {
        MOCK_LOCATION_PROVIDERS.size -> InjectorState.RUNNING
        0 -> InjectorState.FAILED
        else -> InjectorState.DEGRADED
    }
}

internal fun shouldAttemptProviderRebuild(
    lastAttemptTimeMillis: Long?,
    nowMillis: Long,
): Boolean {
    return lastAttemptTimeMillis == null ||
        nowMillis < lastAttemptTimeMillis ||
        nowMillis - lastAttemptTimeMillis >= PROVIDER_REBUILD_COOLDOWN_MS
}

internal fun providerRebuildSuccessTimeMillis(
    previousSuccessTimeMillis: Long?,
    attemptTimeMillis: Long,
    successfulProviders: Collection<String>,
): Long? {
    return if (successfulProviders.isNotEmpty()) attemptTimeMillis else previousSuccessTimeMillis
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
private val MOCK_LOCATION_PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
private const val PROVIDER_REBUILD_COOLDOWN_MS = 2_000L
