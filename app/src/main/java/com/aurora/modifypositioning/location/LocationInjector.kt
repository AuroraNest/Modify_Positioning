package com.aurora.modifypositioning.location

import com.aurora.modifypositioning.model.TargetLocation
import com.aurora.modifypositioning.simulation.LocationSample

interface LocationInjector {
    fun start(target: TargetLocation)
    fun updateTarget(target: TargetLocation)
    fun pause()
    fun stop()
    fun cleanup() {
        stop()
    }

    fun status(): InjectorStatus {
        return InjectorStatus(
            id = javaClass.simpleName,
            displayName = javaClass.simpleName,
            state = InjectorState.IDLE,
        )
    }
}

interface SampledLocationInjector : LocationInjector {
    fun inject(sample: LocationSample)
}

data class InjectorStatus(
    val id: String,
    val displayName: String,
    val state: InjectorState,
    val lastSuccessAtMillis: Long? = null,
    val lastFailureAtMillis: Long? = null,
    val lastErrorCode: InjectorErrorCode? = null,
    val lastErrorMessage: String? = null,
    val lastInjectedSample: LocationSample? = null,
)

data class CompositeInjectorStatus(
    val overallState: InjectorState,
    val activeCount: Int,
    val failedCount: Int,
    val statuses: List<InjectorStatus>,
)

enum class InjectorState {
    IDLE,
    STARTING,
    RUNNING,
    PARTIAL,
    DEGRADED,
    FAILED,
    STOPPED,
}

enum class InjectorErrorCode {
    MOCK_APP_NOT_SELECTED,
    MISSING_FINE_LOCATION,
    MISSING_COARSE_LOCATION,
    MISSING_NOTIFICATION_PERMISSION,
    LOCATION_SERVICE_DISABLED,
    GOOGLE_PLAY_SERVICES_UNAVAILABLE,
    FUSED_MOCK_MODE_FAILED,
    FUSED_SET_LOCATION_FAILED,
    TEST_PROVIDER_ADD_FAILED,
    TEST_PROVIDER_ENABLE_FAILED,
    TEST_PROVIDER_SET_LOCATION_FAILED,
    PROVIDER_NOT_READY,
    SERVICE_KILLED,
    BATTERY_RESTRICTED,
    UNKNOWN,
}
