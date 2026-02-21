package com.aurora.modifypositioning.ui.movement

import com.aurora.modifypositioning.model.DEFAULT_TARGET
import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.MovementPoint
import com.aurora.modifypositioning.model.MovementState
import com.aurora.modifypositioning.model.RandomWalkConfig
import com.aurora.modifypositioning.model.TargetLocation

data class MovementUiState(
    val mode: MovementMode = MovementMode.FIXED,
    val movementState: MovementState = MovementState.Idle,
    val centerTarget: TargetLocation = DEFAULT_TARGET,
    val currentTarget: TargetLocation = DEFAULT_TARGET,
    val tracePoints: List<MovementPoint> = emptyList(),
    val currentSpeedMps: Double = 0.0,
    val distanceFromCenterMeters: Double = 0.0,
    val config: RandomWalkConfig = RandomWalkConfig(),
)
