package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.ai.AiProviderGateway
import com.example.ai.GeminiMasterService
import com.example.ai.GoogleOAuthHelper
import com.example.ai.HiddenPlotPlan
import com.example.ai.MultiplayerRoundContext
import com.example.ai.PartyActionInput
import com.example.data.local.CampaignEntity
import com.example.data.local.WorldEntity
import com.example.data.preferences.AppSettings
import com.example.data.preferences.SecureCredentialVault
import com.example.data.preferences.SettingsDataStore
import com.example.data.remote.MultiplayerRepository
import com.example.data.remote.OnlineRoom
import com.example.data.remote.OnlineRoomPlayer
import com.example.data.remote.OnlineRoundEntry
import com.example.data.remote.friendlyFirestoreErrorMessage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MultiplayerFeedState<out T> {
    data object Loading : MultiplayerFeedState<Nothing>
    data class Success<T>(val data: T) : MultiplayerFeedState<T>
    data class Error(val message: String) : MultiplayerFeedState<Nothing>
}

data class ActiveRoomSnapshot(
    val room: OnlineRoom,
    val players: List<OnlineRoomPlayer>,
    val rounds: List<OnlineRoundEntry>
) {
    val readyPlayersCount: Int
        get() = players.count { it.submittedRound == room.currentRound }

    val totalMembersCount: Int
        get() = room.memberUids.size.coerceAtLeast(players.size).coerceAtLeast(1)

    val allMembersReady: Boolean
        get() = room.memberUids.isNotEmpty() &&
            room.memberUids.all { uid ->
                players.any { p -> p.userId == uid && p.submittedRound == room.currentRound }
            }
}

