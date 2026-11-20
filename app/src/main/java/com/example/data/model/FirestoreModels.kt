package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class SoldierProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val callsign: String = "صقر الصحراء",
    val rankTitle: String = "جندي أول",
    val xp: Long = 0L,
    val totalKills: Long = 0L,
    val headshots: Long = 0L,
    val matchesPlayed: Long = 0L,
    val wins: Long = 0L,
    val bestScore: Long = 0L,
    val equippedWeapon: String = WeaponId.M4A1.name,
    val equippedTactical: String = TacticalId.AIRSTRIKE.name,
    val equippedAttachment: String = AttachmentId.HOLO_OPTIC.name,
    val preferredMap: String = MapId.DESERT_STORM.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun weaponEnum(): WeaponId =
        enumValues<WeaponId>().firstOrNull { it.name == equippedWeapon } ?: WeaponId.M4A1

    fun tacticalEnum(): TacticalId =
        enumValues<TacticalId>().firstOrNull { it.name == equippedTactical } ?: TacticalId.AIRSTRIKE

    fun attachmentEnum(): AttachmentId =
        enumValues<AttachmentId>().firstOrNull { it.name == equippedAttachment } ?: AttachmentId.HOLO_OPTIC

    fun mapEnum(): MapId =
        enumValues<MapId>().firstOrNull { it.name == preferredMap } ?: MapId.DESERT_STORM
}

@Keep
@IgnoreExtraProperties
data class BattleRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val mapId: String = MapId.DESERT_STORM.name,
    val modeId: String = GameModeId.BATTLE_ROYALE.name,
    val weaponId: String = WeaponId.M4A1.name,
    val kills: Long = 0L,
    val headshots: Long = 0L,
    val damageDealt: Long = 0L,
    val survivalSeconds: Long = 0L,
    val score: Long = 0L,
    val outcome: String = MatchOutcome.VICTORY.name,
    val weathersEncountered: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun mapEnum(): MapId =
        enumValues<MapId>().firstOrNull { it.name == mapId } ?: MapId.DESERT_STORM

    fun modeEnum(): GameModeId =
        enumValues<GameModeId>().firstOrNull { it.name == modeId } ?: GameModeId.BATTLE_ROYALE

    fun weaponEnum(): WeaponId =
        enumValues<WeaponId>().firstOrNull { it.name == weaponId } ?: WeaponId.M4A1

    fun outcomeEnum(): MatchOutcome =
        enumValues<MatchOutcome>().firstOrNull { it.name == outcome } ?: MatchOutcome.ELIMINATED
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
