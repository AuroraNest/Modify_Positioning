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
import com.aurora.modifypositioning.location.FusedLocationDiagnosticsStore
import com.aurora.modifypositioning.location.FusedLocationInjector
import com.aurora.modifypositioning.model.CoordinateCalibrationMode
import com.aurora.modifypositioning.model.DEFAULT_TARGET
import com.aurora.modifypositioning.model.ENHANCED_STARTUP_BURST_COUNT
import com.aurora.modifypositioning.model.ENHANCED_STARTUP_BURST_INTERVAL_MS
import com.aurora.modifypositioning.model.ENHANCED_UPDATE_INTERVAL_MS
import com.aurora.modifypositioning.model.MockState
import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.MovementPoint
import com.aurora.modifypositioning.model.MovementState
import com.aurora.modifypositioning.model.PlannedRoute
import com.aurora.modifypositioning.model.RouteProgress
import com.aurora.modifypositioning.model.RandomWalkConfig
import com.aurora.modifypositioning.model.RestorationState
import com.aurora.modifypositioning.model.TargetLocation
import com.aurora.modifypositioning.model.THIRD_PARTY_COMPATIBILITY_INTERVAL_MS
import com.aurora.modifypositioning.model.THIRD_PARTY_COMPATIBILITY_WARMUP_MS
import com.aurora.modifypositioning.model.THIRD_PARTY_RECOVERY_BURST_COUNT
import com.aurora.modifypositioning.model.TravelMode
import com.aurora.modifypositioning.model.evaluateRestorationState
import com.aurora.modifypositioning.simulation.EnvironmentProfile
import com.aurora.modifypositioning.simulation.LocationSimulationEngine
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

    private lateinit var injector: CompositeLocationInjector
    private val controller = MockControllerStore.instance
    private lateinit var mapPreferencesStore: MapPreferencesStore
    private val coordinateCalibrator = MainlandCoordinateCalibrator()
    private val randomWalkEngine = RandomWalkEngine()
    private val routeSimulationEngine = RouteSimulationEngine()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lifecycleGate = ServiceLifecycleGenerationGate()
    private val recoveryBurstPolicy = RecoveryBurstPolicy(THIRD_PARTY_RECOVERY_BURST_COUNT)

    private var randomWalkJob: Job? = null
    private var routeMovementJob: Job? = null
    private var injectionLoopJob: Job? = null
    private var restorationJob: Job? = null
    private var simulationEngine: LocationSimulationEngine? = null
    private var thirdPartyCompatibilityUntilMillis = 0L
    private var explicitStop = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
        mapPreferencesStore = MapPreferencesStore(this)
        val onInjectionError: (String) -> Unit = { error ->
            handleInjectorError(error)
        }
        injector = CompositeLocationInjector(
            injectors = listOf(
                AndroidLocationInjector(
                    context = this,
                    updateIntervalMs = ENHANCED_UPDATE_INTERVAL_MS,
                    onError = onInjectionError,
                    onInjected = { report ->
                        handleInjectionReport(report)
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                lifecycleGate.beginStart()
                explicitStop = false
                setKeepRunning(true)
                restorationJob?.cancel()
                restorationJob = null
                handleStart()
            }

            ACTION_PAUSE -> handlePause()
            ACTION_STOP -> {
                explicitStop = true
                setKeepRunning(false)
                handleStop(startId)
            }

            else -> {
                if (shouldKeepRunning()) {
                    lifecycleGate.beginStart()
                    handleStart()
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun handleStart() {
        controller.resetRestorationState()
        startForeground(NOTIFICATION_ID, buildNotification(getRunningContentText()))

        val missingPermissions = MockEnvironmentChecker.missingLocationPermissions(this)
        if (missingPermissions.isNotEmpty()) {
            failStartup(
                message = "缺少权限: ${missingPermissions.joinToString()}",
                cleanupStartedComponents = true,
                stopService = true,
            )
            return
        }

        if (!MockEnvironmentChecker.isSystemLocationEnabled(this)) {
            failStartup(
                message = "请先开启系统定位服务",
                cleanupStartedComponents = true,
                stopService = true,
            )
            return
        }

        if (!MockEnvironmentChecker.isMockLocationAppSelected(this)) {
            failStartup(
                message = "请在开发者选项中将本应用设为模拟位置信息应用",
                cleanupStartedComponents = true,
                stopService = true,
            )
            return
        }

        val selectedTarget = runBlocking { mapPreferencesStore.getTargetOrNull() } ?: DEFAULT_TARGET
        val calibrationMode = runBlocking { mapPreferencesStore.getCalibrationMode() }
        val movementMode = runBlocking { mapPreferencesStore.getMovementMode() }
        val startupPlanResult = buildMovementStartupPlan(
            movementMode = movementMode,
            movementState = controller.movementState.value,
            movementTrace = controller.movementTrace.value,
            movementCenter = controller.movementCenter.value,
            currentTarget = controller.target.value,
            selectedTarget = selectedTarget,
            plannedRoute = controller.plannedRoute.value,
            routeProgress = controller.routeProgress.value,
        )
        val startupPlan = when (startupPlanResult) {
            is MovementStartupPlanResult.Ready -> startupPlanResult.plan
            is MovementStartupPlanResult.Failure -> {
                failStartup(
                    message = startupPlanResult.message,
                    cleanupStartedComponents = true,
                    stopService = true,
                )
                return
            }
        }

        stopRandomWalkLoop()
        stopRouteMovementLoop()
        stopInjectionLoop()

        val initialInjectTarget = buildInjectTarget(startupPlan.movementStartTarget, calibrationMode)
        simulationEngine = LocationSimulationEngine(
            initialTarget = initialInjectTarget,
            initialMovementMode = startupPlan.movementMode,
            initialEnvironment = environmentFor(startupPlan.movementMode),
        )
        injector.start(initialInjectTarget)
        controller.onServiceStarted(startupPlan.movementStartTarget)
        publishInjectorHealth()
        acquireWakeLock()
        thirdPartyCompatibilityUntilMillis = System.currentTimeMillis() + THIRD_PARTY_COMPATIBILITY_WARMUP_MS
        recoveryBurstPolicy.reset()
        startInjectionLoop()

        when (startupPlan.movementMode) {
            MovementMode.RANDOM_WALK -> {
                val config = runBlocking { mapPreferencesStore.getRandomWalkConfig() }
                controller.onMovementModeChanged(MovementMode.RANDOM_WALK)
                if (startupPlan.resumingRandomWalk) {
                    controller.onMovementResumed()
                } else {
                    controller.onMovementStarted(
                        centerTarget = startupPlan.movementCenterTarget,
                        startPoint = MovementPoint(
                            lat = startupPlan.movementStartTarget.latitude,
                            lng = startupPlan.movementStartTarget.longitude,
                            timestampMs = System.currentTimeMillis(),
                        ),
                    )
                }
                startRandomWalkLoop(
                    centerTarget = startupPlan.movementCenterTarget,
                    currentTarget = startupPlan.movementStartTarget,
                    calibrationMode = calibrationMode,
                    config = config,
                )
            }

            MovementMode.POINT_TO_POINT_NAV,
            MovementMode.CUSTOM_ROUTE -> {
                val plannedRoute = startupPlan.plannedRoute ?: run {
                    failStartup(
                        message = "请先规划并确认路线",
                        cleanupStartedComponents = true,
                        stopService = true,
                    )
                    return
                }
                controller.onMovementModeChanged(startupPlan.movementMode)
                if (startupPlan.resumingRoute) {
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

    private fun failStartup(
        message: String,
        cleanupStartedComponents: Boolean,
        stopService: Boolean,
    ) {
        if (cleanupStartedComponents) {
            stopRandomWalkLoop()
            stopRouteMovementLoop()
            stopInjectionLoop()
            if (::injector.isInitialized) {
                injector.stop()
            }
            releaseWakeLock()
            setKeepRunning(false)
        }
        controller.onError(message)
        refreshNotification()
        if (stopService) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
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
                        simulationEngine?.updateTarget(
                            target = injectTarget,
                            movementMode = MovementMode.RANDOM_WALK,
                            environment = environmentFor(MovementMode.RANDOM_WALK),
                            speedMps = step.speedMps,
                            bearingDegrees = step.headingDeg,
                        )
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
                        simulationEngine?.updateTarget(
                            target = injectTarget,
                            movementMode = MovementMode.RANDOM_WALK,
                            environment = environmentFor(MovementMode.RANDOM_WALK),
                            speedMps = 0.0,
                            bearingDegrees = null,
                        )
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
        val injectTarget = buildInjectTarget(startTarget, calibrationMode)
        injector.updateTarget(injectTarget)
        simulationEngine?.updateTarget(
            target = injectTarget,
            movementMode = plannedRoute.mode.toMovementMode(),
            environment = EnvironmentProfile.MOVING_VEHICLE,
        )
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
        simulationEngine?.updateTarget(
            target = injectTarget,
            movementMode = controller.movementMode.value,
            environment = EnvironmentProfile.MOVING_VEHICLE,
            speedMps = tick.speedMps,
            bearingDegrees = tick.bearingDegrees,
        )
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

    private fun startInjectionLoop() {
        injectionLoopJob?.cancel()
        injectionLoopJob = serviceScope.launch {
            repeat(ENHANCED_STARTUP_BURST_COUNT) {
                if (!isActive) {
                    return@launch
                }
                injectNextSample()
                delay(ENHANCED_STARTUP_BURST_INTERVAL_MS)
            }
            while (isActive) {
                injectNextSample()
                delay(nextSteadyInjectionDelayMillis())
            }
        }
    }

    private fun injectNextSample() {
        val sample = simulationEngine?.nextSample() ?: run {
            controller.onError("缺少定位模拟引擎")
            return
        }
        injector.inject(sample)
        publishInjectorHealth()
    }

    private fun handleInjectorError(error: String) {
        if (::injector.isInitialized) {
            publishInjectorHealth()
        } else {
            controller.onError(error)
        }
        refreshNotification()
    }

    private fun publishInjectorHealth() {
        controller.onInjectorStatus(injector.aggregateStatus())
    }

    private fun nextSteadyInjectionDelayMillis(): Long {
        if (recoveryBurstPolicy.consumeFastInterval()) {
            return ENHANCED_STARTUP_BURST_INTERVAL_MS
        }
        if (System.currentTimeMillis() < thirdPartyCompatibilityUntilMillis) {
            return THIRD_PARTY_COMPATIBILITY_INTERVAL_MS
        }
        return when (controller.movementMode.value) {
            MovementMode.FIXED -> ENHANCED_UPDATE_INTERVAL_MS
            MovementMode.RANDOM_WALK -> 900L
            MovementMode.POINT_TO_POINT_NAV,
            MovementMode.CUSTOM_ROUTE -> 800L
        }
    }

    private fun stopInjectionLoop() {
        injectionLoopJob?.cancel()
        injectionLoopJob = null
        simulationEngine = null
        thirdPartyCompatibilityUntilMillis = 0L
        recoveryBurstPolicy.reset()
    }

    private fun handleInjectionReport(report: com.aurora.modifypositioning.model.InjectionReport) {
        controller.onInjected(report)
        recoveryBurstPolicy.onReport(report.recoveryStatus != null)
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
        simulationEngine?.stopMotion()
        if (controller.movementMode.value == MovementMode.RANDOM_WALK) {
            controller.onMovementPaused()
        } else if (
            controller.movementMode.value == MovementMode.POINT_TO_POINT_NAV ||
            controller.movementMode.value == MovementMode.CUSTOM_ROUTE
        ) {
            controller.onMovementPaused()
        }
        controller.onServicePaused()
        refreshNotification()
    }

    private fun handleStop(startId: Int) {
        val stopToken = lifecycleGate.beginStop()
        restorationJob?.cancel()
        restorationJob = serviceScope.launch {
            val cleanupStarted = lifecycleGate.runIfCurrent(stopToken) {
                controller.onRestorationStarted()
                stopRandomWalkLoop()
                stopRouteMovementLoop()
                stopInjectionLoop()
                injector.stop()
            }
            if (!cleanupStarted) {
                return@launch
            }

            val restorationState = awaitRestorationState()
            lifecycleGate.runIfCurrent(stopToken) {
                publishInjectorHealth()
                releaseWakeLock()
                controller.onServiceStopped(restorationState)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
        }
    }

    private suspend fun awaitRestorationState(): RestorationState {
        val deadlineMillis = System.currentTimeMillis() + RESTORATION_TIMEOUT_MS
        while (true) {
            val fused = FusedLocationDiagnosticsStore.state.value
            val state = evaluateRestorationState(
                fusedMockModeEnabled = fused.mockModeEnabled,
                fusedMockModePending = fused.mockModePending,
                timedOut = System.currentTimeMillis() >= deadlineMillis,
            )
            if (state != RestorationState.CLEANING) {
                return state
            }
            delay(RESTORATION_POLL_INTERVAL_MS)
        }
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
        stopInjectionLoop()
        if (!explicitStop) {
            injector.cleanup()
        }
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
                            MovementState.Walking -> getRunningContentText()
                            MovementState.ReachedBoundary -> "${getRunningContentText()} / 已到边界"
                            MovementState.Paused -> getPausedContentText()
                            else -> getRunningContentText()
                        }
                    }

                    MovementMode.POINT_TO_POINT_NAV,
                    MovementMode.CUSTOM_ROUTE -> {
                        when (controller.movementState.value) {
                            MovementState.ReachedDestination -> "${getRunningContentText()} / 已到终点"
                            MovementState.Paused -> getPausedContentText()
                            else -> getRunningContentText()
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
        val modeLabel = when (controller.movementMode.value) {
            MovementMode.FIXED -> "固定定位"
            MovementMode.RANDOM_WALK -> "随机步行"
            MovementMode.POINT_TO_POINT_NAV -> "两点导航"
            MovementMode.CUSTOM_ROUTE -> "指定路线"
        }
        return getString(
            R.string.notification_content_running,
            modeLabel,
            controller.target.value.name,
        )
    }

    private fun getPausedContentText(): String {
        return "已暂停移动, 保持当前位置"
    }

    private fun environmentFor(mode: MovementMode): EnvironmentProfile {
        return when (mode) {
            MovementMode.FIXED -> EnvironmentProfile.OUTDOOR_OPEN
            MovementMode.RANDOM_WALK -> EnvironmentProfile.OUTDOOR_OPEN
            MovementMode.POINT_TO_POINT_NAV,
            MovementMode.CUSTOM_ROUTE -> EnvironmentProfile.MOVING_VEHICLE
        }
    }

    private fun TravelMode.toMovementMode(): MovementMode {
        return when (this) {
            TravelMode.WALK,
            TravelMode.BIKE,
            TravelMode.CAR -> MovementMode.POINT_TO_POINT_NAV
        }
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
        private const val RESTORATION_TIMEOUT_MS = 3_000L
        private const val RESTORATION_POLL_INTERVAL_MS = 100L

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

internal class ServiceLifecycleGenerationGate {
    private var generation = 0L

    @Synchronized
    fun beginStart(): Long {
        generation += 1L
        return generation
    }

    @Synchronized
    fun beginStop(): Long {
        generation += 1L
        return generation
    }

    @Synchronized
    fun runIfCurrent(token: Long, action: () -> Unit): Boolean {
        if (token != generation) {
            return false
        }
        action()
        return true
    }
}

internal class RecoveryBurstPolicy(
    private val burstCount: Int,
) {
    private var recoveryActive = false
    private var remaining = 0

    @Synchronized
    fun onReport(isRecovery: Boolean) {
        if (isRecovery && !recoveryActive) {
            remaining = burstCount
        }
        recoveryActive = isRecovery
    }

    @Synchronized
    fun consumeFastInterval(): Boolean {
        if (remaining <= 0) {
            return false
        }
        remaining -= 1
        return true
    }

    @Synchronized
    fun reset() {
        recoveryActive = false
        remaining = 0
    }
}
