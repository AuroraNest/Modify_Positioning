package com.aurora.modifypositioning.simulation

import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.TargetLocation

class MovementSampleGenerator(
    private val driftModel: StationaryDriftModel = StationaryDriftModel(),
    private val accuracyModel: AccuracyModel = AccuracyModel(),
) {
    fun next(
        target: TargetLocation,
        movementMode: MovementMode,
        environment: EnvironmentProfile,
        previous: LocationSample?,
        previousAccuracyMeters: Float?,
        timestampMillis: Long,
        elapsedRealtimeNanos: Long,
        sourceLabel: String,
        motionSpeedMps: Double,
        motionBearingDegrees: Double?,
    ): LocationSample {
        val point = if (movementMode == MovementMode.FIXED) {
            driftModel.next(
                anchorLatitude = target.latitude,
                anchorLongitude = target.longitude,
                environment = environment,
                previous = previous,
                nowMillis = timestampMillis,
                elapsedRealtimeNanos = elapsedRealtimeNanos,
            ).copy(
                speedMps = 0f,
                bearingDegrees = null,
            )
        } else {
            val speed = motionSpeedMps.coerceAtLeast(0.0)
            DriftPoint(
                latitude = target.latitude,
                longitude = target.longitude,
                speedMps = speed.toFloat(),
                bearingDegrees = motionBearingDegrees
                    ?.takeIf { speed > 0.0 }
                    ?.let { (((it % 360.0) + 360.0) % 360.0).toFloat() },
            )
        }
        val accuracy = accuracyModel.nextAccuracy(
            environment = environment,
            movementMode = movementMode,
            previous = previousAccuracyMeters,
        )
        return LocationSample(
            latitude = point.latitude,
            longitude = point.longitude,
            altitudeMeters = null,
            accuracyMeters = accuracy.horizontalMeters,
            verticalAccuracyMeters = accuracy.verticalMeters,
            speedMps = point.speedMps,
            speedAccuracyMps = accuracy.speedAccuracyMps,
            bearingDegrees = point.bearingDegrees,
            bearingAccuracyDegrees = accuracy.bearingAccuracyDegrees.takeIf { point.bearingDegrees != null },
            timestampMillis = timestampMillis,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
            movementMode = movementMode,
            environment = environment,
            sourceLabel = sourceLabel,
        )
    }
}
