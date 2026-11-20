package com.example.ui.screens

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.GameModeId
import com.example.data.model.MatchOutcome
import com.example.data.model.OpticsMode
import com.example.data.model.SoldierProfile
import com.example.data.model.WeaponId
import com.example.data.model.WeatherType
import com.example.data.preferences.GameSettings
import com.example.engine.BattleGLRenderer
import com.example.engine.BattleHudState
import com.example.engine.BattleSimulation
import com.example.engine.TacticalAudioEngine
import com.example.ui.theme.ArcticCyan
import com.example.ui.theme.ArmoredSlate
import com.example.ui.theme.CrispIvory
import com.example.ui.theme.CrimsonThreat
import com.example.ui.theme.GunmetalSurface
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NightVisionGreen
import com.example.ui.theme.SandstormGold
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TacticalObsidian
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BattleScreen(
    profile: SoldierProfile,
    modeId: GameModeId,
    settings: GameSettings,
    onMatchFinished: (BattleHudState, List<String>) -> Unit,
    onExitBattle: () -> Unit,
    onRedeploy: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val audioEngine = remember { TacticalAudioEngine(settings.soundEnabled) }
    LaunchedEffect(settings.soundEnabled) {
        audioEngine.enabled = settings.soundEnabled
    }

    val sim = remember(profile.mapEnum(), modeId, profile.weaponEnum(), profile.attachmentEnum(), profile.tacticalEnum()) {
        BattleSimulation(
            mapId = profile.mapEnum(),
            modeId = modeId,
            weaponId = profile.weaponEnum(),
            attachmentId = profile.attachmentEnum(),
            tacticalId = profile.tacticalEnum(),
            autoWeatherCycle = settings.autoWeatherCycle,
            graphicsQuality = settings.graphicsQuality,
            audioEngine = audioEngine,
            onHapticPulse = { heavy ->
                if (settings.hapticsEnabled) triggerTacticalHaptic(context, heavy)
            }
        )
    }

    var hud by remember { mutableStateOf(sim.snapshotHud()) }
    var showPauseDrawer by remember { mutableStateOf(false) }
    var matchRecorded by remember { mutableStateOf(false) }

    LaunchedEffect(hud.matchOutcome) {
        if (hud.matchOutcome != null && !matchRecorded) {
            matchRecorded = true
            onMatchFinished(hud, sim.weatherEngine.encounteredWeathers.toList())
        }
    }

    var glViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> glViewRef?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            audioEngine.release()
        }
    }

    BackHandler {
        if (hud.matchOutcome == null) {
            showPauseDrawer = !showPauseDrawer
        } else {
            onExitBattle()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(TacticalObsidian)) {
        // 1. OpenGL ES 3.0 3D Battlefield Canvas
        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(3)
                    setRenderer(BattleGLRenderer(sim) { newHud -> hud = newHud })
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    glViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Atmospheric Weather & Optics Screen Overlay
        if (hud.weather.sandIntensity > 0.1f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SandstormGold.copy(alpha = 0.12f * hud.weather.sandIntensity))
            )
        }
        if (hud.damageFlashAlpha > 0.02f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CrimsonThreat.copy(alpha = 0.34f * hud.damageFlashAlpha))
            )
        }
        if (hud.opticsMode != OpticsMode.NORMAL) {
            val optColor = if (hud.opticsMode == OpticsMode.NVG) NightVisionGreen else ArcticCyan
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, optColor.copy(alpha = 0.65f))
            )
        }

        // 3. Center Reticle / Sniper 8x Scope / Hitmarker
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (hud.isAds && hud.weaponId == WeaponId.AWM && !hud.usingRpg) {
                SniperScopeOverlay(hud.weather.windX)
            } else {
                DynamicCrosshair(
                    spreadPx = (14f * hud.weather.spreadMultiplier * (if (hud.isAds) 0.35f else 1f) * (if (hud.isCrouching) 0.72f else 1f)),
                    hitmarkerAlpha = hud.hitmarkerAlpha,
                    isHeadshot = hud.hitmarkerHeadshot
                )
            }
        }

        // 4. Touch-Look Right Zone (behind right buttons so drag anywhere aims smoothly)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(settings.aimSensitivity) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        sim.addLookDelta(
                            dxNorm = dragAmount.x / size.width.coerceAtLeast(1),
                            dyNorm = dragAmount.y / size.height.coerceAtLeast(1),
                            sensitivity = settings.aimSensitivity
                        )
                    }
                }
        )

        // 5. Top Tactical HUD (Alive, Kills, Dynamic Weather Impact, Minimap)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Left: Minimap + Pause button
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    TacticalMinimap(hud = hud)
                    Surface(
                        color = ArmoredSlate.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SubtleBorder),
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { showPauseDrawer = true }
                            .testTag("pause_battle_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Pause, contentDescription = "إيقاف مؤقت", tint = CrispIvory)
                        }
                    }
                }

                // Center: Match Counters & Dynamic Weather Badge
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HudBadge("الناجون ${hud.aliveEnemies}", TacticalAmber)
                        HudBadge("قتل ${hud.kills}", CrimsonThreat)
                        HudBadge("${hud.score} PTS", ArcticCyan)
                    }
                    // Dynamic Weather Telemetry Strip
                    val w = hud.weather.currentWeather
                    Surface(
                        color = ArmoredSlate.copy(alpha = 0.86f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, weatherColor(w).copy(alpha = 0.65f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(weatherIcon(w), contentDescription = null, tint = weatherColor(w), modifier = Modifier.size(16.dp))
                                Text(w.titleAr, style = MaterialTheme.typography.labelLarge, color = CrispIvory)
                                if (hud.weather.nextChangeCountdownSec > 0) {
                                    Text(
                                        "${hud.weather.nextChangeCountdownSec}s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MutedSteel
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("رؤية ${(hud.weather.visibilityMultiplier * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = ArcticCyan)
                                Text("صوت ${(hud.weather.soundPropagationMultiplier * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = NightVisionGreen)
                                Text("حركة ${(hud.weather.movementMultiplier * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = TacticalAmber)
                            }
                        }
                    }
                }
            }

            // Incoming Weather Front Warning
            hud.weather.incomingAlertWeather?.let { nextW ->
                Surface(
                    color = weatherColor(nextW).copy(alpha = 0.24f),
                    border = BorderStroke(1.dp, weatherColor(nextW)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "تحذير جوي قادم: ${nextW.titleAr} خلال ${hud.weather.nextChangeCountdownSec} ثوانٍ",
                        style = MaterialTheme.typography.labelLarge,
                        color = CrispIvory,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Safe Zone Storm Warning or Status Banner
            if (hud.outsideZone) {
                Surface(
                    color = CrimsonThreat.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        "تحذير! أنت خارج منطقة الأمان — عد إلى الدائرة فوراً",
                        style = MaterialTheme.typography.labelLarge,
                        color = CrispIvory,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            } else if (hud.statusBanner != null) {
                Surface(
                    color = ArmoredSlate.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, TacticalAmber.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = hud.statusBanner!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TacticalAmber,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            // Kill Feed
            Column(
                modifier = Modifier.align(Alignment.End),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                hud.killFeed.forEach { entry ->
                    Surface(
                        color = TacticalObsidian.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = entry.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (entry.isHeadshot) TacticalAmber else CrispIvory,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // 6. Bottom Controls & Vital Bars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // Left Cluster: Virtual Thumbstick + Stance Buttons + Health/Armor
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // Health & Armor Bars
                Surface(
                    color = ArmoredSlate.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SubtleBorder),
                    modifier = Modifier.width(175.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Favorite, contentDescription = null, tint = CrimsonThreat, modifier = Modifier.size(14.dp))
                            LinearProgressIndicator(
                                progress = { hud.health / 100f },
                                color = if (hud.health > 30) NightVisionGreen else CrimsonThreat,
                                trackColor = GunmetalSurface,
                                modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                            )
                            Text("${hud.health}", style = MaterialTheme.typography.labelSmall, color = CrispIvory)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Security, contentDescription = null, tint = ArcticCyan, modifier = Modifier.size(14.dp))
                            LinearProgressIndicator(
                                progress = { hud.armor / 100f },
                                color = ArcticCyan,
                                trackColor = GunmetalSurface,
                                modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                            )
                            Text("${hud.armor}", style = MaterialTheme.typography.labelSmall, color = CrispIvory)
                        }
                    }
                }

                // Stance + Medkit/Armor quick buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TacticalActionChip(
                        label = if (hud.isSprinting) "ركض" else "مشي",
                        active = hud.isSprinting,
                        onClick = { sim.toggleSprint() }
                    )
                    TacticalActionChip(
                        label = if (hud.isCrouching) "منحني" else "انحناء",
                        active = hud.isCrouching,
                        onClick = { sim.toggleCrouch() }
                    )
                    TacticalActionChip(
                        label = "قفز",
                        active = false,
                        onClick = { sim.jump() }
                    )
                }

                // Virtual Thumbstick
                VirtualThumbstick(
                    onMove = { mx, my ->
                        sim.moveInputX = mx
                        sim.moveInputY = my
                    }
                )
            }

            // Right Cluster: Weapon, Optics, Grenades, Streaks & Fire/ADS
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                // Tactical Streak & Optics Row
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Killstreak button
                    Surface(
                        color = if (hud.streakReady) TacticalAmber else ArmoredSlate.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (hud.streakReady) TacticalAmber else SubtleBorder),
                        modifier = Modifier
                            .height(48.dp)
                            .clickable(enabled = hud.streakReady) { sim.callTacticalStreak() }
                            .testTag("streak_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.AirplanemodeActive,
                                contentDescription = hud.tacticalId.titleAr,
                                tint = if (hud.streakReady) TacticalObsidian else MutedSteel,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (hud.streakReady) "استدعاء" else "${hud.streakPoints}/${hud.tacticalId.pointsRequired}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hud.streakReady) TacticalObsidian else CrispIvory
                            )
                        }
                    }

                    // NVG / Thermal Optics Toggle
                    Surface(
                        color = if (hud.opticsMode != OpticsMode.NORMAL) NightVisionGreen.copy(alpha = 0.25f) else ArmoredSlate.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (hud.opticsMode != OpticsMode.NORMAL) NightVisionGreen else SubtleBorder),
                        modifier = Modifier
                            .height(48.dp)
                            .clickable { sim.cycleOptics() }
                            .testTag("optics_toggle_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Filled.Visibility, contentDescription = "تبديل البصريات", tint = NightVisionGreen, modifier = Modifier.size(18.dp))
                            Text(hud.opticsMode.code, style = MaterialTheme.typography.labelSmall, color = CrispIvory)
                        }
                    }
                }

                // Consumables & Grenades Row
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TacticalActionChip("إسعاف ${hud.medkitCount}", hud.healingProgress > 0f) { sim.useMedkit() }
                    TacticalActionChip("درع ${hud.armorPlateCount}", false) { sim.useArmorPlate() }
                    TacticalActionChip("شظايا ${hud.fragCount}", false) { sim.throwGrenade(false) }
                    TacticalActionChip("دخان ${hud.smokeCount}", false) { sim.throwGrenade(true) }
                }

                // Weapon Ammo HUD + Swap + Reload
                Surface(
                    color = ArmoredSlate.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SubtleBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (hud.usingRpg) "RPG-7" else hud.weaponId.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = TacticalAmber
                            )
                            Text(
                                text = if (hud.usingRpg) "${hud.rpgAmmo} صواريخ"
                                else if (hud.isReloading) "تلقيم ${(hud.reloadProgress * 100).toInt()}%"
                                else "${hud.ammoInMag} / ${hud.reserveAmmo}",
                                style = MaterialTheme.typography.titleMedium,
                                color = CrispIvory
                            )
                        }
                        Surface(
                            color = GunmetalSurface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(48.dp).clickable { sim.startReload() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Refresh, contentDescription = "تلقيم", tint = CrispIvory)
                            }
                        }
                        Surface(
                            color = if (hud.usingRpg) TacticalAmber else GunmetalSurface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(48.dp).clickable { sim.swapWeapon() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.RocketLaunch,
                                    contentDescription = "تبديل لقاذف RPG",
                                    tint = if (hud.usingRpg) TacticalObsidian else TacticalAmber
                                )
                            }
                        }
                    }
                }

                // ADS Scope + Main Fire Button
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = if (hud.isAds) ArcticCyan else ArmoredSlate.copy(alpha = 0.88f),
                        shape = CircleShape,
                        border = BorderStroke(1.5.dp, ArcticCyan),
                        modifier = Modifier
                            .size(58.dp)
                            .clickable { sim.toggleAds() }
                            .testTag("ads_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.ZoomIn,
                                contentDescription = "تصويب بالمنظار",
                                tint = if (hud.isAds) TacticalObsidian else ArcticCyan,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Hold-to-Fire Trigger
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(CrimsonThreat.copy(alpha = 0.88f))
                            .border(3.dp, TacticalAmber, CircleShape)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val anyDown = event.changes.any { it.pressed }
                                        sim.isFiringHeld = anyDown
                                    }
                                }
                            }
                            .testTag("fire_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "إطلاق",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = CrispIvory
                        )
                    }
                }
            }
        }

        // 7. Pause & Dynamic Weather Control Overlay
        if (showPauseDrawer && hud.matchOutcome == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TacticalObsidian.copy(alpha = 0.82f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ArmoredSlate),
                    border = BorderStroke(1.dp, TacticalAmber.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .widthIn(max = 520.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(18.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("غرفة التحكم التكتيكي والطقس", style = MaterialTheme.typography.headlineMedium, color = CrispIvory)
                        Text(
                            "يتغير الطقس تلقائياً أثناء المعركة، ويمكنك أيضاً تجربة أي حالة جوية فوراً لملاحظة تأثيرها على الرؤية والصوت والأسلحة:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSteel
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WeatherType.entries.forEach { w ->
                                val active = hud.weather.targetWeather == w
                                Surface(
                                    color = if (active) weatherColor(w).copy(alpha = 0.25f) else GunmetalSurface,
                                    border = BorderStroke(1.dp, if (active) weatherColor(w) else SubtleBorder),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .clickable { sim.forceWeatherForTest(w) }
                                        .testTag("force_weather_${w.name}")
                                ) {
                                    Text(
                                        w.titleAr,
                                        color = CrispIvory,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { sim.resumeAutoWeather() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("استئناف دورة الطقس التلقائية")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = onExitBattle,
                                border = BorderStroke(1.dp, CrimsonThreat),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonThreat),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("انسحاب من المعركة")
                            }
                            Button(
                                onClick = { showPauseDrawer = false },
                                colors = ButtonDefaults.buttonColors(containerColor = TacticalAmber, contentColor = TacticalObsidian),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("استئناف القتال")
                            }
                        }
                    }
                }
            }
        }

        // 8. Post-Match After-Action Report Modal
        hud.matchOutcome?.let { outcome ->
            val isWin = outcome == MatchOutcome.VICTORY
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TacticalObsidian.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ArmoredSlate),
                    border = BorderStroke(2.dp, if (isWin) NightVisionGreen else CrimsonThreat),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .widthIn(max = 500.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = outcome.titleAr,
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (isWin) NightVisionGreen else CrimsonThreat
                        )
                        Text(
                            text = "${profile.mapEnum().titleAr} • ${hud.weaponId.titleAr}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSteel
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SummaryBox("القتل", "${hud.kills}", TacticalAmber, Modifier.weight(1f))
                            SummaryBox("إصابة رأس", "${hud.headshots}", CrimsonThreat, Modifier.weight(1f))
                            SummaryBox("النقاط", "${hud.score}", ArcticCyan, Modifier.weight(1f))
                        }
                        Text(
                            text = "مدة البقاء: ${hud.survivalSeconds} ثانية • الضرر: ${hud.damageDealt}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CrispIvory
                        )
                        val weatherTitles = sim.weatherEngine.encounteredWeathers.mapNotNull { name ->
                            enumValues<WeatherType>().firstOrNull { it.name == name }?.titleAr
                        }.joinToString(" • ")
                        Text(
                            text = "الأحوال الجوية المواجهة: $weatherTitles",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ArcticCyan
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = onExitBattle,
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) {
                                Text("القيادة")
                            }
                            Button(
                                onClick = onRedeploy,
                                colors = ButtonDefaults.buttonColors(containerColor = TacticalAmber, contentColor = TacticalObsidian),
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) {
                                Text("إعادة الانتشار")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudBadge(text: String, color: Color) {
    Surface(
        color = ArmoredSlate.copy(alpha = 0.84f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun TacticalActionChip(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (active) TacticalAmber else ArmoredSlate.copy(alpha = 0.85f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (active) TacticalAmber else SubtleBorder),
        modifier = Modifier
            .height(48.dp)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) TacticalObsidian else CrispIvory
            )
        }
    }
}

@Composable
private fun SummaryBox(label: String, value: String, color: Color, modifier: Modifier) {
    Surface(
        color = GunmetalSurface,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = color)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
        }
    }
}

