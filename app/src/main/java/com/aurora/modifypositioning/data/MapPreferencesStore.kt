package com.aurora.modifypositioning.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aurora.modifypositioning.model.CoordinateCalibrationMode
import com.aurora.modifypositioning.model.MapCameraSnapshot
import com.aurora.modifypositioning.model.TargetLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.mapPrefsDataStore by preferencesDataStore(name = "map_prefs")

class MapPreferencesStore(
    private val context: Context,
) {

    val calibrationModeFlow: Flow<CoordinateCalibrationMode> = context.mapPrefsDataStore.data
        .map { prefs ->
            val raw = prefs[Keys.CALIBRATION_MODE] ?: CoordinateCalibrationMode.OFF.name
            runCatching { CoordinateCalibrationMode.valueOf(raw) }.getOrDefault(CoordinateCalibrationMode.OFF)
        }

    suspend fun setCalibrationMode(mode: CoordinateCalibrationMode) {
        context.mapPrefsDataStore.edit { prefs ->
            prefs[Keys.CALIBRATION_MODE] = mode.name
        }
    }

    suspend fun saveTarget(target: TargetLocation) {
        context.mapPrefsDataStore.edit { prefs ->
            prefs[Keys.TARGET_NAME] = target.name
            prefs[Keys.TARGET_LAT] = target.latitude
            prefs[Keys.TARGET_LNG] = target.longitude
        }
    }

    suspend fun getTargetOrNull(): TargetLocation? {
        val prefs = context.mapPrefsDataStore.data.first()
        val lat = prefs[Keys.TARGET_LAT] ?: return null
        val lng = prefs[Keys.TARGET_LNG] ?: return null
        val name = prefs[Keys.TARGET_NAME] ?: "Custom Target"
        return TargetLocation(name = name, latitude = lat, longitude = lng)
    }

    suspend fun saveCamera(camera: MapCameraSnapshot) {
        context.mapPrefsDataStore.edit { prefs ->
            prefs[Keys.CAMERA_LAT] = camera.lat
            prefs[Keys.CAMERA_LNG] = camera.lng
            prefs[Keys.CAMERA_ZOOM] = camera.zoom
        }
    }

    suspend fun getCameraOrNull(): MapCameraSnapshot? {
        val prefs = context.mapPrefsDataStore.data.first()
        val lat = prefs[Keys.CAMERA_LAT] ?: return null
        val lng = prefs[Keys.CAMERA_LNG] ?: return null
        val zoom = prefs[Keys.CAMERA_ZOOM] ?: 15f
        return MapCameraSnapshot(lat = lat, lng = lng, zoom = zoom)
    }

    suspend fun getCalibrationMode(): CoordinateCalibrationMode {
        return calibrationModeFlow.first()
    }

    private object Keys {
        val TARGET_NAME: Preferences.Key<String> = stringPreferencesKey("target_name")
        val TARGET_LAT: Preferences.Key<Double> = doublePreferencesKey("target_lat")
        val TARGET_LNG: Preferences.Key<Double> = doublePreferencesKey("target_lng")
        val CALIBRATION_MODE: Preferences.Key<String> = stringPreferencesKey("calibration_mode")
        val CAMERA_LAT: Preferences.Key<Double> = doublePreferencesKey("camera_lat")
        val CAMERA_LNG: Preferences.Key<Double> = doublePreferencesKey("camera_lng")
        val CAMERA_ZOOM: Preferences.Key<Float> = floatPreferencesKey("camera_zoom")
    }
}
