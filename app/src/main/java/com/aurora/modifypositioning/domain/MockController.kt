package com.aurora.modifypositioning.domain

import com.aurora.modifypositioning.location.CompositeInjectorStatus
import com.aurora.modifypositioning.location.InjectorState
import com.aurora.modifypositioning.model.DEFAULT_TARGET
import com.aurora.modifypositioning.model.InjectionReport
import com.aurora.modifypositioning.model.MockState
import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.MovementPoint
import com.aurora.modifypositioning.model.MovementState
import com.aurora.modifypositioning.model.PlannedRoute
import com.aurora.modifypositioning.model.RestorationState
import com.aurora.modifypositioning.model.RouteProgress
import com.aurora.modifypositioning.model.TargetLocation
import com.aurora.modifypositioning.model.TravelMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MockController(
    initialTarget: TargetLocation = DEFAULT_TARGET,
) {
    private var baseStatusText = "尚未启动"
    private var injectorFailureActive = false
    private var stateBeforeInjectorFailure: MockState? = null

    private val _state = MutableStateFlow<MockState>(MockState.Idle)
    val state: StateFlow<MockState> = _state.asStateFlow()

    private val _target = MutableStateFlow(initialTarget)
    val target: StateFlow<TargetLocation> = _target.asStateFlow()

    private val _statusText = MutableStateFlow(baseStatusText)
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _injectorStatus = MutableStateFlow(
        CompositeInjectorStatus(
            overallState = InjectorState.IDLE,
            activeCount = 0,
            failedCount = 0,
            statuses = emptyList(),
        ),
    )
    val injectorStatus: StateFlow<CompositeInjectorStatus> = _injectorStatus.asStateFlow()

    private val _injectorWarning = MutableStateFlow<String?>(null)
    val injectorWarning: StateFlow<String?> = _injectorWarning.asStateFlow()

    private val _lastInjection = MutableStateFlow<InjectionReport?>(null)
    val lastInjection: StateFlow<InjectionReport?> = _lastInjection.asStateFlow()

    private val _restorationState = MutableStateFlow(RestorationState.NOT_REQUESTED)
    val restorationState: StateFlow<RestorationState> = _restorationState.asStateFlow()

    private val _movementMode = MutableStateFlow(MovementMode.FIXED)
    val movementMode: StateFlow<MovementMode> = _movementMode.asStateFlow()

    private val _movementState = MutableStateFlow<MovementState>(MovementState.Idle)
    val movementState: StateFlow<MovementState> = _movementState.asStateFlow()

    private val _movementCenter = MutableStateFlow(initialTarget)
    val movementCenter: StateFlow<TargetLocation> = _movementCenter.asStateFlow()

    private val _movementTrace = MutableStateFlow<List<MovementPoint>>(emptyList())
    val movementTrace: StateFlow<List<MovementPoint>> = _movementTrace.asStateFlow()

    private val _movementCurrentSpeedMps = MutableStateFlow(0.0)
    val movementCurrentSpeedMps: StateFlow<Double> = _movementCurrentSpeedMps.asStateFlow()

    private val _movementDistanceFromCenterMeters = MutableStateFlow(0.0)
    val movementDistanceFromCenterMeters: StateFlow<Double> = _movementDistanceFromCenterMeters.asStateFlow()

    private val _travelMode = MutableStateFlow(TravelMode.WALK)
    val travelMode: StateFlow<TravelMode> = _travelMode.asStateFlow()

    private val _plannedRoute = MutableStateFlow<PlannedRoute?>(null)
    val plannedRoute: StateFlow<PlannedRoute?> = _plannedRoute.asStateFlow()

    private val _routeProgress = MutableStateFlow<RouteProgress?>(null)
    val routeProgress: StateFlow<RouteProgress?> = _routeProgress.asStateFlow()

    private val _routeError = MutableStateFlow<String?>(null)
    val routeError: StateFlow<String?> = _routeError.asStateFlow()

    fun updateTarget(targetLocation: TargetLocation) {
        _target.value = targetLocation
        if (_movementState.value == MovementState.Idle) {
            _movementCenter.value = targetLocation
        }
    }

    fun onServiceStarted(targetLocation: TargetLocation = _target.value) {
        resetRestorationState()
        _target.value = targetLocation
        _state.value = MockState.Running
        injectorFailureActive = false
        stateBeforeInjectorFailure = null
        _injectorWarning.value = null
        setBaseStatusText(when (_movementMode.value) {
            MovementMode.FIXED -> "正在模拟: ${targetLocation.name}"
            MovementMode.RANDOM_WALK -> "随机步行中"
            MovementMode.POINT_TO_POINT_NAV, MovementMode.CUSTOM_ROUTE -> "路线模拟中"
        })
    }

    fun onServicePaused() {
        _state.value = MockState.Paused
        setBaseStatusText("已暂停移动, 保持当前位置")
    }

    fun onRestorationStarted() {
        _restorationState.value = RestorationState.CLEANING
        setBaseStatusText("正在关闭模拟通道")
    }

    fun resetRestorationState() {
        _restorationState.value = RestorationState.NOT_REQUESTED
    }

    fun onServiceStopped(restorationState: RestorationState = RestorationState.RESTORED) {
        _state.value = MockState.Idle
        _restorationState.value = restorationState
        injectorFailureActive = false
        stateBeforeInjectorFailure = null
        _injectorWarning.value = null
        setBaseStatusText(
            if (restorationState == RestorationState.RESTORED) {
                "模拟通道已关闭"
            } else {
                "模拟通道关闭待确认"
            },
        )
        onMovementStopped()
    }

    fun onInjected(report: InjectionReport) {
        _lastInjection.value = report
    }

    fun onError(message: String) {
        injectorFailureActive = false
        stateBeforeInjectorFailure = null
        _state.value = MockState.Error(message)
        setBaseStatusText(message)
        _routeError.value = message
        if (_movementMode.value != MovementMode.FIXED) {
            _movementState.value = MovementState.Error(message)
        }
    }

    fun onInjectorStatus(status: CompositeInjectorStatus) {
        _injectorStatus.value = status
        when (status.overallState) {
            InjectorState.FAILED -> {
                if (stateBeforeInjectorFailure == null) {
                    stateBeforeInjectorFailure = _state.value.takeIf {
                        it == MockState.Running || it == MockState.Paused
                    }
                }
                injectorFailureActive = true
                _injectorWarning.value = null
                val failedNames = status.statuses
                    .filter { it.state == InjectorState.FAILED }
                    .joinToString { it.displayName }
                val message = if (failedNames.isBlank()) {
                    "所有定位通道均失败"
                } else {
                    "所有定位通道均失败: $failedNames"
                }
                _state.value = MockState.Error(message)
                _statusText.value = message
            }

            InjectorState.PARTIAL,
            InjectorState.DEGRADED,
            -> {
                restoreStateAfterInjectorFailure()
                val unavailableNames = status.statuses
                    .filter { it.state != InjectorState.RUNNING }
                    .joinToString { it.displayName }
                _injectorWarning.value = if (unavailableNames.isBlank()) {
                    "部分定位通道不可用"
                } else {
                    "部分定位通道不可用: $unavailableNames"
                }
                renderStatusText()
            }

            InjectorState.RUNNING -> {
                restoreStateAfterInjectorFailure()
                _injectorWarning.value = null
                renderStatusText()
            }

            InjectorState.IDLE,
            InjectorState.STARTING,
            InjectorState.STOPPED,
            -> {
                if (status.overallState != InjectorState.STARTING) {
                    _injectorWarning.value = null
                    renderStatusText()
                }
            }
        }
    }

    fun onMovementModeChanged(mode: MovementMode) {
        _movementMode.value = mode
        if (mode == MovementMode.FIXED) {
            onMovementStopped()
        }
    }

    fun onTravelModeChanged(mode: TravelMode) {
        _travelMode.value = mode
    }

    fun onRoutePlanned(route: PlannedRoute) {
        _plannedRoute.value = route
        _travelMode.value = route.mode
        _routeError.value = null
    }

    fun onRoutePlanningError(message: String) {
        _routeError.value = message
    }

    fun onMovementStarted(centerTarget: TargetLocation, startPoint: MovementPoint) {
        _movementMode.value = MovementMode.RANDOM_WALK
        _movementCenter.value = centerTarget
        _movementState.value = MovementState.Walking
        _movementTrace.value = listOf(startPoint)
        _movementCurrentSpeedMps.value = 0.0
        _movementDistanceFromCenterMeters.value = 0.0
        _routeProgress.value = null
        setBaseStatusText("随机步行中")
    }

    fun onRouteSimulationStarted(route: PlannedRoute, startPoint: MovementPoint, initialProgress: RouteProgress) {
        _plannedRoute.value = route
        _travelMode.value = route.mode
        _movementState.value = MovementState.Walking
        _movementTrace.value = listOf(startPoint)
        _movementCurrentSpeedMps.value = 0.0
        _movementDistanceFromCenterMeters.value = initialProgress.remainingMeters
        _routeProgress.value = initialProgress
        _routeError.value = null
        setBaseStatusText("路线模拟中")
    }

    fun onMovementProgress(point: MovementPoint, speedMps: Double, distanceFromCenterMeters: Double) {
        val updated = _movementTrace.value.toMutableList().apply {
            add(point)
            if (size > MAX_TRACE_POINTS) {
                removeAt(0)
            }
        }
        _movementTrace.value = updated
        _movementCurrentSpeedMps.value = speedMps
        _movementDistanceFromCenterMeters.value = distanceFromCenterMeters
        _movementState.value = MovementState.Walking
        setBaseStatusText("随机步行中")
    }

    fun onRouteSimulationProgress(point: MovementPoint, speedMps: Double, progress: RouteProgress) {
        val updated = _movementTrace.value.toMutableList().apply {
            add(point)
            if (size > MAX_TRACE_POINTS) {
                removeAt(0)
            }
        }
        _movementTrace.value = updated
        _movementCurrentSpeedMps.value = speedMps
        _movementDistanceFromCenterMeters.value = progress.remainingMeters
        _routeProgress.value = progress
        _movementState.value = MovementState.Walking
        setBaseStatusText("路线模拟中")
    }

    fun onMovementReachedBoundary(distanceFromCenterMeters: Double) {
        _movementState.value = MovementState.ReachedBoundary
        _movementCurrentSpeedMps.value = 0.0
        _movementDistanceFromCenterMeters.value = distanceFromCenterMeters
        setBaseStatusText("已到边界, 移动已停止(定位保持当前点)")
    }

    fun onRouteSimulationReachedDestination(point: MovementPoint, progress: RouteProgress) {
        val updated = _movementTrace.value.toMutableList().apply {
            add(point)
            if (size > MAX_TRACE_POINTS) {
                removeAt(0)
            }
        }
        _movementTrace.value = updated
        _movementState.value = MovementState.ReachedDestination
        _movementCurrentSpeedMps.value = 0.0
        _movementDistanceFromCenterMeters.value = 0.0
        _routeProgress.value = progress
        setBaseStatusText("已到终点, 保持当前位置")
    }

    fun onMovementPaused() {
        _movementState.value = MovementState.Paused
        _movementCurrentSpeedMps.value = 0.0
    }

    fun onMovementResumed() {
        _movementState.value = MovementState.Walking
        _movementCurrentSpeedMps.value = 0.0
        setBaseStatusText(if (_movementMode.value == MovementMode.RANDOM_WALK) {
            "随机步行中"
        } else {
            "路线模拟中"
        })
    }

    fun onMovementStopped() {
        _movementState.value = MovementState.Idle
        _movementTrace.value = emptyList()
        _movementCurrentSpeedMps.value = 0.0
        _movementDistanceFromCenterMeters.value = 0.0
        _routeProgress.value = null
    }

    private fun setBaseStatusText(text: String) {
        baseStatusText = text
        renderStatusText()
    }

    private fun restoreStateAfterInjectorFailure() {
        if (!injectorFailureActive) {
            return
        }
        stateBeforeInjectorFailure?.let { _state.value = it }
        injectorFailureActive = false
        stateBeforeInjectorFailure = null
    }

    private fun renderStatusText() {
        if (_restorationState.value == RestorationState.CLEANING) {
            _statusText.value = "正在关闭模拟通道"
            return
        }
        val warning = _injectorWarning.value
        _statusText.value = if (
            warning != null &&
            (_state.value == MockState.Running || _state.value == MockState.Paused)
        ) {
            "$baseStatusText | $warning"
        } else {
            baseStatusText
        }
    }

    companion object {
        private const val MAX_TRACE_POINTS = 300
    }
}

object MockControllerStore {
    val instance: MockController by lazy { MockController() }
}
