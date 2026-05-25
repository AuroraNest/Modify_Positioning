package com.aurora.modifypositioning.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aurora.modifypositioning.MainActivity
import com.aurora.modifypositioning.R
import com.aurora.modifypositioning.data.MapPreferencesStore
import com.aurora.modifypositioning.domain.MockControllerStore
import com.aurora.modifypositioning.domain.RandomWalkEngine
import com.aurora.modifypositioning.domain.RouteSimulationEngine
import com.aurora.modifypositioning.domain.SimulationTick
import com.aurora.modifypositioning.domain.StepResult
import com.aurora.modifypositioning.domain.calibration.MainlandCoordinateCalibrator
import com.aurora.modifypositioning.location.AndroidLocationInjector
import com.aurora.modifypositioning.location.CompositeLocationInjector
import com.aurora.modifypositioning.location.FusedLocationInjector
import com.aurora.modifypositioning.location.LocationInjector
import com.aurora.modifypositioning.model.CoordinateCalibrationMode
import com.aurora.modifypositioning.model.DEFAULT_TARGET
import com.aurora.modifypositioning.model.ENHANCED_UPDATE_INTERVAL_MS
import com.aurora.modifypositioning.model.MockState
import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.MovementPoint
import com.aurora.modifypositioning.model.MovementState
import com.aurora.modifypositioning.model.PlannedRoute
import com.aurora.modifypositioning.model.RouteProgress
import com.aurora.modifypositioning.model.RandomWalkConfig
import com.aurora.modifypositioning.model.TargetLocation
import com.aurora.modifypositioning.util.MockEnvironmentChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MockLocationService : Service() {

    private lateinit var injector: LocationInjector
    private val controller = MockControllerStore.instance
    private lateinit var mapPreferencesStore: MapPreferencesStore
    private val coordinateCalibrator = MainlandCoordinateCalibrator()
    private val randomWalkEngine = RandomWalkEngine()
    private val routeSimulationEngine = RouteSimulationEngine()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var randomWalkJob: Job? = null
    private var routeMovementJob: Job? = null
    private var explicitStop = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
        mapPreferencesStore = MapPreferencesStore(this)
        val onInjectionError: (String) -> Unit = { error ->
            controller.onError(error)
            refreshNotification()
        }
        injector = CompositeLocationInjector(
            injectors = listOf(
                AndroidLocationInjector(
                    context = this,
                    updateIntervalMs = ENHANCED_UPDATE_INTERVAL_MS,
                    onError = onInjectionError,
                    onInjected = { report ->
                        controller.onInjected(report)
                    },
                ),
                FusedLocationInjector(
                    context = this,
                    updateIntervalMs = ENHANCED_UPDATE_INTERVAL_MS,
                    onError = onInjectionError,
                ),
            ),
            onError = onInjectionError,
        )
        if (shouldKeepRunning()) {
            handleStart()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                explicitStop = false
                setKeepRunning(true)
                handleStart()
            }

            ACTION_PAUSE -> handlePause()
            ACTION_STOP -> {
                explicitStop = true
                setKeepRunning(false)
                handleStop()
            }

            else -> {
                if (shouldKeepRunning()) {
                    handleStart()
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun handleStart() {
        startForeground(NOTIFICATION_ID, buildNotification(getRunningContentText()))

        val missingPermissions = MockEnvironmentChecker.missingPermissions(this)
        if (missingPermissions.isNotEmpty()) {
            controller.onError("缺少权限: ${missingPermissions.joinToString()}")
            refreshNotification()
            return
        }

        if (!MockEnvironmentChecker.isMockLocationAppSelected(this)) {
            controller.onError("请在开发者选项中将本应用设为模拟位置信息应用")
            refreshNotification()
            return
        }

        val selectedTarget = runBlocking { mapPreferencesStore.getTargetOrNull() } ?: DEFAULT_TARGET
        val calibrationMode = runBlocking { mapPreferencesStore.getCalibrationMode() }
        val movementMode = runBlocking { mapPreferencesStore.getMovementMode() }
        val resumingRandomWalk = movementMode == MovementMode.RANDOM_WALK &&
            controller.movementState.value == MovementState.Paused &&
            controller.movementTrace.value.isNotEmpty()
        val routeMode = movementMode == MovementMode.POINT_TO_POINT_NAV ||
            movementMode == MovementMode.CUSTOM_ROUTE
        val resumingRoute = routeMode &&
            controller.movementState.value == MovementState.Paused &&
            controller.routeProgress.value != null &&
            controller.plannedRoute.value != null

        stopRandomWalkLoop()
        stopRouteMovementLoop()

        val movementCenterTarget = if (resumingRandomWalk) {
            controller.movementCenter.value
        } else {
            selectedTarget
        }
        val movementStartTarget = if (resumingRandomWalk || resumingRoute) {
            controller.target.value
        } else {
            selectedTarget
        }

        val initialInjectTarget = buildInjectTarget(movementStartTarget, calibrationMode)
        injector.start(initialInjectTarget)
        controller.onServiceStarted(movementStartTarget)
        acquireWakeLock()

        when (movementMode) {
            MovementMode.RANDOM_WALK -> {
                val config = runBlocking { mapPreferencesStore.getRandomWalkConfig() }
                controller.onMovementModeChanged(MovementMode.RANDOM_WALK)
                if (resumingRandomWalk) {
                    controller.onMovementResumed()
                } else {
                    controller.onMovementStarted(
                        centerTarget = movementCenterTarget,
                        startPoint = MovementPoint(
                            lat = movementStartTarget.latitude,
                            lng = movementStartTarget.longitude,
                            timestampMs = System.currentTimeMillis(),
                        ),
                    )
                }
                startRandomWalkLoop(
                    centerTarget = movementCenterTarget,
                    currentTarget = movementStartTarget,
                    calibrationMode = calibrationMode,
                    config = config,
                )
            }

            MovementMode.POINT_TO_POINT_NAV,
            MovementMode.CUSTOM_ROUTE -> {
                val plannedRoute = controller.plannedRoute.value
                if (plannedRoute == null || plannedRoute.points.size < 2) {
                    controller.onError("请先规划并确认路线")
                    refreshNotification()
                    return
                }
                controller.onMovementModeChanged(movementMode)
                if (resumingRoute) {
                    controller.onMovementResumed()
                }
                startRouteMovementLoop(
                    plannedRoute = plannedRoute,
                    calibrationMode = calibrationMode,
                    initialProgress = controller.routeProgress.value,
                )
            }

            MovementMode.FIXED -> {
                controller.onMovementModeChanged(MovementMode.FIXED)
            }
        }

        refreshNotification()
    }

    private fun startRandomWalkLoop(
        centerTarget: TargetLocation,
        currentTarget: TargetLocation,
        calibrationMode: CoordinateCalibrationMode,
        config: RandomWalkConfig,
    ) {
        val normalized = config.normalized()
        val center = MovementPoint(
            lat = centerTarget.latitude,
            lng = centerTarget.longitude,
            timestampMs = System.currentTimeMillis(),
        )
        randomWalkJob = serviceScope.launch {
            var current = MovementPoint(
                lat = currentTarget.latitude,
                lng = currentTarget.longitude,
                timestampMs = System.currentTimeMillis(),
            )
            var heading: Double? = null

            while (isActive) {
                delay(normalized.stepIntervalMs)
                val step = randomWalkEngine.nextStep(
                    center = center,
                    current = current,
                    previousHeadingDeg = heading,
                    config = normalized,
                    nowMs = System.currentTimeMillis(),
                )

                when (step) {
                    is StepResult.Moved -> {
                        current = step.point
                        heading = step.headingDeg
                        val displayTarget = TargetLocation(
                            name = "随机步行",
                            latitude = step.point.lat,
                            longitude = step.point.lng,
                        )
                        val injectTarget = buildInjectTarget(displayTarget, calibrationMode)
                        injector.updateTarget(injectTarget)
                        controller.updateTarget(displayTarget)
                        controller.onMovementProgress(
                            point = step.point,
                            speedMps = step.speedMps,
                            distanceFromCenterMeters = step.distanceFromCenterMeters,
                        )
                        refreshNotification()
                    }

                    is StepResult.ReachedBoundary -> {
                        current = step.point
                        val displayTarget = TargetLocation(
                            name = "随机步行",
                            latitude = step.point.lat,
                            longitude = step.point.lng,
                        )
                        val injectTarget = buildInjectTarget(displayTarget, calibrationMode)
                        injector.updateTarget(injectTarget)
                        controller.updateTarget(displayTarget)
                        controller.onMovementProgress(
                            point = step.point,
                            speedMps = step.speedMps,
                            distanceFromCenterMeters = step.distanceFromCenterMeters,
                        )
                        controller.onMovementReachedBoundary(step.distanceFromCenterMeters)
                        refreshNotification()
                        break
                    }
                }
            }
        }
    }

    private fun startRouteMovementLoop(
        plannedRoute: PlannedRoute,
        calibrationMode: CoordinateCalibrationMode,
        initialProgress: RouteProgress?,
    ) {
        val now = System.currentTimeMillis()
        val startDistance = initialProgress?.traveledMeters ?: 0.0
        val session = routeSimulationEngine.createSession(
            route = plannedRoute,
            mode = plannedRoute.mode,
            startTimeMs = now,
            initialTraveledMeters = startDistance,
        )
        val startTick = session.currentProgress(now)
        val startPoint = startTick.point
        val startTarget = TargetLocation(
            name = "路线起点",
            latitude = startPoint.lat,
            longitude = startPoint.lng,
        )
        injector.updateTarget(buildInjectTarget(startTarget, calibrationMode))
        controller.updateTarget(startTarget)
        controller.onRouteSimulationStarted(
            route = plannedRoute,
            startPoint = MovementPoint(startPoint.lat, startPoint.lng, startPoint.ts),
            initialProgress = startTick.progress,
        )

        routeMovementJob = serviceScope.launch {
            while (isActive) {
                delay(800L)
                val tick = session.advance(System.currentTimeMillis())
                publishRouteTick(
                    tick = tick,
                    calibrationMode = calibrationMode,
                )
                if (tick.reachedDestination) {
                    controller.onRouteSimulationReachedDestination(
                        point = MovementPoint(
                            lat = tick.point.lat,
                            lng = tick.point.lng,
                            timestampMs = tick.point.ts,
                        ),
                        progress = tick.progress,
                    )
                    refreshNotification()
                    break
                }
            }
        }
    }

    private fun publishRouteTick(
        tick: SimulationTick,
        calibrationMode: CoordinateCalibrationMode,
    ) {
        val displayTarget = TargetLocation(
            name = "路线模拟",
            latitude = tick.point.lat,
            longitude = tick.point.lng,
        )
        val injectTarget = buildInjectTarget(displayTarget, calibrationMode)
        injector.updateTarget(injectTarget)
        controller.updateTarget(displayTarget)
        controller.onRouteSimulationProgress(
            point = MovementPoint(
                lat = tick.point.lat,
                lng = tick.point.lng,
                timestampMs = tick.point.ts,
            ),
            speedMps = tick.speedMps,
            progress = tick.progress,
        )
        refreshNotification()
    }

    private fun stopRandomWalkLoop() {
        randomWalkJob?.cancel()
        randomWalkJob = null
    }

    private fun stopRouteMovementLoop() {
        routeMovementJob?.cancel()
        routeMovementJob = null
    }

    private fun buildInjectTarget(
        selectedTarget: TargetLocation,
        calibrationMode: CoordinateCalibrationMode,
    ): TargetLocation {
        val calibrated = coordinateCalibrator.toInjectCoordinate(
            lat = selectedTarget.latitude,
            lng = selectedTarget.longitude,
            mode = calibrationMode,
        )
        return selectedTarget.copy(latitude = calibrated.first, longitude = calibrated.second)
    }

    private fun handlePause() {
        if (controller.state.value == MockState.Idle) {
            return
        }
        stopRandomWalkLoop()
        stopRouteMovementLoop()
        if (controller.movementMode.value == MovementMode.RANDOM_WALK) {
            controller.onMovementPaused()
        } else if (
            controller.movementMode.value == MovementMode.POINT_TO_POINT_NAV ||
            controller.movementMode.value == MovementMode.CUSTOM_ROUTE
        ) {
            controller.onMovementPaused()
        } else {
            injector.pause()
            releaseWakeLock()
        }
        controller.onServicePaused()
        refreshNotification()
    }

    private fun handleStop() {
        stopRandomWalkLoop()
        stopRouteMovementLoop()
        injector.stop()
        releaseWakeLock()
        controller.onServiceStopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (shouldKeepRunning()) {
            ContextCompat.startForegroundService(this, startIntent(this))
        }
    }

    override fun onDestroy() {
        stopRandomWalkLoop()
        stopRouteMovementLoop()
        injector.cleanup()
        releaseWakeLock()
        serviceScope.cancel()
        if (!explicitStop && shouldKeepRunning()) {
            ContextCompat.startForegroundService(this, startIntent(this))
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun refreshNotification() {
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(resolveContentText()))
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) {
            return
        }
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "modify-positioning:mock",
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
        wakeLock = null
    }

    private fun shouldKeepRunning(): Boolean {
        return getSharedPreferences(SERVICE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_KEEP_RUNNING, false)
    }

    private fun setKeepRunning(keepRunning: Boolean) {
        getSharedPreferences(SERVICE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_KEEP_RUNNING, keepRunning)
            .apply()
    }

    private fun resolveContentText(): String {
        return when (val state = controller.state.value) {
            MockState.Running -> {
                when (controller.movementMode.value) {
                    MovementMode.RANDOM_WALK -> {
                        when (controller.movementState.value) {
                            MovementState.Walking -> "随机步行模拟中"
                            MovementState.ReachedBoundary -> "已到边界，保持当前位置"
                            MovementState.Paused -> getPausedContentText()
                            else -> getRunningContentText()
                        }
                    }

                    MovementMode.POINT_TO_POINT_NAV,
                    MovementMode.CUSTOM_ROUTE -> {
                        when (controller.movementState.value) {
                            MovementState.ReachedDestination -> "已到终点，保持当前位置"
                            MovementState.Paused -> getPausedContentText()
                            else -> "路线模拟中"
                        }
                    }

                    MovementMode.FIXED -> getRunningContentText()
                }
            }

            MockState.Paused -> getPausedContentText()
            MockState.Idle -> "服务未运行"
            is MockState.Error -> "${getString(R.string.notification_content_error)}: ${state.message}"
        }
    }

    private fun getRunningContentText(): String {
        return getString(R.string.notification_content_running)
    }

    private fun getPausedContentText(): String {
        return getString(R.string.notification_content_paused)
    }

    private fun buildNotification(content: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_location_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "mock_location_channel"
        const val NOTIFICATION_ID = 10001

        private const val ACTION_PREFIX = "com.aurora.modifypositioning.action"
        const val ACTION_START = "$ACTION_PREFIX.START"
        const val ACTION_PAUSE = "$ACTION_PREFIX.PAUSE"
        const val ACTION_STOP = "$ACTION_PREFIX.STOP"
        private const val SERVICE_PREFS = "service_prefs"
        private const val KEY_KEEP_RUNNING = "keep_running"

        fun startIntent(context: Context): Intent {
            return Intent(context, MockLocationService::class.java).setAction(ACTION_START)
        }

        fun pauseIntent(context: Context): Intent {
            return Intent(context, MockLocationService::class.java).setAction(ACTION_PAUSE)
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, MockLocationService::class.java).setAction(ACTION_STOP)
        }

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
