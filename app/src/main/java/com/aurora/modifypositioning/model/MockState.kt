package com.aurora.modifypositioning.model

sealed class MockState {
    data object Idle : MockState()
    data object Running : MockState()
    data object Paused : MockState()
    data class Error(val message: String) : MockState()
}
