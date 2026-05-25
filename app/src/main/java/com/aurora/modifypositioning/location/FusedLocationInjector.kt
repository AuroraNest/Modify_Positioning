package com.aurora.modifypositioning.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import com.google.android.gms.location.LocationServices
import com.aurora.modifypositioning.model.ENHANCED_STARTUP_BURST_COUNT
import com.aurora.modifypositioning.model.ENHANCED_STARTUP_BURST_INTERVAL_MS
import com.aurora.modifypositioning.model.TargetLocation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FusedLocationInjector(
    context: Context,
    updateIntervalMs: Long,
    onError: (String) -> Unit,
    clientFactory: (Context) -> FusedMockLocationClient? = { appContext ->
        GoogleFusedMockLocationClient(appContext)
    },
) : LocationInjector by FusedLocationInjectorCore(
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
    private val updateIntervalMs: Long,
    private val onError: (String) -> Unit,
    private val elapsedRealtimeNanosProvider: () -> Long = { SystemClock.elapsedRealtimeNanos() },
) : LocationInjector {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var updateJob: Job? = null
    @Volatile
    private var currentTarget: TargetLocation? = null
    private var mockModeEnabled = false
    private var mockModePending = false
    private var desiredMockMode = false
    private var tick = 0L
    private var burstRemain = 0
    private var lastInjectionTimeMillis: Long? = null
    private var lastInjectionPending = false
    private var lastInjectionRequestId = 0L
    private var lastSuccessfulLatitude: Double? = null
    private var lastSuccessfulLongitude: Double? = null
    private var lastError: String? = null

    init {
        publishStatus()
    }

    override fun start(target: TargetLocation) {
        updateTarget(target)
        if (currentTarget == null || client == null) {
            return
        }
        setMockMode(true)
        burstRemain = max(burstRemain, ENHANCED_STARTUP_BURST_COUNT)
    }

    override fun updateTarget(target: TargetLocation) {
        if (!target.isValid()) {
            publishError("fused 目标坐标无效")
            return
        }
        currentTarget = target
        if (mockModeEnabled) {
            inject(target)
        }
    }

    override fun pause() {
        updateJob?.cancel()
        updateJob = null
    }

    override fun stop() {
        pause()
        currentTarget = null
        lastInjectionPending = false
        setMockMode(false)
        tick = 0L
        burstRemain = 0
    }

    override fun cleanup() {
        stop()
        scope.cancel()
    }

    private fun startLoop() {
        updateJob?.cancel()
        updateJob = scope.launch {
            while (isActive) {
                delay(nextDelay())
                val target = currentTarget
                if (target == null) {
                    publishError("fused 缺少目标位置")
                    break
                }
                inject(target)
            }
        }
    }

    private fun nextDelay(): Long {
        return if (burstRemain > 0) {
            burstRemain -= 1
            ENHANCED_STARTUP_BURST_INTERVAL_MS
        } else {
            updateIntervalMs
        }
    }

    private fun setMockMode(enabled: Boolean) {
        desiredMockMode = enabled
        val fusedClient = client ?: run {
            publishStatus()
            return
        }
        runCatching {
            mockModePending = true
            publishStatus()
            fusedClient.setMockMode(
                enabled = enabled,
                onSuccess = mockModeSuccess@{
                    if (desiredMockMode != enabled) {
                        return@mockModeSuccess
                    }
                    mockModePending = false
                    mockModeEnabled = enabled
                    lastError = null
                    publishStatus()
                    if (enabled) {
                        startLoop()
                        currentTarget?.let { inject(it) }
                    }
                },
                onFailure = mockModeFailure@{ error ->
                    if (desiredMockMode != enabled) {
                        return@mockModeFailure
                    }
                    mockModePending = false
                    publishError("fused mock mode ${if (enabled) "开启" else "关闭"}失败: ${error.message ?: "未知错误"}")
                },
            )
        }.onFailure { error ->
            mockModePending = false
            publishError("fused mock mode ${if (enabled) "开启" else "关闭"}失败: ${error.message ?: "未知错误"}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun inject(target: TargetLocation) {
        val fusedClient = client ?: return
        if (!mockModeEnabled) {
            return
        }

        val now = System.currentTimeMillis()
        val point = jitterPoint(target, tick)
        tick += 1
        val accuracy = 4f + ((tick % 3).toFloat())
        val speed = 0.2f + ((tick % 5).toFloat() * 0.12f)
        val bearing = ((tick * 17L) % 360L).toFloat()
        val location = FusedMockLocation(
            provider = LocationManager.GPS_PROVIDER,
            latitude = point.first,
            longitude = point.second,
            accuracyMeters = accuracy,
            timeMillis = now,
            elapsedRealtimeNanos = elapsedRealtimeNanosProvider(),
            altitude = 0.0,
            speedMps = speed,
            bearingDegrees = bearing,
        )

        runCatching {
            val requestId = ++lastInjectionRequestId
            lastInjectionPending = true
            publishStatus()
            fusedClient.setMockLocation(
                location = location,
                onSuccess = locationSuccess@{
                    if (requestId != lastInjectionRequestId) {
                        return@locationSuccess
                    }
                    lastInjectionPending = false
                    lastInjectionTimeMillis = now
                    lastSuccessfulLatitude = point.first
                    lastSuccessfulLongitude = point.second
                    lastError = null
                    publishStatus()
                },
                onFailure = locationFailure@{ error ->
                    if (requestId != lastInjectionRequestId) {
                        return@locationFailure
                    }
                    lastInjectionPending = false
                    publishError("fused 注入失败: ${error.message ?: "未知错误"}")
                },
            )
        }.onFailure { error ->
            lastInjectionPending = false
            publishError("fused 注入失败: ${error.message ?: "未知错误"}")
        }
    }

    private fun jitterPoint(target: TargetLocation, index: Long): Pair<Double, Double> {
        val angle = (index % 360L).toDouble() * (PI / 180.0)
        val meters = com.aurora.modifypositioning.model.ENHANCED_JITTER_RADIUS_METERS
        val latPerMeter = 1.0 / 111_320.0
        val lonPerMeter = 1.0 / (111_320.0 * cos(target.latitude * (PI / 180.0)).coerceAtLeast(0.2))

        val latOffset = sin(angle) * meters * latPerMeter
        val lonOffset = cos(angle) * meters * lonPerMeter
        return (target.latitude + latOffset) to (target.longitude + lonOffset)
    }

    private fun publishError(message: String) {
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
    val altitude: Double,
    val speedMps: Float,
    val bearingDegrees: Float,
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
        altitude = this@toAndroidLocation.altitude
        speed = speedMps
        bearing = bearingDegrees
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            verticalAccuracyMeters = 8f
            speedAccuracyMetersPerSecond = 0.4f
            bearingAccuracyDegrees = 8f
        }
    }
}
