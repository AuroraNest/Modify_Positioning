package com.aurora.modifypositioning.location

import android.util.Log
import com.aurora.modifypositioning.model.TargetLocation

class CompositeLocationInjector(
    private val injectors: List<LocationInjector>,
    private val onError: (String) -> Unit,
) : LocationInjector {

    override fun start(target: TargetLocation) {
        callEach("start") { start(target) }
    }

    override fun updateTarget(target: TargetLocation) {
        callEach("updateTarget") { updateTarget(target) }
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
