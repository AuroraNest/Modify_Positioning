package com.aurora.modifypositioning

import com.aurora.modifypositioning.model.DEFAULT_TARGET
import com.aurora.modifypositioning.model.TargetLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetLocationTest {

    @Test
    fun defaultTarget_isTimesSquare() {
        assertEquals("Times Square, New York", DEFAULT_TARGET.name)
        assertEquals(40.7580, DEFAULT_TARGET.latitude, 0.0001)
        assertEquals(-73.9855, DEFAULT_TARGET.longitude, 0.0001)
    }

    @Test
    fun locationValidation_returnsTrueForValidCoordinates() {
        val location = TargetLocation("Demo", 34.0522, -118.2437)
        assertTrue(location.isValid())
    }

    @Test
    fun locationValidation_returnsFalseForOutOfRangeCoordinates() {
        val location = TargetLocation("Invalid", 99.0, -190.0)
        assertFalse(location.isValid())
    }
}
