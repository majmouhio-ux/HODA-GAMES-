package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.firebase.signOut
import com.example.data.model.AttachmentId
import com.example.data.model.BattleRecord
import com.example.data.model.GameModeId
import com.example.data.model.GraphicsQuality
import com.example.data.model.MapId
import com.example.data.model.MatchOutcome
import com.example.data.model.SoldierProfile
import com.example.data.model.TacticalId
import com.example.data.model.UiState
import com.example.data.model.WeaponId
import com.example.data.model.WeatherType
import com.example.data.preferences.GameSettings
import com.example.ui.CommandViewModel
import com.example.ui.theme.ArcticCyan
import com.example.ui.theme.ArmoredSlate
import com.example.ui.theme.CrispIvory
import com.example.ui.theme.CrimsonThreat
import com.example.ui.theme.GunmetalElevated
import com.example.ui.theme.GunmetalSurface
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.NightVisionGreen
import com.example.ui.theme.SandstormGold
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TacticalAmber
import com.example.ui.theme.TacticalObsidian

@Composable
fun CommandHubScreen(
    viewModel: CommandViewModel,
    onDeployBattle: (SoldierProfile, GameModeId) -> Unit
) {
    val profileUi by viewModel.profileState.collectAsStateWithLifecycle()
    val matchesUi by viewModel.matchesState.collectAsStateWithLifecycle()
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = TacticalObsidian,
        bottomBar = {
            NavigationBar(
                containerColor = ArmoredSlate,
                windowInsets = WindowInsets.navigationBars
            ) {
                val tabs = listOf(
                    Triple(0, stringResource(R.string.nav_lobby), Icons.Filled.GpsFixed),
                    Triple(1, stringResource(R.string.nav_armory), Icons.Filled.Shield),
                    Triple(2, stringResource(R.string.nav_records), Icons.Filled.History),
                    Triple(3, stringResource(R.string.nav_settings), Icons.Filled.Settings)
                )
                tabs.forEach { (idx, label, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TacticalObsidian,
                            selectedTextColor = TacticalAmber,
                            indicatorColor = TacticalAmber,
                            unselectedIconColor = MutedSteel,
                            unselectedTextColor = MutedSteel
                        ),
                        modifier = Modifier.testTag("nav_tab_$idx")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            when (val pState = profileUi) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = TacticalAmber)
                            Text("جاري تجهيز غرفة العمليات...", color = MutedSteel)
                        }
                    }
                }
                is UiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = pState.message,
                            color = CrimsonThreat,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                is UiState.Success -> {
                    val profile = pState.data
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp)
                            .align(Alignment.TopCenter)
                    ) {
                        if (actionError != null) {
                            Surface(
                                color = CrimsonThreat.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, CrimsonThreat),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clickable { viewModel.clearActionError() }
                            ) {
                                Text(
                                    text = actionError!!,
                                    color = CrispIvory,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        when (selectedTab) {
                            0 -> LobbyTab(
                                profile = profile,
                                selectedMode = selectedMode,
                                onSelectMap = { viewModel.updateLoadout(profile, map = it) },
                                onSelectMode = viewModel::selectMode,
                                onDeploy = { onDeployBattle(profile, selectedMode) }
                            )
                            1 -> ArmoryTab(
                                profile = profile,
                                onSelectWeapon = { viewModel.updateLoadout(profile, weapon = it) },
                                onSelectAttachment = { viewModel.updateLoadout(profile, attachment = it) },
                                onSelectTactical = { viewModel.updateLoadout(profile, tactical = it) }
                            )
                            2 -> RecordsTab(
                                profile = profile,
                                matchesUi = matchesUi
                            )
                            3 -> SettingsTab(
                                profile = profile,
                                settings = settings,
                                onUpdateCallsign = { viewModel.updateLoadout(profile, callsign = it) },
                                onSetSound = viewModel::setSoundEnabled,
                                onSetHaptics = viewModel::setHapticsEnabled,
                                onSetSensitivity = viewModel::setAimSensitivity,
                                onSetGraphics = viewModel::setGraphicsQuality,
                                onSetAutoWeather = viewModel::setAutoWeatherCycle
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LobbyTab(
    profile: SoldierProfile,
    selectedMode: GameModeId,
    onSelectMap: (MapId) -> Unit,
    onSelectMode: (GameModeId) -> Unit,
    onDeploy: () -> Unit
) {
    val activeMap = profile.mapEnum()
    val activeWeapon = profile.weaponEnum()
    val activeTactical = profile.tacticalEnum()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Warzone Banner & Soldier Rank Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(215.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, TacticalAmber.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            ) {
                Image(
                    painter = painterResource(R.drawable.img_warzone_hero),
                    contentDescription = "بانر ساحة القتال",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    TacticalObsidian.copy(alpha = 0.72f),
                                    TacticalObsidian.copy(alpha = 0.96f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = TacticalAmber,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = profile.rankTitle,
                                color = TacticalObsidian,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            color = ArmoredSlate.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SubtleBorder)
                        ) {
                            Text(
                                text = "XP ${profile.xp} // W:${profile.wins}",
                                color = ArcticCyan,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = profile.callsign,
                            style = MaterialTheme.typography.headlineMedium,
                            color = CrispIvory
                        )
                        Text(
                            text = "${activeWeapon.titleAr} • ${activeTactical.titleAr}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TacticalAmber
                        )
                    }
                }
            }
        }

        // Primary Deploy Button
        item {
            Button(
                onClick = onDeploy,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TacticalAmber,
                    contentColor = TacticalObsidian
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .testTag("deploy_battle_button")
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.deploy_battle),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Map Selection
        item {
            Text("مسرح العمليات الحربية", style = MaterialTheme.typography.titleLarge, color = CrispIvory)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MapId.entries.forEach { map ->
                    val isSelected = map == activeMap
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) GunmetalElevated else ArmoredSlate
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) TacticalAmber else SubtleBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectMap(map) }
                            .testTag("map_card_${map.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(map.titleAr, style = MaterialTheme.typography.titleMedium, color = CrispIvory)
                                Text(map.descAr, style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                                Text(
                                    text = "الطقس المبدئي: ${map.initialWeather.titleAr}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ArcticCyan,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            if (isSelected) {
                                Surface(color = TacticalAmber, shape = RoundedCornerShape(8.dp)) {
                                    Text(
                                        "محدد",
                                        color = TacticalObsidian,
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Game Mode Selection
        item {
            Text("نمط الاشتباك", style = MaterialTheme.typography.titleLarge, color = CrispIvory)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(GameModeId.entries) { mode ->
                    val selected = mode == selectedMode
                    Surface(
                        color = if (selected) TacticalAmber.copy(alpha = 0.16f) else ArmoredSlate,
                        border = BorderStroke(1.dp, if (selected) TacticalAmber else SubtleBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .widthIn(min = 210.dp, max = 260.dp)
                            .clickable { onSelectMode(mode) }
                            .testTag("mode_card_${mode.name}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                mode.titleAr,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (selected) TacticalAmber else CrispIvory
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(mode.descAr, style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                        }
                    }
                }
            }
        }

        // Dynamic Weather System Tactical Briefing
        item {
            Text("النظام الجوي الديناميكي في المعركة", style = MaterialTheme.typography.titleLarge, color = CrispIvory)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(WeatherType.entries) { w ->
                    Surface(
                        color = ArmoredSlate,
                        border = BorderStroke(1.dp, SubtleBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.widthIn(min = 215.dp, max = 245.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = weatherIcon(w),
                                    contentDescription = w.titleAr,
                                    tint = weatherColor(w),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(w.titleAr, style = MaterialTheme.typography.titleMedium, color = CrispIvory)
                            }
                            Text(w.impactSummaryAr, style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("رؤية ${(w.visibilityMultiplier * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = ArcticCyan)
                                Text("صوت ${(w.soundPropagationMultiplier * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = NightVisionGreen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArmoryTab(
    profile: SoldierProfile,
    onSelectWeapon: (WeaponId) -> Unit,
    onSelectAttachment: (AttachmentId) -> Unit,
    onSelectTactical: (TacticalId) -> Unit
) {
    val activeWeapon = profile.weaponEnum()
    val activeAttachment = profile.attachmentEnum()
    val activeTactical = profile.tacticalEnum()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("الترسانة الرئيسية", style = MaterialTheme.typography.headlineMedium, color = CrispIvory)
            Text("اختر سلاحك الأساسي وملحقاته التكتيكية للقتال", style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
        }

        items(WeaponId.entries) { weapon ->
            val selected = weapon == activeWeapon
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) GunmetalElevated else ArmoredSlate
                ),
                border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) TacticalAmber else SubtleBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectWeapon(weapon) }
                    .testTag("weapon_card_${weapon.name}")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(weapon.titleAr, style = MaterialTheme.typography.titleMedium, color = CrispIvory)
                            Text("${weapon.code} • ${weapon.roleAr}", style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                        }
                        if (selected) {
                            Surface(color = TacticalAmber, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    "مجهز",
                                    color = TacticalObsidian,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    StatBar("الضرر الفتاك", weapon.damage / 115f, CrimsonThreat)
                    StatBar("معدل الإطلاق", (1000f / weapon.fireRateMs) / 13f, TacticalAmber)
                    StatBar("اختراق الدروع", weapon.armorPenetration, ArcticCyan)
                }
            }
        }

        item {
            Text("الملحق التكتيكي", style = MaterialTheme.typography.titleLarge, color = CrispIvory)
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AttachmentId.entries.forEach { att ->
                    val selected = att == activeAttachment
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) GunmetalElevated else ArmoredSlate)
                            .border(1.dp, if (selected) ArcticCyan else SubtleBorder, RoundedCornerShape(12.dp))
                            .clickable { onSelectAttachment(att) }
                            .padding(12.dp)
                            .testTag("attachment_${att.name}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(att.titleAr, style = MaterialTheme.typography.titleMedium, color = CrispIvory)
                            Text(att.bonusAr, style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                        }
                        if (selected) Text("نشط", color = ArcticCyan, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        item {
            Text("الدعم الجوي والتكتيكي (Killstreaks)", style = MaterialTheme.typography.titleLarge, color = CrispIvory)
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TacticalId.entries.forEach { tac ->
                    val selected = tac == activeTactical
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) GunmetalElevated else ArmoredSlate)
                            .border(1.dp, if (selected) NightVisionGreen else SubtleBorder, RoundedCornerShape(12.dp))
                            .clickable { onSelectTactical(tac) }
                            .padding(12.dp)
                            .testTag("tactical_${tac.name}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tac.titleAr, style = MaterialTheme.typography.titleMedium, color = CrispIvory)
                            Text("${tac.descAr} (${tac.pointsRequired} نقطة)", style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                        }
                        if (selected) Text("مختار", color = NightVisionGreen, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBar(label: String, fraction: Float, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MutedSteel, modifier = Modifier.widthIn(min = 105.dp))
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0.08f, 1f) },
            color = color,
            trackColor = GunmetalSurface,
            modifier = Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun RecordsTab(
    profile: SoldierProfile,
    matchesUi: UiState<List<BattleRecord>>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("سجل العمليات القتالية", style = MaterialTheme.typography.headlineMedium, color = CrispIvory)
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CareerMetricCard("إجمالي القتل", "${profile.totalKills}", TacticalAmber, Modifier.weight(1f))
                CareerMetricCard("الانتصارات", "${profile.wins}/${profile.matchesPlayed}", NightVisionGreen, Modifier.weight(1f))
                CareerMetricCard("أعلى نقاط", "${profile.bestScore}", ArcticCyan, Modifier.weight(1f))
            }
        }

        when (matchesUi) {
            is UiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TacticalAmber)
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Text(matchesUi.message, color = CrimsonThreat, style = MaterialTheme.typography.bodyMedium)
                }
            }
            is UiState.Success -> {
                val matches = matchesUi.data
                if (matches.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ArmoredSlate),
                            border = BorderStroke(1.dp, SubtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = TacticalAmber, modifier = Modifier.size(36.dp))
                                Text("لا توجد معارك مسجلة بعد", style = MaterialTheme.typography.titleMedium, color = CrispIvory)
                                Text("انزل إلى ساحة القتال الآن لتسجيل أول انتصار ومواجهة العواصف المتقلبة.", style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                            }
                        }
                    }
                } else {
                    items(matches, key = { it.id }) { record ->
                        val isWin = record.outcomeEnum() == MatchOutcome.VICTORY
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ArmoredSlate),
                            border = BorderStroke(1.dp, if (isWin) NightVisionGreen.copy(alpha = 0.6f) else SubtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        record.outcomeEnum().titleAr,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isWin) NightVisionGreen else CrimsonThreat
                                    )
                                    Text("${record.score} PTS", style = MaterialTheme.typography.labelSmall, color = TacticalAmber)
                                }
                                Text(
                                    "${record.mapEnum().titleAr} • ${record.weaponEnum().code} • قتل: ${record.kills} (رأس: ${record.headshots})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CrispIvory
                                )
                                if (record.weathersEncountered.isNotEmpty()) {
                                    val weatherNames = record.weathersEncountered.mapNotNull { wName ->
                                        enumValues<WeatherType>().firstOrNull { it.name == wName }?.titleAr
                                    }.joinToString(" • ")
                                    if (weatherNames.isNotBlank()) {
                                        Text(
                                            "الطقس المواجه: $weatherNames",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = ArcticCyan
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CareerMetricCard(label: String, value: String, accent: Color, modifier: Modifier) {
    Surface(
        color = ArmoredSlate,
        border = BorderStroke(1.dp, SubtleBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = accent)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
        }
    }
}

@Composable
private fun SettingsTab(
    profile: SoldierProfile,
    settings: GameSettings,
    onUpdateCallsign: (String) -> Unit,
    onSetSound: (Boolean) -> Unit,
    onSetHaptics: (Boolean) -> Unit,
    onSetSensitivity: (Float) -> Unit,
    onSetGraphics: (GraphicsQuality) -> Unit,
    onSetAutoWeather: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var callsignDraft by remember(profile.callsign) { mutableStateOf(profile.callsign) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("إعدادات القتال والرسوميات", style = MaterialTheme.typography.headlineMedium, color = CrispIvory)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ArmoredSlate),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("الاسم الحربي (Callsign)", style = MaterialTheme.typography.titleMedium, color = CrispIvory)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = callsignDraft,
                            onValueChange = { if (it.length <= 30) callsignDraft = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onUpdateCallsign(callsignDraft) },
                            colors = ButtonDefaults.buttonColors(containerColor = TacticalAmber, contentColor = TacticalObsidian)
                        ) {
                            Text("حفظ")
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ArmoredSlate),
                border = BorderStroke(1.dp, SubtleBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("جودة الجرافيك ثلاثي الأبعاد", style = MaterialTheme.typography.titleMedium, color = CrispIvory)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GraphicsQuality.entries.forEach { q ->
                            val selected = q == settings.graphicsQuality
                            Surface(
                                color = if (selected) TacticalAmber else GunmetalSurface,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onSetGraphics(q) }
                            ) {
                                Text(
                                    text = q.titleAr,
                                    color = if (selected) TacticalObsidian else CrispIvory,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
                                )
                            }
                        }
                    }

                    Text("حساسية التصويب (${String.format("%.1fx", settings.aimSensitivity)})", style = MaterialTheme.typography.titleMedium, color = CrispIvory)
                    Slider(
                        value = settings.aimSensitivity,
                        onValueChange = onSetSensitivity,
                        valueRange = 0.4f..2.2f,
                        colors = SliderDefaults.colors(thumbColor = TacticalAmber, activeTrackColor = TacticalAmber)
                    )

                    SettingToggleRow("تبديل الطقس الديناميكي تلقائياً", settings.autoWeatherCycle, onSetAutoWeather)
                    SettingToggleRow("المؤثرات الصوتية القتالية والرعد", settings.soundEnabled, onSetSound)
                    SettingToggleRow("الاهتزاز التفاعلي عند الإطلاق والانفجار", settings.hapticsEnabled, onSetHaptics)
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { signOut(credentialManager, scope) },
                border = BorderStroke(1.dp, CrimsonThreat),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonThreat),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("sign_out_button")
            ) {
                Icon(Icons.Filled.Logout, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.sign_out), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SettingToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = CrispIvory, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

fun weatherIcon(weather: WeatherType): ImageVector = when (weather) {
    WeatherType.CLEAR_DAY -> Icons.Filled.WbSunny
    WeatherType.DENSE_FOG -> Icons.Filled.Cloud
    WeatherType.HEAVY_RAIN -> Icons.Filled.Thunderstorm
    WeatherType.SANDSTORM -> Icons.Filled.Air
    WeatherType.GOLDEN_DUSK -> Icons.Filled.WbTwilight
    WeatherType.NIGHT_STORM -> Icons.Filled.NightsStay
}

fun weatherColor(weather: WeatherType): Color = when (weather) {
    WeatherType.CLEAR_DAY -> TacticalAmber
    WeatherType.DENSE_FOG -> MutedSteel
    WeatherType.HEAVY_RAIN -> ArcticCyan
    WeatherType.SANDSTORM -> SandstormGold
    WeatherType.GOLDEN_DUSK -> Color(0xFFFF7A3D)
    WeatherType.NIGHT_STORM -> NightVisionGreen
}
