package com.aurora.modifypositioning

import android.location.Criteria
import android.location.LocationManager
import com.aurora.modifypositioning.location.LastKnownObservation
import com.aurora.modifypositioning.location.LastKnownVerification
import com.aurora.modifypositioning.location.LastKnownVerificationStatus
import com.aurora.modifypositioning.location.attemptLocationProviders
import com.aurora.modifypositioning.location.evaluateLastKnownVerification
import com.aurora.modifypositioning.location.providerAvailabilityState
import com.aurora.modifypositioning.location.providerRebuildSuccessTimeMillis
import com.aurora.modifypositioning.location.shouldAttemptProviderRebuild
import com.aurora.modifypositioning.location.testProviderProfile
import com.aurora.modifypositioning.location.verificationDistanceMeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidLocationInjectorTest {

    @Test
    fun providerAttempts_continueAfterEitherProviderFails() {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

        providers.forEach { failedProvider ->
            val attemptedProviders = mutableListOf<String>()
            val result = attemptLocationProviders { provider ->
                attemptedProviders += provider
                if (provider == failedProvider) {
                    error("denied")
                }
            }

            assertEquals(providers, attemptedProviders)
            assertEquals(providers - failedProvider, result.successfulProviders)
            assertEquals(setOf(failedProvider), result.failures.keys)
        }
    }

    @Test
    fun providerAvailability_keepsSingleHealthyProviderDegraded() {
        assertEquals(
            com.aurora.modifypositioning.location.InjectorState.DEGRADED,
            providerAvailabilityState(listOf(LocationManager.GPS_PROVIDER)),
        )
        assertEquals(
            com.aurora.modifypositioning.location.InjectorState.FAILED,
            providerAvailabilityState(emptyList()),
        )
    }

    @Test
    fun providerRebuildCooldown_startsAtFailedAttempt() {
        val attemptTimeMillis = 1_000L
        val result = attemptLocationProviders(listOf(LocationManager.NETWORK_PROVIDER)) {
            error("denied")
        }

        assertTrue(result.successfulProviders.isEmpty())
        assertFalse(shouldAttemptProviderRebuild(attemptTimeMillis, attemptTimeMillis + 1_999L))
        assertTrue(shouldAttemptProviderRebuild(attemptTimeMillis, attemptTimeMillis + 2_000L))
    }

    @Test
    fun providerRebuildSuccessTime_onlyAdvancesAfterSuccessfulSetup() {
        val previousSuccessTimeMillis = 500L

        assertEquals(
            previousSuccessTimeMillis,
            providerRebuildSuccessTimeMillis(
                previousSuccessTimeMillis = previousSuccessTimeMillis,
                attemptTimeMillis = 1_000L,
                successfulProviders = emptyList(),
            ),
        )
        assertEquals(
            1_000L,
            providerRebuildSuccessTimeMillis(
                previousSuccessTimeMillis = previousSuccessTimeMillis,
                attemptTimeMillis = 1_000L,
                successfulProviders = listOf(LocationManager.GPS_PROVIDER),
            ),
        )
    }

    @Test
    fun verification_requestsRecoveryForFreshNonMockFarLocation() {
        val result = evaluateLastKnownVerification(
            provider = LocationManager.GPS_PROVIDER,
            targetLatitude = 40.0,
            targetLongitude = -73.0,
            injectionTimeMillis = 10_000L,
            nowMillis = 10_100L,
            observation = LastKnownObservation(
                provider = LocationManager.GPS_PROVIDER,
                latitude = 41.0,
                longitude = -74.0,
                timeMillis = 10_000L,
                isMock = false,
            ),
        )

        assertEquals(LastKnownVerificationStatus.NonMockOverwrite, result.status)
        assertTrue(result.shouldRecover)
        assertTrue(result.distanceMeters!! > 75.0)
    }

    @Test
    fun verification_treatsNullAndStaleCacheAsSoftSignal() {
        val missing = evaluateLastKnownVerification(
            provider = LocationManager.GPS_PROVIDER,
            targetLatitude = 40.0,
            targetLongitude = -73.0,
            injectionTimeMillis = 10_000L,
            nowMillis = 10_100L,
            observation = null,
        )
        val stale = evaluateLastKnownVerification(
            provider = LocationManager.NETWORK_PROVIDER,
            targetLatitude = 40.0,
            targetLongitude = -73.0,
            injectionTimeMillis = 10_000L,
            nowMillis = 10_100L,
            observation = LastKnownObservation(
                provider = LocationManager.NETWORK_PROVIDER,
                latitude = 41.0,
                longitude = -74.0,
                timeMillis = 1_000L,
                isMock = false,
            ),
        )

        assertEquals(LastKnownVerificationStatus.MissingCache, missing.status)
        assertEquals(LastKnownVerificationStatus.StaleCache, stale.status)
        assertFalse(missing.shouldRecover)
        assertFalse(stale.shouldRecover)
    }

    @Test
    fun verificationDistanceMeters_usesRecoveryProviderDistance() {
        val verification = listOf(
            LastKnownVerification(
                provider = LocationManager.GPS_PROVIDER,
                status = LastKnownVerificationStatus.MockNearTarget,
                isMock = true,
                distanceMeters = 2.0,
                shouldRecover = false,
                recoveryReason = null,
            ),
            LastKnownVerification(
                provider = LocationManager.NETWORK_PROVIDER,
                status = LastKnownVerificationStatus.MockFarFromTarget,
                isMock = true,
                distanceMeters = 120.0,
                shouldRecover = true,
                recoveryReason = "network far",
            ),
        )

        assertEquals(
            120.0,
            verification.verificationDistanceMeters(LocationManager.NETWORK_PROVIDER)!!,
            0.0,
        )
    }

    @Test
    fun verificationDistanceMeters_usesMaxDistanceWithoutRecovery() {
        val verification = listOf(
            LastKnownVerification(
                provider = LocationManager.GPS_PROVIDER,
                status = LastKnownVerificationStatus.MockNearTarget,
                isMock = true,
                distanceMeters = 2.0,
                shouldRecover = false,
                recoveryReason = null,
            ),
            LastKnownVerification(
                provider = LocationManager.NETWORK_PROVIDER,
                status = LastKnownVerificationStatus.StaleCache,
                isMock = true,
                distanceMeters = 45.0,
                shouldRecover = false,
                recoveryReason = null,
            ),
        )

        assertEquals(
            45.0,
            verification.verificationDistanceMeters(null)!!,
            0.0,
        )
    }

    @Test
    fun providerProfilesDifferentiateGpsAndNetwork() {
        val gps = testProviderProfile(LocationManager.GPS_PROVIDER)
        val network = testProviderProfile(LocationManager.NETWORK_PROVIDER)

        assertTrue(gps.requiresSatellite)
        assertFalse(gps.requiresNetwork)
        assertEquals(Criteria.POWER_HIGH, gps.powerRequirement)
        assertEquals(Criteria.ACCURACY_FINE, gps.accuracy)

        assertTrue(network.requiresNetwork)
        assertTrue(network.requiresCell)
        assertFalse(network.requiresSatellite)
        assertEquals(Criteria.POWER_LOW, network.powerRequirement)
        assertEquals(Criteria.ACCURACY_COARSE, network.accuracy)
    }
}
