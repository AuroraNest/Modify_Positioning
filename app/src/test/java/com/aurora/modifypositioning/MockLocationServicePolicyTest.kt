package com.aurora.modifypositioning

import com.aurora.modifypositioning.model.MockState
import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.TargetLocation
import com.aurora.modifypositioning.service.RecoveryBurstPolicy
import com.aurora.modifypositioning.service.RestabilizeDispatch
import com.aurora.modifypositioning.service.ServiceLifecycleGenerationGate
import com.aurora.modifypositioning.service.isLargeFixedTargetJump
import com.aurora.modifypositioning.service.resolveRestabilizeDispatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockLocationServicePolicyTest {

    @Test
    fun lifecycleGate_startInvalidatesOlderStopCleanup() {
        val gate = ServiceLifecycleGenerationGate()
        val stopToken = gate.beginStop()
        var cleanupRan = false

        gate.beginStart()
        val accepted = gate.runIfCurrent(stopToken) {
            cleanupRan = true
        }

        assertFalse(accepted)
        assertFalse(cleanupRan)
    }

    @Test
    fun recoveryBurst_isEdgeTriggeredUntilNormalReport() {
        val policy = RecoveryBurstPolicy(burstCount = 3)

        policy.onReport(isRecovery = true)
        repeat(3) { assertTrue(policy.consumeFastInterval()) }
        assertFalse(policy.consumeFastInterval())

        policy.onReport(isRecovery = true)
        assertFalse(policy.consumeFastInterval())

        policy.onReport(isRecovery = false)
        policy.onReport(isRecovery = true)
        assertTrue(policy.consumeFastInterval())
    }

    @Test
    fun recoveryBurst_forceStartsBoundedBurst() {
        val policy = RecoveryBurstPolicy(burstCount = 3)

        policy.force(count = 2)

        assertTrue(policy.consumeFastInterval())
        assertTrue(policy.consumeFastInterval())
        assertFalse(policy.consumeFastInterval())
    }

    @Test
    fun fixedTargetJump_usesTwoKilometerThreshold() {
        val start = TargetLocation("start", 30.0, 120.0)

        assertFalse(isLargeFixedTargetJump(start, start.copy(latitude = 30.005)))
        assertTrue(isLargeFixedTargetJump(start, start.copy(latitude = 30.03)))
    }

    @Test
    fun restabilizeDispatch_gatesMissingSessionOnPersistedModeAndKeepsActiveRules() {
        assertEquals(
            RestabilizeDispatch.START,
            resolveRestabilizeDispatch(
                movementMode = MovementMode.FIXED,
                persistedMovementMode = MovementMode.FIXED,
                state = MockState.Idle,
                hasSimulationSession = false,
            ),
        )
        assertEquals(
            RestabilizeDispatch.IGNORE,
            resolveRestabilizeDispatch(
                movementMode = MovementMode.FIXED,
                persistedMovementMode = MovementMode.RANDOM_WALK,
                state = MockState.Idle,
                hasSimulationSession = false,
            ),
        )
        assertEquals(
            RestabilizeDispatch.RESTABILIZE,
            resolveRestabilizeDispatch(
                movementMode = MovementMode.FIXED,
                persistedMovementMode = MovementMode.RANDOM_WALK,
                state = MockState.Running,
                hasSimulationSession = true,
            ),
        )
        assertEquals(
            RestabilizeDispatch.IGNORE,
            resolveRestabilizeDispatch(
                movementMode = MovementMode.RANDOM_WALK,
                persistedMovementMode = MovementMode.FIXED,
                state = MockState.Running,
                hasSimulationSession = false,
            ),
        )
    }
}
