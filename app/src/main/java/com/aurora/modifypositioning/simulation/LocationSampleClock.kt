package com.aurora.modifypositioning.simulation

import android.os.SystemClock

class LocationSampleClock(
    private val wallTimeProvider: () -> Long = { System.currentTimeMillis() },
    private val elapsedRealtimeNanosProvider: () -> Long = { SystemClock.elapsedRealtimeNanos() },
) {
    private var lastWallTimeMillis = Long.MIN_VALUE
    private var lastElapsedRealtimeNanos = Long.MIN_VALUE

    @Synchronized
    fun nextWallTimeMillis(): Long {
        val now = wallTimeProvider()
        lastWallTimeMillis = if (now > lastWallTimeMillis) now else lastWallTimeMillis + 1L
        return lastWallTimeMillis
    }

    @Synchronized
    fun nextElapsedRealtimeNanos(): Long {
        val now = elapsedRealtimeNanosProvider()
        lastElapsedRealtimeNanos = if (now > lastElapsedRealtimeNanos) now else lastElapsedRealtimeNanos + 1L
        return lastElapsedRealtimeNanos
    }
}
