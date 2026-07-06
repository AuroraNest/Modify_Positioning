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
    ): LocationSample {
        val point = driftModel.next(
            anchorLatitude = target.latitude,
            anchorLongitude = target.longitude,
            environment = environment,
            previous = previous,
            nowMillis = timestampMillis,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
        )
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
            bearingAccuracyDegrees = accuracy.bearingAccuracyDegrees,
            timestampMillis = timestampMillis,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
            movementMode = movementMode,
            environment = environment,
            sourceLabel = sourceLabel,
        )
    }
}
