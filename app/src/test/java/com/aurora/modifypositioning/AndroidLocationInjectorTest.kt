package com.aurora.modifypositioning

import android.location.Criteria
import android.location.LocationManager
import com.aurora.modifypositioning.location.LastKnownObservation
import com.aurora.modifypositioning.location.LastKnownVerification
import com.aurora.modifypositioning.location.LastKnownVerificationStatus
import com.aurora.modifypositioning.location.evaluateLastKnownVerification
import com.aurora.modifypositioning.location.testProviderProfile
import com.aurora.modifypositioning.location.verificationDistanceMeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidLocationInjectorTest {

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