@Composable
private fun VirtualThumbstick(
    onMove: (Float, Float) -> Unit
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    val maxRadius = 62f

    Box(
        modifier = Modifier
            .size(124.dp)
            .clip(CircleShape)
            .background(ArmoredSlate.copy(alpha = 0.55f))
            .border(2.dp, CrispIvory.copy(alpha = 0.25f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        knobOffset = Offset.Zero
                        onMove(0f, 0f)
                    },
                    onDragCancel = {
                        knobOffset = Offset.Zero
                        onMove(0f, 0f)
                    }
                ) { change, dragAmount ->
                    change.consume()
                    val next = knobOffset + dragAmount
                    val len = sqrt(next.x * next.x + next.y * next.y)
                    val clamped = if (len > maxRadius) {
                        Offset(next.x / len * maxRadius, next.y / len * maxRadius)
                    } else next
                    knobOffset = clamped
                    // Screen +Y is down, game forward +Y is up (-Y screen)
                    onMove(clamped.x / maxRadius, -clamped.y / maxRadius)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(knobOffset.x.roundToInt(), knobOffset.y.roundToInt()) }
                .size(48.dp)
                .clip(CircleShape)
                .background(TacticalAmber.copy(alpha = 0.85f))
                .border(2.dp, CrispIvory, CircleShape)
        )
    }
}

