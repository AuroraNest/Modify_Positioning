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
) : LocationInjector {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var updateJob: Job? = null
    @Volatile
    private var currentTarget: TargetLocation? = null
    private var mockModeEnabled = false
    private var tick = 0L
    private var burstRemain = 0
    private var lastInjectionTimeMillis: Long? = null
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
        startLoop()
        inject(target)
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
                val target = currentTarget
                if (target == null) {
                    publishError("fused 缺少目标位置")
                    break
                }
                inject(target)
                delay(nextDelay())
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
        val fusedClient = client ?: run {
            publishStatus()
            return
        }
        runCatching {
            fusedClient.setMockMode(enabled) { error ->
                publishError("fused mock mode ${if (enabled) "开启" else "关闭"}失败: ${error.message ?: "未知错误"}")
            }
            mockModeEnabled = enabled
            publishStatus()
        }.onFailure { error ->
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
        val location = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = point.first
            longitude = point.second
            accuracy = 4f + ((tick % 3).toFloat())
            time = now
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            altitude = 0.0
            speed = 0.2f + ((tick % 5).toFloat() * 0.12f)
            bearing = ((tick * 17L) % 360L).toFloat()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                verticalAccuracyMeters = 8f
                speedAccuracyMetersPerSecond = 0.4f
                bearingAccuracyDegrees = 8f
            }
        }

        runCatching {
            fusedClient.setMockLocation(location) { error ->
                publishError("fused 注入失败: ${error.message ?: "未知错误"}")
            }
            lastInjectionTimeMillis = now
            lastError = null
            publishStatus()
        }.onFailure { error ->
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
                lastInjectionTimeMillis = lastInjectionTimeMillis,
                lastError = lastError,
            ),
        )
    }
}

interface FusedMockLocationClient {
    fun setMockMode(enabled: Boolean, onFailure: (Throwable) -> Unit)
    fun setMockLocation(location: Location, onFailure: (Throwable) -> Unit)
}

private class GoogleFusedMockLocationClient(context: Context) : FusedMockLocationClient {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    override fun setMockMode(enabled: Boolean, onFailure: (Throwable) -> Unit) {
        client.setMockMode(enabled).addOnFailureListener(onFailure)
    }

    override fun setMockLocation(location: Location, onFailure: (Throwable) -> Unit) {
        client.setMockLocation(location).addOnFailureListener(onFailure)
    }
}
