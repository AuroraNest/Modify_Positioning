package com.aurora.modifypositioning.fixed

import com.aurora.modifypositioning.model.FixedPointReadinessState
import com.aurora.modifypositioning.model.TargetLocation

data class FixedPointSessionSnapshot(
    val generation: Long,
    val phase: FixedPointReadinessState,
    val rawTarget: TargetLocation?,
    val injectedTarget: TargetLocation?,
    val startedAtElapsedRealtimeMillis: Long?,
    val stableSinceElapsedRealtimeMillis: Long?,
)

object FixedPointSessionStore {
    private var generation = 0L
    private var phase = FixedPointReadinessState.IDLE
    private var rawTarget: TargetLocation? = null
    private var injectedTarget: TargetLocation? = null
    private var startedAtElapsedRealtimeMillis: Long? = null
    private var stableSinceElapsedRealtimeMillis: Long? = null

    @Synchronized
    fun start(rawTarget: TargetLocation, injectedTarget: TargetLocation, nowElapsedRealtimeMillis: Long) {
        generation += 1L
        phase = FixedPointReadinessState.STARTING
        this.rawTarget = rawTarget
        this.injectedTarget = injectedTarget
        startedAtElapsedRealtimeMillis = nowElapsedRealtimeMillis
        clearReadiness()
    }

    @Synchronized
    fun onBursting() {
        if (phase != FixedPointReadinessState.IDLE && phase != FixedPointReadinessState.STOPPED) {
            phase = FixedPointReadinessState.BURSTING
        }
    }

    @Synchronized
    fun onSettling() {
        if (phase != FixedPointReadinessState.IDLE && phase != FixedPointReadinessState.STOPPED) {
            phase = FixedPointReadinessState.SETTLING
        }
    }

    @Synchronized
    fun restart(rawTarget: TargetLocation, injectedTarget: TargetLocation, nowElapsedRealtimeMillis: Long) {
        generation += 1L
        phase = FixedPointReadinessState.SETTLING
        this.rawTarget = rawTarget
        this.injectedTarget = injectedTarget
        startedAtElapsedRealtimeMillis = nowElapsedRealtimeMillis
        clearReadiness()
    }

    @Synchronized
    fun applyEvaluation(
        expectedGeneration: Long,
        stableSinceElapsedRealtimeMillis: Long?,
    ) {
        if (generation == expectedGeneration) {
            this.stableSinceElapsedRealtimeMillis = stableSinceElapsedRealtimeMillis
        }
    }

    @Synchronized
    fun stop() {
        generation += 1L
        phase = FixedPointReadinessState.STOPPED
        clearReadiness()
    }

    @Synchronized
    fun resetToIdle() {
        generation += 1L
        phase = FixedPointReadinessState.IDLE
        rawTarget = null
        injectedTarget = null
        startedAtElapsedRealtimeMillis = null
        clearReadiness()
    }

    @Synchronized
    fun snapshot() = FixedPointSessionSnapshot(
        generation = generation,
        phase = phase,
        rawTarget = rawTarget,
        injectedTarget = injectedTarget,
        startedAtElapsedRealtimeMillis = startedAtElapsedRealtimeMillis,
        stableSinceElapsedRealtimeMillis = stableSinceElapsedRealtimeMillis,
    )

    private fun clearReadiness() {
        stableSinceElapsedRealtimeMillis = null
    }
}
