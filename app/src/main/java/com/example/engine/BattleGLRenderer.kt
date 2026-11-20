package com.example.engine

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.data.model.OpticsMode
import com.example.data.model.WeaponId
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class BattleGLRenderer(
    private val sim: BattleSimulation,
    private val onHudTick: (BattleHudState) -> Unit
) : GLSurfaceView.Renderer {

    private var worldProgram = 0
    private var fxProgram = 0
    private var skyProgram = 0

    private lateinit var terrainMesh: GpuMesh
    private lateinit var cubeMesh: GpuMesh
    private lateinit var ringMesh: GpuMesh
    private lateinit var textures: IntArray

    private var rainVbo = 0
    private var sandVbo = 0
    private var skyVbo = 0
    private var lineVbo = 0

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private var screenW = 1
    private var screenH = 1
    private var lastFrameNs = System.nanoTime()
    private var hudEmitAccum = 0f
    private var elapsedSec = 0f

    private val rainCount = (260 * sim.graphicsQuality.particleScale).toInt().coerceAtLeast(80)
    private val sandCount = (200 * sim.graphicsQuality.particleScale).toInt().coerceAtLeast(60)
    private val rainCoords = FloatArray(rainCount * 6)
    private val sandCoords = FloatArray(sandCount * 3)
    private val particleBuf: FloatBuffer = ByteBuffer
        .allocateDirect(maxOf(rainCount * 6, sandCount * 3) * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    private val lineBuf: FloatBuffer = ByteBuffer
        .allocateDirect(6 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        worldProgram = compileProgram(WORLD_VS, WORLD_FS)
        fxProgram = compileProgram(FX_VS, FX_FS)
        skyProgram = compileProgram(SKY_VS, SKY_FS)

        terrainMesh = BattleMeshBuilder.buildTerrainMesh(sim.mapId, sim.graphicsQuality.terrainStep)
        cubeMesh = BattleMeshBuilder.buildUnitCubeMesh()
        ringMesh = BattleMeshBuilder.buildRingWallMesh(48)
        textures = ProceduralTextures.createTextureSet(sim.mapId)

        initDynamicBuffers()
        lastFrameNs = System.nanoTime()
    }

    private fun initDynamicBuffers() {
        val vbos = IntArray(4)
        GLES30.glGenBuffers(4, vbos, 0)
        rainVbo = vbos[0]
        sandVbo = vbos[1]
        skyVbo = vbos[2]
        lineVbo = vbos[3]

        val quad = floatArrayOf(
            -1f, -1f, 1f, -1f, 1f, 1f,
            -1f, -1f, 1f, 1f, -1f, 1f
        )
        val qBuf = ByteBuffer.allocateDirect(quad.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(quad)
        qBuf.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, skyVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.size * 4, qBuf, GLES30.GL_STATIC_DRAW)

        for (i in 0 until rainCount) {
            val rx = (Random.nextFloat() - 0.5f) * 34f
            val ry = Random.nextFloat() * 18f
            val rz = (Random.nextFloat() - 0.5f) * 34f
            rainCoords[i * 6] = rx
            rainCoords[i * 6 + 1] = ry
            rainCoords[i * 6 + 2] = rz
            rainCoords[i * 6 + 3] = rx - 0.14f
            rainCoords[i * 6 + 4] = ry - 1.15f
            rainCoords[i * 6 + 5] = rz
        }

        for (i in 0 until sandCount) {
            sandCoords[i * 3] = (Random.nextFloat() - 0.5f) * 38f
            sandCoords[i * 3 + 1] = Random.nextFloat() * 9f
            sandCoords[i * 3 + 2] = (Random.nextFloat() - 0.5f) * 38f
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        screenW = width.coerceAtLeast(1)
        screenH = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, screenW, screenH)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNs) / 1_000_000_000.0f).coerceIn(0.001f, 0.05f)
        lastFrameNs = now
        elapsedSec += dt

        sim.step(dt)
        hudEmitAccum += dt
        if (hudEmitAccum >= 0.045f) {
            hudEmitAccum = 0f
            onHudTick(sim.snapshotHud())
        }

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        sim.withRenderLock { s ->
            val weather = s.weatherSnapshot
            val opticsInt = when (s.opticsMode) {
                OpticsMode.NORMAL -> 0
                OpticsMode.NVG -> 1
                OpticsMode.THERMAL -> 2
            }

            // 1. Dynamic SkyDome gradient + lightning + sun glow
            drawSkyPass(weather, opticsInt)

            // 2. Camera Matrices
            val fov = s.getCurrentFov()
            val aspect = screenW.toFloat() / screenH.toFloat()
            Matrix.perspectiveM(projMatrix, 0, fov, aspect, 0.15f, 220f)

            val eyeX = s.playerX
            val bobY = sin(s.walkBobPhase) * 0.045f
            val eyeY = s.playerY + s.eyeHeight() + bobY
            val eyeZ = s.playerZ
            val shake = s.getShakeOffset()
            val cosP = cos(s.playerPitch + shake)
            val lookX = eyeX - sin(s.playerYaw + shake) * cosP * 10f
            val lookY = eyeY + sin(s.playerPitch + shake) * 10f
            val lookZ = eyeZ - cos(s.playerYaw + shake) * cosP * 10f

            Matrix.setLookAtM(viewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, 0f, 1f, 0f)
            Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

            // 3. World Lit Shader setup
            GLES30.glUseProgram(worldProgram)
            val uMvp = GLES30.glGetUniformLocation(worldProgram, "uMvp")
            val uModel = GLES30.glGetUniformLocation(worldProgram, "uModel")
            val uCamPos = GLES30.glGetUniformLocation(worldProgram, "uCamPos")
            val uSunDir = GLES30.glGetUniformLocation(worldProgram, "uSunDir")
            val uSunColor = GLES30.glGetUniformLocation(worldProgram, "uSunColor")
            val uAmbient = GLES30.glGetUniformLocation(worldProgram, "uAmbient")
            val uFogColor = GLES30.glGetUniformLocation(worldProgram, "uFogColor")
            val uFogDensity = GLES30.glGetUniformLocation(worldProgram, "uFogDensity")
            val uWetness = GLES30.glGetUniformLocation(worldProgram, "uWetness")
            val uLightning = GLES30.glGetUniformLocation(worldProgram, "uLightning")
            val uOptics = GLES30.glGetUniformLocation(worldProgram, "uOpticsMode")
            val uHeat = GLES30.glGetUniformLocation(worldProgram, "uHeat")
            val uTint = GLES30.glGetUniformLocation(worldProgram, "uTint")
            val uTex = GLES30.glGetUniformLocation(worldProgram, "uTexture")

            GLES30.glUniform3f(uCamPos, eyeX, eyeY, eyeZ)
            GLES30.glUniform3f(uSunDir, weather.sunDirX, weather.sunDirY, weather.sunDirZ)
            GLES30.glUniform3f(uSunColor, weather.sunR, weather.sunG, weather.sunB)
            GLES30.glUniform1f(uAmbient, weather.ambientStrength)
            GLES30.glUniform3f(uFogColor, weather.fogR, weather.fogG, weather.fogB)
            GLES30.glUniform1f(uFogDensity, weather.fogDensity)
            GLES30.glUniform1f(uWetness, weather.wetnessSpecular)
            GLES30.glUniform1f(uLightning, weather.lightningFlash)
            GLES30.glUniform1i(uOptics, opticsInt)
            GLES30.glUniform1i(uTex, 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

            // Draw Terrain
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(uMvp, 1, false, mvpMatrix, 0)
            GLES30.glUniformMatrix4fv(uModel, 1, false, modelMatrix, 0)
            GLES30.glUniform1f(uHeat, 0.05f)
            GLES30.glUniform3f(uTint, 1f, 1f, 1f)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
            terrainMesh.bindAndDraw()

            // Draw Covers / Bunkers / Vehicles / Crates
            for (c in s.covers) {
                drawBox(uMvp, uModel, uHeat, uTint, c.x, c.y, c.z, c.halfW * 2f, c.halfH * 2f, c.halfD * 2f, 0f, textures[c.texIndex], 0.1f, c.tintR, c.tintG, c.tintB)
            }

            // Draw Loot Crates (pulsing glow)
            val pulse = 0.85f + 0.15f * sin(elapsedSec * 5f)
            for (crate in s.lootCrates) {
                drawBox(
                    uMvp, uModel, uHeat, uTint,
                    crate.x, crate.y, crate.z, 0.9f, 0.7f, 0.9f,
                    elapsedSec * 40f,
                    textures[2], 0.45f,
                    if (crate.kind == 0) 0.4f else 1.0f, pulse, if (crate.kind == 0) 1.0f else 0.35f
                )
            }

            // Draw Enemy Soldiers & Patrol Drones (Hot thermal signature = 1.0!)
            for (e in s.enemies) {
                val yawDeg = Math.toDegrees(e.yaw.toDouble()).toFloat()
                val flash = e.hitFlash > 0f
                val r = if (flash) 1.6f else 1f
                val g = if (flash) 0.4f else 1f
                val b = if (flash) 0.4f else 1f

                if (e.kind == EnemyKind.HEAVY_DRONE) {
                    drawBox(uMvp, uModel, uHeat, uTint, e.x, e.y, e.z, 1.5f, 0.55f, 1.5f, elapsedSec * 90f, textures[5], 1.0f, r, g, b)
                    drawBox(uMvp, uModel, uHeat, uTint, e.x, e.y - 0.3f, e.z, 0.5f, 0.35f, 0.5f, yawDeg, textures[4], 1.0f, 1.4f, 0.25f, 0.2f)
                } else {
                    val stride = sin(e.walkPhase) * 0.22f
                    // Legs
                    drawBox(uMvp, uModel, uHeat, uTint, e.x - 0.15f, e.y + 0.42f, e.z + stride, 0.22f, 0.84f, 0.24f, yawDeg, textures[3], 0.85f, r, g, b)
                    drawBox(uMvp, uModel, uHeat, uTint, e.x + 0.15f, e.y + 0.42f, e.z - stride, 0.22f, 0.84f, 0.24f, yawDeg, textures[3], 0.85f, r, g, b)
                    // Torso Armor
                    drawBox(uMvp, uModel, uHeat, uTint, e.x, e.y + 1.15f, e.z, 0.56f, 0.68f, 0.34f, yawDeg, textures[3], 1.0f, r, g, b)
                    // Helmet / Head
                    val headTintR = if (e.kind == EnemyKind.SNIPER) 0.7f else 1.0f
                    drawBox(uMvp, uModel, uHeat, uTint, e.x, e.y + 1.68f, e.z, 0.34f, 0.34f, 0.34f, yawDeg, textures[4], 1.0f, headTintR * r, g, b)
                    // Enemy Rifle
                    val gunFwdX = sin(e.yaw) * 0.45f
                    val gunFwdZ = cos(e.yaw) * 0.45f
                    drawBox(uMvp, uModel, uHeat, uTint, e.x + gunFwdX, e.y + 1.24f, e.z + gunFwdZ, 0.12f, 0.14f, 0.75f, yawDeg, textures[4], 0.65f, 1f, 1f, 1f)
                }
            }

            // Draw AC-130 / Strike Jet Flyby
            if (s.airstrikeTimerSec > 0f) {
                drawBox(uMvp, uModel, uHeat, uTint, s.jetX, s.jetY, s.jetZ, 6.5f, 1.1f, 2.2f, -24f, textures[5], 1.0f, 1.1f, 1.1f, 1.2f)
                drawBox(uMvp, uModel, uHeat, uTint, s.jetX, s.jetY, s.jetZ, 1.8f, 0.4f, 9.0f, -24f, textures[5], 0.9f, 1.0f, 1.0f, 1.0f)
            }

            // Draw Projectiles & Grenades
            for (p in s.projectiles) {
                val sz = if (p.isRocket) 0.32f else 0.09f
                drawBox(uMvp, uModel, uHeat, uTint, p.x, p.y, p.z, sz, sz, sz * 2.5f, 0f, textures[4], 1.0f, 1.5f, 1.1f, 0.4f)
            }
            for (g in s.grenades) {
                drawBox(uMvp, uModel, uHeat, uTint, g.x, g.y, g.z, 0.24f, 0.24f, 0.24f, elapsedSec * 180f, textures[2], 0.8f, 0.9f, 1.0f, 0.4f)
            }

            // 4. FX Pass (Safe Zone Ring, Smoke, Explosions, Tracers, Rain, Sandstorm)
            drawFxPass(s, weather, opticsInt, eyeX, eyeY, eyeZ, dt)

            // 5. First-Person 3D Weapon Viewmodel (depth cleared so it renders cleanly in front)
            drawWeaponViewmodel(s, weather, opticsInt)
        }
    }

    private fun drawBox(
        uMvp: Int, uModel: Int, uHeat: Int, uTint: Int,
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        yawDeg: Float,
        texId: Int,
        heat: Float,
        r: Float, g: Float, b: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        if (yawDeg != 0f) Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(uModel, 1, false, modelMatrix, 0)
        GLES30.glUniform1f(uHeat, heat)
        GLES30.glUniform3f(uTint, r, g, b)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        cubeMesh.bindAndDraw()
    }

    private fun drawSkyPass(weather: WeatherSnapshot, opticsInt: Int) {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glUseProgram(skyProgram)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(skyProgram, "uSkyTop"), weather.skyTopR, weather.skyTopG, weather.skyTopB)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(skyProgram, "uSkyHor"), weather.skyHorR, weather.skyHorG, weather.skyHorB)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(skyProgram, "uLightning"), weather.lightningFlash)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(skyProgram, "uOpticsMode"), opticsInt)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, skyVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    private fun drawFxPass(
        s: BattleSimulation,
        weather: WeatherSnapshot,
        opticsInt: Int,
        eyeX: Float, eyeY: Float, eyeZ: Float,
        dt: Float
    ) {
        GLES30.glUseProgram(fxProgram)
        val uMvp = GLES30.glGetUniformLocation(fxProgram, "uMvp")
        val uColor = GLES30.glGetUniformLocation(fxProgram, "uColor")

        // Safe Zone Glowing Storm Barrier
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, 0f, -6f, 0f)
        Matrix.scaleM(modelMatrix, 0, s.safeZoneRadius, 34f, s.safeZoneRadius)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvpMatrix, 0)
        GLES30.glUniform4f(uColor, 0.95f, 0.25f, 0.18f, 0.20f)
        GLES30.glDepthMask(false)
        ringMesh.bindAndDraw()

        // Volumetric Smoke Grenade Clouds
        for (smoke in s.smokeClouds) {
            val grow = ((smoke.maxLifeSec - smoke.lifeSec) / 1.5f).coerceIn(0.25f, 1f)
            val fade = (smoke.lifeSec / 3f).coerceIn(0f, 1f)
            val rad = 7.8f * grow
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, smoke.x, smoke.y + 1.5f, smoke.z)
            Matrix.rotateM(modelMatrix, 0, elapsedSec * 18f, 0f, 1f, 0f)
            Matrix.scaleM(modelMatrix, 0, rad, rad * 0.65f, rad)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(uMvp, 1, false, mvpMatrix, 0)
            GLES30.glUniform4f(uColor, 0.80f, 0.84f, 0.86f, 0.72f * fade)
            cubeMesh.bindAndDraw()
        }

        // Explosions
        for (ex in s.explosions) {
            val u = (ex.age / ex.maxAge).coerceIn(0f, 1f)
            val scale = ex.maxScale * (0.35f + u * 0.9f)
            val alpha = (1f - u).coerceIn(0f, 1f)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, ex.x, ex.y + scale * 0.35f, ex.z)
            Matrix.rotateM(modelMatrix, 0, u * 160f, 0.4f, 1f, 0.2f)
            Matrix.scaleM(modelMatrix, 0, scale, scale, scale)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(uMvp, 1, false, mvpMatrix, 0)
            GLES30.glUniform4f(uColor, 1.0f, 0.52f * (1f - u * 0.5f), 0.12f, 0.85f * alpha)
            cubeMesh.bindAndDraw()
        }

        // Bullet Tracers
        GLES30.glUniformMatrix4fv(uMvp, 1, false, vpMatrix, 0)
        GLES30.glLineWidth(3.5f)
        for (tr in s.tracers) {
            lineBuf.clear()
            lineBuf.put(tr.x0).put(tr.y0).put(tr.z0).put(tr.x1).put(tr.y1).put(tr.z1)
            lineBuf.position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, lineVbo)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 6 * 4, lineBuf, GLES30.GL_DYNAMIC_DRAW)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)
            if (tr.isPlayer) {
                GLES30.glUniform4f(uColor, 1.0f, 0.85f, 0.28f, 0.92f)
            } else {
                GLES30.glUniform4f(uColor, 1.0f, 0.28f, 0.22f, 0.90f)
            }
            GLES30.glDrawArrays(GLES30.GL_LINES, 0, 2)
        }

        // 3D Dynamic Weather Particles: Rain Streaks
        if (weather.rainIntensity > 0.04f) {
            val activeRain = (rainCount * weather.rainIntensity).toInt().coerceIn(1, rainCount)
            val fallSpeed = 24f * dt
            val wx = weather.windX * dt * 0.8f
            val wz = weather.windZ * dt * 0.8f
            for (i in 0 until activeRain) {
                val base = i * 6
                var x = rainCoords[base] + wx
                var y = rainCoords[base + 1] - fallSpeed
                var z = rainCoords[base + 2] + wz
                if (y < -4f) {
                    x = (Random.nextFloat() - 0.5f) * 32f
                    y = 14f
                    z = (Random.nextFloat() - 0.5f) * 32f
                }
                rainCoords[base] = x; rainCoords[base + 1] = y; rainCoords[base + 2] = z
                rainCoords[base + 3] = x - 0.14f - weather.windX * 0.04f
                rainCoords[base + 4] = y - 1.1f
                rainCoords[base + 5] = z - weather.windZ * 0.04f
            }
            particleBuf.clear()
            particleBuf.put(rainCoords, 0, activeRain * 6)
            particleBuf.position(0)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, eyeX, eyeY, eyeZ)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(uMvp, 1, false, mvpMatrix, 0)
            val alpha = (0.58f * weather.rainIntensity).coerceIn(0f, 0.7f)
            if (opticsInt == 1) GLES30.glUniform4f(uColor, 0.45f, 0.95f, 0.55f, alpha)
            else GLES30.glUniform4f(uColor, 0.76f, 0.86f, 0.98f, alpha)
            GLES30.glLineWidth(2f)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, rainVbo)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, activeRain * 6 * 4, particleBuf, GLES30.GL_DYNAMIC_DRAW)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)
            GLES30.glDrawArrays(GLES30.GL_LINES, 0, activeRain * 2)
        }

        // 3D Dynamic Weather Particles: Sandstorm Swirling Dust
        if (weather.sandIntensity > 0.05f) {
            val activeSand = (sandCount * weather.sandIntensity).toInt().coerceIn(1, sandCount)
            val wx = (weather.windX + 6f) * dt * 1.4f
            val wz = (weather.windZ + 2f) * dt * 1.4f
            for (i in 0 until activeSand) {
                val base = i * 3
                var x = sandCoords[base] + wx
                var y = sandCoords[base + 1] + sin(elapsedSec * 2f + i) * 0.6f * dt
                var z = sandCoords[base + 2] + wz
                if (x > 19f) x = -19f
                if (z > 19f) z = -19f
                sandCoords[base] = x; sandCoords[base + 1] = y.coerceIn(-2f, 9f); sandCoords[base + 2] = z
            }
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, eyeX, eyeY, eyeZ)
            Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(uMvp, 1, false, mvpMatrix, 0)
            GLES30.glUniform4f(uColor, 0.88f, 0.64f, 0.34f, 0.32f * weather.sandIntensity)
            for (i in 0 until activeSand step 3) {
                val sx = sandCoords[i * 3]
                val sy = sandCoords[i * 3 + 1]
                val sz = sandCoords[i * 3 + 2]
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, eyeX + sx, eyeY + sy - 2f, eyeZ + sz)
                Matrix.scaleM(modelMatrix, 0, 1.3f, 0.38f, 0.38f)
                Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
                GLES30.glUniformMatrix4fv(uMvp, 1, false, mvpMatrix, 0)
                cubeMesh.bindAndDraw()
            }
        }

        GLES30.glDepthMask(true)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun drawWeaponViewmodel(s: BattleSimulation, weather: WeatherSnapshot, opticsInt: Int) {
        if (s.isAds && s.weaponId == WeaponId.AWM && !s.usingRpg) {
            // Sniper scope view uses full-screen tactical reticle overlay instead of weapon block
            return
        }
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glUseProgram(worldProgram)
        val uMvp = GLES30.glGetUniformLocation(worldProgram, "uMvp")
        val uModel = GLES30.glGetUniformLocation(worldProgram, "uModel")
        val uHeat = GLES30.glGetUniformLocation(worldProgram, "uHeat")
        val uTint = GLES30.glGetUniformLocation(worldProgram, "uTint")
        GLES30.glUniform1f(GLES30.glGetUniformLocation(worldProgram, "uFogDensity"), 0.0f)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(worldProgram, "uOpticsMode"), opticsInt)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(worldProgram, "uWetness"), weather.wetnessSpecular)

        val aspect = screenW.toFloat() / screenH.toFloat()
        Matrix.perspectiveM(projMatrix, 0, 58f, aspect, 0.05f, 12f)

        val adsT = if (s.isAds) 1f else 0f
        val swayX = sin(s.walkBobPhase * 0.5f) * 0.014f * (1f - adsT * 0.7f)
        val swayY = cos(s.walkBobPhase) * 0.012f * (1f - adsT * 0.7f)
        val kickZ = s.weaponKick * 0.085f
        val baseX = 0.25f * (1f - adsT) + swayX
        val baseY = -0.24f + adsT * 0.07f + swayY - (if (s.isReloading) 0.14f else 0f)
        val baseZ = -0.52f + kickZ

        fun drawPart(ox: Float, oy: Float, oz: Float, sx: Float, sy: Float, sz: Float, tex: Int, r: Float, g: Float, b: Float) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, baseX + ox, baseY + oy, baseZ + oz)
            if (s.isSprinting && !s.isAds) Matrix.rotateM(modelMatrix, 0, -22f, 0f, 1f, 0f)
            Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
            Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(uMvp, 1, false, mvpMatrix, 0)
            GLES30.glUniformMatrix4fv(uModel, 1, false, modelMatrix, 0)
            GLES30.glUniform1f(uHeat, 0.25f)
            GLES30.glUniform3f(uTint, r, g, b)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
            cubeMesh.bindAndDraw()
        }

        if (s.usingRpg) {
            drawPart(0f, 0f, -0.15f, 0.11f, 0.11f, 0.85f, textures[3], 0.6f, 0.72f, 0.45f)
            drawPart(0f, 0f, -0.62f, 0.15f, 0.15f, 0.22f, textures[2], 0.9f, 0.45f, 0.2f)
        } else {
            val receiverR = if (s.weaponId == WeaponId.AK47) 0.72f else 1f
            val receiverG = if (s.weaponId == WeaponId.AK47) 0.52f else 1f
            val receiverB = if (s.weaponId == WeaponId.AK47) 0.34f else 1f
            // Receiver body
            drawPart(0f, 0f, 0f, 0.085f, 0.11f, 0.46f, textures[4], receiverR, receiverG, receiverB)
            // Barrel
            drawPart(0f, 0.02f, -0.36f, 0.036f, 0.036f, 0.36f, textures[4], 0.85f, 0.85f, 0.9f)
            // Magazine
            val magW = if (s.weaponId == WeaponId.M249) 0.13f else 0.055f
            drawPart(0f, -0.12f, -0.04f, magW, 0.16f, 0.09f, textures[4], 0.75f, 0.75f, 0.75f)
            // Optic rail
            drawPart(0f, 0.085f, 0.02f, 0.045f, 0.05f, 0.13f, textures[4], 0.3f, 0.9f, 1.0f)
        }

        if (s.isMuzzleFlashActive()) {
            GLES30.glUseProgram(fxProgram)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, baseX, baseY + 0.02f, baseZ - 0.58f)
            Matrix.rotateM(modelMatrix, 0, elapsedSec * 700f, 0f, 0f, 1f)
            Matrix.scaleM(modelMatrix, 0, 0.16f, 0.16f, 0.16f)
            Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, modelMatrix, 0)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(fxProgram, "uMvp"), 1, false, mvpMatrix, 0)
            GLES30.glUniform4f(GLES30.glGetUniformLocation(fxProgram, "uColor"), 1.0f, 0.78f, 0.25f, 0.95f)
            cubeMesh.bindAndDraw()
        }
    }

    private fun compileProgram(vsSrc: String, fsSrc: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vsSrc)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fsSrc)
        val prog = GLES30.glCreateProgram()
        GLES30.glAttachShader(prog, vs)
        GLES30.glAttachShader(prog, fs)
        GLES30.glLinkProgram(prog)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        return shader
    }

    companion object {
        private const val WORLD_VS = """#version 300 es
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNormal;
            layout(location = 2) in vec2 aUv;
            uniform mat4 uMvp;
            uniform mat4 uModel;
            out vec3 vWorldPos;
            out vec3 vNormal;
            out vec2 vUv;
            void main() {
                vec4 wp = uModel * vec4(aPos, 1.0);
                vWorldPos = wp.xyz;
                vNormal = mat3(uModel) * aNormal;
                vUv = aUv;
                gl_Position = uMvp * vec4(aPos, 1.0);
            }
        """

        private const val WORLD_FS = """#version 300 es
            precision mediump float;
            in vec3 vWorldPos;
            in vec3 vNormal;
            in vec2 vUv;
            uniform sampler2D uTexture;
            uniform vec3 uCamPos;
            uniform vec3 uSunDir;
            uniform vec3 uSunColor;
            uniform float uAmbient;
            uniform vec3 uFogColor;
            uniform float uFogDensity;
            uniform float uWetness;
            uniform float uLightning;
            uniform int uOpticsMode;
            uniform float uHeat;
            uniform vec3 uTint;
            out vec4 fragColor;
            void main() {
                vec4 tex = texture(uTexture, vUv);
                vec3 albedo = tex.rgb * uTint;
                vec3 N = normalize(vNormal);
                vec3 L = normalize(uSunDir);
                vec3 V = normalize(uCamPos - vWorldPos);
                vec3 H = normalize(L + V);

                float diff = max(dot(N, L), 0.0);
                float specPower = mix(18.0, 64.0, uWetness);
                float specStrength = mix(0.12, 0.85, uWetness);
                float spec = pow(max(dot(N, H), 0.0), specPower) * specStrength;

                float hemi = 0.5 + 0.5 * N.y;
                vec3 ambientLight = mix(vec3(0.18, 0.16, 0.15), vec3(0.42, 0.48, 0.56), hemi) * uAmbient;
                vec3 lit = albedo * (ambientLight + uSunColor * diff * (0.95 + uLightning * 1.4)) + uSunColor * spec;

                float dist = length(uCamPos - vWorldPos);
                float fogFactor = clamp(1.0 - exp(-pow(dist * uFogDensity, 2.0)), 0.0, 1.0);
                vec3 finalRgb = mix(lit, uFogColor + vec3(uLightning * 0.45), fogFactor);

                if (uOpticsMode == 1) {
                    float lum = dot(lit, vec3(0.299, 0.587, 0.114));
                    float boosted = clamp(lum * 2.35 + 0.12, 0.0, 1.0);
                    float scan = 0.90 + 0.10 * sin(gl_FragCoord.y * 1.6);
                    finalRgb = vec3(boosted * 0.22, boosted * 1.15, boosted * 0.32) * scan;
                    finalRgb = mix(finalRgb, vec3(0.08, 0.32, 0.12), fogFactor * 0.55);
                } else if (uOpticsMode == 2) {
                    float cold = dot(lit, vec3(0.3, 0.5, 0.2));
                    vec3 coldCol = mix(vec3(0.05, 0.07, 0.22), vec3(0.22, 0.36, 0.65), clamp(cold * 1.3, 0.0, 1.0));
                    vec3 hotCol = mix(vec3(1.0, 0.45, 0.08), vec3(1.0, 0.98, 0.85), clamp(uHeat, 0.0, 1.0));
                    finalRgb = mix(coldCol, hotCol, smoothstep(0.35, 0.95, uHeat));
                }

                fragColor = vec4(finalRgb, 1.0);
            }
        """

        private const val FX_VS = """#version 300 es
            layout(location = 0) in vec3 aPos;
            uniform mat4 uMvp;
            void main() {
                gl_Position = uMvp * vec4(aPos, 1.0);
            }
        """

        private const val FX_FS = """#version 300 es
            precision mediump float;
            uniform vec4 uColor;
            out vec4 fragColor;
            void main() {
                fragColor = uColor;
            }
        """

        private const val SKY_VS = """#version 300 es
            layout(location = 0) in vec2 aPos;
            out vec2 vPos;
            void main() {
                vPos = aPos;
                gl_Position = vec4(aPos, 0.9999, 1.0);
            }
        """

        private const val SKY_FS = """#version 300 es
            precision mediump float;
            in vec2 vPos;
            uniform vec3 uSkyTop;
            uniform vec3 uSkyHor;
            uniform float uLightning;
            uniform int uOpticsMode;
            out vec4 fragColor;
            void main() {
                float t = clamp(vPos.y * 0.5 + 0.5, 0.0, 1.0);
                vec3 col = mix(uSkyHor, uSkyTop, pow(t, 0.85)) + vec3(uLightning * 0.55);
                if (uOpticsMode == 1) {
                    col = vec3(0.05, 0.28, 0.09);
                } else if (uOpticsMode == 2) {
                    col = vec3(0.03, 0.05, 0.14);
                }
                fragColor = vec4(col, 1.0);
            }
        """
    }
}
