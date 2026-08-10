package com.aurora.modifypositioning.domain.calibration

import com.aurora.modifypositioning.model.CoordinateCalibrationMode
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class MainlandCoordinateCalibrator : CoordinateCalibrator {

    override fun toInjectCoordinate(
        lat: Double,
        lng: Double,
        mode: CoordinateCalibrationMode,
    ): Pair<Double, Double> {
        if (mode == CoordinateCalibrationMode.OFF || outOfChina(lat, lng)) {
            return lat to lng
        }
        return wgs84ToGcj02(lat, lng)
    }

    fun wgs84ToGcj02(lat: Double, lng: Double): Pair<Double, Double> {
        if (outOfChina(lat, lng)) {
            return lat to lng
        }
        var dLat = transformLat(lng - 105.0, lat - 35.0)
        var dLng = transformLng(lng - 105.0, lat - 35.0)
        val radLat = lat / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLng = (dLng * 180.0) / (A / sqrtMagic * kotlin.math.cos(radLat) * PI)
        val mgLat = lat + dLat
        val mgLng = lng + dLng
        return mgLat to mgLng
    }

    fun gcj02ToWgs84(lat: Double, lng: Double): Pair<Double, Double> {
        if (outOfChina(lat, lng)) {
            return lat to lng
        }
        var wgsLat = lat
        var wgsLng = lng
        repeat(6) {
            val converted = wgs84ToGcj02(wgsLat, wgsLng)
            wgsLat += lat - converted.first
            wgsLng += lng - converted.second
        }
        return wgsLat to wgsLng
    }

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLng(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }

    private fun outOfChina(lat: Double, lng: Double): Boolean {
        return lng !in 72.004..137.8347 || lat !in 0.8293..55.8271
    }

    private companion object {
        const val PI = 3.14159265358979324
        const val A = 6378245.0
        const val EE = 0.00669342162296594323
    }
}
