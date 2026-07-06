package com.aurora.modifypositioning.travel

import com.aurora.modifypositioning.simulation.EnvironmentProfile
import com.aurora.modifypositioning.simulation.TravelMotionType

object CityPreset {
    val losAngelesClassicDay = TripScenario(
        id = "la-classic-day",
        title = "Los Angeles Classic Day",
        cityName = "Los Angeles",
        timezoneId = "America/Los_Angeles",
        stops = listOf(
            TripStop("lax", "LAX Airport", 33.9425, -118.4081, EnvironmentProfile.AIRPORT, 20, 40),
            TripStop("santa-monica", "Santa Monica Pier", 34.0100, -118.4960, EnvironmentProfile.OUTDOOR_OPEN, 35, 70),
            TripStop("beverly-hills", "Beverly Hills", 34.0736, -118.4004, EnvironmentProfile.URBAN_CANYON, 30, 60),
            TripStop("the-grove", "The Grove", 34.0720, -118.3570, EnvironmentProfile.RESTAURANT, 35, 75),
            TripStop("griffith", "Griffith Observatory", 34.1184, -118.3004, EnvironmentProfile.OUTDOOR_OPEN, 30, 60),
            TripStop("hotel", "Hotel Area", 34.0980, -118.3267, EnvironmentProfile.HOTEL, 120, 240),
        ),
        segments = listOf(
            TripSegment("lax", "santa-monica", TravelMotionType.DRIVING, 25, 55),
            TripSegment("santa-monica", "beverly-hills", TravelMotionType.DRIVING, 25, 45),
            TripSegment("beverly-hills", "the-grove", TravelMotionType.DRIVING, 15, 35),
            TripSegment("the-grove", "griffith", TravelMotionType.DRIVING, 20, 45),
            TripSegment("griffith", "hotel", TravelMotionType.DRIVING, 20, 40),
        ),
    )

    val newYorkClassicDay = TripScenario(
        id = "nyc-classic-day",
        title = "New York Classic Day",
        cityName = "New York",
        timezoneId = "America/New_York",
        stops = listOf(
            TripStop("jfk", "JFK Airport", 40.6413, -73.7781, EnvironmentProfile.AIRPORT, 20, 45),
            TripStop("times-square", "Times Square", 40.7580, -73.9855, EnvironmentProfile.URBAN_CANYON, 30, 60),
            TripStop("bryant-park", "Bryant Park", 40.7536, -73.9832, EnvironmentProfile.OUTDOOR_OPEN, 20, 45),
            TripStop("central-park", "Central Park", 40.7829, -73.9654, EnvironmentProfile.OUTDOOR_OPEN, 40, 80),
            TripStop("brooklyn-bridge", "Brooklyn Bridge", 40.7061, -73.9969, EnvironmentProfile.URBAN_CANYON, 30, 60),
            TripStop("hotel", "Hotel Area", 40.7567, -73.9903, EnvironmentProfile.HOTEL, 120, 240),
        ),
        segments = listOf(
            TripSegment("jfk", "times-square", TravelMotionType.DRIVING, 40, 75),
            TripSegment("times-square", "bryant-park", TravelMotionType.WALKING, 10, 20),
            TripSegment("bryant-park", "central-park", TravelMotionType.TRANSIT, 15, 30),
            TripSegment("central-park", "brooklyn-bridge", TravelMotionType.TRANSIT, 25, 45),
            TripSegment("brooklyn-bridge", "hotel", TravelMotionType.DRIVING, 20, 45),
        ),
    )

    val all: List<TripScenario> = listOf(losAngelesClassicDay, newYorkClassicDay)
}
