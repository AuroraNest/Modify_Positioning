package com.aurora.modifypositioning.travel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TravelScenarioEngineTest {
    @Test
    fun presetMovesThroughStopsInOrder() {
        val engine = TravelScenarioEngine(CityPreset.newYorkClassicDay)

        val start = engine.currentState()
        val next = engine.skipToNextStop()

        assertEquals("JFK Airport", start.currentStop.name)
        assertEquals("Times Square", next.currentStop.name)
        assertNotNull(start.segment)
    }
}
