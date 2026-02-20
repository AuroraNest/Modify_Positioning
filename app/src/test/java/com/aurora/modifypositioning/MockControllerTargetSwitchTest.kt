package com.aurora.modifypositioning

import com.aurora.modifypositioning.domain.MockController
import com.aurora.modifypositioning.model.SelectionSource
import com.aurora.modifypositioning.model.TargetLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class MockControllerTargetSwitchTest {

    @Test
    fun updateTarget_switchesToLatestSelection() {
        val controller = MockController()
        val first = TargetLocation("A", 31.2, 121.5)
        val second = TargetLocation("B", 39.9, 116.3)

        controller.updateTarget(first)
        controller.updateTarget(second)

        assertEquals("B", controller.target.value.name)
        assertEquals(39.9, controller.target.value.latitude, 0.0001)
        assertEquals(116.3, controller.target.value.longitude, 0.0001)
    }

    @Test
    fun selectionSource_enumIsStable() {
        assertEquals("MAP_DRAG", SelectionSource.MAP_DRAG.name)
        assertEquals("SEARCH", SelectionSource.SEARCH.name)
        assertEquals("FAVORITE", SelectionSource.FAVORITE.name)
        assertEquals("MANUAL_INPUT", SelectionSource.MANUAL_INPUT.name)
    }
}
