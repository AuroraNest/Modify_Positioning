package com.aurora.modifypositioning.util

import java.util.concurrent.atomic.AtomicInteger

object AppSessionMetrics {
    private val _searchRequests = AtomicInteger(0)

    val searchRequests: Int
        get() = _searchRequests.get()

    fun increaseSearchRequests() {
        _searchRequests.incrementAndGet()
    }

    fun reset() {
        _searchRequests.set(0)
    }
}
