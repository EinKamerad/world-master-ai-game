package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.ai.HiddenPlotPlan
import com.example.data.local.WorldEntity
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class MultiplayerRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth
    private val codeAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()
    private val pastRoundsCache = ConcurrentHashMap<String, OnlineRoundEntry>()

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Войдите через аккаунт Google для доступа к онлайн-комнатам")
    }

    private fun normalizeRoomCode(raw: String): String {
        val cleaned = raw.trim().uppercase()
        require(cleaned.length == 6 && cleaned.all { it in 'A'..'Z' || it in '0'..'9' }) {
            "Код комнаты должен состоять ровно из 6 латинских букв или цифр"
        }
        return cleaned
    }

    private fun generateInviteCode(): String = buildString(6) {
        repeat(6) {
            append(codeAlphabet[random.nextInt(codeAlphabet.length)])
        }
    }

    /**
     * Scoped list query strictly aligned with `allow list: if isSignedIn() && request.auth.uid in resource.data.memberUids;`
     */
    fun observeMyRooms(currentUserId: String): Flow<List<OnlineRoom>> = flow {
        if (currentUserId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val uid = requireUserId()
        val path = "rooms"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        OnlineRoom::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { room ->
                        room.updatedAt ?: room.createdAt ?: Timestamp(0, 0)
                    }
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

    /**
     * Observes a single room document `/rooms/{roomCode}`.
     */
    fun observeRoom(roomCode: String): Flow<OnlineRoom?> = flow {
        val uid = requireUserId()
        val cleanCode = normalizeRoomCode(roomCode)
        val path = "rooms/$cleanCode"
        emitAll(
            db.collection("rooms").document(cleanCode)
                .snapshots()
                .map { snap ->
                    if (!snap.exists()) null
                    else snap.toObject(
                        OnlineRoom::class.java,
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
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Observes each member's player sheet via individual DocumentReference.snapshots()
     * (aligning with `allow get: if ... isRoomMember(roomCode)` and `allow list: if false`).
     */
    fun observeRoomPlayers(roomCode: String, memberUids: List<String>): Flow<List<OnlineRoomPlayer>> = flow {
        val uid = requireUserId()
        val cleanCode = normalizeRoomCode(roomCode)
        val cleanMembers = memberUids.filter { it.isNotBlank() }.distinct().take(8)
        if (cleanMembers.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val memberFlows = cleanMembers.map { mUid ->
            val docPath = "rooms/$cleanCode/players/$mUid"
            db.collection("rooms").document(cleanCode)
                .collection("players").document(mUid)
                .snapshots()
                .map { snap ->
                    if (!snap.exists()) null
                    else snap.toObject(
                        OnlineRoomPlayer::class.java,
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
                        handleFirestoreError(exceptionToLog, OperationType.GET, docPath)
                    }
                    throw error
                }
        }

        emitAll(
            combine(memberFlows) { playersArray ->
                playersArray.filterNotNull().sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
        )
    }

    /**
     * Observes sequential round entries `round_0 .. round_{currentRound - 1}` without collection `list`.
     * Earlier completed rounds are cached in memory and the most recent rounds use live DocumentReference.snapshots().
     */
    fun observeRoomRounds(roomCode: String, currentRound: Long): Flow<List<OnlineRoundEntry>> = flow {
        val uid = requireUserId()
        val cleanCode = normalizeRoomCode(roomCode)
        val maxPublishedIndex = (currentRound - 1L).coerceAtLeast(0L)

        // Fetch any historical immutable rounds older than the live window once
        val liveStartIndex = (maxPublishedIndex - 2L).coerceAtLeast(0L)
        val historical = mutableListOf<OnlineRoundEntry>()
        for (r in 0L until liveStartIndex) {
            val cacheKey = "$cleanCode:round_$r"
            val cached = pastRoundsCache[cacheKey]
            if (cached != null) {
                historical.add(cached)
            } else {
                val docPath = "rooms/$cleanCode/rounds/round_$r"
                try {
                    val snap = db.collection("rooms").document(cleanCode)
                        .collection("rounds").document("round_$r")
                        .get()
                        .await()
                    val parsed = snap.toObject(
                        OnlineRoundEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    if (parsed != null) {
                        pastRoundsCache[cacheKey] = parsed
                        historical.add(parsed)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(e, OperationType.GET, docPath)
                    }
                    throw e
                }
            }
        }

        val liveRoundIndices = (liveStartIndex..maxPublishedIndex).toList()
        if (liveRoundIndices.isEmpty()) {
            emit(historical.sortedBy { it.roundNumber })
            return@flow
        }

        val liveFlows = liveRoundIndices.map { r ->
            val docPath = "rooms/$cleanCode/rounds/round_$r"
            db.collection("rooms").document(cleanCode)
                .collection("rounds").document("round_$r")
                .snapshots()
                .map { snap ->
                    if (!snap.exists()) null
                    else snap.toObject(
                        OnlineRoundEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )?.also { entry ->
                        if (r < maxPublishedIndex) {
                            pastRoundsCache["$cleanCode:round_$r"] = entry
                        }
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docPath)
                    }
                    throw error
                }
        }

        emitAll(
            combine(liveFlows) { latestArr ->
                (historical + latestArr.filterNotNull())
                    .distinctBy { it.roundNumber }
                    .sortedBy { it.roundNumber }
            }
        )
    }

    /**
     * Creates a new multiplayer room, registers the host's hero sheet, and posts Prologue `round_0`.
     */
    suspend fun createRoom(
        world: WorldEntity,
        roomTitle: String,
        hostDisplayName: String,
        heroName: String,
        heroArchetype: String,
        heroStats: String,
        openingScene: String,
        useDiceAndStats: Boolean,
        useInventory: Boolean,
        useSpells: Boolean
    ): Result<String> {
        var currentPath = "rooms"
        return try {
            val uid = requireUserId()
            val cleanHeroName = heroName.trim().ifBlank { "Странник" }.take(80)
            val cleanHostName = hostDisplayName.trim().ifBlank { cleanHeroName }.take(80)
            val cleanTitle = roomTitle.trim().ifBlank { "${world.name}: Совместный поход" }.take(120)
            val cleanArchetype = heroArchetype.trim().ifBlank {
                world.archetypesList().firstOrNull() ?: "Искатель приключений"
            }.take(120)
            val cleanStats = heroStats.trim().take(500)
            val cleanPrologue = openingScene.trim().ifBlank {
                world.defaultOpeningHook.ifBlank {
                    "Группа героев собирается в мире «${world.name}» (${world.genre}). ${world.worldDescription}\n\nРаунд 1 открыт: опишите действия ваших персонажей!"
                }
            }.take(20000)

            // Pick an unused 6-char room code
            var roomCode = generateInviteCode()
            for (attempt in 0 until 4) {
                val candidateRef = db.collection("rooms").document(roomCode)
                currentPath = candidateRef.path
                val existing = candidateRef.get().await()
                if (!existing.exists()) break
                roomCode = generateInviteCode()
            }

            // 1. Create /rooms/{roomCode} with exact 23 required keys
            val roomRef = db.collection("rooms").document(roomCode)
            currentPath = roomRef.path
            val roomPayload = mapOf(
                "roomCode" to roomCode,
                "title" to cleanTitle,
                "hostUid" to uid,
                "hostName" to cleanHostName,
                "memberUids" to listOf(uid),
                "worldName" to world.name.trim().ifBlank { "Авторский мир" }.take(120),
                "worldGenre" to world.genre.trim().ifBlank { "Ролевой сеттинг" }.take(120),
                "worldDescription" to world.worldDescription.trim().take(4000),
                "diceSystem" to world.diceSystem.trim().take(300),
                "magicOrTechSystem" to world.magicOrTechSystem.trim().take(500),
                "narrativeTone" to world.narrativeTone.trim().take(300),
                "customRules" to world.customRules.trim().take(2000),
                "useDiceAndStats" to useDiceAndStats,
                "useInventory" to useInventory,
                "useSpells" to useSpells,
                "currentRound" to 1L,
                "roundPhase" to "COLLECTING",
                "hiddenPlotArc" to "",
                "hiddenNpcKnowledge" to "",
                "hiddenPlotTriggers" to "",
                "memorySummary" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            roomRef.set(roomPayload).await()

            // 2. Create /rooms/{roomCode}/players/{uid} with exact 10 required keys
            val playerRef = roomRef.collection("players").document(uid)
            currentPath = playerRef.path
            val playerPayload = mapOf(
                "userId" to uid,
                "roomCode" to roomCode,
                "heroName" to cleanHeroName,
                "heroArchetype" to cleanArchetype,
                "heroStats" to cleanStats,
                "pendingAction" to "",
                "pendingDiceRoll" to "",
                "submittedRound" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            playerRef.set(playerPayload).await()

            // 3. Create Prologue /rooms/{roomCode}/rounds/round_0 with exact 7 required keys
            val prologueRef = roomRef.collection("rounds").document("round_0")
            currentPath = prologueRef.path
            val prologuePayload = mapOf(
                "roomCode" to roomCode,
                "roundNumber" to 0L,
                "authorUid" to uid,
                "partyActionsSummary" to "",
                "gmNarrative" to cleanPrologue,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            prologueRef.set(prologuePayload).await()

            Result.success(roomCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, currentPath)
            Result.failure(e)
        }
    }

    /**
     * Joins an existing room by 6-char invite code (Action 1 on /rooms/{roomCode}) and creates/updates caller's hero sheet.
     */
    suspend fun joinRoomByCode(
        rawCode: String,
        heroName: String,
        heroArchetype: String,
        heroStats: String
    ): Result<String> {
        var currentPath = "rooms"
        return try {
            val uid = requireUserId()
            val cleanCode = normalizeRoomCode(rawCode)
            val cleanHeroName = heroName.trim().ifBlank { "Путник" }.take(80)
            val cleanArchetype = heroArchetype.trim().ifBlank { "Искатель приключений" }.take(120)
            val cleanStats = heroStats.trim().take(500)

            val roomRef = db.collection("rooms").document(cleanCode)
            currentPath = roomRef.path
            val roomSnap = roomRef.get().await()
            if (!roomSnap.exists()) {
                return Result.failure(IllegalArgumentException("Комната с кодом $cleanCode не найдена"))
            }
            val room = roomSnap.toObject(
                OnlineRoom::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Не удалось прочитать данные комнаты"))

            if (uid !in room.memberUids) {
                if (room.memberUids.size >= 8) {
                    return Result.failure(IllegalStateException("В комнате уже максимальное число участников (8)"))
                }
                roomRef.update(
                    mapOf(
                        "memberUids" to FieldValue.arrayUnion(uid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val playerRef = roomRef.collection("players").document(uid)
            currentPath = playerRef.path
            val existingPlayer = playerRef.get().await()
            if (!existingPlayer.exists()) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "roomCode" to cleanCode,
                    "heroName" to cleanHeroName,
                    "heroArchetype" to cleanArchetype,
                    "heroStats" to cleanStats,
                    "pendingAction" to "",
                    "pendingDiceRoll" to "",
                    "submittedRound" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                playerRef.set(createPayload).await()
            } else {
                playerRef.update(
                    mapOf(
                        "heroName" to cleanHeroName,
                        "heroArchetype" to cleanArchetype,
                        "heroStats" to cleanStats,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Result.success(cleanCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, currentPath)
            Result.failure(e)
        }
    }

    /**
     * Submits or updates the signed-in player's action for `roundNumber`.
     */
    suspend fun submitPlayerAction(
        roomCode: String,
        roundNumber: Long,
        actionText: String,
        diceRollInfo: String
    ): Result<Unit> {
        var docPath = "rooms/$roomCode/players"
        return try {
            val uid = requireUserId()
            val cleanCode = normalizeRoomCode(roomCode)
            val cleanAction = actionText.trim().take(3000)
            require(cleanAction.isNotEmpty() || diceRollInfo.isNotBlank()) {
                "Введите заявку на раунд или прикрепите бросок"
            }
            val playerRef = db.collection("rooms").document(cleanCode)
                .collection("players").document(uid)
            docPath = playerRef.path

            playerRef.update(
                mapOf(
                    "pendingAction" to cleanAction.ifBlank { "Совершаю проверку кубика." },
                    "pendingDiceRoll" to diceRollInfo.trim().take(200),
                    "submittedRound" to roundNumber.coerceIn(1L, 100000L),
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

    /**
     * Updates the signed-in player's hero profile inside the room.
     */
    suspend fun updateMyHeroProfile(
        roomCode: String,
        heroName: String,
        heroArchetype: String,
        heroStats: String
    ): Result<Unit> {
        var docPath = "rooms/$roomCode/players"
        return try {
            val uid = requireUserId()
            val cleanCode = normalizeRoomCode(roomCode)
            val playerRef = db.collection("rooms").document(cleanCode)
                .collection("players").document(uid)
            docPath = playerRef.path
            playerRef.update(
                mapOf(
                    "heroName" to heroName.trim().ifBlank { "Странник" }.take(80),
                    "heroArchetype" to heroArchetype.trim().take(120),
                    "heroStats" to heroStats.trim().take(500),
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

    /**
     * Host marks room phase (`COLLECTING` or `GM_RESPONDING`).
     */
    suspend fun setRoomPhase(roomCode: String, phase: String): Result<Unit> {
        var docPath = "rooms/$roomCode"
        return try {
            requireUserId()
            val cleanCode = normalizeRoomCode(roomCode)
            require(phase == "COLLECTING" || phase == "GM_RESPONDING") { "Invalid phase: $phase" }
            val roomRef = db.collection("rooms").document(cleanCode)
            docPath = roomRef.path
            roomRef.update(
                mapOf(
                    "roundPhase" to phase,
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

    /**
     * Host publishes `/rooms/{roomCode}/rounds/round_{roundNumber}` and advances `currentRound` + hidden plot.
     */
    suspend fun publishGmRoundAndAdvance(
        roomCode: String,
        roundNumber: Long,
        partyActionsSummary: String,
        gmNarrative: String,
        updatedHiddenPlot: HiddenPlotPlan
    ): Result<Unit> {
        var currentPath = "rooms/$roomCode"
        return try {
            val uid = requireUserId()
            val cleanCode = normalizeRoomCode(roomCode)
            val roomRef = db.collection("rooms").document(cleanCode)
            val roundRef = roomRef.collection("rounds").document("round_$roundNumber")
            currentPath = roundRef.path

            val cleanNarrative = gmNarrative.trim().ifBlank { "Мастер завершает сцену раунда." }.take(20000)
            val cleanSummary = partyActionsSummary.trim().take(16000)

            val existingRound = roundRef.get().await()
            if (!existingRound.exists()) {
                roundRef.set(
                    mapOf(
                        "roomCode" to cleanCode,
                        "roundNumber" to roundNumber.coerceIn(0L, 100000L),
                        "authorUid" to uid,
                        "partyActionsSummary" to cleanSummary,
                        "gmNarrative" to cleanNarrative,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                roundRef.update(
                    mapOf(
                        "partyActionsSummary" to cleanSummary,
                        "gmNarrative" to cleanNarrative,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            currentPath = roomRef.path
            val nextRound = (roundNumber + 1L).coerceAtMost(100000L)
            roomRef.update(
                mapOf(
                    "currentRound" to nextRound,
                    "roundPhase" to "COLLECTING",
                    "hiddenPlotArc" to updatedHiddenPlot.plotArc.trim().take(4000),
                    "hiddenNpcKnowledge" to updatedHiddenPlot.npcKnowledgeBoundaries.trim().take(4000),
                    "hiddenPlotTriggers" to updatedHiddenPlot.triggersAndConsequences.trim().take(4000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, currentPath)
            Result.failure(e)
        }
    }

    /**
     * Host updates room hidden plot plan or Non-D&D module flags.
     */
    suspend fun updateRoomHostSettings(
        roomCode: String,
        hiddenPlot: HiddenPlotPlan,
        useDiceAndStats: Boolean,
        useInventory: Boolean,
        useSpells: Boolean
    ): Result<Unit> {
        var docPath = "rooms/$roomCode"
        return try {
            requireUserId()
            val cleanCode = normalizeRoomCode(roomCode)
            val roomRef = db.collection("rooms").document(cleanCode)
            docPath = roomRef.path
            roomRef.update(
                mapOf(
                    "hiddenPlotArc" to hiddenPlot.plotArc.trim().take(4000),
                    "hiddenNpcKnowledge" to hiddenPlot.npcKnowledgeBoundaries.trim().take(4000),
                    "hiddenPlotTriggers" to hiddenPlot.triggersAndConsequences.trim().take(4000),
                    "useDiceAndStats" to useDiceAndStats,
                    "useInventory" to useInventory,
                    "useSpells" to useSpells,
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

    /**
     * Host deletes room.
     */
    suspend fun deleteRoom(roomCode: String): Result<Unit> {
        var docPath = "rooms/$roomCode"
        return try {
            requireUserId()
            val cleanCode = normalizeRoomCode(roomCode)
            val roomRef = db.collection("rooms").document(cleanCode)
            docPath = roomRef.path
            roomRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    fun emptyFlow(): Flow<Nothing> = flowOf()
}
