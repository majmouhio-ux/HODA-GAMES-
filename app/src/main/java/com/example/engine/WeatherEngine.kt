package com.example.engine

import com.example.data.model.MapId
import com.example.data.model.WeatherType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class WeatherSnapshot(
    val currentWeather: WeatherType = WeatherType.CLEAR_DAY,
    val targetWeather: WeatherType = WeatherType.CLEAR_DAY,
    val incomingAlertWeather: WeatherType? = null,
    val nextChangeCountdownSec: Int = 30,
    val transitionProgress: Float = 1f,
    val fogDensity: Float = WeatherType.CLEAR_DAY.fogDensity,
    val fogR: Float = WeatherType.CLEAR_DAY.fogColorRgb[0],
    val fogG: Float = WeatherType.CLEAR_DAY.fogColorRgb[1],
    val fogB: Float = WeatherType.CLEAR_DAY.fogColorRgb[2],
    val skyTopR: Float = WeatherType.CLEAR_DAY.skyTopRgb[0],
    val skyTopG: Float = WeatherType.CLEAR_DAY.skyTopRgb[1],
    val skyTopB: Float = WeatherType.CLEAR_DAY.skyTopRgb[2],
    val skyHorR: Float = WeatherType.CLEAR_DAY.skyHorizonRgb[0],
    val skyHorG: Float = WeatherType.CLEAR_DAY.skyHorizonRgb[1],
    val skyHorB: Float = WeatherType.CLEAR_DAY.skyHorizonRgb[2],
    val sunR: Float = WeatherType.CLEAR_DAY.sunColorRgb[0],
    val sunG: Float = WeatherType.CLEAR_DAY.sunColorRgb[1],
    val sunB: Float = WeatherType.CLEAR_DAY.sunColorRgb[2],
    val sunDirX: Float = 0.45f,
    val sunDirY: Float = 0.85f,
    val sunDirZ: Float = 0.25f,
    val ambientStrength: Float = WeatherType.CLEAR_DAY.ambientStrength,
    val rainIntensity: Float = 0f,
    val sandIntensity: Float = 0f,
    val lightningFlash: Float = 0f,
    val wetnessSpecular: Float = 0.1f,
    val visibilityMultiplier: Float = 1f,
    val soundPropagationMultiplier: Float = 1f,
    val movementMultiplier: Float = 1f,
    val spreadMultiplier: Float = 1f,
    val windX: Float = 0f,
    val windZ: Float = 0f
)