@Composable
private fun TacticalMinimap(hud: BattleHudState) {
    Surface(
        color = TacticalObsidian.copy(alpha = 0.84f),
        shape = CircleShape,
        border = BorderStroke(1.5.dp, TacticalAmber.copy(alpha = 0.7f)),
        modifier = Modifier.size(92.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val scale = size.width / 120f

            // Safe Zone Circle approximation
            drawCircle(
                color = ArcticCyan.copy(alpha = 0.45f),
                radius = (hud.safeZoneRadius * scale * 0.55f).coerceAtMost(cx),
                style = Stroke(width = 2f)
            )
            // Blips rotated relative to player yaw
            val yawRad = Math.toRadians(hud.playerYawDeg.toDouble()).toFloat()
            val c = cos(yawRad)
            val s = sin(yawRad)
            for (blip in hud.minimapBlips) {
                val rx = blip.dx * c - blip.dz * s
                val rz = blip.dx * s + blip.dz * c
                val px = cx + rx * scale
                val py = cy + rz * scale
                if ((px - cx) * (px - cx) + (py - cy) * (py - cy) <= cx * cx) {
                    val col = when {
                        blip.isLoot -> NightVisionGreen
                        blip.isDrone -> TacticalAmber
                        else -> CrimsonThreat
                    }
                    drawCircle(color = col, radius = if (blip.isDrone) 4.5f else 3.5f, center = Offset(px, py))
                }
            }
            // Player center marker
            drawCircle(color = CrispIvory, radius = 4f, center = Offset(cx, cy))
        }
    }
}

