package com.aurora.modifypositioning.travel

data class TravelScenarioState(
    val currentStop: TripStop,
    val nextStop: TripStop?,
    val segment: TripSegment?,
    val stopIndex: Int,
)

class TravelScenarioEngine(
    private val scenario: TripScenario,
) {
    private var stopIndex = 0

    fun currentState(): TravelScenarioState {
        val current = scenario.stops[stopIndex]
        val next = scenario.stops.getOrNull(stopIndex + 1)
        return TravelScenarioState(
            currentStop = current,
            nextStop = next,
            segment = next?.let { nextStop ->
                scenario.segments.firstOrNull {
                    it.fromStopId == current.id && it.toStopId == nextStop.id
                }
            },
            stopIndex = stopIndex,
        )
    }

    fun skipToNextStop(): TravelScenarioState {
        if (stopIndex < scenario.stops.lastIndex) {
            stopIndex += 1
        }
        return currentState()
    }
}
