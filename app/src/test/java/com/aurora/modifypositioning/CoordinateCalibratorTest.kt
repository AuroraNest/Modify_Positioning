package com.aurora.modifypositioning

import com.aurora.modifypositioning.domain.calibration.MainlandCoordinateCalibrator
import com.aurora.modifypositioning.model.CoordinateCalibrationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateCalibratorTest {

    private val calibrator = MainlandCoordinateCalibrator()

    @Test
    fun calibration_off_returnsSameCoordinate() {
        val result = calibrator.toInjectCoordinate(
            lat = 39.9087,
            lng = 116.3975,
            mode = CoordinateCalibrationMode.OFF,
        )

        assertEquals(39.9087, result.first, 0.000001)
        assertEquals(116.3975, result.second, 0.000001)
    }

    @Test
    fun calibration_mainland_adjustsChinaCoordinate() {
        val result = calibrator.toInjectCoordinate(
            lat = 39.9087,
            lng = 116.3975,
            mode = CoordinateCalibrationMode.MAINLAND_CHINA_COMPAT,
        )

        assertTrue(kotlin.math.abs(result.first - 39.9087) > 0.0001)
        assertTrue(kotlin.math.abs(result.second - 116.3975) > 0.0001)
    }

    @Test
    fun calibration_mainland_keepsOverseaCoordinate() {
        val result = calibrator.toInjectCoordinate(
            lat = 40.7580,
            lng = -73.9855,
            mode = CoordinateCalibrationMode.MAINLAND_CHINA_COMPAT,
        )

        assertEquals(40.7580, result.first, 0.000001)
        assertEquals(-73.9855, result.second, 0.000001)
    }

    @Test
    fun mapBoundary_roundTripsMainlandCoordinateWithoutAccumulatingOffset() {
        val wgs84 = 39.9087 to 116.3975
        val gcj02 = calibrator.wgs84ToGcj02(wgs84.first, wgs84.second)
        val restored = calibrator.gcj02ToWgs84(gcj02.first, gcj02.second)
        val renderedAgain = calibrator.wgs84ToGcj02(restored.first, restored.second)

        assertEquals(wgs84.first, restored.first, 0.00001)
        assertEquals(wgs84.second, restored.second, 0.00001)
        assertEquals(gcj02.first, renderedAgain.first, 0.00001)
        assertEquals(gcj02.second, renderedAgain.second, 0.00001)
    }

    @Test
    fun mapBoundary_keepsOverseaCoordinateUnchangedInBothDirections() {
        val wgs84 = 40.7580 to -73.9855

        assertEquals(wgs84, calibrator.wgs84ToGcj02(wgs84.first, wgs84.second))
        assertEquals(wgs84, calibrator.gcj02ToWgs84(wgs84.first, wgs84.second))
    }
}
