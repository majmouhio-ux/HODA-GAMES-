package com.example.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class SoundCue {
    RIFLE_SHOT,
    SUPPRESSED_SHOT,
    SNIPER_SHOT,
    LMG_SHOT,
    RPG_LAUNCH,
    EXPLOSION,
    THUNDER,
    HITMARKER,
    HEADSHOT,
    RELOAD,
    JET_FLYBY
}

class TacticalAudioEngine(
    @Volatile var enabled: Boolean = true
) {
    private val sampleRate = 22050
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val buffers: Map<SoundCue, ShortArray>

    init {
        buffers = mapOf(
            SoundCue.RIFLE_SHOT to synthGunshot(0.14f, 150f, 0.85f),
            SoundCue.SUPPRESSED_SHOT to synthGunshot(0.08f, 260f, 0.35f),
            SoundCue.SNIPER_SHOT to synthGunshot(0.28f, 95f, 1.0f),
            SoundCue.LMG_SHOT to synthGunshot(0.11f, 175f, 0.78f),
            SoundCue.RPG_LAUNCH to synthWhoosh(0.45f),
            SoundCue.EXPLOSION to synthExplosion(0.70f),
            SoundCue.THUNDER to synthThunder(1.10f),
            SoundCue.HITMARKER to synthBeep(0.055f, 1400f, 0.45f),
            SoundCue.HEADSHOT to synthDoubleChime(0.13f),
            SoundCue.RELOAD to synthClick(0.09f),
            SoundCue.JET_FLYBY to synthWhoosh(0.90f)
        )
    }

    fun play(cue: SoundCue, gain: Float = 1.0f) {
        if (!enabled) return
        val data = buffers[cue] ?: return
        val clampedGain = gain.coerceIn(0.05f, 1.0f)
        scope.launch {
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(data.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(data, 0, data.size)
                track.setVolume(clampedGain)
                track.play()
                val durationMs = (data.size * 1000L) / sampleRate + 60L
                kotlinx.coroutines.delay(durationMs)
                track.stop()
                track.release()
            } catch (_: Exception) {
                // Ignore transient audio focus or emulator audio buffer issues
            }
        }
    }

    fun release() {
        scope.cancel()
    }

    private fun synthGunshot(durationSec: Float, baseFreq: Float, punch: Float): ShortArray {
        val count = (sampleRate * durationSec).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val env = exp(-t * (18f / durationSec.coerceAtLeast(0.08f)) * 0.14f)
            val crack = sin(2.0 * PI * (baseFreq * exp(-t * 14f)) * t).toFloat()
            val noise = (Random.nextFloat() * 2f - 1f) * env
            val sample = (crack * 0.55f + noise * 0.65f) * env * punch
            out[i] = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthExplosion(durationSec: Float): ShortArray {
        val count = (sampleRate * durationSec).toInt()
        val out = ShortArray(count)
        var lp = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val env = exp(-t * 4.5f)
            val sub = sin(2.0 * PI * (48.0 * exp(-t * 2.2)) * t).toFloat()
            val rawNoise = Random.nextFloat() * 2f - 1f
            lp = lp * 0.86f + rawNoise * 0.14f
            val sample = (sub * 0.65f + lp * 1.15f) * env
            out[i] = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthThunder(durationSec: Float): ShortArray {
        val count = (sampleRate * durationSec).toInt()
        val out = ShortArray(count)
        var lp = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val attack = (t / 0.12f).coerceAtMost(1f)
            val env = attack * exp(-t * 2.1f)
            val rumble = sin(2.0 * PI * 36.0 * t).toFloat()
            val noise = Random.nextFloat() * 2f - 1f
            lp = lp * 0.92f + noise * 0.08f
            val sample = (rumble * 0.35f + lp * 1.2f) * env * 0.9f
            out[i] = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthWhoosh(durationSec: Float): ShortArray {
        val count = (sampleRate * durationSec).toInt()
        val out = ShortArray(count)
        var lp = 0f
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val u = t / durationSec
            val env = sin(PI * u).toFloat()
            val noise = Random.nextFloat() * 2f - 1f
            lp = lp * 0.78f + noise * 0.22f
            val turbine = sin(2.0 * PI * (120f + 160f * u) * t).toFloat() * 0.25f
            val sample = (lp * 0.75f + turbine) * env * 0.8f
            out[i] = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthBeep(durationSec: Float, freq: Float, gain: Float): ShortArray {
        val count = (sampleRate * durationSec).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val env = exp(-t * 42f)
            val sample = sin(2.0 * PI * freq * t).toFloat() * env * gain
            out[i] = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthDoubleChime(durationSec: Float): ShortArray {
        val count = (sampleRate * durationSec).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val freq = if (t < durationSec * 0.45f) 1180f else 1760f
            val env = exp(-((t % (durationSec * 0.45f)) * 30f))
            val sample = sin(2.0 * PI * freq * t).toFloat() * env * 0.65f
            out[i] = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthClick(durationSec: Float): ShortArray {
        val count = (sampleRate * durationSec).toInt()
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toFloat() / sampleRate
            val pulse = if (t < 0.02f || (t > 0.045f && t < 0.068f)) 1f else 0.1f
            val sample = (Random.nextFloat() * 2f - 1f) * pulse * 0.55f
            out[i] = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}