class WeatherEngine(
    initialMap: MapId,
    private val cycleIntervalSec: Float = 28f,
    private val autoCycleEnabled: Boolean = true
) {
    private val rotationOrder: List<WeatherType> = when (initialMap) {
        MapId.DESERT_STORM -> listOf(
            WeatherType.CLEAR_DAY,
            WeatherType.SANDSTORM,
            WeatherType.GOLDEN_DUSK,
            WeatherType.NIGHT_STORM,
            WeatherType.HEAVY_RAIN,
            WeatherType.DENSE_FOG
        )
        MapId.URBAN_SIEGE -> listOf(
            WeatherType.DENSE_FOG,
            WeatherType.HEAVY_RAIN,
            WeatherType.GOLDEN_DUSK,
            WeatherType.NIGHT_STORM,
            WeatherType.CLEAR_DAY,
            WeatherType.SANDSTORM
        )
        MapId.ARCTIC_OUTPOST -> listOf(
            WeatherType.HEAVY_RAIN,
            WeatherType.DENSE_FOG,
            WeatherType.NIGHT_STORM,
            WeatherType.CLEAR_DAY,
            WeatherType.GOLDEN_DUSK,
            WeatherType.SANDSTORM
        )
    }

    private var rotationIndex = 0
    private var fromWeather: WeatherType = rotationOrder.first()
    private var toWeather: WeatherType = rotationOrder.first()
    private var transitionAlpha = 1f
    private var cycleTimerSec = 0f
    private var lightningTimerSec = 3f
    private var lightningFlash = 0f
    private var elapsedSec = 0f
    private var manualOverride = false

    val encounteredWeathers = LinkedHashSet<String>().apply {
        add(fromWeather.name)
    }

    var onThunderStrike: (() -> Unit)? = null

    fun forceWeather(weather: WeatherType) {
        manualOverride = true
        fromWeather = currentBlendedAnchor()
        toWeather = weather
        transitionAlpha = 0f
        cycleTimerSec = 0f
        encounteredWeathers.add(weather.name)
    }

    fun resumeAutoCycle() {
        manualOverride = false
        cycleTimerSec = 0f
    }

    fun update(dt: Float): WeatherSnapshot {
        elapsedSec += dt
        if (transitionAlpha < 1f) {
            transitionAlpha = (transitionAlpha + dt / 5.5f).coerceAtMost(1f)
        }

        if (autoCycleEnabled && !manualOverride) {
            cycleTimerSec += dt
            if (cycleTimerSec >= cycleIntervalSec) {
                cycleTimerSec = 0f
                rotationIndex = (rotationIndex + 1) % rotationOrder.size
                fromWeather = toWeather
                toWeather = rotationOrder[rotationIndex]
                transitionAlpha = 0f
                encounteredWeathers.add(toWeather.name)
            }
        }

        val t = smoothStep(transitionAlpha)
        val rainNow = lerp(fromWeather.rainIntensity, toWeather.rainIntensity, t)
        val sandNow = lerp(fromWeather.sandIntensity, toWeather.sandIntensity, t)
        val lightningFreq = lerp(fromWeather.lightningFreq, toWeather.lightningFreq, t)

        if (lightningFreq > 0.05f) {
            lightningTimerSec -= dt
            if (lightningTimerSec <= 0f) {
                lightningFlash = 1.0f
                lightningTimerSec = 4.5f + Random.nextFloat() * (8.5f / lightningFreq.coerceAtLeast(0.15f))
                onThunderStrike?.invoke()
            }
        }
        if (lightningFlash > 0f) {
            lightningFlash = (lightningFlash - dt * 2.4f).coerceAtLeast(0f)
        }

        val elevDeg = lerp(fromWeather.sunElevationDeg, toWeather.sunElevationDeg, t)
        val elevRad = elevDeg * (PI.toFloat() / 180f)
        val azimuth = 0.55f + elapsedSec * 0.008f
        val sunY = sin(elevRad).coerceAtLeast(0.12f)
        val horiz = cos(elevRad)
        val sunX = cos(azimuth) * horiz
        val sunZ = sin(azimuth) * horiz

        val windAngle = elapsedSec * 0.14f
        val windMag = (sandNow * 4.5f + rainNow * 1.4f)
        val windX = cos(windAngle) * windMag
        val windZ = sin(windAngle) * windMag

        val remaining = if (autoCycleEnabled && !manualOverride) {
            (cycleIntervalSec - cycleTimerSec).coerceAtLeast(0f).toInt()
        } else {
            0
        }
        val upcoming = if (autoCycleEnabled && !manualOverride && remaining in 1..6) {
            rotationOrder[(rotationIndex + 1) % rotationOrder.size]
        } else {
            null
        }

        return WeatherSnapshot(
            currentWeather = if (t > 0.5f) toWeather else fromWeather,
            targetWeather = toWeather,
            incomingAlertWeather = upcoming,
            nextChangeCountdownSec = remaining,
            transitionProgress = t,
            fogDensity = lerp(fromWeather.fogDensity, toWeather.fogDensity, t),
            fogR = lerp(fromWeather.fogColorRgb[0], toWeather.fogColorRgb[0], t),
            fogG = lerp(fromWeather.fogColorRgb[1], toWeather.fogColorRgb[1], t),
            fogB = lerp(fromWeather.fogColorRgb[2], toWeather.fogColorRgb[2], t),
            skyTopR = lerp(fromWeather.skyTopRgb[0], toWeather.skyTopRgb[0], t),
            skyTopG = lerp(fromWeather.skyTopRgb[1], toWeather.skyTopRgb[1], t),
            skyTopB = lerp(fromWeather.skyTopRgb[2], toWeather.skyTopRgb[2], t),
            skyHorR = lerp(fromWeather.skyHorizonRgb[0], toWeather.skyHorizonRgb[0], t),
            skyHorG = lerp(fromWeather.skyHorizonRgb[1], toWeather.skyHorizonRgb[1], t),
            skyHorB = lerp(fromWeather.skyHorizonRgb[2], toWeather.skyHorizonRgb[2], t),
            sunR = lerp(fromWeather.sunColorRgb[0], toWeather.sunColorRgb[0], t),
            sunG = lerp(fromWeather.sunColorRgb[1], toWeather.sunColorRgb[1], t),
            sunB = lerp(fromWeather.sunColorRgb[2], toWeather.sunColorRgb[2], t),
            sunDirX = sunX,
            sunDirY = sunY,
            sunDirZ = sunZ,
            ambientStrength = lerp(fromWeather.ambientStrength, toWeather.ambientStrength, t),
            rainIntensity = rainNow,
            sandIntensity = sandNow,
            lightningFlash = lightningFlash,
            wetnessSpecular = lerp(fromWeather.wetnessSpecular, toWeather.wetnessSpecular, t),
            visibilityMultiplier = lerp(fromWeather.visibilityMultiplier, toWeather.visibilityMultiplier, t),
            soundPropagationMultiplier = lerp(fromWeather.soundPropagationMultiplier, toWeather.soundPropagationMultiplier, t),
            movementMultiplier = lerp(fromWeather.movementMultiplier, toWeather.movementMultiplier, t),
            spreadMultiplier = lerp(fromWeather.spreadMultiplier, toWeather.spreadMultiplier, t),
            windX = windX,
            windZ = windZ
        )
    }

    private fun currentBlendedAnchor(): WeatherType {
        return if (transitionAlpha >= 0.5f) toWeather else fromWeather
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun smoothStep(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }
}
