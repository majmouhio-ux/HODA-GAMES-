package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.GraphicsQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "battle_storm_settings")

data class GameSettings(
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val aimSensitivity: Float = 1.0f,
    val graphicsQuality: GraphicsQuality = GraphicsQuality.ULTRA,
    val autoWeatherCycle: Boolean = true
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val SOUND = booleanPreferencesKey("sound_enabled")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val SENSITIVITY = floatPreferencesKey("aim_sensitivity")
        val GRAPHICS = stringPreferencesKey("graphics_quality")
        val AUTO_WEATHER = booleanPreferencesKey("auto_weather_cycle")
    }

    val settingsFlow: Flow<GameSettings> = context.settingsDataStore.data.map { prefs ->
        val gfxName = prefs[Keys.GRAPHICS] ?: GraphicsQuality.ULTRA.name
        val gfx = enumValues<GraphicsQuality>().firstOrNull { it.name == gfxName }
            ?: GraphicsQuality.ULTRA
        GameSettings(
            soundEnabled = prefs[Keys.SOUND] ?: true,
            hapticsEnabled = prefs[Keys.HAPTICS] ?: true,
            aimSensitivity = (prefs[Keys.SENSITIVITY] ?: 1.0f).coerceIn(0.4f, 2.2f),
            graphicsQuality = gfx,
            autoWeatherCycle = prefs[Keys.AUTO_WEATHER] ?: true
        )
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SOUND] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.HAPTICS] = enabled }
    }

    suspend fun setAimSensitivity(value: Float) {
        context.settingsDataStore.edit { it[Keys.SENSITIVITY] = value.coerceIn(0.4f, 2.2f) }
    }

    suspend fun setGraphicsQuality(quality: GraphicsQuality) {
        context.settingsDataStore.edit { it[Keys.GRAPHICS] = quality.name }
    }

    suspend fun setAutoWeatherCycle(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_WEATHER] = enabled }
    }
}
