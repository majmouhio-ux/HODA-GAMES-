package com.example

import com.example.data.model.AttachmentId
import com.example.data.model.GameModeId
import com.example.data.model.GraphicsQuality
import com.example.data.model.MapId
import com.example.data.model.TacticalId
import com.example.data.model.WeaponId
import com.example.data.model.WeatherType
import com.example.engine.BattleSimulation
import com.example.engine.TacticalAudioEngine
import com.example.engine.WeatherEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherAndCombatTest {

    @Test
    fun `weather engine cycles periodically and impacts visibility sound movement and spread`() {
        val engine = WeatherEngine(
            initialMap = MapId.DESERT_STORM,
            cycleIntervalSec = 10f,
            autoCycleEnabled = true
        )
        val initial = engine.update(0.1f)
        assertEquals(WeatherType.CLEAR_DAY, initial.currentWeather)
        assertEquals(1.0f, initial.visibilityMultiplier, 0.01f)

        // Advance past cycle interval + transition
        repeat(170) { engine.update(0.1f) }
        val transitioned = engine.update(0.1f)
        assertNotEquals(WeatherType.CLEAR_DAY, transitioned.currentWeather)
        assertTrue(transitioned.visibilityMultiplier < 0.5f)
        assertTrue(transitioned.soundPropagationMultiplier < 0.7f)
        assertTrue(transitioned.movementMultiplier < 0.95f)
        assertTrue(transitioned.spreadMultiplier > 1.1f)
    }

    @Test
    fun `manual weather force updates simulation telemetry and encountered weathers`() {
        val audio = TacticalAudioEngine(enabled = false)
        val sim = BattleSimulation(
            mapId = MapId.DESERT_STORM,
            modeId = GameModeId.BATTLE_ROYALE,
            weaponId = WeaponId.M4A1,
            attachmentId = AttachmentId.SUPPRESSOR,
            tacticalId = TacticalId.AIRSTRIKE,
            autoWeatherCycle = true,
            graphicsQuality = GraphicsQuality.BALANCED,
            audioEngine = audio,
            onHapticPulse = {}
        )
        sim.forceWeatherForTest(WeatherType.HEAVY_RAIN)
        repeat(80) { sim.step(0.05f) }
        val hud = sim.snapshotHud()
        assertEquals(WeatherType.HEAVY_RAIN, hud.weather.targetWeather)
        assertTrue(sim.weatherEngine.encounteredWeathers.contains(WeatherType.HEAVY_RAIN.name))
        assertTrue(hud.weather.rainIntensity > 0.5f)
        assertTrue(hud.weather.soundPropagationMultiplier < 0.7f)
        audio.release()
    }
}
