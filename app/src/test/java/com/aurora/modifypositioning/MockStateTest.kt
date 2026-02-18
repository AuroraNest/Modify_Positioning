package com.aurora.modifypositioning

import com.aurora.modifypositioning.model.MockState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockStateTest {

    @Test
    fun errorState_holdsMessage() {
        val state = MockState.Error("test error")
        assertEquals("test error", state.message)
    }

    @Test
    fun runningState_isSingletonObject() {
        assertTrue(MockState.Running === MockState.Running)
    }
}
