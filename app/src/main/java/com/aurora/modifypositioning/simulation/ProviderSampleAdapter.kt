package com.aurora.modifypositioning.simulation

import android.location.Location
import android.location.LocationManager
import android.os.Build
import kotlin.math.max

fun LocationSample.toGpsLocation(): Location {
    return toAndroidLocation(
        provider = LocationManager.GPS_PROVIDER,
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracyMeters,
        verticalAccuracy = verticalAccuracyMeters,
        speedAccuracy = speedAccuracyMps,
        bearingAccuracy = bearingAccuracyDegrees,
    )
}

fun LocationSample.toNetworkLocation(): Location {
    val offset = meterOffsetForProvider()
    return toAndroidLocation(
        provider = LocationManager.NETWORK_PROVIDER,
        latitude = latitude + offset.first,
        longitude = longitude + offset.second,
        accuracy = max(accuracyMeters * 2.5f, 12f),
        verticalAccuracy = verticalAccuracyMeters?.let { max(it * 1.8f, 20f) },
        speedAccuracy = speedAccuracyMps?.let { max(it, 1.2f) },
        bearingAccuracy = bearingAccuracyDegrees?.let { max(it, 16f) },
    )
}

private fun LocationSample.toAndroidLocation(
    provider: String,
    latitude: Double,
    longitude: Double,
    accuracy: Float,
    verticalAccuracy: Float?,
    speedAccuracy: Float?,
    bearingAccuracy: Float?,
): Location {
    return Location(provider).apply {
        this.latitude = latitude
        this.longitude = longitude
        this.accuracy = accuracy
        time = timestampMillis
        elapsedRealtimeNanos = this@toAndroidLocation.elapsedRealtimeNanos
        altitudeMeters?.let { altitude = it }
        speedMps?.let { speed = it }
        bearingDegrees?.let { bearing = it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            verticalAccuracy?.let { verticalAccuracyMeters = it }
            speedAccuracy?.let { speedAccuracyMetersPerSecond = it }
            bearingAccuracy?.let { bearingAccuracyDegrees = it }
        }
    }
}

private fun LocationSample.meterOffsetForProvider(): Pair<Double, Double> {
    val offsetMeters = when (environment) {
        EnvironmentProfile.OUTDOOR_OPEN -> 0.35
        EnvironmentProfile.URBAN_CANYON -> 0.8
        EnvironmentProfile.INDOOR_MALL -> 1.2
        EnvironmentProfile.AIRPORT -> 1.0
        EnvironmentProfile.HOTEL -> 1.0
        EnvironmentProfile.RESTAURANT -> 0.7
        EnvironmentProfile.TRANSIT_STATION -> 1.1
        EnvironmentProfile.MOVING_VEHICLE -> 0.5
    }
    val point = offsetToCoordinate(latitude, longitude, offsetMeters, -offsetMeters / 2.0)
    return (point.first - latitude) to (point.second - longitude)
}
