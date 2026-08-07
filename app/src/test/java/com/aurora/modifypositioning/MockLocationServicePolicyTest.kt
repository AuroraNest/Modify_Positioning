package com.aurora.modifypositioning

import com.aurora.modifypositioning.service.RecoveryBurstPolicy
import com.aurora.modifypositioning.service.ServiceLifecycleGenerationGate
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
}
