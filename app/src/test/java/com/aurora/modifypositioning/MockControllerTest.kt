package com.aurora.modifypositioning

import com.aurora.modifypositioning.domain.MockController
import com.aurora.modifypositioning.model.DEFAULT_TARGET
import com.aurora.modifypositioning.model.MockState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockControllerTest {

    @Test
    fun stateMachine_changesStateInOrder() {
        val controller = MockController()

        controller.onServiceStarted(DEFAULT_TARGET)
        assertTrue(controller.state.value is MockState.Running)

        controller.onServicePaused()
        assertTrue(controller.state.value is MockState.Paused)

        controller.onServiceStopped()
        assertTrue(controller.state.value is MockState.Idle)
    }

    @Test
    fun stateMachine_setsErrorAndMessage() {
        val controller = MockController()

        controller.onError("boom")

        val state = controller.state.value
        assertTrue(state is MockState.Error)
        assertEquals("boom", (state as MockState.Error).message)
        assertEquals("boom", controller.statusText.value)
    }
}
