package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.AttachmentId
import com.example.data.model.BattleRecord
import com.example.data.model.GameModeId
import com.example.data.model.MapId
import com.example.data.model.MatchOutcome
import com.example.data.model.SoldierProfile
import com.example.data.model.TacticalId
import com.example.data.model.WeaponId
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class GameRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("يجب تسجيل الدخول بحساب Google قبل الوصول إلى قاعدة البيانات.")
    }

    fun observeProfile(userId: String): Flow<SoldierProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Mismatch between authenticated UID and requested profile." }
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            SoldierProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeRecentMatches(userId: String): Flow<List<BattleRecord>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Mismatch between authenticated UID and requested matches." }
        val path = "users/$userId/matches"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("matches")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        BattleRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureProfileInitialized(userId: String, rawDisplayName: String?): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Authenticated user mismatch." }
            val docRef = db.collection("users").document(userId)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val cleanCallsign = (rawDisplayName?.trim() ?: "صقر الصحراء")
                    .ifBlank { "صقر الصحراء" }
                    .take(30)
                    .let { if (it.length < 2) "صقر الصحراء" else it }
                val payload = mapOf(
                    "userId" to uid,
                    "callsign" to cleanCallsign,
                    "rankTitle" to computeRankTitle(0L),
                    "xp" to 0L,
                    "totalKills" to 0L,
                    "headshots" to 0L,
                    "matchesPlayed" to 0L,
                    "wins" to 0L,
                    "bestScore" to 0L,
                    "equippedWeapon" to WeaponId.M4A1.name,
                    "equippedTactical" to TacticalId.AIRSTRIKE.name,
                    "equippedAttachment" to AttachmentId.HOLO_OPTIC.name,
                    "preferredMap" to MapId.DESERT_STORM.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateLoadout(
        userId: String,
        callsign: String,
        weapon: WeaponId,
        tactical: TacticalId,
        attachment: AttachmentId,
        map: MapId
    ): Result<Unit> {
        val docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Authenticated user mismatch." }
            val sanitizedCallsign = callsign.trim().take(30).let {
                if (it.length < 2) "صقر الصحراء" else it
            }
            db.collection("users").document(userId).update(
                mapOf(
                    "callsign" to sanitizedCallsign,
                    "equippedWeapon" to weapon.name,
                    "equippedTactical" to tactical.name,
                    "equippedAttachment" to attachment.name,
                    "preferredMap" to map.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordMatchResult(
        userId: String,
        currentProfile: SoldierProfile,
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
    ): Result<Unit> {
        val matchId = "m_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        val matchPath = "users/$userId/matches/$matchId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Authenticated user mismatch." }

            val safeKills = kills.coerceIn(0, 999)
            val safeHeadshots = headshots.coerceIn(0, 999)
            val safeDamage = damageDealt.coerceIn(0, 1_000_000)
            val safeDuration = survivalSeconds.coerceIn(0, 86_400)
            val safeScore = score.coerceIn(0, 10_000_000)
            val safeWeathers = weathersEncountered
                .distinct()
                .map { it.take(32) }
                .take(10)
                .ifEmpty { listOf("CLEAR_DAY") }

            val matchPayload = mapOf(
                "userId" to uid,
                "mapId" to mapId.name,
                "modeId" to modeId.name,
                "weaponId" to weaponId.name,
                "kills" to safeKills.toLong(),
                "headshots" to safeHeadshots.toLong(),
                "damageDealt" to safeDamage.toLong(),
                "survivalSeconds" to safeDuration.toLong(),
                "score" to safeScore.toLong(),
                "outcome" to outcome.name,
                "weathersEncountered" to safeWeathers,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("users")
                .document(userId)
                .collection("matches")
                .document(matchId)
                .set(matchPayload)
                .await()

            val gainedXp = safeScore.toLong() / 2L + (if (outcome == MatchOutcome.VICTORY) 350L else 80L)
            val newXp = (currentProfile.xp + gainedXp).coerceAtMost(100_000_000L)
            val newKills = (currentProfile.totalKills + safeKills).coerceAtMost(10_000_000L)
            val newHeadshots = (currentProfile.headshots + safeHeadshots).coerceAtMost(10_000_000L)
            val newMatches = (currentProfile.matchesPlayed + 1L).coerceAtMost(10_000_000L)
            val newWins = (currentProfile.wins + if (outcome == MatchOutcome.VICTORY) 1L else 0L)
                .coerceAtMost(10_000_000L)
            val newBestScore = maxOf(currentProfile.bestScore, safeScore.toLong()).coerceAtMost(100_000_000L)

            db.collection("users").document(userId).update(
                mapOf(
                    "rankTitle" to computeRankTitle(newXp),
                    "xp" to newXp,
                    "totalKills" to newKills,
                    "headshots" to newHeadshots,
                    "matchesPlayed" to newMatches,
                    "wins" to newWins,
                    "bestScore" to newBestScore,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, matchPath)
            Result.failure(e)
        }
    }

    companion object {
        fun computeRankTitle(xp: Long): String = when {
            xp >= 25000L -> "فريق أول نخبة"
            xp >= 14000L -> "لواء عمليات"
            xp >= 7500L -> "عقيد تكتيكي"
            xp >= 3500L -> "نقيب صاعقة"
            xp >= 1200L -> "عريف تكتيكي"
            else -> "جندي أول"
        }
    }
}
