package com.aurora.modifypositioning.simulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationSampleClockTest {
    @Test
    fun clockNeverMovesBackward() {
        val wallValues = ArrayDeque(listOf(10L, 9L, 9L, 12L))
        val elapsedValues = ArrayDeque(listOf(100L, 90L, 90L, 120L))
        val clock = LocationSampleClock(
            wallTimeProvider = { wallValues.removeFirst() },
            elapsedRealtimeNanosProvider = { elapsedValues.removeFirst() },
        )

        assertEquals(listOf(10L, 11L, 12L, 13L), List(4) { clock.nextWallTimeMillis() })
        assertEquals(listOf(100L, 101L, 102L, 120L), List(4) { clock.nextElapsedRealtimeNanos() })
    }

    @Test
    fun burstCallsStillIncrease() {
        val clock = LocationSampleClock(
            wallTimeProvider = { 1_000L },
            elapsedRealtimeNanosProvider = { 2_000L },
        )

        val times = List(5) { clock.nextWallTimeMillis() }
        val nanos = List(5) { clock.nextElapsedRealtimeNanos() }

        assertTrue(times.zipWithNext().all { it.second > it.first })
        assertTrue(nanos.zipWithNext().all { it.second > it.first })
    }
}
