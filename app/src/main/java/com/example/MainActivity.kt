package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.firebase.GameRepository
import com.example.data.firebase.authStateFlow
import com.example.data.model.GameModeId
import com.example.data.model.SoldierProfile
import com.example.data.preferences.SettingsRepository
import com.example.ui.CommandViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BattleScreen
import com.example.ui.screens.CommandHubScreen
import com.example.ui.theme.BattleStormTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BattleStormTheme {
                AppNavigation()
            }
        }
    }
}

private data class ActiveBattleSession(
    val profile: SoldierProfile,
    val mode: GameModeId,
    val sessionNonce: Int
)

@Composable
fun AppNavigation() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        val uid = user.uid
        val displayName = user.displayName
        key(uid) {
            val viewModel: CommandViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        CommandViewModel(
                            repository = GameRepository(db),
                            settingsRepository = SettingsRepository(app.applicationContext),
                            currentUserId = uid,
                            initialDisplayName = displayName
                        )
                    }
                }
            )

            val settings by viewModel.settings.collectAsStateWithLifecycle()
            var battleSession by remember { mutableStateOf<ActiveBattleSession?>(null) }
            var nonceCounter by remember { mutableIntStateOf(1) }

            val active = battleSession
            if (active == null) {
                CommandHubScreen(
                    viewModel = viewModel,
                    onDeployBattle = { profile, mode ->
                        battleSession = ActiveBattleSession(profile, mode, nonceCounter++)
                    }
                )
            } else {
                key(active.sessionNonce) {
                    BattleScreen(
                        profile = active.profile,
                        modeId = active.mode,
                        settings = settings,
                        onMatchFinished = { hud, weathers ->
                            val outcome = hud.matchOutcome ?: return@BattleScreen
                            viewModel.recordCompletedMatch(
                                profile = active.profile,
                                mapId = active.profile.mapEnum(),
                                modeId = active.mode,
                                weaponId = hud.weaponId,
                                kills = hud.kills,
                                headshots = hud.headshots,
                                damageDealt = hud.damageDealt,
                                survivalSeconds = hud.survivalSeconds,
                                score = hud.score,
                                outcome = outcome,
                                weathersEncountered = weathers
                            )
                        },
                        onExitBattle = { battleSession = null },
                        onRedeploy = {
                            battleSession = ActiveBattleSession(active.profile, active.mode, nonceCounter++)
                        }
                    )
                }
            }
        }
    }
}
