package com.aurora.modifypositioning.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aurora.modifypositioning.model.CoordinateCalibrationMode
import com.aurora.modifypositioning.model.MapCameraSnapshot
import com.aurora.modifypositioning.model.MapProvider
import com.aurora.modifypositioning.model.MapProviderSettings
import com.aurora.modifypositioning.model.MovementPageTab
import com.aurora.modifypositioning.model.MovementMode
import com.aurora.modifypositioning.model.RandomWalkConfig
import com.aurora.modifypositioning.model.TargetLocation
import com.aurora.modifypositioning.model.TravelMode
import com.aurora.modifypositioning.model.resolveMapProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.mapPrefsDataStore by preferencesDataStore(name = "map_prefs")

class MapPreferencesStore internal constructor(
    private val dataStore: DataStore<Preferences>,
) {

    constructor(context: Context) : this(context.mapPrefsDataStore)

    val calibrationModeFlow: Flow<CoordinateCalibrationMode> = dataStore.data
        .map { prefs ->
            val raw = prefs[Keys.CALIBRATION_MODE] ?: CoordinateCalibrationMode.OFF.name
            runCatching { CoordinateCalibrationMode.valueOf(raw) }.getOrDefault(CoordinateCalibrationMode.OFF)
        }

    val movementModeFlow: Flow<MovementMode> = dataStore.data
        .map { prefs ->
            val raw = prefs[Keys.MOVEMENT_MODE] ?: MovementMode.FIXED.name
            runCatching { MovementMode.valueOf(raw) }.getOrDefault(MovementMode.FIXED)
        }

    val movementTabFlow: Flow<MovementPageTab> = dataStore.data
        .map { prefs ->
            val raw = prefs[Keys.MOVEMENT_TAB] ?: MovementPageTab.RANDOM_WALK.name
            runCatching { MovementPageTab.valueOf(raw) }.getOrDefault(MovementPageTab.RANDOM_WALK)
        }

    val defaultTravelModeFlow: Flow<TravelMode> = dataStore.data
        .map { prefs ->
            val raw = prefs[Keys.DEFAULT_TRAVEL_MODE] ?: TravelMode.WALK.name
            runCatching { TravelMode.valueOf(raw) }.getOrDefault(TravelMode.WALK)
        }

    val mapProviderSettingsFlow: Flow<MapProviderSettings> = dataStore.data
        .map { prefs ->
            MapProviderSettings(
                mapProvider = resolveMapProvider(prefs[Keys.MAP_PROVIDER]),
                amapAndroidKey = prefs[Keys.AMAP_ANDROID_KEY]?.trim().orEmpty(),
                amapPrivacyAccepted = prefs[Keys.AMAP_PRIVACY_ACCEPTED] ?: false,
            )
        }

    suspend fun setCalibrationMode(mode: CoordinateCalibrationMode) {
        dataStore.edit { prefs ->
            prefs[Keys.CALIBRATION_MODE] = mode.name
        }
    }

    suspend fun saveTarget(target: TargetLocation) {
        dataStore.edit { prefs ->
            prefs[Keys.TARGET_NAME] = target.name
            prefs[Keys.TARGET_LAT] = target.latitude
            prefs[Keys.TARGET_LNG] = target.longitude
        }
    }

    suspend fun getTargetOrNull(): TargetLocation? {
        val prefs = dataStore.data.first()
        val lat = prefs[Keys.TARGET_LAT] ?: return null
        val lng = prefs[Keys.TARGET_LNG] ?: return null
        val name = prefs[Keys.TARGET_NAME] ?: "Custom Target"
        return TargetLocation(name = name, latitude = lat, longitude = lng)
    }

    suspend fun saveCamera(camera: MapCameraSnapshot) {
        dataStore.edit { prefs ->
            prefs[Keys.CAMERA_LAT] = camera.lat
            prefs[Keys.CAMERA_LNG] = camera.lng
            prefs[Keys.CAMERA_ZOOM] = camera.zoom
        }
    }

    suspend fun getCameraOrNull(): MapCameraSnapshot? {
        val prefs = dataStore.data.first()
        val lat = prefs[Keys.CAMERA_LAT] ?: return null
        val lng = prefs[Keys.CAMERA_LNG] ?: return null
        val zoom = prefs[Keys.CAMERA_ZOOM] ?: 15f
        return MapCameraSnapshot(lat = lat, lng = lng, zoom = zoom)
    }

    suspend fun getCalibrationMode(): CoordinateCalibrationMode {
        return calibrationModeFlow.first()
    }

    suspend fun setMovementMode(mode: MovementMode) {
        dataStore.edit { prefs ->
            prefs[Keys.MOVEMENT_MODE] = mode.name
        }
    }

    suspend fun getMovementMode(): MovementMode {
        return movementModeFlow.first()
    }

    suspend fun setMovementTab(tab: MovementPageTab) {
        dataStore.edit { prefs ->
            prefs[Keys.MOVEMENT_TAB] = tab.name
        }
    }

    suspend fun getMovementTab(): MovementPageTab {
        return movementTabFlow.first()
    }

    suspend fun setDefaultTravelMode(mode: TravelMode) {
        dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_TRAVEL_MODE] = mode.name
        }
    }

    suspend fun getDefaultTravelMode(): TravelMode {
        return defaultTravelModeFlow.first()
    }

    suspend fun setDefaultSnapToRoad(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_SNAP_TO_ROAD] = enabled
        }
    }

    suspend fun getDefaultSnapToRoad(): Boolean {
        val prefs = dataStore.data.first()
        return prefs[Keys.DEFAULT_SNAP_TO_ROAD] ?: true
    }

    suspend fun saveRandomWalkConfig(config: RandomWalkConfig) {
        val normalized = config.normalized()
        dataStore.edit { prefs ->
            prefs[Keys.RANDOM_WALK_RADIUS_METERS] = normalized.radiusMeters
            prefs[Keys.RANDOM_WALK_MIN_SPEED_MPS] = normalized.minSpeedMps
            prefs[Keys.RANDOM_WALK_MAX_SPEED_MPS] = normalized.maxSpeedMps
            prefs[Keys.RANDOM_WALK_STEP_INTERVAL_MS] = normalized.stepIntervalMs
        }
    }

    suspend fun getRandomWalkConfig(): RandomWalkConfig {
        val prefs = dataStore.data.first()
        return RandomWalkConfig(
            radiusMeters = prefs[Keys.RANDOM_WALK_RADIUS_METERS] ?: 300.0,
            minSpeedMps = prefs[Keys.RANDOM_WALK_MIN_SPEED_MPS] ?: 0.8,
            maxSpeedMps = prefs[Keys.RANDOM_WALK_MAX_SPEED_MPS] ?: 1.6,
            stepIntervalMs = prefs[Keys.RANDOM_WALK_STEP_INTERVAL_MS] ?: 800L,
        ).normalized()
    }

    suspend fun setMapProvider(provider: MapProvider) {
        dataStore.edit { prefs ->
            prefs[Keys.MAP_PROVIDER] = provider.name
        }
    }

    suspend fun getMapProvider(): MapProvider {
        return mapProviderSettingsFlow.first().mapProvider
    }

    suspend fun setAmapAndroidKey(key: String) {
        val normalized = key.trim()
        dataStore.edit { prefs ->
            prefs[Keys.AMAP_ANDROID_KEY] = normalized
            prefs[Keys.MAP_PROVIDER] = MapProvider.OSM.name
        }
    }

    suspend fun getAmapAndroidKey(): String {
        return mapProviderSettingsFlow.first().amapAndroidKey
    }

    suspend fun setAmapPrivacyAccepted(accepted: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.AMAP_PRIVACY_ACCEPTED] = accepted
            if (!accepted) {
                prefs[Keys.MAP_PROVIDER] = MapProvider.OSM.name
            }
        }
    }

    suspend fun getAmapPrivacyAccepted(): Boolean {
        return mapProviderSettingsFlow.first().amapPrivacyAccepted
    }

    suspend fun getMapProviderSettings(): MapProviderSettings {
        return mapProviderSettingsFlow.first()
    }

    private object Keys {
        val TARGET_NAME: Preferences.Key<String> = stringPreferencesKey("target_name")
        val TARGET_LAT: Preferences.Key<Double> = doublePreferencesKey("target_lat")
        val TARGET_LNG: Preferences.Key<Double> = doublePreferencesKey("target_lng")
        val CALIBRATION_MODE: Preferences.Key<String> = stringPreferencesKey("calibration_mode")
        val CAMERA_LAT: Preferences.Key<Double> = doublePreferencesKey("camera_lat")
        val CAMERA_LNG: Preferences.Key<Double> = doublePreferencesKey("camera_lng")
        val CAMERA_ZOOM: Preferences.Key<Float> = floatPreferencesKey("camera_zoom")
        val MOVEMENT_MODE: Preferences.Key<String> = stringPreferencesKey("movement_mode")
        val MOVEMENT_TAB: Preferences.Key<String> = stringPreferencesKey("movement_tab")
        val DEFAULT_TRAVEL_MODE: Preferences.Key<String> = stringPreferencesKey("default_travel_mode")
        val DEFAULT_SNAP_TO_ROAD: Preferences.Key<Boolean> = booleanPreferencesKey("default_snap_to_road")
        val MAP_PROVIDER: Preferences.Key<String> = stringPreferencesKey("map_provider")
        val AMAP_ANDROID_KEY: Preferences.Key<String> = stringPreferencesKey("amap_android_key")
        val AMAP_PRIVACY_ACCEPTED: Preferences.Key<Boolean> = booleanPreferencesKey("amap_privacy_accepted")
        val RANDOM_WALK_RADIUS_METERS: Preferences.Key<Double> =
            doublePreferencesKey("random_walk_radius_meters")
        val RANDOM_WALK_MIN_SPEED_MPS: Preferences.Key<Double> =
            doublePreferencesKey("random_walk_min_speed_mps")
        val RANDOM_WALK_MAX_SPEED_MPS: Preferences.Key<Double> =
            doublePreferencesKey("random_walk_max_speed_mps")
        val RANDOM_WALK_STEP_INTERVAL_MS: Preferences.Key<Long> =
            longPreferencesKey("random_walk_step_interval_ms")
    }
}
