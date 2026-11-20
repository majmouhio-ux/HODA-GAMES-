package com.example.engine

import com.example.data.model.AttachmentId
import com.example.data.model.GameModeId
import com.example.data.model.GraphicsQuality
import com.example.data.model.MapId
import com.example.data.model.MatchOutcome
import com.example.data.model.OpticsMode
import com.example.data.model.TacticalId
import com.example.data.model.WeaponId
import com.example.data.model.WeatherType
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class BoxCover(
    val x: Float,
    val y: Float,
    val z: Float,
    val halfW: Float,
    val halfH: Float,
    val halfD: Float,
    val texIndex: Int,
    val tintR: Float = 1f,
    val tintG: Float = 1f,
    val tintB: Float = 1f
)

enum class EnemyKind { RIFLEMAN, SNIPER, HEAVY_DRONE }

class EnemyUnit(
    val id: Int,
    var x: Float,
    var y: Float,
    var z: Float,
    var yaw: Float,
    var hp: Float,
    val maxHp: Float,
    val kind: EnemyKind,
    var stateTimer: Float = 0f,
    var fireCooldown: Float = 1.2f,
    var strafeDir: Float = 1f,
    var alertLevel: Float = 0f,
    var lastSpottedTime: Float = -99f,
    var walkPhase: Float = 0f,
    var hitFlash: Float = 0f
)

class Projectile(
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    var vz: Float,
    val fromPlayer: Boolean,
    val isRocket: Boolean,
    val damage: Float,
    var life: Float
)

class GrenadeEntity(
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    var vz: Float,
    val isSmoke: Boolean,
    var fuseSec: Float
)

class SmokeCloud(
    val x: Float,
    val y: Float,
    val z: Float,
    var lifeSec: Float,
    val maxLifeSec: Float = 14f
)

class ExplosionFx(
    val x: Float,
    val y: Float,
    val z: Float,
    var age: Float = 0f,
    val maxAge: Float = 0.75f,
    val maxScale: Float = 7.5f
)

class LootCrate(
    val x: Float,
    val y: Float,
    val z: Float,
    val kind: Int // 0 = Ammo+Medkit, 1 = Armor+RPG
)

class TracerFx(
    val x0: Float,
    val y0: Float,
    val z0: Float,
    val x1: Float,
    val y1: Float,
    val z1: Float,
    val isPlayer: Boolean,
    var life: Float = 0.14f
)

data class MinimapBlip(
    val dx: Float,
    val dz: Float,
    val isDrone: Boolean,
    val isLoot: Boolean = false
)

data class KillFeedEntry(
    val text: String,
    val isHeadshot: Boolean,
    val timestampMs: Long
)

data class BattleHudState(
    val health: Int = 100,
    val armor: Int = 60,
    val stamina: Int = 100,
    val aliveEnemies: Int = 24,
    val totalEnemies: Int = 24,
    val kills: Int = 0,
    val headshots: Int = 0,
    val damageDealt: Int = 0,
    val score: Int = 0,
    val survivalSeconds: Int = 0,
    val ammoInMag: Int = 30,
    val maxMag: Int = 30,
    val reserveAmmo: Int = 150,
    val rpgAmmo: Int = 3,
    val fragCount: Int = 3,
    val smokeCount: Int = 2,
    val medkitCount: Int = 2,
    val armorPlateCount: Int = 2,
    val usingRpg: Boolean = false,
    val isAds: Boolean = false,
    val isCrouching: Boolean = false,
    val isSprinting: Boolean = false,
    val isReloading: Boolean = false,
    val reloadProgress: Float = 0f,
    val opticsMode: OpticsMode = OpticsMode.NORMAL,
    val weaponId: WeaponId = WeaponId.M4A1,
    val attachmentId: AttachmentId = AttachmentId.HOLO_OPTIC,
    val tacticalId: TacticalId = TacticalId.AIRSTRIKE,
    val streakPoints: Int = 0,
    val streakReady: Boolean = false,
    val uavActiveSec: Int = 0,
    val airstrikeActive: Boolean = false,
    val safeZoneRadius: Float = 82f,
    val outsideZone: Boolean = false,
    val weather: WeatherSnapshot = WeatherSnapshot(),
    val hitmarkerAlpha: Float = 0f,
    val hitmarkerHeadshot: Boolean = false,
    val damageFlashAlpha: Float = 0f,
    val healingProgress: Float = 0f,
    val minimapBlips: List<MinimapBlip> = emptyList(),
    val playerYawDeg: Float = 0f,
    val killFeed: List<KillFeedEntry> = emptyList(),
    val statusBanner: String? = null,
    val matchOutcome: MatchOutcome? = null
)

