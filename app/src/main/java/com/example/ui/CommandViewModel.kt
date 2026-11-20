package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.GameRepository
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
import com.example.data.preferences.GameSettings
import com.example.data.preferences.SettingsRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CommandViewModel(
    private val repository: GameRepository,
    private val settingsRepository: SettingsRepository,
    private val currentUserId: String,
    private val initialDisplayName: String?
) : ViewModel() {

    private val _selectedMode = MutableStateFlow(GameModeId.BATTLE_ROYALE)
    val selectedMode: StateFlow<GameModeId> = _selectedMode.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    val settings: StateFlow<GameSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = GameSettings()
        )

    val profileState: StateFlow<UiState<SoldierProfile>> = repository.observeProfile(currentUserId)
        .map<SoldierProfile?, UiState<SoldierProfile>> { profile ->
            if (profile != null) UiState.Success(profile) else UiState.Loading
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            emit(UiState.Error(root.message ?: "تعذر تحميل ملف المقاتل"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val matchesState: StateFlow<UiState<List<BattleRecord>>> = repository.observeRecentMatches(currentUserId)
        .map<List<BattleRecord>, UiState<List<BattleRecord>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            emit(UiState.Error(root.message ?: "تعذر تحميل سجل المعارك"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    init {
        viewModelScope.launch {
            repository.ensureProfileInitialized(currentUserId, initialDisplayName)
                .onFailure { _actionError.value = it.localizedMessage }
        }
    }

    fun selectMode(mode: GameModeId) {
        _selectedMode.value = mode
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun updateLoadout(
        profile: SoldierProfile,
        callsign: String = profile.callsign,
        weapon: WeaponId = profile.weaponEnum(),
        tactical: TacticalId = profile.tacticalEnum(),
        attachment: AttachmentId = profile.attachmentEnum(),
        map: MapId = profile.mapEnum()
    ) {
        viewModelScope.launch {
            repository.updateLoadout(
                userId = currentUserId,
                callsign = callsign,
                weapon = weapon,
                tactical = tactical,
                attachment = attachment,
                map = map
            ).onFailure {
                _actionError.value = it.localizedMessage ?: "فشل حفظ التجهيز"
            }
        }
    }

    fun recordCompletedMatch(
        profile: SoldierProfile,
        mapId: MapId,
        modeId: GameModeId,
        weaponId: WeaponId,
        kills: Int,
        headshots: Int,
        damageDealt: Int,
        survivalSeconds: Int,
        score: Int,
        outcome: MatchOutcome,
        weathersEncountered: List<String>
    ) {
        viewModelScope.launch {
            repository.recordMatchResult(
                userId = currentUserId,
                currentProfile = profile,
                mapId = mapId,
                modeId = modeId,
                weaponId = weaponId,
                kills = kills,
                headshots = headshots,
                damageDealt = damageDealt,
                survivalSeconds = survivalSeconds,
                score = score,
                outcome = outcome,
                weathersEncountered = weathersEncountered
            ).onFailure {
                _actionError.value = it.localizedMessage ?: "تعذر حفظ نتيجة المعركة"
            }
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSoundEnabled(enabled) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHapticsEnabled(enabled) }
    }

    fun setAimSensitivity(value: Float) {
        viewModelScope.launch { settingsRepository.setAimSensitivity(value) }
    }

    fun setGraphicsQuality(quality: GraphicsQuality) {
        viewModelScope.launch { settingsRepository.setGraphicsQuality(quality) }
    }

    fun setAutoWeatherCycle(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoWeatherCycle(enabled) }
    }
}
