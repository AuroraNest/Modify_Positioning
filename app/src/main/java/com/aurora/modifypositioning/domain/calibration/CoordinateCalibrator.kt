package com.aurora.modifypositioning.domain.calibration

import com.aurora.modifypositioning.model.CoordinateCalibrationMode

interface CoordinateCalibrator {
    fun toInjectCoordinate(
        lat: Double,
        lng: Double,
        mode: CoordinateCalibrationMode,
    ): Pair<Double, Double>
}
