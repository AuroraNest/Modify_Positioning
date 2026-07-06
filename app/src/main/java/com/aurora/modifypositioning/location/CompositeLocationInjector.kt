package com.aurora.modifypositioning.location

import android.util.Log
import com.aurora.modifypositioning.model.TargetLocation
import com.aurora.modifypositioning.simulation.LocationSample

class CompositeLocationInjector(
    private val injectors: List<LocationInjector>,
    private val onError: (String) -> Unit,
) : SampledLocationInjector {

    override fun start(target: TargetLocation) {
        callEach("start") { start(target) }
    }

    override fun updateTarget(target: TargetLocation) {
        callEach("updateTarget") { updateTarget(target) }
    }

    override fun inject(sample: LocationSample) {
        injectors.forEach { injector ->
            runCatching {
                if (injector is SampledLocationInjector) {
                    injector.inject(sample)
                } else {
                    injector.updateTarget(
                        TargetLocation(
                            name = sample.sourceLabel,
                            latitude = sample.latitude,
                            longitude = sample.longitude,
                        ),
                    )
                }
            }.onFailure { error ->
                val message = "${injector.javaClass.simpleName} inject 失败: ${error.message ?: "未知错误"}"
                runCatching { Log.w(TAG, message, error) }
                onError(message)
            }
        }
    }

    override fun pause() {
        callEach("pause") { pause() }
    }

    override fun stop() {
        callEach("stop") { stop() }
    }

    override fun cleanup() {
        callEach("cleanup") { cleanup() }
    }

    override fun status(): InjectorStatus {
        val aggregate = aggregateStatus()
        return InjectorStatus(
            id = "composite",
            displayName = "Composite injector",
            state = aggregate.overallState,
        )
    }

    fun aggregateStatus(): CompositeInjectorStatus {
        val statuses = injectors.map { it.status() }
        val runningCount = statuses.count { it.state == InjectorState.RUNNING }
        val failedCount = statuses.count { it.state == InjectorState.FAILED }
        val startingCount = statuses.count { it.state == InjectorState.STARTING }
        val stoppedCount = statuses.count { it.state == InjectorState.STOPPED }
        val overall = when {
            statuses.isEmpty() -> InjectorState.IDLE
            runningCount == statuses.size -> InjectorState.RUNNING
            runningCount > 0 && failedCount > 0 -> InjectorState.PARTIAL
            failedCount == statuses.size -> InjectorState.FAILED
            startingCount > 0 -> InjectorState.STARTING
            stoppedCount == statuses.size -> InjectorState.STOPPED
            runningCount > 0 -> InjectorState.DEGRADED
            else -> InjectorState.IDLE
        }
        return CompositeInjectorStatus(
            overallState = overall,
            activeCount = runningCount,
            failedCount = failedCount,
            statuses = statuses,
        )
    }

    private fun callEach(action: String, call: LocationInjector.() -> Unit) {
        injectors.forEach { injector ->
            runCatching { injector.call() }
                .onFailure { error ->
                    val message = "${injector.javaClass.simpleName} $action 失败: ${error.message ?: "未知错误"}"
                    runCatching { Log.w(TAG, message, error) }
                    onError(message)
                }
        }
    }

    private companion object {
        private const val TAG = "CompositeLocationInjector"
    }
}