data class OnlineMultiplayerUiState(
    val currentUserId: String = "",
    val currentUserDisplayName: String = "",
    val myRoomsState: MultiplayerFeedState<List<OnlineRoom>> = MultiplayerFeedState.Loading,
    val activeRoomCode: String? = null,
    val activeRoomState: MultiplayerFeedState<ActiveRoomSnapshot>? = null,
    val isCreatingOrJoining: Boolean = false,
    val isSubmittingAction: Boolean = false,
    val gmRoundTurnState: AiTurnState = AiTurnState.Idle,
    val isGeneratingHiddenPlot: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class OnlineMultiplayerViewModel(
    private val currentUserId: String,
    private val currentUserDisplayName: String,
    private val repository: MultiplayerRepository,
    private val settingsStore: SettingsDataStore,
    private val geminiService: GeminiMasterService
) : ViewModel() {

    private val _activeRoomCode = MutableStateFlow<String?>(null)
    private val _isCreatingOrJoining = MutableStateFlow(false)
    private val _isSubmittingAction = MutableStateFlow(false)
    private val _gmRoundTurnState = MutableStateFlow<AiTurnState>(AiTurnState.Idle)
    private val _isGeneratingHiddenPlot = MutableStateFlow(false)
    private var lastAutoTriggeredRoundKey: String? = null

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    private val myRoomsStateFlow: StateFlow<MultiplayerFeedState<List<OnlineRoom>>> =
        repository.observeMyRooms(currentUserId)
            .map<List<OnlineRoom>, MultiplayerFeedState<List<OnlineRoom>>> { rooms ->
                MultiplayerFeedState.Success(rooms)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                emit(MultiplayerFeedState.Error(friendlyFirestoreErrorMessage(rootError)))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = MultiplayerFeedState.Loading
            )

    private val activeRoomStateFlow: StateFlow<MultiplayerFeedState<ActiveRoomSnapshot>?> =
        _activeRoomCode.flatMapLatest { code ->
            if (code.isNullOrBlank()) {
                flowOf(null)
            } else {
                repository.observeRoom(code)
                    .flatMapLatest { room ->
                        if (room == null) {
                            flowOf<MultiplayerFeedState<ActiveRoomSnapshot>>(
                                MultiplayerFeedState.Error("Комната была закрыта или удалена хостом")
                            )
                        } else {
                            val playersFlow = repository.observeRoomPlayers(
                                roomCode = room.roomCode,
                                memberUids = room.memberUids
                            )
                            val roundsFlow = repository.observeRoomRounds(
                                roomCode = room.roomCode,
                                currentRound = room.currentRound
                            )
                            combine(playersFlow, roundsFlow) { players, rounds ->
                                MultiplayerFeedState.Success(
                                    ActiveRoomSnapshot(
                                        room = room,
                                        players = players,
                                        rounds = rounds
                                    )
                                ) as MultiplayerFeedState<ActiveRoomSnapshot>
                            }
                        }
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        emit(MultiplayerFeedState.Error(friendlyFirestoreErrorMessage(rootError)))
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null
        )

    private data class TransientFlags(
        val creatingOrJoining: Boolean,
        val submitting: Boolean,
        val gmTurn: AiTurnState,
        val generatingPlot: Boolean
    )

    private val transientFlagsFlow = combine(
        _isCreatingOrJoining,
        _isSubmittingAction,
        _gmRoundTurnState,
        _isGeneratingHiddenPlot
    ) { cj, sub, gm, gp ->
        TransientFlags(cj, sub, gm, gp)
    }

    val uiState: StateFlow<OnlineMultiplayerUiState> = combine(
        myRoomsStateFlow,
        _activeRoomCode,
        activeRoomStateFlow,
        transientFlagsFlow
    ) { myRooms, code, activeSnap, flags ->
        OnlineMultiplayerUiState(
            currentUserId = currentUserId,
            currentUserDisplayName = currentUserDisplayName,
            myRoomsState = myRooms,
            activeRoomCode = code,
            activeRoomState = activeSnap,
            isCreatingOrJoining = flags.creatingOrJoining,
            isSubmittingAction = flags.submitting,
            gmRoundTurnState = flags.gmTurn,
            isGeneratingHiddenPlot = flags.generatingPlot
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = OnlineMultiplayerUiState(
            currentUserId = currentUserId,
            currentUserDisplayName = currentUserDisplayName
        )
    )

    init {
        // Host auto-resolution watcher: when all members in the active room submit actions for currentRound,
        // the Host automatically synthesizes the unified AI Master turn!
        viewModelScope.launch {
            activeRoomStateFlow
                .map { state -> (state as? MultiplayerFeedState.Success)?.data }
                .distinctUntilChanged { old, new ->
                    old?.room?.roomCode == new?.room?.roomCode &&
                        old?.room?.currentRound == new?.room?.currentRound &&
                        old?.room?.roundPhase == new?.room?.roundPhase &&
                        old?.allMembersReady == new?.allMembersReady
                }
                .collect { snap ->
                    if (snap != null &&
                        snap.room.hostUid == currentUserId &&
                        snap.room.roundPhase == "COLLECTING" &&
                        snap.allMembersReady &&
                        snap.room.memberUids.size >= 2
                    ) {
                        val roundKey = "${snap.room.roomCode}:${snap.room.currentRound}"
                        if (lastAutoTriggeredRoundKey != roundKey &&
                            _gmRoundTurnState.value !is AiTurnState.Loading &&
                            _gmRoundTurnState.value !is AiTurnState.Streaming
                        ) {
                            lastAutoTriggeredRoundKey = roundKey
                            resolveCurrentRoundWithAi(forceEvenIfIncomplete = false)
                        }
                    }
                }
        }
    }

    fun openRoom(roomCode: String) {
        _gmRoundTurnState.value = AiTurnState.Idle
        _activeRoomCode.value = roomCode.trim().uppercase()
    }

    fun leaveRoomToLobby() {
        _gmRoundTurnState.value = AiTurnState.Idle
        _activeRoomCode.value = null
    }

    fun dismissGmError() {
        if (_gmRoundTurnState.value is AiTurnState.Error) {
            _gmRoundTurnState.value = AiTurnState.Idle
        }
    }

    fun createOnlineRoom(
        world: WorldEntity,
        roomTitle: String,
        heroName: String,
        heroArchetype: String,
        heroStats: String,
        openingScene: String,
        useDiceAndStats: Boolean,
        useInventory: Boolean,
        useSpells: Boolean
    ) {
        if (_isCreatingOrJoining.value) return
        viewModelScope.launch {
            _isCreatingOrJoining.value = true
            try {
                val res = repository.createRoom(
                    world = world,
                    roomTitle = roomTitle,
                    hostDisplayName = currentUserDisplayName.ifBlank { heroName },
                    heroName = heroName,
                    heroArchetype = heroArchetype,
                    heroStats = heroStats,
                    openingScene = openingScene,
                    useDiceAndStats = useDiceAndStats,
                    useInventory = useInventory,
                    useSpells = useSpells
                )
                res.onSuccess { code ->
                    _activeRoomCode.value = code
                    _snackbarEvents.tryEmit("Комната $code создана! Поделитесь кодом с друзьями")
                }.onFailure { err ->
                    _snackbarEvents.tryEmit(friendlyFirestoreErrorMessage(err))
                }
            } finally {
                _isCreatingOrJoining.value = false
            }
        }
    }

    fun joinRoomByInviteCode(
        inviteCode: String,
        heroName: String,
        heroArchetype: String,
        heroStats: String
    ) {
        if (_isCreatingOrJoining.value) return
        viewModelScope.launch {
            _isCreatingOrJoining.value = true
            try {
                val res = repository.joinRoomByCode(
                    rawCode = inviteCode,
                    heroName = heroName,
                    heroArchetype = heroArchetype,
                    heroStats = heroStats
                )
                res.onSuccess { code ->
                    _activeRoomCode.value = code
                    _snackbarEvents.tryEmit("Вы присоединились к комнате $code!")
                }.onFailure { err ->
                    _snackbarEvents.tryEmit(friendlyFirestoreErrorMessage(err))
                }
            } finally {
                _isCreatingOrJoining.value = false
            }
        }
    }

    fun submitMyAction(actionText: String, diceRollInfo: String?) {
        val snap = (activeRoomStateFlow.value as? MultiplayerFeedState.Success)?.data ?: return
        if (_isSubmittingAction.value) return
        viewModelScope.launch {
            _isSubmittingAction.value = true
            try {
                val res = repository.submitPlayerAction(
                    roomCode = snap.room.roomCode,
                    roundNumber = snap.room.currentRound,
                    actionText = actionText,
                    diceRollInfo = diceRollInfo.orEmpty()
                )
                res.onSuccess {
                    if (snap.room.memberUids.size == 1 && snap.room.hostUid == currentUserId) {
                        _snackbarEvents.tryEmit("Заявка принята! Нажмите «Ход ИИ-Мастера», когда будете готовы или дождитесь друзей")
                    } else {
                        _snackbarEvents.tryEmit("Заявка на раунд №${snap.room.currentRound} отправлена!")
                    }
                }.onFailure { err ->
                    _snackbarEvents.tryEmit(friendlyFirestoreErrorMessage(err))
                }
            } finally {
                _isSubmittingAction.value = false
            }
        }
    }

    fun updateMyHeroInRoom(heroName: String, heroArchetype: String, heroStats: String) {
        val code = _activeRoomCode.value ?: return
        viewModelScope.launch {
            repository.updateMyHeroProfile(code, heroName, heroArchetype, heroStats)
                .onSuccess { _snackbarEvents.tryEmit("Лист героя в комнате обновлён") }
                .onFailure { _snackbarEvents.tryEmit(friendlyFirestoreErrorMessage(it)) }
        }
    }

    /**
     * Host resolves the current round: collects all submitted player actions, streams the unified AI scene,
     * writes `/rooms/{code}/rounds/round_{N}`, and increments `currentRound`.
     */
    fun resolveCurrentRoundWithAi(forceEvenIfIncomplete: Boolean = true) {
        val snap = (activeRoomStateFlow.value as? MultiplayerFeedState.Success)?.data ?: return
        val room = snap.room
        if (room.hostUid != currentUserId) {
            _snackbarEvents.tryEmit("Запустить генерацию хода Мастера может только хост комнаты")
            return
        }
        if (_gmRoundTurnState.value is AiTurnState.Loading || _gmRoundTurnState.value is AiTurnState.Streaming) {
            return
        }

        val readyPlayers = snap.players.filter {
            it.submittedRound == room.currentRound && (it.pendingAction.isNotBlank() || it.pendingDiceRoll.isNotBlank())
        }
        if (readyPlayers.isEmpty()) {
            _snackbarEvents.tryEmit("Хотя бы один игрок должен отправить заявку на раунд №${room.currentRound}")
            return
        }
        if (!forceEvenIfIncomplete && !snap.allMembersReady) return

        viewModelScope.launch {
            _gmRoundTurnState.value = AiTurnState.Loading
            repository.setRoomPhase(room.roomCode, "GM_RESPONDING")
            try {
                val settings: AppSettings = settingsStore.settingsFlow.first()
                val inputs = snap.players.mapNotNull { p ->
                    if (p.submittedRound == room.currentRound && (p.pendingAction.isNotBlank() || p.pendingDiceRoll.isNotBlank())) {
                        PartyActionInput(
                            heroName = p.heroName,
                            heroArchetype = p.heroArchetype,
                            heroStats = if (room.useDiceAndStats) p.heroStats else "",
                            actionText = p.pendingAction,
                            diceRoll = if (room.useDiceAndStats) p.pendingDiceRoll else ""
                        )
                    } else {
                        PartyActionInput(
                            heroName = p.heroName,
                            heroArchetype = p.heroArchetype,
                            heroStats = "",
                            actionText = "(Ожидает действий спутников и держится рядом с отрядом)"
                        )
                    }
                }

                val partySummaryText = readyPlayers.joinToString("\n") { p ->
                    val roll = if (room.useDiceAndStats && p.pendingDiceRoll.isNotBlank()) " [${p.pendingDiceRoll}]" else ""
                    "• ${p.heroName}$roll: ${p.pendingAction}"
                }

                val context = MultiplayerRoundContext(
                    roomTitle = room.title,
                    worldName = room.worldName,
                    worldGenre = room.worldGenre,
                    worldDescription = room.worldDescription,
                    diceSystem = room.diceSystem,
                    magicOrTechSystem = room.magicOrTechSystem,
                    narrativeTone = room.narrativeTone,
                    customRules = room.customRules,
                    useDiceAndStats = room.useDiceAndStats,
                    useInventory = room.useInventory,
                    useSpells = room.useSpells,
                    roundNumber = room.currentRound.toInt(),
                    hiddenPlot = HiddenPlotPlan(
                        plotArc = room.hiddenPlotArc,
                        npcKnowledgeBoundaries = room.hiddenNpcKnowledge,
                        triggersAndConsequences = room.hiddenPlotTriggers
                    ),
                    memorySummary = room.memorySummary,
                    previousRoundsTranscript = snap.rounds.takeLast(6).map { r ->
                        if (r.partyActionsSummary.isNotBlank()) {
                            "Раунд ${r.roundNumber}:\n${r.partyActionsSummary}\n\nМастер:\n${r.gmNarrative}"
                        } else {
                            "Пролог:\n${r.gmNarrative}"
                        }
                    }
                )

                val (gmText, updatedHiddenPlot) = geminiService.generateMultiplayerRoundTurn(
                    settings = settings,
                    context = context,
                    partyActions = inputs,
                    onStreamingChunk = { partial ->
                        _gmRoundTurnState.value = AiTurnState.Streaming(partial)
                    }
                )

                val publishRes = repository.publishGmRoundAndAdvance(
                    roomCode = room.roomCode,
                    roundNumber = room.currentRound,
                    partyActionsSummary = partySummaryText,
                    gmNarrative = gmText,
                    updatedHiddenPlot = updatedHiddenPlot
                )
                publishRes.onSuccess {
                    _gmRoundTurnState.value = AiTurnState.Idle
                }.onFailure { err ->
                    repository.setRoomPhase(room.roomCode, "COLLECTING")
                    _gmRoundTurnState.value = AiTurnState.Error(friendlyFirestoreErrorMessage(err))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                repository.setRoomPhase(room.roomCode, "COLLECTING")
                _gmRoundTurnState.value = AiTurnState.Error(geminiService.mapErrorToMessage(e))
            }
        }
    }

    fun saveRoomHiddenPlotAndModules(
        hiddenPlot: HiddenPlotPlan,
        useDiceAndStats: Boolean,
        useInventory: Boolean,
        useSpells: Boolean
    ) {
        val snap = (activeRoomStateFlow.value as? MultiplayerFeedState.Success)?.data ?: return
        if (snap.room.hostUid != currentUserId) return
        viewModelScope.launch {
            repository.updateRoomHostSettings(
                roomCode = snap.room.roomCode,
                hiddenPlot = hiddenPlot,
                useDiceAndStats = useDiceAndStats,
                useInventory = useInventory,
                useSpells = useSpells
            ).onSuccess {
                _snackbarEvents.tryEmit("Настройки комнаты и закулисье Мастера сохранены")
            }.onFailure {
                _snackbarEvents.tryEmit(friendlyFirestoreErrorMessage(it))
            }
        }
    }

    fun generateRoomHiddenPlotWithAi(authorDirectionHint: String) {
        val snap = (activeRoomStateFlow.value as? MultiplayerFeedState.Success)?.data ?: return
        val room = snap.room
        if (room.hostUid != currentUserId || _isGeneratingHiddenPlot.value) return
        viewModelScope.launch {
            _isGeneratingHiddenPlot.value = true
            try {
                val settings = settingsStore.settingsFlow.first()
                val syntheticCampaign = CampaignEntity(
                    title = room.title,
                    heroName = snap.players.joinToString(", ") { it.heroName }.ifBlank { "Отряд героев" },
                    heroArchetype = "Партия игроков",
                    heroStatsSummary = "",
                    settingName = room.worldName,
                    settingGenre = room.worldGenre,
                    diceSystem = room.diceSystem,
                    magicOrTechSystem = room.magicOrTechSystem,
                    narrativeTone = room.narrativeTone,
                    customRules = room.customRules,
                    useDiceAndStats = room.useDiceAndStats,
                    useInventory = room.useInventory,
                    useSpells = room.useSpells,
                    hiddenPlotArc = room.hiddenPlotArc,
                    hiddenNpcKnowledge = room.hiddenNpcKnowledge,
                    hiddenPlotTriggers = room.hiddenPlotTriggers,
                    memorySummary = snap.rounds.takeLast(5).joinToString("\n\n") { it.gmNarrative.take(500) }
                )
                val generated = geminiService.generateOrRefreshHiddenPlotPlan(
                    settings = settings,
                    campaign = syntheticCampaign,
                    activeWorld = WorldEntity(
                        name = room.worldName,
                        originType = "CUSTOM",
                        genre = room.worldGenre,
                        worldDescription = room.worldDescription,
                        diceSystem = room.diceSystem,
                        magicOrTechSystem = room.magicOrTechSystem,
                        narrativeTone = room.narrativeTone,
                        customRules = room.customRules
                    ),
                    recentMessages = emptyList(),
                    journal = emptyList(),
                    authorDirectionHint = authorDirectionHint
                )
                repository.updateRoomHostSettings(
                    roomCode = room.roomCode,
                    hiddenPlot = generated,
                    useDiceAndStats = room.useDiceAndStats,
                    useInventory = room.useInventory,
                    useSpells = room.useSpells
                ).onSuccess {
                    _snackbarEvents.tryEmit("Скрытый сюжетный план комнаты обновлён ИИ!")
                }.onFailure {
                    _snackbarEvents.tryEmit(friendlyFirestoreErrorMessage(it))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarEvents.tryEmit(geminiService.mapErrorToMessage(e))
            } finally {
                _isGeneratingHiddenPlot.value = false
            }
        }
    }

    fun deleteActiveRoom() {
        val code = _activeRoomCode.value ?: return
        viewModelScope.launch {
            repository.deleteRoom(code)
                .onSuccess {
                    _activeRoomCode.value = null
                    _snackbarEvents.tryEmit("Комната $code удалена")
                }
                .onFailure {
                    _snackbarEvents.tryEmit(friendlyFirestoreErrorMessage(it))
                }
        }
    }

    companion object {
        fun provideFactory(
            context: Context,
            currentUserId: String,
            currentUserDisplayName: String
        ): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = FirebaseFirestore.getInstance(
                        appContext.getString(R.string.firestore_database_id)
                    )
                    val repo = MultiplayerRepository(db)
                    val prefs = SettingsDataStore(appContext)
                    val vault = SecureCredentialVault(appContext)
                    val oauthHelper = GoogleOAuthHelper()
                    val gateway = AiProviderGateway(
                        appContext = appContext,
                        credentialVault = vault,
                        googleOAuthHelper = oauthHelper
                    )
                    return OnlineMultiplayerViewModel(
                        currentUserId = currentUserId,
                        currentUserDisplayName = currentUserDisplayName,
                        repository = repo,
                        settingsStore = prefs,
                        geminiService = GeminiMasterService(gateway)
                    ) as T
                }
            }
        }
    }
}
