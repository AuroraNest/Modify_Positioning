package com.aurora.modifypositioning.simulation

import com.aurora.modifypositioning.model.MovementMode
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class AccuracyModelTest {
    @Test
    fun outdoorAccuracyStaysInRangeAndSmooths() {
        val model = AccuracyModel(Random(7))
        val first = model.nextAccuracy(EnvironmentProfile.OUTDOOR_OPEN, MovementMode.FIXED, null)
        val second = model.nextAccuracy(EnvironmentProfile.OUTDOOR_OPEN, MovementMode.FIXED, first.horizontalMeters)

        assertTrue(first.horizontalMeters in 3f..8f)
        assertTrue(second.horizontalMeters in 3f..8f)
        assertTrue(abs(second.horizontalMeters - first.horizontalMeters) <= 2f)
    }

    @Test
    fun indoorAccuracyIsBroaderThanOutdoor() {
        val model = AccuracyModel(Random(9))
        val outdoor = model.nextAccuracy(EnvironmentProfile.OUTDOOR_OPEN, MovementMode.FIXED, null)
        val indoor = model.nextAccuracy(EnvironmentProfile.INDOOR_MALL, MovementMode.FIXED, null)

        assertTrue(indoor.horizontalMeters >= 15f)
        assertTrue(outdoor.horizontalMeters <= 8f)
    }
}