@Composable
private fun DynamicCrosshair(
    spreadPx: Float,
    hitmarkerAlpha: Float,
    isHeadshot: Boolean
) {
    Canvas(modifier = Modifier.size(90.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val gap = spreadPx.coerceIn(5f, 36f)
        val len = 10f
        val col = CrispIvory.copy(alpha = 0.9f)
        drawLine(col, Offset(cx - gap - len, cy), Offset(cx - gap, cy), strokeWidth = 2.5f)
        drawLine(col, Offset(cx + gap, cy), Offset(cx + gap + len, cy), strokeWidth = 2.5f)
        drawLine(col, Offset(cx, cy - gap - len), Offset(cx, cy - gap), strokeWidth = 2.5f)
        drawLine(col, Offset(cx, cy + gap), Offset(cx, cy + gap + len), strokeWidth = 2.5f)
        drawCircle(TacticalAmber, radius = 2.2f, center = Offset(cx, cy))

        if (hitmarkerAlpha > 0.05f) {
            val hmCol = (if (isHeadshot) CrimsonThreat else CrispIvory).copy(alpha = hitmarkerAlpha)
            val d1 = 7f; val d2 = 17f
            drawLine(hmCol, Offset(cx - d2, cy - d2), Offset(cx - d1, cy - d1), strokeWidth = 3f)
            drawLine(hmCol, Offset(cx + d2, cy - d2), Offset(cx + d1, cy - d1), strokeWidth = 3f)
            drawLine(hmCol, Offset(cx - d2, cy + d2), Offset(cx - d1, cy + d1), strokeWidth = 3f)
            drawLine(hmCol, Offset(cx + d2, cy + d2), Offset(cx + d1, cy + d1), strokeWidth = 3f)
        }
    }
}

@Composable
private fun SniperScopeOverlay(windX: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(size.width, size.height) * 0.42f
        drawCircle(color = TacticalObsidian, radius = size.maxDimension, style = Stroke(width = (size.maxDimension - r) * 2f))
        drawCircle(color = TacticalAmber.copy(alpha = 0.85f), radius = r, style = Stroke(width = 3f))
        drawLine(CrispIvory.copy(alpha = 0.8f), Offset(cx - r, cy), Offset(cx + r, cy), strokeWidth = 1.6f)
        drawLine(CrispIvory.copy(alpha = 0.8f), Offset(cx, cy - r), Offset(cx, cy + r), strokeWidth = 1.6f)
        // Windage ticks
        for (i in -3..3) {
            if (i != 0) {
                val x = cx + i * 24f + windX * 2f
                drawLine(TacticalAmber, Offset(x, cy - 6f), Offset(x, cy + 6f), strokeWidth = 1.6f)
            }
        }
        drawCircle(CrimsonThreat, radius = 3.5f, center = Offset(cx, cy))
    }
}

private fun triggerTacticalHaptic(context: Context, heavy: Boolean) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            mgr?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createOneShot(
                    if (heavy) 55L else 20L,
                    if (heavy) 210 else 110
                )
            )
        }
    } catch (_: Exception) {
    }
}
