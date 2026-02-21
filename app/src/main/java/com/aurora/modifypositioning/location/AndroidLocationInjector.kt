package com.aurora.modifypositioning.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import com.aurora.modifypositioning.model.ENHANCED_JITTER_RADIUS_METERS
import com.aurora.modifypositioning.model.ENHANCED_STARTUP_BURST_COUNT
import com.aurora.modifypositioning.model.ENHANCED_STARTUP_BURST_INTERVAL_MS
import com.aurora.modifypositioning.model.InjectionReport
import com.aurora.modifypositioning.model.TargetLocation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class AndroidLocationInjector(
    context: Context,
    private val updateIntervalMs: Long,
    private val onError: (String) -> Unit,
    private val onInjected: (InjectionReport) -> Unit,
) : LocationInjector {

    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var updateJob: Job? = null
    @Volatile
    private var currentTarget: TargetLocation? = null
    private var providersReady = false
    private var tick = 0L
    private var burstRemain = 0

    override fun start(target: TargetLocation) {
        updateTarget(target)
        if (currentTarget == null) {
            return
        }
        ensureProviders()
        burstRemain = ENHANCED_STARTUP_BURST_COUNT
        startLoop()
        inject(target)
    }

    override fun updateTarget(target: TargetLocation) {
        if (!target.isValid()) {
            onError("目标坐标无效")
            return
        }
        currentTarget = target
    }

    override fun pause() {
        updateJob?.cancel()
        updateJob = null
    }

    override fun stop() {
        pause()
        currentTarget = null
        removeProviders()
        tick = 0L
        burstRemain = 0
    }

    private fun startLoop() {
        updateJob?.cancel()
        updateJob = scope.launch {
            while (isActive) {
                val target = currentTarget
                if (target == null) {
                    onError("缺少目标位置")
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

    private fun ensureProviders() {
        val manager = locationManager ?: run {
            onError("定位服务不可用")
            return
        }
        if (providersReady) {
            return
        }
        runCatching {
            addTestProvider(LocationManager.GPS_PROVIDER)
            addTestProvider(LocationManager.NETWORK_PROVIDER)
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            manager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
            providersReady = true
        }.onFailure {
            onError("创建测试定位通道失败: ${it.message ?: "未知错误"}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun inject(target: TargetLocation) {
        val manager = locationManager ?: run {
            onError("定位服务不可用")
            return
        }
        if (!providersReady || !isProviderReady(manager, LocationManager.GPS_PROVIDER) || !isProviderReady(manager, LocationManager.NETWORK_PROVIDER)) {
            providersReady = false
            ensureProviders()
        }
        if (!providersReady) {
            onError("测试定位通道未就绪")
            return
        }

        val now = System.currentTimeMillis()
        val nanos = SystemClock.elapsedRealtimeNanos()
        val point = jitterPoint(target, tick)
        tick += 1

        val gpsAccuracy = 4f + ((tick % 3).toFloat())
        val networkAccuracy = 10f + ((tick % 7).toFloat())
        val mockSpeed = 0.2f + ((tick % 5).toFloat() * 0.12f)
        val bearing = ((tick * 17L) % 360L).toFloat()

        val gpsLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = point.first
            longitude = point.second
            accuracy = gpsAccuracy
            time = now
            elapsedRealtimeNanos = nanos
            altitude = 0.0
            speed = mockSpeed
            this.bearing = bearing
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                verticalAccuracyMeters = 8f
                speedAccuracyMetersPerSecond = 0.4f
                bearingAccuracyDegrees = 8f
            }
        }

        val networkLocation = Location(LocationManager.NETWORK_PROVIDER).apply {
            latitude = point.first
            longitude = point.second
            accuracy = networkAccuracy
            time = now
            elapsedRealtimeNanos = nanos
            altitude = 0.0
            speed = mockSpeed
            this.bearing = bearing
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                verticalAccuracyMeters = 20f
                speedAccuracyMetersPerSecond = 1.2f
                bearingAccuracyDegrees = 16f
            }
        }

        runCatching {
            manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, gpsLocation)
            manager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, networkLocation)
            onInjected(
                InjectionReport(
                    provider = LocationManager.GPS_PROVIDER,
                    latitude = gpsLocation.latitude,
                    longitude = gpsLocation.longitude,
                    accuracyMeters = gpsLocation.accuracy,
                    timeMillis = gpsLocation.time,
                ),
            )
        }.onFailure {
            providersReady = false
            onError("注入模拟定位失败: ${it.message ?: "未知错误"}")
        }
    }

    private fun isProviderReady(manager: LocationManager, provider: String): Boolean {
        return runCatching {
            manager.allProviders.contains(provider) && manager.isProviderEnabled(provider)
        }.getOrDefault(false)
    }

    private fun jitterPoint(target: TargetLocation, index: Long): Pair<Double, Double> {
        val angle = (index % 360L).toDouble() * (PI / 180.0)
        val meters = ENHANCED_JITTER_RADIUS_METERS
        val latPerMeter = 1.0 / 111_320.0
        val lonPerMeter = 1.0 / (111_320.0 * cos(target.latitude * (PI / 180.0)).coerceAtLeast(0.2))

        val latOffset = sin(angle) * meters * latPerMeter
        val lonOffset = cos(angle) * meters * lonPerMeter
        return (target.latitude + latOffset) to (target.longitude + lonOffset)
    }

    private fun addTestProvider(provider: String) {
        val manager = locationManager ?: return
        runCatching { manager.removeTestProvider(provider) }
        manager.addTestProvider(
            provider,
            false,
            false,
            false,
            false,
            true,
            true,
            true,
            Criteria.POWER_LOW,
            Criteria.ACCURACY_FINE,
        )
    }

    private fun removeProviders() {
        val manager = locationManager ?: return
        if (!providersReady) {
            return
        }
        runCatching { manager.removeTestProvider(LocationManager.GPS_PROVIDER) }
        runCatching { manager.removeTestProvider(LocationManager.NETWORK_PROVIDER) }
        providersReady = false
    }

    fun dispose() {
        stop()
        scope.cancel()
    }
}