class BattleSimulation(
    val mapId: MapId,
    val modeId: GameModeId,
    val weaponId: WeaponId,
    val attachmentId: AttachmentId,
    val tacticalId: TacticalId,
    val autoWeatherCycle: Boolean,
    val graphicsQuality: GraphicsQuality,
    private val audioEngine: TacticalAudioEngine,
    private val onHapticPulse: (heavy: Boolean) -> Unit
) {
    val weatherEngine = WeatherEngine(
        initialMap = mapId,
        cycleIntervalSec = modeId.weatherIntervalSec,
        autoCycleEnabled = autoWeatherCycle
    )

    val covers = ArrayList<BoxCover>()
    val enemies = ArrayList<EnemyUnit>()
    val projectiles = ArrayList<Projectile>()
    val grenades = ArrayList<GrenadeEntity>()
    val smokeClouds = ArrayList<SmokeCloud>()
    val explosions = ArrayList<ExplosionFx>()
    val lootCrates = ArrayList<LootCrate>()
    val tracers = ArrayList<TracerFx>()
    val killFeed = ArrayList<KillFeedEntry>()

    // Player physics & combat state
    @Volatile var playerX = 0f
    @Volatile var playerY = 0f
    @Volatile var playerZ = 34f
    @Volatile var playerYaw = 0f // radians, 0 faces -Z
    @Volatile var playerPitch = 0f // radians
    private var playerVy = 0f
    private var grounded = true

    var health = 100f
    var armor = 65f
    var stamina = 100f
    var isCrouching = false
    var isSprinting = false
    var isAds = false
    var usingRpg = false
    var opticsMode = OpticsMode.NORMAL

    val maxMag = weaponId.magSize + attachmentId.magBonus
    var ammoInMag = maxMag
    var reserveAmmo = maxMag * 5
    var rpgAmmo = 3
    var fragCount = 3
    var smokeCount = 2
    var medkitCount = 2
    var armorPlateCount = 2

    private var fireCooldownSec = 0f
    private var reloadTimerSec = 0f
    var isReloading = false
        private set
    private var healTimerSec = 0f
    private var recoilPitchOffset = 0f
    private var muzzleFlashTimer = 0f
    private var screenShakeTimer = 0f
    private var hitmarkerTimer = 0f
    private var hitmarkerHeadshot = false
    private var damageFlashTimer = 0f
    var walkBobPhase = 0f
    var weaponKick = 0f

    // Match & Battle Royale state
    private var remainingToSpawn = modeId.enemyTotal
    private var nextEnemyId = 1
    private var matchElapsedSec = 0f
    var safeZoneRadius = 80f
    private var safeZoneDamageAccum = 0f
    var kills = 0
    var headshots = 0
    var damageDealt = 0
    var score = 0
    var streakPoints = 0
    var uavTimerSec = 0f
    var airstrikeTimerSec = 0f
    var jetX = 0f
    var jetY = 38f
    var jetZ = 0f
    private var airstrikeBombsLeft = 0
    private var airstrikeNextBomb = 0f
    private var airstrikeTargetX = 0f
    private var airstrikeTargetZ = 0f

    private var statusBanner: String? = null
    private var statusBannerTimer = 0f
    var outcome: MatchOutcome? = null

    // Input state written from Compose touch handlers
    @Volatile var moveInputX = 0f
    @Volatile var moveInputY = 0f
    @Volatile var isFiringHeld = false
    @Volatile var weatherSnapshot = WeatherSnapshot()

    private val lock = Any()

    init {
        weatherEngine.onThunderStrike = {
            audioEngine.play(SoundCue.THUNDER, 0.72f)
        }
        buildMapCover()
        playerY = BattleMeshBuilder.getTerrainHeight(mapId, playerX, playerZ)
        val initialActive = 7.coerceAtMost(remainingToSpawn)
        repeat(initialActive) { spawnEnemy() }
        showBanner("بدأت العملية • ${mapId.titleAr}", 3.5f)
    }

    private fun buildMapCover() {
        val rng = Random(mapId.ordinal * 4096 + 77)
        // Central fortified compound & bunkers
        val presetCoords = listOf(
            floatArrayOf(-14f, -12f, 4.2f, 2.4f, 2.6f, 1f),
            floatArrayOf(15f, -14f, 4.0f, 2.6f, 3.0f, 1f),
            floatArrayOf(0f, -26f, 6.0f, 3.2f, 2.5f, 1f),
            floatArrayOf(-28f, 8f, 3.5f, 2.2f, 5.2f, 1f),
            floatArrayOf(27f, 10f, 3.6f, 2.4f, 4.8f, 1f),
            floatArrayOf(-10f, 18f, 2.4f, 0.9f, 1.0f, 2f),
            floatArrayOf(12f, 19f, 2.4f, 0.9f, 1.0f, 2f),
            floatArrayOf(0f, 4f, 1.5f, 0.9f, 1.5f, 2f),
            floatArrayOf(-24f, -34f, 3.2f, 1.5f, 1.8f, 5f), // Armored APC cover
            floatArrayOf(28f, -30f, 3.2f, 1.5f, 1.8f, 5f)  // Armored APC cover
        )
        for (p in presetCoords) {
            val groundY = BattleMeshBuilder.getTerrainHeight(mapId, p[0], p[1])
            covers.add(
                BoxCover(
                    x = p[0],
                    y = groundY + p[3],
                    z = p[1],
                    halfW = p[2],
                    halfH = p[3],
                    halfD = p[4],
                    texIndex = p[5].toInt()
                )
            )
        }
        // Procedural outer ruins, watchtowers, crates, and barricades
        for (i in 0 until 24) {
            val angle = (i / 24f) * (2f * PI.toFloat()) + rng.nextFloat() * 0.2f
            val dist = 24f + rng.nextFloat() * 44f
            val cx = cos(angle) * dist
            val cz = sin(angle) * dist
            val groundY = BattleMeshBuilder.getTerrainHeight(mapId, cx, cz)
            val isCrate = i % 3 == 0
            val hw = if (isCrate) 1.3f else 2.2f + rng.nextFloat() * 2.2f
            val hh = if (isCrate) 1.0f else 1.8f + rng.nextFloat() * 2.4f
            val hd = if (isCrate) 1.3f else 1.8f + rng.nextFloat() * 2.0f
            covers.add(
                BoxCover(
                    x = cx,
                    y = groundY + hh,
                    z = cz,
                    halfW = hw,
                    halfH = hh,
                    halfD = hd,
                    texIndex = if (isCrate) 2 else 1
                )
            )
        }
        // Scatter initial supply crates
        repeat(6) {
            val a = rng.nextFloat() * 2f * PI.toFloat()
            val d = 14f + rng.nextFloat() * 38f
            val lx = cos(a) * d
            val lz = sin(a) * d
            lootCrates.add(LootCrate(lx, BattleMeshBuilder.getTerrainHeight(mapId, lx, lz) + 0.45f, lz, it % 2))
        }
    }

    private fun spawnEnemy() {
        if (remainingToSpawn <= 0) return
        remainingToSpawn--
        val id = nextEnemyId++
        val rng = Random(id * 911 + System.nanoTime().toInt())
        val angle = rng.nextFloat() * 2f * PI.toFloat()
        val radius = (32f + rng.nextFloat() * 34f).coerceAtMost((safeZoneRadius - 5f).coerceAtLeast(18f))
        val ex = (playerX * 0.25f + cos(angle) * radius).coerceIn(-72f, 72f)
        val ez = (playerZ * 0.25f + sin(angle) * radius).coerceIn(-72f, 72f)
        val ey = BattleMeshBuilder.getTerrainHeight(mapId, ex, ez)

        val kind = when {
            id % 6 == 0 -> EnemyKind.HEAVY_DRONE
            modeId == GameModeId.SNIPER_OPS || id % 4 == 0 -> EnemyKind.SNIPER
            else -> EnemyKind.RIFLEMAN
        }
        val maxHp = when (kind) {
            EnemyKind.HEAVY_DRONE -> 185f
            EnemyKind.SNIPER -> 90f
            EnemyKind.RIFLEMAN -> 110f
        }
        enemies.add(
            EnemyUnit(
                id = id,
                x = ex,
                y = if (kind == EnemyKind.HEAVY_DRONE) ey + 3.2f else ey,
                z = ez,
                yaw = atan2(playerX - ex, playerZ - ez),
                hp = maxHp,
                maxHp = maxHp,
                kind = kind,
                fireCooldown = 1.0f + rng.nextFloat() * 1.4f,
                strafeDir = if (rng.nextBoolean()) 1f else -1f
            )
        )
    }

    fun step(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0.001f, 0.05f)
        synchronized(lock) {
            if (outcome != null) return

            matchElapsedSec += dt
            val weather = weatherEngine.update(dt)
            weatherSnapshot = weather

            if (statusBannerTimer > 0f) {
                statusBannerTimer -= dt
                if (statusBannerTimer <= 0f) statusBanner = null
            }
            if (muzzleFlashTimer > 0f) muzzleFlashTimer = (muzzleFlashTimer - dt).coerceAtLeast(0f)
            if (screenShakeTimer > 0f) screenShakeTimer = (screenShakeTimer - dt).coerceAtLeast(0f)
            if (hitmarkerTimer > 0f) hitmarkerTimer = (hitmarkerTimer - dt).coerceAtLeast(0f)
            if (damageFlashTimer > 0f) damageFlashTimer = (damageFlashTimer - dt).coerceAtLeast(0f)
            if (uavTimerSec > 0f) uavTimerSec = (uavTimerSec - dt).coerceAtLeast(0f)
            if (weaponKick > 0f) weaponKick = (weaponKick - dt * 6.5f).coerceAtLeast(0f)
            if (recoilPitchOffset > 0f) {
                val recover = (dt * 0.14f).coerceAtMost(recoilPitchOffset)
                playerPitch -= recover
                recoilPitchOffset -= recover
            }

            // Shrinking Battle Royale Safe Zone
            if (safeZoneRadius > 16f) {
                safeZoneRadius = (safeZoneRadius - dt * 0.42f).coerceAtLeast(16f)
            }
            val playerDistFromOrigin = sqrt(playerX * playerX + playerZ * playerZ)
            if (playerDistFromOrigin > safeZoneRadius) {
                safeZoneDamageAccum += dt * 4.5f
                if (safeZoneDamageAccum >= 1f) {
                    val dmg = safeZoneDamageAccum.toInt()
                    safeZoneDamageAccum -= dmg
                    applyPlayerDamage(dmg.toFloat(), false)
                }
            } else {
                safeZoneDamageAccum = 0f
            }

            updatePlayerMovement(dt, weather)
            updateReloadAndHeal(dt)
            updateFiring(dt, weather)
            updateAirstrike(dt)
            updateGrenadesAndSmoke(dt)
            updateProjectiles(dt, weather)
            updateEnemies(dt, weather)
            updateExplosionsAndTracers(dt)
            checkLootPickups()

            // Maintain active enemy squad pressure
            val desiredActive = if (modeId == GameModeId.TACTICAL_SURVIVAL) 8 else 6
            while (enemies.size < desiredActive && remainingToSpawn > 0) {
                spawnEnemy()
            }

            if (enemies.isEmpty() && remainingToSpawn == 0) {
                outcome = MatchOutcome.VICTORY
                score += 650
                showBanner("انتصار ساحق! تم تطهير ساحة المعركة", 5f)
            }
        }
    }

    private fun updatePlayerMovement(dt: Float, weather: WeatherSnapshot) {
        val fwdX = -sin(playerYaw)
        val fwdZ = -cos(playerYaw)
        val rightX = cos(playerYaw)
        val rightZ = -sin(playerYaw)

        val moveMag = sqrt(moveInputX * moveInputX + moveInputY * moveInputY).coerceAtMost(1f)
        if (isSprinting && moveMag > 0.25f && !isAds && !isCrouching && stamina > 2f) {
            stamina = (stamina - dt * 18f).coerceAtLeast(0f)
            if (stamina <= 2f) isSprinting = false
        } else {
            stamina = (stamina + dt * 14f).coerceAtMost(100f)
        }

        val baseSpeed = 8.4f * weather.movementMultiplier
        val stanceMult = when {
            isAds -> 0.56f
            isCrouching -> 0.62f
            isSprinting && moveMag > 0.25f -> 1.42f
            else -> 1f
        }
        val speed = baseSpeed * stanceMult

        val wishX = (rightX * moveInputX + fwdX * moveInputY) * speed
        val wishZ = (rightZ * moveInputX + fwdZ * moveInputY) * speed

        val nextX = (playerX + wishX * dt).coerceIn(-78f, 78f)
        val nextZ = (playerZ + wishZ * dt).coerceIn(-78f, 78f)

        if (!collidesWithCover(nextX, playerY + 0.9f, playerZ, 0.55f)) {
            playerX = nextX
        }
        if (!collidesWithCover(playerX, playerY + 0.9f, nextZ, 0.55f)) {
            playerZ = nextZ
        }

        val groundY = BattleMeshBuilder.getTerrainHeight(mapId, playerX, playerZ)
        if (!grounded || playerY > groundY + 0.02f) {
            playerVy -= 22f * dt
            playerY += playerVy * dt
            if (playerY <= groundY) {
                playerY = groundY
                playerVy = 0f
                grounded = true
            }
        } else {
            playerY = groundY
        }

        if (moveMag > 0.1f && grounded) {
            walkBobPhase += dt * (if (isSprinting) 13f else 8.5f) * moveMag
        }
    }

    private fun updateReloadAndHeal(dt: Float) {
        if (isReloading) {
            reloadTimerSec -= dt
            if (reloadTimerSec <= 0f) {
                isReloading = false
                val needed = maxMag - ammoInMag
                val taken = needed.coerceAtMost(reserveAmmo)
                ammoInMag += taken
                reserveAmmo -= taken
            }
        }
        if (healTimerSec > 0f) {
            healTimerSec -= dt
            if (healTimerSec <= 0f) {
                health = (health + 65f).coerceAtMost(100f)
                showBanner("تم استخدام الإسعافات الأولية (+65 صحة)", 2.2f)
            }
        }
    }

    private fun updateFiring(dt: Float, weather: WeatherSnapshot) {
        if (fireCooldownSec > 0f) fireCooldownSec = (fireCooldownSec - dt).coerceAtLeast(0f)
        if (!isFiringHeld || isReloading || fireCooldownSec > 0f) return

        if (usingRpg) {
            if (rpgAmmo <= 0) {
                usingRpg = false
                return
            }
            rpgAmmo--
            fireCooldownSec = 1.05f
            val dir = lookDirection(0f)
            val eyeY = playerY + eyeHeight() - 0.12f
            projectiles.add(
                Projectile(
                    x = playerX + dir[0] * 0.9f,
                    y = eyeY + dir[1] * 0.9f,
                    z = playerZ + dir[2] * 0.9f,
                    vx = dir[0] * 58f,
                    vy = dir[1] * 58f,
                    vz = dir[2] * 58f,
                    fromPlayer = true,
                    isRocket = true,
                    damage = 160f,
                    life = 3.0f
                )
            )
            weaponKick = 1.0f
            muzzleFlashTimer = 0.08f
            applyRecoil(0.06f)
            audioEngine.play(SoundCue.RPG_LAUNCH, 0.9f)
            onHapticPulse(true)
            alertNearbyEnemies(72f * weather.soundPropagationMultiplier)
            return
        }

        if (ammoInMag <= 0) {
            startReload()
            return
        }

        ammoInMag--
        fireCooldownSec = weaponId.fireRateMs / 1000f
        if (!weaponId.isAutomatic) {
            isFiringHeld = false
        }

        val spread = weaponId.baseSpread *
            attachmentId.spreadMult *
            weather.spreadMultiplier *
            (if (isCrouching) 0.68f else 1f) *
            (if (isAds) 0.32f else 1f)

        val dir = lookDirection(spread)
        val eyeY = playerY + eyeHeight()
        projectiles.add(
            Projectile(
                x = playerX + dir[0] * 0.8f,
                y = eyeY + dir[1] * 0.8f,
                z = playerZ + dir[2] * 0.8f,
                vx = dir[0] * weaponId.muzzleSpeed,
                vy = dir[1] * weaponId.muzzleSpeed,
                vz = dir[2] * weaponId.muzzleSpeed,
                fromPlayer = true,
                isRocket = false,
                damage = weaponId.damage.toFloat(),
                life = 1.6f
            )
        )

        weaponKick = 0.85f
        if (attachmentId != AttachmentId.SUPPRESSOR) {
            muzzleFlashTimer = 0.05f
        }
        val kick = weaponId.recoilKick * attachmentId.recoilMult * (if (isCrouching) 0.72f else 1f)
        applyRecoil(kick)

        val cue = when {
            attachmentId == AttachmentId.SUPPRESSOR -> SoundCue.SUPPRESSED_SHOT
            weaponId == WeaponId.AWM -> SoundCue.SNIPER_SHOT
            weaponId == WeaponId.M249 -> SoundCue.LMG_SHOT
            else -> SoundCue.RIFLE_SHOT
        }
        audioEngine.play(cue, 0.82f)
        onHapticPulse(weaponId == WeaponId.AWM)

        val soundRadius = 66f * weather.soundPropagationMultiplier * attachmentId.soundMult
        alertNearbyEnemies(soundRadius)

        if (ammoInMag == 0 && reserveAmmo > 0) {
            startReload()
        }
    }

    private fun applyRecoil(kick: Float) {
        playerPitch = (playerPitch + kick).coerceIn(-1.25f, 1.25f)
        recoilPitchOffset = (recoilPitchOffset + kick * 0.7f).coerceAtMost(0.24f)
        playerYaw += (Random.nextFloat() - 0.5f) * kick * 0.45f
    }

    private fun alertNearbyEnemies(radius: Float) {
        for (e in enemies) {
            val dx = e.x - playerX
            val dz = e.z - playerZ
            if (dx * dx + dz * dz <= radius * radius) {
                e.alertLevel = 1f
            }
        }
    }

    private fun updateAirstrike(dt: Float) {
        if (airstrikeTimerSec <= 0f) return
        airstrikeTimerSec -= dt
        jetX += 38f * dt
        jetZ -= 18f * dt
        if (airstrikeBombsLeft > 0) {
            airstrikeNextBomb -= dt
            if (airstrikeNextBomb <= 0f) {
                airstrikeBombsLeft--
                airstrikeNextBomb = 0.38f
                val bx = airstrikeTargetX + (Random.nextFloat() - 0.5f) * 20f
                val bz = airstrikeTargetZ + (Random.nextFloat() - 0.5f) * 20f
                val by = BattleMeshBuilder.getTerrainHeight(mapId, bx, bz)
                triggerExplosion(bx, by + 0.5f, bz, 15f, 145f)
            }
        }
    }

    private fun updateGrenadesAndSmoke(dt: Float) {
        val gIt = grenades.iterator()
        while (gIt.hasNext()) {
            val g = gIt.next()
            g.vy -= 18f * dt
            g.x += g.vx * dt
            g.y += g.vy * dt
            g.z += g.vz * dt
            val groundY = BattleMeshBuilder.getTerrainHeight(mapId, g.x, g.z)
            if (g.y <= groundY + 0.18f) {
                g.y = groundY + 0.18f
                g.vy = -g.vy * 0.35f
                g.vx *= 0.55f
                g.vz *= 0.55f
            }
            g.fuseSec -= dt
            if (g.fuseSec <= 0f) {
                if (g.isSmoke) {
                    smokeClouds.add(SmokeCloud(g.x, g.y + 1.2f, g.z, 14f))
                    showBanner("تم نشر ستار الدخان التكتيكي", 2f)
                } else {
                    triggerExplosion(g.x, g.y, g.z, 12.5f, 130f)
                }
                gIt.remove()
            }
        }

        val sIt = smokeClouds.iterator()
        while (sIt.hasNext()) {
            val s = sIt.next()
            s.lifeSec -= dt
            if (s.lifeSec <= 0f) sIt.remove()
        }
    }

    private fun triggerExplosion(ex: Float, ey: Float, ez: Float, radius: Float, maxDamage: Float) {
        explosions.add(ExplosionFx(ex, ey, ez, maxScale = radius * 0.72f))
        audioEngine.play(SoundCue.EXPLOSION, 0.95f)
        onHapticPulse(true)
        screenShakeTimer = 0.35f

        val eIt = enemies.iterator()
        while (eIt.hasNext()) {
            val enemy = eIt.next()
            val dx = enemy.x - ex
            val dy = (enemy.y + 1.0f) - ey
            val dz = enemy.z - ez
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist <= radius) {
                val falloff = (1f - dist / radius).coerceIn(0.2f, 1f)
                val dmg = maxDamage * falloff
                damageDealt += dmg.toInt()
                enemy.hp -= dmg
                enemy.hitFlash = 0.25f
                hitmarkerTimer = 0.22f
                hitmarkerHeadshot = false
                if (enemy.hp <= 0f) {
                    handleEnemyKilled(enemy, false)
                    eIt.remove()
                }
            }
        }
    }

    private fun updateProjectiles(dt: Float, weather: WeatherSnapshot) {
        val it = projectiles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) {
                if (p.isRocket) triggerExplosion(p.x, p.y, p.z, 10f, 150f)
                it.remove()
                continue
            }

            // Windage drift from dynamic weather (sandstorm & heavy rain crosswind)
            val windFactor = if (p.isRocket) 0.15f else 0.55f
            val prevX = p.x; val prevY = p.y; val prevZ = p.z
            p.vx += weather.windX * windFactor * dt
            p.vz += weather.windZ * windFactor * dt
            p.vy -= (if (p.isRocket) 1.2f else 3.8f) * dt

            p.x += p.vx * dt
            p.y += p.vy * dt
            p.z += p.vz * dt

            // Spawn visible tracer line periodically
            if (!p.isRocket && Random.nextFloat() < 0.72f) {
                tracers.add(TracerFx(prevX, prevY, prevZ, p.x, p.y, p.z, p.fromPlayer, 0.09f))
            }

            val groundY = BattleMeshBuilder.getTerrainHeight(mapId, p.x, p.z)
            if (p.y <= groundY || collidesWithCover(p.x, p.y, p.z, 0.15f)) {
                if (p.isRocket) triggerExplosion(p.x, p.y.coerceAtLeast(groundY), p.z, 10f, 150f)
                it.remove()
                continue
            }

            if (p.fromPlayer) {
                var hit = false
                val eIt = enemies.iterator()
                while (eIt.hasNext()) {
                    val enemy = eIt.next()
                    val bodyY = enemy.y + (if (enemy.kind == EnemyKind.HEAVY_DRONE) 0f else 1.0f)
                    val dx = p.x - enemy.x
                    val dz = p.z - enemy.z
                    val horizSq = dx * dx + dz * dz
                    val hitRadius = if (enemy.kind == EnemyKind.HEAVY_DRONE) 1.15f else 0.72f
                    val yMin = if (enemy.kind == EnemyKind.HEAVY_DRONE) bodyY - 0.8f else enemy.y
                    val yMax = if (enemy.kind == EnemyKind.HEAVY_DRONE) bodyY + 0.8f else enemy.y + 1.95f
                    if (horizSq <= hitRadius * hitRadius && p.y in yMin..yMax) {
                        if (p.isRocket) {
                            triggerExplosion(p.x, p.y, p.z, 10.5f, 165f)
                        } else {
                            val isHead = enemy.kind != EnemyKind.HEAVY_DRONE && p.y >= enemy.y + 1.48f
                            val dmg = p.damage * (if (isHead) 1.85f else 1.0f)
                            enemy.hp -= dmg
                            enemy.hitFlash = 0.18f
                            enemy.alertLevel = 1f
                            damageDealt += dmg.toInt()
                            hitmarkerTimer = 0.22f
                            hitmarkerHeadshot = isHead
                            audioEngine.play(if (isHead) SoundCue.HEADSHOT else SoundCue.HITMARKER, 0.75f)
                            if (enemy.hp <= 0f) {
                                handleEnemyKilled(enemy, isHead)
                                eIt.remove()
                            }
                        }
                        hit = true
                        break
                    }
                }
                if (hit) it.remove()
            } else {
                val dx = p.x - playerX
                val dz = p.z - playerZ
                val pEye = playerY + eyeHeight()
                if (dx * dx + dz * dz <= 0.45f * 0.45f && p.y in playerY..(pEye + 0.2f)) {
                    applyPlayerDamage(p.damage, true)
                    it.remove()
                }
            }
        }
    }

    private fun handleEnemyKilled(enemy: EnemyUnit, isHead: Boolean) {
        kills++
        if (isHead) headshots++
        val pts = when {
            enemy.kind == EnemyKind.HEAVY_DRONE -> 180
            isHead -> 140
            else -> 100
        }
        score += pts
        streakPoints += pts
        val label = when {
            enemy.kind == EnemyKind.HEAVY_DRONE -> "تدمير طائرة مسيّرة مدرعة (+180)"
            isHead -> "إصابة رأس قاتلة (+140)"
            else -> "تم القضاء على مقاتل معادي (+100)"
        }
        killFeed.add(0, KillFeedEntry(label, isHead, System.currentTimeMillis()))
        if (killFeed.size > 4) killFeed.removeAt(killFeed.lastIndex)

        if (Random.nextFloat() < 0.75f) {
            lootCrates.add(
                LootCrate(
                    enemy.x,
                    BattleMeshBuilder.getTerrainHeight(mapId, enemy.x, enemy.z) + 0.45f,
                    enemy.z,
                    if (enemy.kind == EnemyKind.HEAVY_DRONE) 1 else Random.nextInt(2)
                )
            )
        }
    }

    private fun applyPlayerDamage(dmg: Float, flash: Boolean) {
        if (flash) {
            damageFlashTimer = 0.35f
            onHapticPulse(false)
        }
        val absorbed = (dmg * 0.55f).coerceAtMost(armor)
        armor -= absorbed
        health -= (dmg - absorbed)
        if (health <= 0f) {
            health = 0f
            outcome = MatchOutcome.ELIMINATED
            showBanner("سقطت في أرض المعركة", 5f)
        }
    }

    private fun updateEnemies(dt: Float, weather: WeatherSnapshot) {
        val playerInSmoke = isInsideSmoke(playerX, playerZ)
        val sightRange = (58f * weather.visibilityMultiplier) *
            (if (isCrouching) 0.78f else 1f) *
            (if (playerInSmoke) 0.25f else 1f)

        for (e in enemies) {
            if (e.hitFlash > 0f) e.hitFlash = (e.hitFlash - dt).coerceAtLeast(0f)
            e.stateTimer += dt

            val dx = playerX - e.x
            val dz = playerZ - e.z
            val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.01f)
            val targetYaw = atan2(dx, dz)

            val canSee = dist <= sightRange && !isPathBlockedBySmoke(e.x, e.z, playerX, playerZ)
            if (canSee) {
                e.alertLevel = 1f
                e.lastSpottedTime = matchElapsedSec
            }

            if (e.alertLevel > 0.2f || canSee) {
                e.yaw = targetYaw
                val preferredDist = when (e.kind) {
                    EnemyKind.SNIPER -> 34f
                    EnemyKind.HEAVY_DRONE -> 22f
                    EnemyKind.RIFLEMAN -> 18f
                }
                var moveX = 0f
                var moveZ = 0f
                if (dist > preferredDist + 4f) {
                    moveX += dx / dist
                    moveZ += dz / dist
                } else if (dist < preferredDist - 5f) {
                    moveX -= dx / dist
                    moveZ -= dz / dist
                }
                // Tactical strafe
                if (e.stateTimer > 2.4f) {
                    e.stateTimer = 0f
                    e.strafeDir = -e.strafeDir
                }
                moveX += (-dz / dist) * e.strafeDir * 0.7f
                moveZ += (dx / dist) * e.strafeDir * 0.7f

                val moveSpeed = (if (e.kind == EnemyKind.HEAVY_DRONE) 5.5f else 3.8f) * weather.movementMultiplier
                val nx = (e.x + moveX * moveSpeed * dt).coerceIn(-76f, 76f)
                val nz = (e.z + moveZ * moveSpeed * dt).coerceAtInZone()
                if (e.kind == EnemyKind.HEAVY_DRONE || !collidesWithCover(nx, e.y + 0.9f, nz, 0.55f)) {
                    e.x = nx; e.z = nz
                }
                e.walkPhase += dt * 7f

                // Shoot at player if visible
                e.fireCooldown -= dt
                if (canSee && e.fireCooldown <= 0f) {
                    e.fireCooldown = when (e.kind) {
                        EnemyKind.SNIPER -> 2.1f
                        EnemyKind.HEAVY_DRONE -> 0.55f
                        EnemyKind.RIFLEMAN -> 0.95f
                    }
                    val gunY = if (e.kind == EnemyKind.HEAVY_DRONE) e.y else e.y + 1.45f
                    val toPlayerY = (playerY + eyeHeight() - 0.15f) - gunY
                    val d3 = sqrt(dx * dx + toPlayerY * toPlayerY + dz * dz).coerceAtLeast(0.01f)
                    val inaccuracy = 0.085f * weather.spreadMultiplier + (if (isSprinting) 0.04f else 0f)
                    val vx = (dx / d3 + (Random.nextFloat() - 0.5f) * inaccuracy) * 115f
                    val vy = (toPlayerY / d3 + (Random.nextFloat() - 0.5f) * inaccuracy * 0.5f) * 115f
                    val vz = (dz / d3 + (Random.nextFloat() - 0.5f) * inaccuracy) * 115f
                    projectiles.add(
                        Projectile(
                            x = e.x + (dx / dist) * 0.7f,
                            y = gunY,
                            z = e.z + (dz / dist) * 0.7f,
                            vx = vx,
                            vy = vy,
                            vz = vz,
                            fromPlayer = false,
                            isRocket = false,
                            damage = if (e.kind == EnemyKind.SNIPER) 16f else 8.5f,
                            life = 1.4f
                        )
                    )
                    if (dist < 65f * weather.soundPropagationMultiplier) {
                        val vol = (1f - dist / (65f * weather.soundPropagationMultiplier)).coerceIn(0.08f, 0.5f)
                        audioEngine.play(SoundCue.RIFLE_SHOT, vol)
                    }
                }
            } else {
                // Patrol gently
                val patrolAngle = e.id * 1.1f + matchElapsedSec * 0.35f
                val px = (e.x + cos(patrolAngle) * 2.2f * dt).coerceIn(-74f, 74f)
                val pz = (e.z + sin(patrolAngle) * 2.2f * dt).coerceIn(-74f, 74f)
                if (e.kind == EnemyKind.HEAVY_DRONE || !collidesWithCover(px, e.y + 0.9f, pz, 0.55f)) {
                    e.x = px; e.z = pz
                }
                e.yaw = patrolAngle
                e.walkPhase += dt * 4f
            }

            val gY = BattleMeshBuilder.getTerrainHeight(mapId, e.x, e.z)
            e.y = if (e.kind == EnemyKind.HEAVY_DRONE) {
                gY + 3.1f + sin(matchElapsedSec * 3f + e.id) * 0.35f
            } else {
                gY
            }
        }
    }

    private fun Float.coerceAtInZone(): Float = this.coerceIn(-76f, 76f)

    private fun updateExplosionsAndTracers(dt: Float) {
        val exIt = explosions.iterator()
        while (exIt.hasNext()) {
            val ex = exIt.next()
            ex.age += dt
            if (ex.age >= ex.maxAge) exIt.remove()
        }
        val trIt = tracers.iterator()
        while (trIt.hasNext()) {
            val tr = trIt.next()
            tr.life -= dt
            if (tr.life <= 0f) trIt.remove()
        }
    }

    private fun checkLootPickups() {
        val it = lootCrates.iterator()
        while (it.hasNext()) {
            val c = it.next()
            val dx = c.x - playerX
            val dz = c.z - playerZ
            if (dx * dx + dz * dz <= 2.6f * 2.6f) {
                if (c.kind == 0) {
                    reserveAmmo += maxMag * 2
                    medkitCount = (medkitCount + 1).coerceAtMost(5)
                    fragCount = (fragCount + 1).coerceAtMost(5)
                    showBanner("تم التقاط إمداد ذخيرة وإسعافات", 2f)
                } else {
                    armorPlateCount = (armorPlateCount + 1).coerceAtMost(5)
                    rpgAmmo = (rpgAmmo + 1).coerceAtMost(6)
                    smokeCount = (smokeCount + 1).coerceAtMost(4)
                    showBanner("تم التقاط دروع وصاروخ RPG-7", 2f)
                }
                audioEngine.play(SoundCue.RELOAD, 0.6f)
                it.remove()
            }
        }
    }

    private fun isInsideSmoke(x: Float, z: Float): Boolean {
        return smokeClouds.any { s ->
            val dx = s.x - x
            val dz = s.z - z
            dx * dx + dz * dz <= 8.5f * 8.5f
        }
    }

    private fun isPathBlockedBySmoke(x0: Float, z0: Float, x1: Float, z1: Float): Boolean {
        val mx = (x0 + x1) * 0.5f
        val mz = (z0 + z1) * 0.5f
        return isInsideSmoke(mx, mz) || isInsideSmoke(x1, z1)
    }

    private fun collidesWithCover(x: Float, y: Float, z: Float, margin: Float): Boolean {
        for (c in covers) {
            if (x >= c.x - c.halfW - margin && x <= c.x + c.halfW + margin &&
                z >= c.z - c.halfD - margin && z <= c.z + c.halfD + margin &&
                y >= c.y - c.halfH && y <= c.y + c.halfH
            ) {
                return true
            }
        }
        return false
    }

    fun eyeHeight(): Float = if (isCrouching) 1.05f else 1.68f

    fun getCurrentFov(): Float {
        if (!isAds) return 66f
        return (weaponId.adsZoomFov + attachmentId.adsFovDelta).coerceIn(14f, 52f)
    }

    fun getShakeOffset(): Float {
        return if (screenShakeTimer > 0f) (Random.nextFloat() - 0.5f) * 0.045f else 0f
    }

    fun isMuzzleFlashActive(): Boolean = muzzleFlashTimer > 0f

    // Player actions from UI
    fun addLookDelta(dxNorm: Float, dyNorm: Float, sensitivity: Float) {
        synchronized(lock) {
            val adsScale = if (isAds) (getCurrentFov() / 66f).coerceIn(0.28f, 0.85f) else 1f
            playerYaw -= dxNorm * 2.4f * sensitivity * adsScale
            playerPitch = (playerPitch - dyNorm * 1.9f * sensitivity * adsScale).coerceIn(-1.25f, 1.25f)
        }
    }

    fun toggleAds() {
        synchronized(lock) {
            isAds = !isAds
            if (isAds) isSprinting = false
        }
    }

    fun toggleCrouch() {
        synchronized(lock) {
            isCrouching = !isCrouching
            if (isCrouching) isSprinting = false
        }
    }

    fun toggleSprint() {
        synchronized(lock) {
            isSprinting = !isSprinting
            if (isSprinting) {
                isCrouching = false
                isAds = false
            }
        }
    }

    fun jump() {
        synchronized(lock) {
            if (grounded) {
                isCrouching = false
                playerVy = 8.4f
                grounded = false
            }
        }
    }

    fun startReload() {
        synchronized(lock) {
            if (usingRpg || isReloading || ammoInMag >= maxMag || reserveAmmo <= 0) return
            isReloading = true
            reloadTimerSec = weaponId.reloadMs / 1000f
            audioEngine.play(SoundCue.RELOAD, 0.7f)
        }
    }

    fun swapWeapon() {
        synchronized(lock) {
            if (rpgAmmo > 0 || usingRpg) {
                usingRpg = !usingRpg
                isReloading = false
                audioEngine.play(SoundCue.RELOAD, 0.55f)
            } else {
                showBanner("لا توجد صواريخ RPG متبقية", 1.8f)
            }
        }
    }

    fun throwGrenade(isSmoke: Boolean) {
        synchronized(lock) {
            if (isSmoke) {
                if (smokeCount <= 0) return
                smokeCount--
            } else {
                if (fragCount <= 0) return
                fragCount--
            }
            val dir = lookDirection(0f)
            val startY = playerY + eyeHeight() - 0.1f
            grenades.add(
                GrenadeEntity(
                    x = playerX + dir[0] * 0.7f,
                    y = startY,
                    z = playerZ + dir[2] * 0.7f,
                    vx = dir[0] * 22f,
                    vy = dir[1] * 22f + 4.8f,
                    vz = dir[2] * 22f,
                    isSmoke = isSmoke,
                    fuseSec = 1.65f
                )
            )
            audioEngine.play(SoundCue.RPG_LAUNCH, 0.4f)
        }
    }

    fun useMedkit() {
        synchronized(lock) {
            if (medkitCount <= 0 || health >= 99f || healTimerSec > 0f) return
            medkitCount--
            healTimerSec = 1.6f
        }
    }

    fun useArmorPlate() {
        synchronized(lock) {
            if (armorPlateCount <= 0 || armor >= 99f) return
            armorPlateCount--
            armor = (armor + 60f).coerceAtMost(100f)
            audioEngine.play(SoundCue.RELOAD, 0.65f)
            showBanner("تم تركيب درع كيفلر تكتيكي (+60 درع)", 2f)
        }
    }

    fun cycleOptics() {
        synchronized(lock) {
            val values = OpticsMode.entries
            opticsMode = values[(opticsMode.ordinal + 1) % values.size]
            audioEngine.play(SoundCue.HITMARKER, 0.45f)
            showBanner("البصريات: ${opticsMode.titleAr}", 1.8f)
        }
    }

    fun callTacticalStreak() {
        synchronized(lock) {
            if (streakPoints < tacticalId.pointsRequired) return
            streakPoints -= tacticalId.pointsRequired
            when (tacticalId) {
                TacticalId.AIRSTRIKE -> {
                    val fwd = lookDirection(0f)
                    airstrikeTargetX = (playerX + fwd[0] * 26f).coerceIn(-68f, 68f)
                    airstrikeTargetZ = (playerZ + fwd[2] * 26f).coerceIn(-68f, 68f)
                    jetX = airstrikeTargetX - 65f
                    jetZ = airstrikeTargetZ + 30f
                    jetY = 34f
                    airstrikeTimerSec = 3.5f
                    airstrikeBombsLeft = 5
                    airstrikeNextBomb = 0.65f
                    audioEngine.play(SoundCue.JET_FLYBY, 0.95f)
                    showBanner("تم استدعاء غارة جوية AC-130!", 3.2f)
                }
                TacticalId.UAV_DRONE -> {
                    uavTimerSec = 18f
                    audioEngine.play(SoundCue.HEADSHOT, 0.7f)
                    showBanner("طائرة الاستطلاع UAV نشطة • كشف كامل للأعداء", 3f)
                }
                TacticalId.ARMOR_DROP -> {
                    armor = 100f
                    health = (health + 45f).coerceAtMost(100f)
                    reserveAmmo += maxMag * 2
                    rpgAmmo = (rpgAmmo + 2).coerceAtMost(6)
                    audioEngine.play(SoundCue.RELOAD, 0.8f)
                    showBanner("وصل إمداد الدروع والذخيرة الكامل!", 2.5f)
                }
            }
        }
    }

    fun forceWeatherForTest(weather: WeatherType) {
        synchronized(lock) {
            weatherEngine.forceWeather(weather)
            showBanner("تغيير الطقس التكتيكي: ${weather.titleAr}", 2.6f)
        }
    }

    fun resumeAutoWeather() {
        synchronized(lock) {
            weatherEngine.resumeAutoCycle()
            showBanner("تم تفعيل دورة الطقس التلقائية", 2.2f)
        }
    }

    private fun showBanner(msg: String, duration: Float) {
        statusBanner = msg
        statusBannerTimer = duration
    }

    private fun lookDirection(spread: Float): FloatArray {
        val spX = if (spread > 0f) (Random.nextFloat() - 0.5f) * spread * 2f else 0f
        val spY = if (spread > 0f) (Random.nextFloat() - 0.5f) * spread * 2f else 0f
        val yaw = playerYaw + spX
        val pitch = playerPitch + spY
        val cosP = cos(pitch)
        val dx = -sin(yaw) * cosP
        val dy = sin(pitch)
        val dz = -cos(yaw) * cosP
        return floatArrayOf(dx, dy, dz)
    }

    fun snapshotHud(): BattleHudState = synchronized(lock) {
        val radarRange = 55f
        val blips = ArrayList<MinimapBlip>(enemies.size + lootCrates.size)
        val uavOn = uavTimerSec > 0f
        val detectMult = weatherSnapshot.soundPropagationMultiplier
        for (e in enemies) {
            val dx = e.x - playerX
            val dz = e.z - playerZ
            val dist = sqrt(dx * dx + dz * dz)
            if (uavOn || e.alertLevel > 0.3f || dist <= radarRange * detectMult) {
                blips.add(MinimapBlip(dx, dz, e.kind == EnemyKind.HEAVY_DRONE, false))
            }
        }
        for (c in lootCrates) {
            blips.add(MinimapBlip(c.x - playerX, c.z - playerZ, false, true))
        }

        val reloadFrac = if (isReloading) {
            (1f - reloadTimerSec / (weaponId.reloadMs / 1000f)).coerceIn(0f, 1f)
        } else 0f

        BattleHudState(
            health = health.toInt().coerceIn(0, 100),
            armor = armor.toInt().coerceIn(0, 100),
            stamina = stamina.toInt().coerceIn(0, 100),
            aliveEnemies = enemies.size + remainingToSpawn,
            totalEnemies = modeId.enemyTotal,
            kills = kills,
            headshots = headshots,
            damageDealt = damageDealt,
            score = score,
            survivalSeconds = matchElapsedSec.toInt(),
            ammoInMag = ammoInMag,
            maxMag = maxMag,
            reserveAmmo = reserveAmmo,
            rpgAmmo = rpgAmmo,
            fragCount = fragCount,
            smokeCount = smokeCount,
            medkitCount = medkitCount,
            armorPlateCount = armorPlateCount,
            usingRpg = usingRpg,
            isAds = isAds,
            isCrouching = isCrouching,
            isSprinting = isSprinting,
            isReloading = isReloading,
            reloadProgress = reloadFrac,
            opticsMode = opticsMode,
            weaponId = weaponId,
            attachmentId = attachmentId,
            tacticalId = tacticalId,
            streakPoints = streakPoints,
            streakReady = streakPoints >= tacticalId.pointsRequired,
            uavActiveSec = uavTimerSec.toInt(),
            airstrikeActive = airstrikeTimerSec > 0f,
            safeZoneRadius = safeZoneRadius,
            outsideZone = sqrt(playerX * playerX + playerZ * playerZ) > safeZoneRadius,
            weather = weatherSnapshot,
            hitmarkerAlpha = (hitmarkerTimer / 0.22f).coerceIn(0f, 1f),
            hitmarkerHeadshot = hitmarkerHeadshot,
            damageFlashAlpha = (damageFlashTimer / 0.35f).coerceIn(0f, 1f),
            healingProgress = if (healTimerSec > 0f) (1f - healTimerSec / 1.6f).coerceIn(0f, 1f) else 0f,
            minimapBlips = blips,
            playerYawDeg = Math.toDegrees(playerYaw.toDouble()).toFloat(),
            killFeed = killFeed.toList(),
            statusBanner = statusBanner,
            matchOutcome = outcome
        )
    }

    fun <T> withRenderLock(block: (BattleSimulation) -> T): T = synchronized(lock) {
        block(this)
    }
}
