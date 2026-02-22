package com.aurora.modifypositioning.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Query("SELECT * FROM favorite_locations ORDER BY updated_at DESC")
    fun observeFavorites(): Flow<List<FavoriteLocationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(entity: FavoriteLocationEntity)

    @Update
    suspend fun updateFavorite(entity: FavoriteLocationEntity)

    @Delete
    suspend fun deleteFavorite(entity: FavoriteLocationEntity)

    @Query("SELECT * FROM recent_selections ORDER BY selected_at DESC LIMIT 20")
    fun observeRecentSelections(): Flow<List<RecentSelectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecent(entity: RecentSelectionEntity)

    @Query("DELETE FROM recent_selections WHERE id NOT IN (SELECT id FROM recent_selections ORDER BY selected_at DESC LIMIT 20)")
    suspend fun trimRecentTo20()

    @Query("SELECT * FROM saved_routes ORDER BY updated_at DESC")
    fun observeSavedRoutes(): Flow<List<SavedRouteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedRoute(entity: SavedRouteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedRoutePoints(entities: List<SavedRoutePointEntity>)

    @Query("DELETE FROM saved_route_points WHERE route_id = :routeId")
    suspend fun deleteSavedRoutePoints(routeId: String)

    @Query("DELETE FROM saved_routes WHERE id = :routeId")
    suspend fun deleteSavedRoute(routeId: String)

    @Transaction
    @Query("SELECT * FROM saved_routes WHERE id = :routeId LIMIT 1")
    suspend fun findSavedRoute(routeId: String): SavedRouteWithPoints?

    @Query("SELECT * FROM recent_route_selections ORDER BY selected_at DESC LIMIT 20")
    fun observeRecentRoutes(): Flow<List<RecentRouteSelectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentRoute(entity: RecentRouteSelectionEntity)

    @Query(
        "DELETE FROM recent_route_selections WHERE route_id NOT IN " +
            "(SELECT route_id FROM recent_route_selections ORDER BY selected_at DESC LIMIT 20)",
    )
    suspend fun trimRecentRoutesTo20()
}
