package com.aurora.modifypositioning.simulation

import com.aurora.modifypositioning.model.MovementMode
import kotlin.random.Random

data class AccuracyReading(
    val horizontalMeters: Float,
    val verticalMeters: Float?,
    val speedAccuracyMps: Float?,
    val bearingAccuracyDegrees: Float?,
)

class AccuracyModel(
    private val random: Random = Random.Default,
) {
    fun nextAccuracy(
        environment: EnvironmentProfile,
        movementMode: MovementMode,
        previous: Float?,
    ): AccuracyReading {
        val range = environment.horizontalAccuracyRange()
        val target = random.nextDouble(range.first, range.second).toFloat()
        val horizontal = if (previous == null) {
            target
        } else {
            (previous * 0.75f + target * 0.25f).coerceIn(range.first.toFloat(), range.second.toFloat())
        }
        val vertical = (horizontal * environment.verticalMultiplier()).coerceAtLeast(5f)
        val moving = movementMode != MovementMode.FIXED
        return AccuracyReading(
            horizontalMeters = horizontal,
            verticalMeters = vertical,
            speedAccuracyMps = if (moving) 0.6f else 0.25f,
            bearingAccuracyDegrees = if (moving) 10f else 18f,
        )
    }

    private fun EnvironmentProfile.horizontalAccuracyRange(): Pair<Double, Double> {
        return when (this) {
            EnvironmentProfile.OUTDOOR_OPEN -> 3.0 to 8.0
            EnvironmentProfile.URBAN_CANYON -> 8.0 to 35.0
            EnvironmentProfile.INDOOR_MALL -> 15.0 to 60.0
            EnvironmentProfile.AIRPORT -> 15.0 to 50.0
            EnvironmentProfile.HOTEL -> 15.0 to 80.0
            EnvironmentProfile.RESTAURANT -> 10.0 to 45.0
            EnvironmentProfile.TRANSIT_STATION -> 20.0 to 80.0
            EnvironmentProfile.MOVING_VEHICLE -> 4.0 to 20.0
        }
    }

    private fun EnvironmentProfile.verticalMultiplier(): Float {
        return when (this) {
            EnvironmentProfile.OUTDOOR_OPEN -> 1.8f
            EnvironmentProfile.URBAN_CANYON -> 1.4f
            EnvironmentProfile.INDOOR_MALL -> 1.5f
            EnvironmentProfile.AIRPORT -> 1.4f
            EnvironmentProfile.HOTEL -> 1.6f
            EnvironmentProfile.RESTAURANT -> 1.5f
            EnvironmentProfile.TRANSIT_STATION -> 1.4f
            EnvironmentProfile.MOVING_VEHICLE -> 1.3f
        }
    }
}
