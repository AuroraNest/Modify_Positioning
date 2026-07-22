package com.aurora.modifypositioning.model

enum class RestorationState {
    NOT_REQUESTED,
    CLEANING,
    RESTORED,
    ATTENTION_REQUIRED,
}

fun evaluateRestorationState(
    fusedMockModeEnabled: Boolean,
    fusedMockModePending: Boolean,
    timedOut: Boolean,
): RestorationState {
    return when {
        !fusedMockModeEnabled && !fusedMockModePending -> RestorationState.RESTORED
        timedOut -> RestorationState.ATTENTION_REQUIRED
        else -> RestorationState.CLEANING
    }
}
