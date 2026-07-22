package com.aurora.modifypositioning

import com.aurora.modifypositioning.domain.MockController
import com.aurora.modifypositioning.model.InjectionReport
import com.aurora.modifypositioning.model.RestorationState
import com.aurora.modifypositioning.model.evaluateRestorationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RestorationStateTest {

    @Test
    fun evaluationWaitsThenRestoresOrRequestsAttention() {
        assertEquals(
            RestorationState.CLEANING,
            evaluateRestorationState(
                fusedMockModeEnabled = true,
                fusedMockModePending = true,
                timedOut = false,
            ),
        )
        assertEquals(
            RestorationState.RESTORED,
            evaluateRestorationState(
                fusedMockModeEnabled = false,
                fusedMockModePending = false,
                timedOut = false,
            ),
        )
        assertEquals(
            RestorationState.ATTENTION_REQUIRED,
            evaluateRestorationState(
                fusedMockModeEnabled = true,
                fusedMockModePending = false,
                timedOut = true,
            ),
        )
    }

    @Test
    fun controllerPublishesRestorationAndKeepsLastInjection() {
        val controller = MockController()
        controller.onInjected(
            InjectionReport(
                provider = "gps",
                latitude = 31.0,
                longitude = 121.0,
                accuracyMeters = 4f,
                timeMillis = 1_000L,
            ),
        )

        controller.onRestorationStarted()
        assertEquals(RestorationState.CLEANING, controller.restorationState.value)

        controller.onServiceStopped(RestorationState.ATTENTION_REQUIRED)
        assertEquals(RestorationState.ATTENTION_REQUIRED, controller.restorationState.value)
        assertEquals("模拟通道关闭待确认", controller.statusText.value)
        assertNotNull(controller.lastInjection.value)

        controller.onServiceStarted()
        assertEquals(RestorationState.NOT_REQUESTED, controller.restorationState.value)
    }
}
