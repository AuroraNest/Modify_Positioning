package com.aurora.modifypositioning

import com.aurora.modifypositioning.domain.MockController
import com.aurora.modifypositioning.location.CompositeInjectorStatus
import com.aurora.modifypositioning.location.InjectorState
import com.aurora.modifypositioning.location.InjectorStatus
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

    @Test
    fun partialInjectorFailure_preservesOperationalStateAndShowsWarning() {
        val controller = MockController()
        controller.onServiceStarted(DEFAULT_TARGET)

        controller.onInjectorStatus(
            injectorStatus(
                InjectorStatus("system", "GPS / Network", InjectorState.RUNNING),
                InjectorStatus("fused", "Fused", InjectorState.FAILED),
            ),
        )

        assertEquals(MockState.Running, controller.state.value)
        assertTrue(controller.injectorWarning.value?.contains("Fused") == true)
        assertTrue(controller.statusText.value.contains("部分定位通道不可用"))

        controller.onServicePaused()
        assertEquals(MockState.Paused, controller.state.value)
        assertTrue(controller.statusText.value.contains("部分定位通道不可用"))
    }

    @Test
    fun allInjectorFailuresBecomeError_andHealthyRecoveryRestoresRunning() {
        val controller = MockController()
        controller.onServiceStarted(DEFAULT_TARGET)

        controller.onInjectorStatus(
            injectorStatus(
                InjectorStatus("system", "GPS / Network", InjectorState.FAILED),
                InjectorStatus("fused", "Fused", InjectorState.FAILED),
            ),
        )
        assertTrue(controller.state.value is MockState.Error)

        controller.onInjectorStatus(
            injectorStatus(
                InjectorStatus("system", "GPS / Network", InjectorState.RUNNING),
                InjectorStatus("fused", "Fused", InjectorState.RUNNING),
            ),
        )

        assertEquals(MockState.Running, controller.state.value)
        assertEquals(null, controller.injectorWarning.value)
        assertTrue(controller.statusText.value.contains(DEFAULT_TARGET.name))
    }

    private fun injectorStatus(vararg statuses: InjectorStatus): CompositeInjectorStatus {
        val values = statuses.toList()
        val running = values.count { it.state == InjectorState.RUNNING }
        val failed = values.count { it.state == InjectorState.FAILED }
        val overall = when {
            failed == values.size -> InjectorState.FAILED
            running == values.size -> InjectorState.RUNNING
            running > 0 && failed > 0 -> InjectorState.PARTIAL
            else -> InjectorState.DEGRADED
        }
        return CompositeInjectorStatus(
            overallState = overall,
            activeCount = running,
            failedCount = failed,
            statuses = values,
        )
    }
}
