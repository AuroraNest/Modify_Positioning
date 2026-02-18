package com.aurora.modifypositioning.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import com.aurora.modifypositioning.model.TargetLocation
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
) : LocationInjector {

    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var updateJob: Job? = null
    private var currentTarget: TargetLocation? = null
    private var providersReady = false

    override fun start(target: TargetLocation) {
        if (!target.isValid()) {
            onError("目标坐标无效")
            return
        }
        currentTarget = target
        ensureProviders()
        startLoop()
    }

    override fun pause() {
        updateJob?.cancel()
        updateJob = null
    }

    override fun stop() {
        pause()
        currentTarget = null
        removeProviders()
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
                delay(updateIntervalMs)
            }
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
        if (!providersReady) {
            onError("测试定位通道未就绪")
            return
        }
        val now = System.currentTimeMillis()
        val nanos = SystemClock.elapsedRealtimeNanos()

        val gpsLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = target.latitude
            longitude = target.longitude
            accuracy = 1f
            time = now
            elapsedRealtimeNanos = nanos
            altitude = 0.0
            speed = 0f
            bearing = 0f
        }

        val networkLocation = Location(LocationManager.NETWORK_PROVIDER).apply {
            latitude = target.latitude
            longitude = target.longitude
            accuracy = 3f
            time = now
            elapsedRealtimeNanos = nanos
            altitude = 0.0
            speed = 0f
            bearing = 0f
        }

        runCatching {
            manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, gpsLocation)
            manager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, networkLocation)
        }.onFailure {
            onError("注入模拟定位失败: ${it.message ?: "未知错误"}")
        }
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
            android.location.Criteria.POWER_LOW,
            android.location.Criteria.ACCURACY_FINE,
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
