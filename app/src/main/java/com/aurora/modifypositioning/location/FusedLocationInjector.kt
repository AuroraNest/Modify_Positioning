package com.aurora.modifypositioning.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
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
)

internal class FusedLocationInjectorCore(
    private val client: FusedMockLocationClient?,
    updateIntervalMs: Long,
    private val onError: (String) -> Unit,
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
    private var nextMockModeRequestId = 0L
    private var lastInjectionTimeMillis: Long? = null
    private var lastInjectionPending = false
    private var pendingSample: LocationSample? = null
    private var activeLocationRequestId: Long? = null
    private var nextLocationRequestId = 0L
    private var injectionGeneration = 0L
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
        activeLocationRequestId = requestId
        lastInjectionPending = true
        publishStatus()
        runCatching {
            fusedClient.setMockLocation(
                location = location,
                onSuccess = locationSuccess@{
                    completeLocationSuccess(requestId, requestGeneration, sample)
                },
                onFailure = locationFailure@{ error ->
                    completeLocationFailure(requestId, requestGeneration, error)
                },
            )
        }.onFailure { error ->
            completeLocationFailure(requestId, requestGeneration, error)
        }
    }

    @Synchronized
    private fun completeLocationSuccess(
        requestId: Long,
        requestGeneration: Long,
        sample: LocationSample,
    ) {
        if (activeLocationRequestId != requestId) {
            return
        }
        activeLocationRequestId = null
        lastInjectionPending = false
        if (isCurrentInjection(requestGeneration)) {
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
        error: Throwable,
    ) {
        if (activeLocationRequestId != requestId) {
            return
        }
        activeLocationRequestId = null
        lastInjectionPending = false
        if (isCurrentInjection(requestGeneration)) {
            publishError(InjectorErrorCode.FUSED_SET_LOCATION_FAILED, "fused 注入失败: ${error.message ?: "未知错误"}")
        }
        dispatchPendingSample()
        if (activeLocationRequestId == null) {
            publishStatus()
        }
    }

    private fun isCurrentInjection(requestGeneration: Long): Boolean {
        return requestGeneration == injectionGeneration &&
            desiredMockMode &&
            mockModeEnabled &&
            mockModeReadyForRequest &&
            currentTarget != null
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
            ),
        )
    }

    @Synchronized
    override fun status(): InjectorStatus {
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
