package com.aurora.modifypositioning.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import com.google.android.gms.location.LocationServices
import com.aurora.modifypositioning.model.TargetLocation
import com.aurora.modifypositioning.simulation.LocationSample

class FusedLocationInjector(
    context: Context,
    updateIntervalMs: Long,
    onError: (String) -> Unit,
    clientFactory: (Context) -> FusedMockLocationClient? = { appContext ->
        GoogleFusedMockLocationClient(appContext)
    },
) : SampledLocationInjector by FusedLocationInjectorCore(
    client = runCatching { clientFactory(context.applicationContext) }
        .onFailure { error ->
            onError("fused 初始化失败: ${error.message ?: "未知错误"}")
        }
        .getOrNull(),
    updateIntervalMs = updateIntervalMs,
    onError = onError,
    monotonicNowMillis = { SystemClock.elapsedRealtime() },
    mockModeRequestTimeoutMillis = 2_500L,
)

internal class FusedLocationInjectorCore(
    private val client: FusedMockLocationClient?,
    updateIntervalMs: Long,
    private val onError: (String) -> Unit,
    private val monotonicNowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val mockModeRequestTimeoutMillis: Long = 3_000L,
    private val setLocationRequestTimeoutMillis: Long = 2_000L,
) : SampledLocationInjector {
    @Volatile
    private var currentTarget: TargetLocation? = null
    @Volatile
    private var lastSuccessfulSample: LocationSample? = null
    private var mockModeEnabled = false
    private var mockModePending = false
    private var desiredMockMode = false
    private var mockModeReadyForRequest = false
    private var activeMockModeRequestId: Long? = null
    private var activeMockModeStartedAtMillis: Long? = null
    private var nextMockModeRequestId = 0L
    private var lastInjectionTimeMillis: Long? = null
    private var lastInjectionPending = false
    private var pendingSample: LocationSample? = null
    private var activeLocationRequestId: Long? = null
    private var activeLocationStartedAtMillis: Long? = null
    private var activeLocationSample: LocationSample? = null
    private var activeLocationTargetRevision: Long? = null
    private var nextLocationRequestId = 0L
    private var injectionGeneration = 0L
    private var targetRevision = 0L
    private var lastSuccessfulLatitude: Double? = null
    private var lastSuccessfulLongitude: Double? = null
    private var lastError: String? = null
    private var state = if (client == null) InjectorState.FAILED else InjectorState.IDLE
    private var lastFailureAtMillis: Long? = null
    private var lastErrorCode: InjectorErrorCode? = if (client == null) {
        InjectorErrorCode.GOOGLE_PLAY_SERVICES_UNAVAILABLE
    } else {
        null
    }

    init {
        publishStatus()
    }

    @Synchronized
    override fun start(target: TargetLocation) {
        updateTarget(target)
        if (currentTarget == null || client == null) {
            return
        }
        injectionGeneration++
        activeLocationRequestId = null
        activeLocationStartedAtMillis = null
        activeLocationSample = null
        activeLocationTargetRevision = null
        lastInjectionPending = false
        pendingSample = null
        state = InjectorState.STARTING
        setMockMode(true)
    }

    @Synchronized
    override fun updateTarget(target: TargetLocation) {
        if (!target.isValid()) {
            publishError(InjectorErrorCode.UNKNOWN, "fused 目标坐标无效")
            return
        }
        currentTarget = target
        targetRevision += 1L
        pendingSample = null
    }

    @Synchronized
    override fun pause() {
        if (state == InjectorState.RUNNING) {
            state = InjectorState.DEGRADED
        }
    }

    @Synchronized
    override fun stop() {
        injectionGeneration++
        currentTarget = null
        lastSuccessfulSample = null
        pendingSample = null
        lastInjectionPending = false
        activeLocationRequestId = null
        activeLocationStartedAtMillis = null
        activeLocationSample = null
        activeLocationTargetRevision = null
        setMockMode(false)
        state = InjectorState.STOPPED
    }

    override fun cleanup() {
        stop()
    }

    @Synchronized
    private fun setMockMode(enabled: Boolean) {
        desiredMockMode = enabled
        val fusedClient = client ?: run {
            publishStatus()
            return
        }
        val requestId = ++nextMockModeRequestId
        activeMockModeRequestId = requestId
        activeMockModeStartedAtMillis = monotonicNowMillis()
        mockModeReadyForRequest = false
        runCatching {
            mockModePending = true
            publishStatus()
            fusedClient.setMockMode(
                enabled = enabled,
                onSuccess = mockModeSuccess@{
                    completeMockModeSuccess(requestId, enabled)
                },
                onFailure = mockModeFailure@{ error ->
                    completeMockModeFailure(requestId, enabled, error)
                },
            )
        }.onFailure { error ->
            completeMockModeFailure(requestId, enabled, error)
        }
    }

    @Synchronized
    private fun completeMockModeSuccess(requestId: Long, enabled: Boolean) {
        if (activeMockModeRequestId != requestId) {
            return
        }
        activeMockModeRequestId = null
        activeMockModeStartedAtMillis = null
        mockModePending = false
        mockModeEnabled = enabled
        mockModeReadyForRequest = true
        lastError = null
        lastErrorCode = null
        state = if (enabled) InjectorState.RUNNING else InjectorState.STOPPED
        publishStatus()
        if (enabled) {
            dispatchPendingSample()
        }
    }

    @Synchronized
    private fun completeMockModeFailure(
        requestId: Long,
        enabled: Boolean,
        error: Throwable,
    ) {
        if (activeMockModeRequestId != requestId) {
            return
        }
        activeMockModeRequestId = null
        activeMockModeStartedAtMillis = null
        mockModePending = false
        mockModeReadyForRequest = false
        publishError(
            code = InjectorErrorCode.FUSED_MOCK_MODE_FAILED,
            message = "fused mock mode ${if (enabled) "开启" else "关闭"}失败: ${error.message ?: "未知错误"}",
        )
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    override fun inject(sample: LocationSample) {
        pendingSample = sample
        recoverTimedOutRequests(monotonicNowMillis())
        client ?: run {
            publishError(InjectorErrorCode.GOOGLE_PLAY_SERVICES_UNAVAILABLE, "fused client 不可用")
            return
        }
        if (!mockModeEnabled || !desiredMockMode || !mockModeReadyForRequest) {
            return
        }
        dispatchPendingSample()
    }

    @Synchronized
    private fun dispatchPendingSample() {
        val fusedClient = client ?: return
        if (activeLocationRequestId != null || !mockModeEnabled || !desiredMockMode || !mockModeReadyForRequest) {
            return
        }
        val sample = pendingSample ?: return
        pendingSample = null

        val location = FusedMockLocation(
            provider = LocationManager.GPS_PROVIDER,
            latitude = sample.latitude,
            longitude = sample.longitude,
            accuracyMeters = sample.accuracyMeters,
            timeMillis = sample.timestampMillis,
            elapsedRealtimeNanos = sample.elapsedRealtimeNanos,
            altitude = sample.altitudeMeters,
            speedMps = sample.speedMps,
            bearingDegrees = sample.bearingDegrees,
            verticalAccuracyMeters = sample.verticalAccuracyMeters,
            speedAccuracyMps = sample.speedAccuracyMps,
            bearingAccuracyDegrees = sample.bearingAccuracyDegrees,
        )

        val requestId = ++nextLocationRequestId
        val requestGeneration = injectionGeneration
        val requestTargetRevision = targetRevision
        activeLocationRequestId = requestId
        activeLocationStartedAtMillis = monotonicNowMillis()
        activeLocationSample = sample
        activeLocationTargetRevision = requestTargetRevision
        lastInjectionPending = true
        publishStatus()
        runCatching {
            fusedClient.setMockLocation(
                location = location,
                onSuccess = locationSuccess@{
                    completeLocationSuccess(requestId, requestGeneration, requestTargetRevision, sample)
                },
                onFailure = locationFailure@{ error ->
                    completeLocationFailure(requestId, requestGeneration, requestTargetRevision, error)
                },
            )
        }.onFailure { error ->
            completeLocationFailure(requestId, requestGeneration, requestTargetRevision, error)
        }
    }

    @Synchronized
    private fun completeLocationSuccess(
        requestId: Long,
        requestGeneration: Long,
        requestTargetRevision: Long,
        sample: LocationSample,
    ) {
        if (activeLocationRequestId != requestId) {
            return
        }
        activeLocationRequestId = null
        activeLocationStartedAtMillis = null
        activeLocationSample = null
        activeLocationTargetRevision = null
        lastInjectionPending = false
        if (isCurrentInjection(requestGeneration, requestTargetRevision)) {
            lastInjectionTimeMillis = sample.timestampMillis
            lastSuccessfulLatitude = sample.latitude
            lastSuccessfulLongitude = sample.longitude
            lastSuccessfulSample = sample
            lastError = null
            lastErrorCode = null
            state = InjectorState.RUNNING
        }
        dispatchPendingSample()
        if (activeLocationRequestId == null) {
            publishStatus()
        }
    }

    @Synchronized
    private fun completeLocationFailure(
        requestId: Long,
        requestGeneration: Long,
        requestTargetRevision: Long,
        error: Throwable,
    ) {
        if (activeLocationRequestId != requestId) {
            return
        }
        activeLocationRequestId = null
        activeLocationStartedAtMillis = null
        activeLocationSample = null
        activeLocationTargetRevision = null
        lastInjectionPending = false
        if (isCurrentInjection(requestGeneration, requestTargetRevision)) {
            publishError(InjectorErrorCode.FUSED_SET_LOCATION_FAILED, "fused 注入失败: ${error.message ?: "未知错误"}")
        }
        dispatchPendingSample()
        if (activeLocationRequestId == null) {
            publishStatus()
        }
    }

    private fun isCurrentInjection(requestGeneration: Long, requestTargetRevision: Long): Boolean {
        return requestGeneration == injectionGeneration &&
            requestTargetRevision == targetRevision &&
            desiredMockMode &&
            mockModeEnabled &&
            mockModeReadyForRequest &&
            currentTarget != null
    }

    @Synchronized
    private fun recoverTimedOutRequests(nowMillis: Long) {
        val mockModeStartedAt = activeMockModeStartedAtMillis
        if (
            activeMockModeRequestId != null &&
            mockModeStartedAt != null &&
            nowMillis >= mockModeStartedAt &&
            nowMillis - mockModeStartedAt >= mockModeRequestTimeoutMillis
        ) {
            activeMockModeRequestId = null
            activeMockModeStartedAtMillis = null
            mockModePending = false
            mockModeReadyForRequest = false
            lastFailureAtMillis = System.currentTimeMillis()
            lastErrorCode = InjectorErrorCode.FUSED_MOCK_MODE_FAILED
            lastError = "fused mock mode 请求超时, 正在重试"
            state = if (desiredMockMode) InjectorState.STARTING else InjectorState.STOPPED
            setMockMode(desiredMockMode)
        }

        val locationStartedAt = activeLocationStartedAtMillis
        if (
            activeLocationRequestId != null &&
            locationStartedAt != null &&
            nowMillis >= locationStartedAt &&
            nowMillis - locationStartedAt >= setLocationRequestTimeoutMillis
        ) {
            val retrySample = pendingSample ?: activeLocationSample.takeIf {
                activeLocationTargetRevision == targetRevision
            }
            activeLocationRequestId = null
            activeLocationStartedAtMillis = null
            activeLocationSample = null
            activeLocationTargetRevision = null
            lastInjectionPending = false
            pendingSample = retrySample
            lastFailureAtMillis = System.currentTimeMillis()
            lastErrorCode = InjectorErrorCode.FUSED_SET_LOCATION_FAILED
            lastError = "fused setMockLocation 请求超时, 正在重试最新样本"
            state = InjectorState.DEGRADED
            dispatchPendingSample()
            if (activeLocationRequestId == null) {
                publishStatus()
            }
        }
    }

    private fun publishError(code: InjectorErrorCode, message: String) {
        state = InjectorState.FAILED
        lastFailureAtMillis = System.currentTimeMillis()
        lastErrorCode = code
        lastError = message
        onError(message)
        publishStatus()
    }

    private fun publishStatus() {
        FusedLocationDiagnosticsStore.update(
            FusedLocationDiagnostics(
                available = client != null,
                mockModeEnabled = mockModeEnabled,
                mockModePending = mockModePending,
                lastInjectionPending = lastInjectionPending,
                lastInjectionTimeMillis = lastInjectionTimeMillis,
                lastSuccessfulLatitude = lastSuccessfulLatitude,
                lastSuccessfulLongitude = lastSuccessfulLongitude,
                lastError = lastError,
                lastInjectionElapsedRealtimeMillis = lastSuccessfulSample
                    ?.elapsedRealtimeNanos
                    ?.div(1_000_000L),
                lastSuccessfulAccuracyMeters = lastSuccessfulSample?.accuracyMeters,
            ),
        )
    }

    @Synchronized
    override fun status(): InjectorStatus {
        recoverTimedOutRequests(monotonicNowMillis())
        return InjectorStatus(
            id = "fused-location",
            displayName = "Fused",
            state = state,
            lastSuccessAtMillis = lastInjectionTimeMillis,
            lastFailureAtMillis = lastFailureAtMillis,
            lastErrorCode = lastErrorCode,
            lastErrorMessage = lastError,
            lastInjectedSample = lastSuccessfulSample,
        )
    }

    @Suppress("unused")
    private val retainedUpdateIntervalMs = updateIntervalMs
}

interface FusedMockLocationClient {
    fun setMockMode(
        enabled: Boolean,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit,
    )

    fun setMockLocation(
        location: FusedMockLocation,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit,
    )
}

data class FusedMockLocation(
    val provider: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timeMillis: Long,
    val elapsedRealtimeNanos: Long,
    val altitude: Double?,
    val speedMps: Float?,
    val bearingDegrees: Float?,
    val verticalAccuracyMeters: Float?,
    val speedAccuracyMps: Float?,
    val bearingAccuracyDegrees: Float?,
)

private class GoogleFusedMockLocationClient(context: Context) : FusedMockLocationClient {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    override fun setMockMode(
        enabled: Boolean,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        client.setMockMode(enabled)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }

    override fun setMockLocation(
        location: FusedMockLocation,
        onSuccess: () -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        client.setMockLocation(location.toAndroidLocation())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onFailure)
    }
}

private fun FusedMockLocation.toAndroidLocation(): Location {
    return Location(provider).apply {
        this.latitude = this@toAndroidLocation.latitude
        this.longitude = this@toAndroidLocation.longitude
        accuracy = accuracyMeters
        time = timeMillis
        elapsedRealtimeNanos = this@toAndroidLocation.elapsedRealtimeNanos
        this@toAndroidLocation.altitude?.let { altitude = it }
        speedMps?.let { speed = it }
        bearingDegrees?.let { bearing = it }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            this@toAndroidLocation.verticalAccuracyMeters?.let { verticalAccuracyMeters = it }
            speedAccuracyMps?.let { speedAccuracyMetersPerSecond = it }
            this@toAndroidLocation.bearingAccuracyDegrees?.let { bearingAccuracyDegrees = it }
        }
    }
}
