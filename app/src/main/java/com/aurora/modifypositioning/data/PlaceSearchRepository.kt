package com.aurora.modifypositioning.data

import com.aurora.modifypositioning.model.PlaceSuggestion

interface PlaceSearchRepository {
    suspend fun autocomplete(query: String): List<PlaceSuggestion>
}
