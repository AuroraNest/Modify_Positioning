package com.aurora.modifypositioning.simulation

import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.TargetLocation

class LocationSimulationEngine(
    initialTarget: TargetLocation,
    initialMovementMode: MovementMode,
    initialEnvironment: EnvironmentProfile = EnvironmentProfile.OUTDOOR_OPEN,
    private val clock: LocationSampleClock = LocationSampleClock(),
    private val generator: MovementSampleGenerator = MovementSampleGenerator(),
) {
    private var target = initialTarget
    private var movementMode = initialMovementMode
    private var environment = initialEnvironment
    private var previousSample: LocationSample? = null
    private var previousAccuracyMeters: Float? = null
    private var motionSpeedMps = 0.0
    private var motionBearingDegrees: Double? = null

    @Synchronized
    fun updateTarget(
        target: TargetLocation,
        movementMode: MovementMode = this.movementMode,
        environment: EnvironmentProfile = this.environment,
        speedMps: Double = 0.0,
        bearingDegrees: Double? = null,
    ) {
        this.target = target
        this.movementMode = movementMode
        this.environment = environment
        motionSpeedMps = speedMps.coerceAtLeast(0.0)
        motionBearingDegrees = bearingDegrees.takeIf { motionSpeedMps > 0.0 }
    }

    @Synchronized
    fun stopMotion() {
        motionSpeedMps = 0.0
        motionBearingDegrees = null
    }

    @Synchronized
    fun nextSample(sourceLabel: String = "mock-service"): LocationSample {
        val sample = generator.next(
            target = target,
            movementMode = movementMode,
            environment = environment,
            previous = previousSample,
            previousAccuracyMeters = previousAccuracyMeters,
            timestampMillis = clock.nextWallTimeMillis(),
            elapsedRealtimeNanos = clock.nextElapsedRealtimeNanos(),
            sourceLabel = sourceLabel,
            motionSpeedMps = motionSpeedMps,
            motionBearingDegrees = motionBearingDegrees,
        )
        previousSample = sample
        previousAccuracyMeters = sample.accuracyMeters
        return sample
    }

    @Synchronized
    fun diagnostics(): SimulationDiagnostics {
        return SimulationDiagnostics(
            lastSample = previousSample,
            generatedAtMillis = clock.nextWallTimeMillis(),
        )
    }
}
