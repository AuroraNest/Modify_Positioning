package com.aurora.modifypositioning.domain

import com.aurora.modifypositioning.model.DEFAULT_TARGET
import com.aurora.modifypositioning.model.MockState
import com.aurora.modifypositioning.model.TargetLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MockController(
    initialTarget: TargetLocation = DEFAULT_TARGET,
) {
    private val _state = MutableStateFlow<MockState>(MockState.Idle)
    val state: StateFlow<MockState> = _state.asStateFlow()

    private val _target = MutableStateFlow(initialTarget)
    val target: StateFlow<TargetLocation> = _target.asStateFlow()

    private val _statusText = MutableStateFlow("尚未启动")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    fun updateTarget(targetLocation: TargetLocation) {
        _target.value = targetLocation
    }

    fun onServiceStarted(targetLocation: TargetLocation = _target.value) {
        _target.value = targetLocation
        _state.value = MockState.Running
        _statusText.value = "正在模拟: ${targetLocation.name}"
    }

    fun onServicePaused() {
        _state.value = MockState.Paused
        _statusText.value = "已暂停模拟"
    }

    fun onServiceStopped() {
        _state.value = MockState.Idle
        _statusText.value = "模拟已停止"
    }

    fun onError(message: String) {
        _state.value = MockState.Error(message)
        _statusText.value = message
    }
}

object MockControllerStore {
    val instance: MockController by lazy { MockController() }
}
