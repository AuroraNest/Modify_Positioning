package com.aurora.modifypositioning

import com.aurora.modifypositioning.ui.onboardingRequiredStepsReady
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingReadinessTest {

    @Test
    fun recommendedSettingsDoNotBlockRequiredReadiness() {
        assertTrue(
            onboardingRequiredStepsReady(
                isMockAppSelected = true,
                isSystemLocationEnabled = true,
                missingLocationPermissions = emptyList(),
            ),
        )
    }

    @Test
    fun eachRequiredStepBlocksReadiness() {
        assertFalse(onboardingRequiredStepsReady(false, true, emptyList()))
        assertFalse(onboardingRequiredStepsReady(true, false, emptyList()))
        assertFalse(onboardingRequiredStepsReady(true, true, listOf("location")))
    }
}
