package com.aurora.modifypositioning.data

import com.aurora.modifypositioning.data.local.FavoriteLocationEntity
import com.aurora.modifypositioning.data.local.LocationDao
import com.aurora.modifypositioning.data.local.RecentSelectionEntity
import com.aurora.modifypositioning.model.FavoriteLocation
import com.aurora.modifypositioning.model.RecentSelection
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FavoriteLocationRepository(
    private val dao: LocationDao,
) {

    fun observeFavorites(): Flow<List<FavoriteLocation>> {
        return dao.observeFavorites().map { list -> list.map { it.toModel() } }
    }

    fun observeRecentSelections(): Flow<List<RecentSelection>> {
        return dao.observeRecentSelections().map { list -> list.map { it.toModel() } }
    }

    suspend fun addFavorite(name: String, lat: Double, lng: Double) {
        val now = System.currentTimeMillis()
        dao.upsertFavorite(
            FavoriteLocationEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                lat = lat,
                lng = lng,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun renameFavorite(id: String, name: String, lat: Double, lng: Double, createdAt: Long) {
        dao.upsertFavorite(
            FavoriteLocationEntity(
                id = id,
                name = name,
                lat = lat,
                lng = lng,
                createdAt = createdAt,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteFavorite(item: FavoriteLocation) {
        dao.deleteFavorite(
            FavoriteLocationEntity(
                id = item.id,
                name = item.name,
                lat = item.lat,
                lng = item.lng,
                createdAt = item.createdAt,
                updatedAt = item.updatedAt,
            ),
        )
    }

    suspend fun recordRecent(label: String, lat: Double, lng: Double) {
        val now = System.currentTimeMillis()
        dao.upsertRecent(
            RecentSelectionEntity(
                id = "${label}_${lat}_${lng}".hashCode().toString(),
                label = label,
                lat = lat,
                lng = lng,
                selectedAt = now,
            ),
        )
        dao.trimRecentTo20()
    }

    private fun FavoriteLocationEntity.toModel(): FavoriteLocation {
        return FavoriteLocation(
            id = id,
            name = name,
            lat = lat,
            lng = lng,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun RecentSelectionEntity.toModel(): RecentSelection {
        return RecentSelection(
            id = id,
            label = label,
            lat = lat,
            lng = lng,
            selectedAt = selectedAt,
        )
    }
}
