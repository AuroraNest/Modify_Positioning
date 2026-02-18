package com.aurora.modifypositioning.location

import com.aurora.modifypositioning.model.TargetLocation

interface LocationInjector {
    fun start(target: TargetLocation)
    fun pause()
    fun stop()
}
