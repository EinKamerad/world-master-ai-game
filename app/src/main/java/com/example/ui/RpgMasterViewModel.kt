package com.example.ui

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.AiProviderGateway
import com.example.ai.AiWorldArchitectService
import com.example.ai.ConnectionProbeResult
import com.example.ai.GeminiMasterService
import com.example.ai.GeneratedSpellDraft
import com.example.ai.GeneratedWorldDraft
import com.example.ai.GoogleAuthStepResult
import com.example.ai.GoogleOAuthHelper
import com.example.data.local.CampaignEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.CustomSpellEntity
import com.example.data.local.InventoryItemEntity
import com.example.data.local.JournalEntryEntity
import com.example.data.local.KnowledgeChunkEntity
import com.example.data.local.RpgDatabase
import com.example.data.local.WorldEntity
import com.example.data.local.WorldLoreEntity
import com.example.data.local.serializeGroundingSources
import com.example.data.preferences.AiCredentialsSnapshot
import com.example.data.preferences.AiEndpointValidator
import com.example.data.preferences.AiProviderType
import com.example.data.preferences.AppSettings
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.SecureCredentialVault
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.MemoryUsageStats
import com.example.data.repository.RpgRepository
import com.example.data.transfer.CampaignTransferManager
import com.example.data.transfer.DiceRoller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.example.ai.HiddenPlotPlan
import com.example.data.local.QaMessageEntity
import com.example.data.transfer.JsonTransferSelection
import com.example.data.transfer.ParsedJsonTransferBundle

private const val TAG = "RpgMasterViewModel"

enum class MainTab {
    MASTER,
    INVENTORY,
    JOURNAL,
    SPELLS,
    ONLINE,
    WORLD_SETTINGS
}

sealed interface AiTurnState {
    data object Idle : AiTurnState
    data object Loading : AiTurnState
    data class Streaming(val partialText: String) : AiTurnState
    data class Error(
        val message: String,
        val lastPrompt: String? = null,
        val lastDiceInfo: String? = null
    ) : AiTurnState
}

sealed interface SpellAiState {
    data object Idle : SpellAiState
    data object Loading : SpellAiState
    data class Success(val draft: GeneratedSpellDraft) : SpellAiState
    data class Error(val message: String) : SpellAiState
}

sealed interface WorldGenUiState {
    data object Idle : WorldGenUiState
    data class Loading(val progressMessage: String) : WorldGenUiState
    data class Ready(
        val draft: GeneratedWorldDraft,
        val originType: String,
        val franchiseSource: String = ""
    ) : WorldGenUiState
    data class Error(val message: String) : WorldGenUiState
}

data class ExportPayload(
    val fileName: String,
    val mimeType: String,
    val content: String,
    val shareDirectly: Boolean
)

data class RpgUiState(
    val settings: AppSettings = AppSettings(),
    val credentials: AiCredentialsSnapshot = AiCredentialsSnapshot(),
    val currentTab: MainTab = MainTab.MASTER,
    val worldHubSubTab: Int = 0, // 0=Миры, 1=С нуля, 2=По вселенной, 3=ИИ и Ключи, 4=Игры и Настройки
    val worlds: List<WorldEntity> = emptyList(),
    val allWorldLore: List<WorldLoreEntity> = emptyList(),
    val campaigns: List<CampaignEntity> = emptyList(),
    val activeCampaign: CampaignEntity? = null,
    val activeWorld: WorldEntity? = null,
    val messages: List<ChatMessageEntity> = emptyList(),
    val inventory: List<InventoryItemEntity> = emptyList(),
    val journal: List<JournalEntryEntity> = emptyList(),
    val spells: List<CustomSpellEntity> = emptyList(),
    val knowledgeChunks: List<KnowledgeChunkEntity> = emptyList(),
    val qaMessages: List<QaMessageEntity> = emptyList(),
    val memoryStats: MemoryUsageStats = MemoryUsageStats(),
    val aiTurnState: AiTurnState = AiTurnState.Idle,
    val qaTurnState: AiTurnState = AiTurnState.Idle,
    val isGeneratingHiddenPlot: Boolean = false,
    val pendingJsonImportPreview: ParsedJsonTransferBundle? = null,
    val isOptimizingMemory: Boolean = false,
    val spellAiState: SpellAiState = SpellAiState.Idle,
    val scratchWorldGenState: WorldGenUiState = WorldGenUiState.Idle,
    val universeSearchState: WorldGenUiState = WorldGenUiState.Idle,
    val isTestingAiConnection: Boolean = false,
    val connectionProbeResult: ConnectionProbeResult? = null,
    val isFetchingProviderModels: Boolean = false,
    val discoveredProviderModels: List<String> = emptyList(),
    val isGoogleOAuthLoading: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class RpgMasterViewModel(
    private val repository: RpgRepository,
    private val settingsStore: SettingsDataStore,
    private val credentialVault: SecureCredentialVault,
    private val aiGateway: AiProviderGateway,
    private val geminiService: GeminiMasterService = GeminiMasterService(aiGateway),
    private val worldArchitectService: AiWorldArchitectService = AiWorldArchitectService(aiGateway),
    private val googleOAuthHelper: GoogleOAuthHelper = GoogleOAuthHelper()
) : ViewModel() {

    private val _currentTab = MutableStateFlow(MainTab.MASTER)
    private val _worldHubSubTab = MutableStateFlow(0)
    private val _aiTurnState = MutableStateFlow<AiTurnState>(AiTurnState.Idle)
    private val _qaTurnState = MutableStateFlow<AiTurnState>(AiTurnState.Idle)
    private val _isGeneratingHiddenPlot = MutableStateFlow(false)
    private val _pendingJsonImportPreview = MutableStateFlow<ParsedJsonTransferBundle?>(null)
    private val _isOptimizingMemory = MutableStateFlow(false)
    private val _spellAiState = MutableStateFlow<SpellAiState>(SpellAiState.Idle)
    private val _scratchWorldGenState = MutableStateFlow<WorldGenUiState>(WorldGenUiState.Idle)
    private val _universeSearchState = MutableStateFlow<WorldGenUiState>(WorldGenUiState.Idle)

    private val _isTestingAiConnection = MutableStateFlow(false)
    private val _connectionProbeResult = MutableStateFlow<ConnectionProbeResult?>(null)
    private val _isFetchingProviderModels = MutableStateFlow(false)
    private val _discoveredProviderModels = MutableStateFlow<List<String>>(emptyList())
    private val _isGoogleOAuthLoading = MutableStateFlow(false)

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarEvents = _snackbarEvents.asSharedFlow()

    private val _exportEvents = MutableSharedFlow<ExportPayload>(extraBufferCapacity = 2)
    val exportEvents = _exportEvents.asSharedFlow()

    private val _googleOAuthConsentEvents = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val googleOAuthConsentEvents = _googleOAuthConsentEvents.asSharedFlow()

    val settingsFlow: StateFlow<AppSettings> = settingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    init {
        viewModelScope.launch {
            val currentSettings = settingsFlow.value
            val starterCampaignId = repository.ensureStarterDataExists(currentSettings.defaultMaxTokens)
            if (settingsFlow.value.activeCampaignId == 0L) {
                settingsStore.setActiveCampaignId(starterCampaignId)
            }
        }
    }

    private val activeCampaignIdFlow = combine(
        settingsStore.settingsFlow.map { it.activeCampaignId }.distinctUntilChanged(),
        repository.observeCampaigns()
    ) { savedId, campaigns ->
        when {
            campaigns.any { it.id == savedId } -> savedId
            campaigns.isNotEmpty() -> campaigns.first().id
            else -> 0L
        }
    }.distinctUntilChanged()

    private data class CampaignBundle(
        val campaign: CampaignEntity?,
        val messages: List<ChatMessageEntity>,
        val inventory: List<InventoryItemEntity>,
        val journal: List<JournalEntryEntity>,
        val spells: List<CustomSpellEntity>,
        val knowledge: List<KnowledgeChunkEntity>,
        val qaMessages: List<QaMessageEntity>
    )

    private val activeBundleFlow = activeCampaignIdFlow.flatMapLatest { campId ->
        if (campId <= 0L) {
            flowOf(CampaignBundle(null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()))
        } else {
            combine(
                repository.observeCampaign(campId),
                repository.observeMessages(campId),
                repository.observeInventory(campId),
                repository.observeJournal(campId),
                combine(
                    repository.observeSpells(campId),
                    repository.observeKnowledge(campId),
                    repository.observeQaMessages(campId)
                ) { sp, kb, qa -> Triple(sp, kb, qa) }
            ) { camp, msgs, inv, jrn, (sp, kb, qa) ->
                CampaignBundle(camp, msgs, inv, jrn, sp, kb, qa)
            }
        }
    }

    private data class WorldsBundle(
        val worlds: List<WorldEntity>,
        val allWorldLore: List<WorldLoreEntity>,
        val campaigns: List<CampaignEntity>
    )

    private val worldsAndCampaignsFlow = combine(
        repository.observeWorlds(),
        repository.observeAllWorldLore(),
        repository.observeCampaigns()
    ) { worlds, lore, campaigns ->
        WorldsBundle(worlds, lore, campaigns)
    }

    private data class ProviderHubUiBundle(
        val testing: Boolean,
        val probe: ConnectionProbeResult?,
        val fetchingModels: Boolean,
        val models: List<String>,
        val googleLoading: Boolean
    )

    private val providerHubFlow = combine(
        _isTestingAiConnection,
        _connectionProbeResult,
        _isFetchingProviderModels,
        _discoveredProviderModels,
        _isGoogleOAuthLoading
    ) { testing, probe, fetching, models, gLoading ->
        ProviderHubUiBundle(testing, probe, fetching, models, gLoading)
    }

    private data class TransientAiBundle(
        val turnState: AiTurnState,
        val qaState: AiTurnState,
        val generatingPlot: Boolean,
        val importPreview: ParsedJsonTransferBundle?,
        val optimizing: Boolean,
        val spellState: SpellAiState,
        val scratchWorldState: WorldGenUiState,
        val universeState: WorldGenUiState,
        val hubBundle: ProviderHubUiBundle
    )

    private data class ExtraFeaturesBundle(
        val qaState: AiTurnState,
        val generatingPlot: Boolean,
        val importPreview: ParsedJsonTransferBundle?
    )

    private val extraFeaturesFlow = combine(
        _qaTurnState,
        _isGeneratingHiddenPlot,
        _pendingJsonImportPreview
    ) { qa, genPlot, imp ->
        ExtraFeaturesBundle(qa, genPlot, imp)
    }

    private val transientAiFlow = combine(
        combine(_aiTurnState, _isOptimizingMemory, _spellAiState) { t, o, s -> Triple(t, o, s) },
        combine(_scratchWorldGenState, _universeSearchState) { sc, un -> sc to un },
        providerHubFlow,
        extraFeaturesFlow
    ) { (turn, opt, spAi), (scratch, univ), hub, extra ->
        TransientAiBundle(
            turnState = turn,
            qaState = extra.qaState,
            generatingPlot = extra.generatingPlot,
            importPreview = extra.importPreview,
            optimizing = opt,
            spellState = spAi,
            scratchWorldState = scratch,
            universeState = univ,
            hubBundle = hub
        )
    }

    private val navAndAuthStateFlow = combine(
        settingsStore.settingsFlow,
        credentialVault.credentialsFlow,
        _currentTab,
        _worldHubSubTab
    ) { settings, creds, tab, subTab ->
        NavAndAuthBundle(settings, creds, tab, subTab)
    }

    private data class NavAndAuthBundle(
        val settings: AppSettings,
        val creds: AiCredentialsSnapshot,
        val tab: MainTab,
        val subTab: Int
    )

    val uiState: StateFlow<RpgUiState> = combine(
        navAndAuthStateFlow,
        worldsAndCampaignsFlow,
        activeBundleFlow,
        transientAiFlow
    ) { navAuth, wcBundle, bundle, aiBundle ->
        val settings = navAuth.settings
        val activeWorld = bundle.campaign?.let { camp ->
            wcBundle.worlds.firstOrNull { it.id == camp.worldId }
                ?: wcBundle.worlds.firstOrNull { it.name.equals(camp.settingName, ignoreCase = true) }
        }
        val activeWorldLore = if (activeWorld != null) {
            wcBundle.allWorldLore.filter { it.worldId == activeWorld.id }
        } else emptyList()

        val memStats = repository.calculateMemoryStats(
            campaign = bundle.campaign,
            allMessages = bundle.messages,
            inventory = bundle.inventory,
            journal = bundle.journal,
            spells = bundle.spells,
            worldLore = activeWorldLore,
            knowledge = bundle.knowledge,
            warningThresholdPercent = settings.memoryWarningPercent
        )

        RpgUiState(
            settings = settings,
            credentials = navAuth.creds,
            currentTab = navAuth.tab,
            worldHubSubTab = navAuth.subTab,
            worlds = wcBundle.worlds,
            allWorldLore = wcBundle.allWorldLore,
            campaigns = wcBundle.campaigns,
            activeCampaign = bundle.campaign,
            activeWorld = activeWorld,
            messages = bundle.messages,
            inventory = bundle.inventory,
            journal = bundle.journal,
            spells = bundle.spells,
            knowledgeChunks = bundle.knowledge,
            qaMessages = bundle.qaMessages,
            memoryStats = memStats,
            aiTurnState = aiBundle.turnState,
            qaTurnState = aiBundle.qaState,
            isGeneratingHiddenPlot = aiBundle.generatingPlot,
            pendingJsonImportPreview = aiBundle.importPreview,
            isOptimizingMemory = aiBundle.optimizing,
            spellAiState = aiBundle.spellState,
            scratchWorldGenState = aiBundle.scratchWorldState,
            universeSearchState = aiBundle.universeState,
            isTestingAiConnection = aiBundle.hubBundle.testing,
            connectionProbeResult = aiBundle.hubBundle.probe,
            isFetchingProviderModels = aiBundle.hubBundle.fetchingModels,
            discoveredProviderModels = aiBundle.hubBundle.models,
            isGoogleOAuthLoading = aiBundle.hubBundle.googleLoading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RpgUiState()
    )

    fun selectTab(tab: MainTab) {
        _currentTab.value = tab
    }

    fun selectWorldHubSubTab(index: Int) {
        _worldHubSubTab.value = index.coerceIn(0, 4)
    }

    fun openAiConnectionsHub() {
        _worldHubSubTab.value = 3
        _currentTab.value = MainTab.WORLD_SETTINGS
    }

    fun dismissAiError() {
        if (_aiTurnState.value is AiTurnState.Error) {
            _aiTurnState.value = AiTurnState.Idle
        }
    }

    fun resetSpellAiState() {
        _spellAiState.value = SpellAiState.Idle
    }

    fun resetScratchWorldGenState() {
        _scratchWorldGenState.value = WorldGenUiState.Idle
    }

    fun resetUniverseSearchState() {
        _universeSearchState.value = WorldGenUiState.Idle
    }

    // --- AI Provider & Credentials Actions ---
    fun setAiProvider(provider: AiProviderType) {
        viewModelScope.launch {
            _connectionProbeResult.value = null
            _discoveredProviderModels.value = emptyList()
            settingsStore.setAiProvider(provider)
        }
    }

    fun setAutoFallbackToFirebase(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoFallbackToFirebase(enabled) }
    }

    fun saveAntigravitySettings(baseUrl: String, model: String, token: String) {
        val validated = AiEndpointValidator.normalizeAndValidateBaseUrl(baseUrl)
        if (validated.isFailure) {
            _snackbarEvents.tryEmit(validated.exceptionOrNull()?.message ?: "Некорректный URL шлюза Антигравити")
            return
        }
        viewModelScope.launch {
            credentialVault.saveAntigravityToken(token)
            settingsStore.updateAntigravityConfig(validated.getOrThrow(), model)
            _snackbarEvents.tryEmit("Настройки Антигравити API сохранены")
        }
    }

    fun startGoogleOAuthSignIn(context: Context) {
        if (_isGoogleOAuthLoading.value) return
        viewModelScope.launch {
            _isGoogleOAuthLoading.value = true
            try {
                when (val step = googleOAuthHelper.beginAuthorization(context)) {
                    is GoogleAuthStepResult.Authorized -> {
                        credentialVault.saveGoogleOAuthSession(step.accessToken, step.email)
                        val label = step.email.ifBlank { "токен получен" }
                        _snackbarEvents.tryEmit("Подключён аккаунт Google ($label)")
                    }
                    is GoogleAuthStepResult.NeedsConsent -> {
                        _googleOAuthConsentEvents.tryEmit(step.intentSender)
                    }
                    is GoogleAuthStepResult.Failed -> {
                        _snackbarEvents.tryEmit(step.message)
                    }
                }
            } finally {
                _isGoogleOAuthLoading.value = false
            }
        }
    }

    fun finishGoogleOAuthFromIntent(context: Context, data: Intent?) {
        viewModelScope.launch {
            _isGoogleOAuthLoading.value = true
            try {
                when (val res = googleOAuthHelper.completeAuthorizationFromIntent(context, data)) {
                    is GoogleAuthStepResult.Authorized -> {
                        credentialVault.saveGoogleOAuthSession(res.accessToken, res.email)
                        val label = res.email.ifBlank { "токен получен" }
                        _snackbarEvents.tryEmit("Вход через аккаунт Google выполнен ($label)")
                    }
                    is GoogleAuthStepResult.Failed -> {
                        _snackbarEvents.tryEmit(res.message)
                    }
                    is GoogleAuthStepResult.NeedsConsent -> Unit
                }
            } finally {
                _isGoogleOAuthLoading.value = false
            }
        }
    }

    fun signOutGoogleOAuth() {
        credentialVault.clearGoogleOAuthSession()
        _snackbarEvents.tryEmit("Сессия Google OAuth отключена")
    }

    fun saveGoogleOAuthManualSettings(model: String, projectId: String, manualToken: String) {
        viewModelScope.launch {
            settingsStore.updateGoogleOAuthConfig(model = model, projectId = projectId)
            val cleanToken = manualToken.trim()
            if (cleanToken != credentialVault.current.googleOAuthAccessToken) {
                if (cleanToken.isBlank()) {
                    credentialVault.clearGoogleOAuthSession()
                } else {
                    val email = googleOAuthHelper.fetchUserEmail(cleanToken)
                    credentialVault.saveGoogleOAuthSession(cleanToken, email)
                }
            }
            _snackbarEvents.tryEmit("Параметры Google / Gemini OAuth сохранены")
        }
    }

    fun saveGeminiApiKeysSettings(keysRaw: String, model: String) {
        viewModelScope.launch {
            credentialVault.saveGeminiApiKeys(keysRaw)
            settingsStore.setGeminiKeyModel(model)
            val count = credentialVault.current.geminiKeysPool().size
            _snackbarEvents.tryEmit("Сохранено ключей Gemini в пуле ротации: $count")
        }
    }

    fun saveOpenRouterSettings(keysRaw: String, model: String) {
        viewModelScope.launch {
            credentialVault.saveOpenRouterApiKeys(keysRaw)
            settingsStore.setOpenRouterModel(model)
            val count = credentialVault.current.openRouterKeysPool().size
            _snackbarEvents.tryEmit("Настройки OpenRouter сохранены (ключей: $count)")
        }
    }

    fun saveOpenAiCompatibleSettings(baseUrl: String, model: String, keysRaw: String) {
        val validated = AiEndpointValidator.normalizeAndValidateBaseUrl(baseUrl)
        if (validated.isFailure) {
            _snackbarEvents.tryEmit(validated.exceptionOrNull()?.message ?: "Некорректный Base URL")
            return
        }
        viewModelScope.launch {
            credentialVault.saveOpenAiCompatibleApiKeys(keysRaw)
            settingsStore.updateOpenAiCompatibleConfig(validated.getOrThrow(), model)
            _snackbarEvents.tryEmit("Настройки совместимого API сохранены")
        }
    }

    fun saveAnthropicSettings(keysRaw: String, model: String) {
        viewModelScope.launch {
            credentialVault.saveAnthropicApiKeys(keysRaw)
            settingsStore.setAnthropicModel(model)
            val count = credentialVault.current.anthropicKeysPool().size
            _snackbarEvents.tryEmit("Ключи Anthropic Claude сохранены ($count шт.)")
        }
    }

    fun saveLocalServerSettings(baseUrl: String, model: String, apiKey: String) {
        val validated = AiEndpointValidator.normalizeAndValidateBaseUrl(baseUrl)
        if (validated.isFailure) {
            _snackbarEvents.tryEmit(validated.exceptionOrNull()?.message ?: "Некорректный адрес локального сервера")
            return
        }
        viewModelScope.launch {
            credentialVault.saveLocalServerApiKey(apiKey)
            settingsStore.updateLocalServerConfig(validated.getOrThrow(), model)
            _snackbarEvents.tryEmit("Локальный сервер ИИ сохранён")
        }
    }

    fun fetchModelsForCurrentProvider() {
        if (_isFetchingProviderModels.value) return
        viewModelScope.launch {
            _isFetchingProviderModels.value = true
            try {
                // Allow any preceding DataStore save call to flush before reading settings
                delay(80)
                val settings = settingsStore.settingsFlow.first()
                val res = aiGateway.fetchProviderModels(settings)
                res.onSuccess { list ->
                    _discoveredProviderModels.value = list
                    if (list.isEmpty()) {
                        val hint = if (settings.aiProvider == AiProviderType.ANTIGRAVITY_PROXY) {
                            "Связь с прокси есть, но список моделей пуст: добавьте аккаунт в прокси на ПК (http://localhost:3000 или npx antigravity-proxy accounts add)"
                        } else {
                            "Сервер не вернул список моделей"
                        }
                        _snackbarEvents.tryEmit(hint)
                    } else {
                        _snackbarEvents.tryEmit("Загружено моделей: ${list.size}")
                    }
                }.onFailure { err ->
                    _snackbarEvents.tryEmit(aiGateway.mapReadableError(err))
                }
            } finally {
                _isFetchingProviderModels.value = false
            }
        }
    }

    fun testCurrentAiConnection() {
        if (_isTestingAiConnection.value) return
        viewModelScope.launch {
            _isTestingAiConnection.value = true
            _connectionProbeResult.value = null
            try {
                delay(60)
                val freshSettings = settingsStore.settingsFlow.first()
                val result = aiGateway.testActiveConnection(freshSettings)
                _connectionProbeResult.value = result
            } finally {
                _isTestingAiConnection.value = false
            }
        }
    }

    // --- General Settings Actions ---
    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun toggleDarkLightQuick() {
        val next = if (uiState.value.settings.themeMode == AppThemeMode.DARK) {
            AppThemeMode.LIGHT
        } else {
            AppThemeMode.DARK
        }
        setThemeMode(next)
    }

    fun setModelName(modelName: String) {
        viewModelScope.launch { settingsStore.setModelName(modelName) }
    }

    fun setWebSearchEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setWebSearchEnabled(enabled) }
    }

    fun setAutoTrackWorldState(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoTrackWorldState(enabled) }
    }

    fun setAutoCompressMemory(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoCompressMemory(enabled) }
    }

    fun setMemoryWarningPercent(percent: Int) {
        viewModelScope.launch { settingsStore.setMemoryWarningPercent(percent) }
    }

    fun setCampaignMaxTokens(tokens: Int) {
        viewModelScope.launch {
            settingsStore.setDefaultMaxTokens(tokens)
            uiState.value.activeCampaign?.let { camp ->
                repository.updateCampaign(camp.copy(maxContextTokens = tokens))
            }
        }
    }

    // --- 1. Worlds Library & AI World Creation ---
    fun generateWorldFromScratchAi(description: String, rulesOrToneHint: String) {
        if (description.isBlank()) return
        val settings = uiState.value.settings
        viewModelScope.launch {
            _scratchWorldGenState.value = WorldGenUiState.Loading(
                "ИИ (${settings.aiProvider.shortBadge}) создаёт мир, механику правил, архетипы героев и базу знаний..."
            )
            try {
                val draft = worldArchitectService.generateWorldFromDescription(
                    settings = settings,
                    userIdea = description,
                    rulesOrToneHint = rulesOrToneHint
                )
                _scratchWorldGenState.value = WorldGenUiState.Ready(
                    draft = draft,
                    originType = "AI_ORIGINAL"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _scratchWorldGenState.value = WorldGenUiState.Error(worldArchitectService.mapError(e))
            }
        }
    }

    fun searchAndBuildExistingUniverseAi(universeTitle: String, eraOrFocusHint: String) {
        if (universeTitle.isBlank()) return
        val settings = uiState.value.settings
        viewModelScope.launch {
            _universeSearchState.value = WorldGenUiState.Loading(
                "Ищем информацию о вселенной «${universeTitle.trim()}»..."
            )
            try {
                val draft = worldArchitectService.researchExistingUniverse(
                    settings = settings,
                    universeName = universeTitle,
                    eraOrFocusHint = eraOrFocusHint,
                    onStageUpdate = { stageMsg ->
                        _universeSearchState.value = WorldGenUiState.Loading(stageMsg)
                    }
                )
                _universeSearchState.value = WorldGenUiState.Ready(
                    draft = draft,
                    originType = "EXISTING_UNIVERSE",
                    franchiseSource = universeTitle.trim()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _universeSearchState.value = WorldGenUiState.Error(worldArchitectService.mapError(e))
            }
        }
    }

    fun saveWorldDraftToLibrary(
        draft: GeneratedWorldDraft,
        originType: String,
        franchiseSource: String = "",
        onSavedWorld: ((WorldEntity) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val worldId = repository.saveGeneratedWorldToLibrary(
                draft = draft,
                originType = originType,
                franchiseSource = franchiseSource
            )
            if (originType == "EXISTING_UNIVERSE") {
                _universeSearchState.value = WorldGenUiState.Idle
            } else {
                _scratchWorldGenState.value = WorldGenUiState.Idle
            }
            _snackbarEvents.tryEmit("Мир «${draft.name}» добавлен в вашу библиотеку миров!")
            if (onSavedWorld != null) {
                repository.getWorldById(worldId)?.let { onSavedWorld(it) }
            }
        }
    }

    fun saveManualWorld(world: WorldEntity) {
        viewModelScope.launch {
            repository.saveManualWorld(world)
            val activeCamp = uiState.value.activeCampaign
            if (activeCamp != null && activeCamp.worldId == world.id && world.id > 0L) {
                repository.updateCampaign(
                    activeCamp.copy(
                        settingName = world.name,
                        settingGenre = world.genre,
                        diceSystem = world.diceSystem,
                        magicOrTechSystem = world.magicOrTechSystem,
                        narrativeTone = world.narrativeTone,
                        customRules = world.customRules
                    )
                )
            }
            _snackbarEvents.tryEmit("Мир «${world.name}» сохранён в библиотеке")
        }
    }

    fun deleteWorld(world: WorldEntity) {
        viewModelScope.launch {
            if (uiState.value.worlds.size <= 1) {
                _snackbarEvents.tryEmit("В библиотеке должен оставаться хотя бы один мир")
                return@launch
            }
            repository.deleteWorld(world)
            _snackbarEvents.tryEmit("Мир «${world.name}» удалён из библиотеки")
        }
    }

    fun saveWorldLore(item: WorldLoreEntity) {
        viewModelScope.launch {
            repository.saveWorldLore(item)
            _snackbarEvents.tryEmit("Раздел базы знаний мира сохранён")
        }
    }

    fun toggleWorldLoreAlwaysActive(item: WorldLoreEntity) {
        viewModelScope.launch {
            repository.saveWorldLore(item.copy(isAlwaysActive = !item.isAlwaysActive))
        }
    }

    fun deleteWorldLore(item: WorldLoreEntity) {
        viewModelScope.launch {
            repository.deleteWorldLore(item)
        }
    }

    fun importWorldLoreFromUri(context: Context, uri: Uri, targetWorldId: Long) {
        viewModelScope.launch {
            try {
                val fileName = resolveFileName(context, uri) ?: "Лор_мира.txt"
                val rawText = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
                if (rawText.isBlank()) {
                    _snackbarEvents.tryEmit("Выбранный файл пуст")
                    return@launch
                }
                val sections = CampaignTransferManager.parseWorldLoreDocument(
                    fileName = fileName,
                    rawText = rawText,
                    worldId = targetWorldId
                )
                if (sections.isEmpty()) {
                    _snackbarEvents.tryEmit("Не удалось распознать разделы в файле")
                } else {
                    repository.insertWorldLoreList(sections)
                    _snackbarEvents.tryEmit("В базу знаний мира импортировано статей: ${sections.size}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "World lore import failed", e)
                _snackbarEvents.tryEmit("Ошибка чтения файла базы знаний")
            }
        }
    }

    // --- 2. Campaigns / Games & Character Creation ---
    fun switchCampaign(campaignId: Long) {
        viewModelScope.launch {
            _aiTurnState.value = AiTurnState.Idle
            _qaTurnState.value = AiTurnState.Idle
            settingsStore.setActiveCampaignId(campaignId)
        }
    }

    fun createNewGameWithCharacter(
        selectedWorld: WorldEntity,
        campaignTitle: String,
        heroName: String,
        heroArchetype: String,
        heroStatsSummary: String,
        heroBackstory: String,
        customOpeningScene: String,
        useDiceAndStats: Boolean = true,
        useInventory: Boolean = true,
        useSpells: Boolean = true
    ) {
        viewModelScope.launch {
            val newId = repository.startNewGameInWorld(
                world = selectedWorld,
                campaignTitle = campaignTitle,
                heroName = heroName,
                heroArchetype = heroArchetype,
                heroStatsSummary = heroStatsSummary,
                heroBackstory = heroBackstory,
                customOpeningScene = customOpeningScene,
                maxTokens = uiState.value.settings.defaultMaxTokens,
                useDiceAndStats = useDiceAndStats,
                useInventory = useInventory,
                useSpells = useSpells
            )
            settingsStore.setActiveCampaignId(newId)
            _currentTab.value = MainTab.MASTER
            _snackbarEvents.tryEmit("Начата игра «${campaignTitle.ifBlank { selectedWorld.name }}» за героя ${heroName.ifBlank { "Странник" }}")
        }
    }

    fun updateActiveCampaignDetails(updated: CampaignEntity) {
        viewModelScope.launch {
            repository.updateCampaign(updated)
            if (!updated.useInventory && _currentTab.value == MainTab.INVENTORY) {
                _currentTab.value = MainTab.MASTER
            }
            if (!updated.useSpells && _currentTab.value == MainTab.SPELLS) {
                _currentTab.value = MainTab.MASTER
            }
            _snackbarEvents.tryEmit("Данные персонажа и сессии сохранены")
        }
    }

    fun deleteCampaign(campaign: CampaignEntity) {
        viewModelScope.launch {
            repository.deleteCampaign(campaign)
            val remaining = repository.getAllCampaignsOnce()
            if (remaining.isEmpty()) {
                val newId = repository.ensureStarterDataExists(uiState.value.settings.defaultMaxTokens)
                settingsStore.setActiveCampaignId(newId)
            } else if (uiState.value.activeCampaign?.id == campaign.id) {
                settingsStore.setActiveCampaignId(remaining.first().id)
            }
            _snackbarEvents.tryEmit("Сохранение удалено")
        }
    }

    // --- 3. Core Dungeon Master Chat & Memory Optimization ---
    fun sendPlayerAction(
        userText: String,
        diceRollInfo: String? = null,
        recordUserMessage: Boolean = true
    ) {
        val state = uiState.value
        val campaign = state.activeCampaign ?: return
        val cleanText = userText.trim()
        val effectiveDice = if (campaign.useDiceAndStats) diceRollInfo else null
        if (cleanText.isEmpty() && effectiveDice.isNullOrBlank()) return
        if (state.aiTurnState is AiTurnState.Loading || state.aiTurnState is AiTurnState.Streaming) return

        val finalPrompt = cleanText.ifEmpty { "Совершаю проверку кубика." }

        viewModelScope.launch {
            try {
                _aiTurnState.value = AiTurnState.Loading

                val priorActiveMessages = repository.getActiveUnarchivedMessages(campaign.id)

                if (recordUserMessage) {
                    repository.addMessage(
                        campaignId = campaign.id,
                        senderRole = "USER",
                        content = finalPrompt,
                        diceRollInfo = effectiveDice
                    )
                }

                var latestCampaign = campaign
                if (state.settings.autoCompressMemory && state.memoryStats.usagePercent >= 90 && priorActiveMessages.size >= 5) {
                    runCatching {
                        val toCompress = priorActiveMessages.dropLast(3)
                        if (toCompress.isNotEmpty()) {
                            val summary = geminiService.compressMemoryChronicle(
                                settings = state.settings,
                                campaign = latestCampaign,
                                messagesToArchive = toCompress
                            )
                            repository.applyMemoryCompression(
                                campaignId = latestCampaign.id,
                                newSummary = summary,
                                archivedMessageIds = toCompress.map { it.id }
                            )
                            latestCampaign = latestCampaign.copy(
                                memorySummary = summary,
                                compressedChaptersCount = latestCampaign.compressedChaptersCount + 1
                            )
                            _snackbarEvents.tryEmit("Память автоматически сжата в хронику")
                        }
                    }
                }

                val activeHistoryForTurn = repository.getActiveUnarchivedMessages(campaign.id)
                    .let { list -> if (list.isNotEmpty()) list.dropLast(1) else emptyList() }

                val fullQueryForRag = listOfNotNull(effectiveDice, finalPrompt).joinToString("\n")
                val currentWorld = state.activeWorld
                val worldLorePool = if (currentWorld != null) {
                    state.allWorldLore.filter { it.worldId == currentWorld.id }
                } else emptyList()

                val matchedWorldLore = repository.selectRelevantWorldLore(
                    worldLoreList = worldLorePool,
                    queryText = fullQueryForRag
                )
                val matchedSessionLore = repository.selectRelevantLore(
                    allChunks = state.knowledgeChunks,
                    queryText = fullQueryForRag
                )

                val turnResponse = geminiService.streamOrGenerateMasterTurn(
                    settings = state.settings,
                    campaign = latestCampaign,
                    activeWorld = currentWorld,
                    recentMessages = activeHistoryForTurn,
                    userPromptWithDice = fullQueryForRag,
                    inventory = state.inventory,
                    journal = state.journal,
                    spells = state.spells,
                    relevantWorldLore = matchedWorldLore,
                    relevantLore = matchedSessionLore,
                    useWebSearch = state.settings.webSearchEnabled,
                    onStreamingChunk = { partial ->
                        _aiTurnState.value = AiTurnState.Streaming(partial)
                    }
                )

                repository.addMessage(
                    campaignId = campaign.id,
                    senderRole = "GM",
                    content = turnResponse.narrativeText,
                    groundingSourcesJson = serializeGroundingSources(turnResponse.groundingSources)
                )
                _aiTurnState.value = AiTurnState.Idle

                if (!turnResponse.fallbackNotice.isNullOrBlank()) {
                    _snackbarEvents.tryEmit(turnResponse.fallbackNotice)
                }

                if (state.settings.autoTrackWorldState) {
                    launch {
                        val delta = geminiService.extractWorldStateDelta(
                            settings = state.settings,
                            campaign = latestCampaign,
                            playerAction = fullQueryForRag,
                            gmReply = turnResponse.narrativeText,
                            currentInventory = uiState.value.inventory,
                            currentJournal = uiState.value.journal
                        )
                        val count = repository.applyAiWorldDelta(campaign.id, delta)
                        if (count > 0) {
                            _snackbarEvents.tryEmit("Обновлены записи инвентаря / журнала ($count)")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "GM turn failed", e)
                _aiTurnState.value = AiTurnState.Error(
                    message = geminiService.mapErrorToMessage(e),
                    lastPrompt = finalPrompt,
                    lastDiceInfo = diceRollInfo
                )
            }
        }
    }

    fun retryFailedTurn() {
        val err = _aiTurnState.value as? AiTurnState.Error ?: return
        val prompt = err.lastPrompt ?: return
        sendPlayerAction(
            userText = prompt,
            diceRollInfo = err.lastDiceInfo,
            recordUserMessage = false
        )
    }

    fun optimizeMemoryNow() {
        val state = uiState.value
        val campaign = state.activeCampaign ?: return
        if (_isOptimizingMemory.value) return

        viewModelScope.launch {
            _isOptimizingMemory.value = true
            try {
                val unarchived = repository.getActiveUnarchivedMessages(campaign.id)
                if (unarchived.size <= 2) {
                    _snackbarEvents.tryEmit("Недостаточно новых сообщений для сжатия (нужно хотя бы 3)")
                    return@launch
                }
                val keepCount = if (unarchived.size > 4) 3 else 1
                val toArchive = unarchived.dropLast(keepCount)

                val newSummary = geminiService.compressMemoryChronicle(
                    settings = state.settings,
                    campaign = campaign,
                    messagesToArchive = toArchive
                )
                repository.applyMemoryCompression(
                    campaignId = campaign.id,
                    newSummary = newSummary,
                    archivedMessageIds = toArchive.map { it.id }
                )
                _snackbarEvents.tryEmit("Память оптимизирована! Сжато сообщений: ${toArchive.size}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Memory compression failed", e)
                _snackbarEvents.tryEmit("Ошибка сжатия памяти: ${geminiService.mapErrorToMessage(e)}")
            } finally {
                _isOptimizingMemory.value = false
            }
        }
    }

    // --- Interactive Inventory Actions ---
    fun saveInventoryItem(item: InventoryItemEntity) {
        viewModelScope.launch { repository.saveInventoryItem(item) }
    }

    fun toggleEquipItem(item: InventoryItemEntity) {
        viewModelScope.launch { repository.saveInventoryItem(item.copy(isEquipped = !item.isEquipped)) }
    }

    fun adjustItemQuantity(item: InventoryItemEntity, delta: Int) {
        viewModelScope.launch {
            val nextQty = item.quantity + delta
            if (nextQty <= 0) {
                repository.deleteInventoryItem(item)
                _snackbarEvents.tryEmit("Предмет «${item.name}» израсходован")
            } else {
                repository.saveInventoryItem(item.copy(quantity = nextQty))
            }
        }
    }

    fun deleteInventoryItem(item: InventoryItemEntity) {
        viewModelScope.launch { repository.deleteInventoryItem(item) }
    }

    fun useItemInGame(item: InventoryItemEntity) {
        viewModelScope.launch {
            if (item.category == "Расходники") {
                val remaining = item.quantity - 1
                if (remaining <= 0) repository.deleteInventoryItem(item)
                else repository.saveInventoryItem(item.copy(quantity = remaining))
            }
            val rollInfo = DiceRoller.rollFormulaOrNull(item.bonusOrEffect)?.formattedSummary
            val effectNote = if (item.bonusOrEffect.isNotBlank()) " (${item.bonusOrEffect})" else ""
            _currentTab.value = MainTab.MASTER
            sendPlayerAction(
                userText = "Использую снаряжение: «${item.name}»$effectNote.",
                diceRollInfo = rollInfo
            )
        }
    }

    fun inspectItemWithMaster(item: InventoryItemEntity) {
        _currentTab.value = MainTab.MASTER
        sendPlayerAction(
            userText = "Внимательно осматриваю предмет «${item.name}» и пытаюсь понять его скрытые свойства или историю."
        )
    }

    // --- Journal & Tags Actions ---
    fun saveJournalEntry(entry: JournalEntryEntity) {
        viewModelScope.launch {
            repository.saveJournalEntry(entry)
            _snackbarEvents.tryEmit("Запись журнала сохранена")
        }
    }

    fun togglePinJournalEntry(entry: JournalEntryEntity) {
        viewModelScope.launch { repository.saveJournalEntry(entry.copy(isPinned = !entry.isPinned)) }
    }

    fun deleteJournalEntry(entry: JournalEntryEntity) {
        viewModelScope.launch { repository.deleteJournalEntry(entry) }
    }

    fun interactWithJournalInChat(entry: JournalEntryEntity) {
        _currentTab.value = MainTab.MASTER
        val prompt = when (entry.entryType) {
            "НПС" -> "Обращаюсь к персонажу «${entry.title}» или вспоминаю детали нашей встречи."
            "Локация" -> "Осматриваюсь в локации «${entry.title}» и ищу интересные зацепки."
            "Квест" -> "Проверяю зацепки и продумываю следующий шаг по заданию «${entry.title}»."
            else -> "Обращаю внимание на сведения о «${entry.title}»."
        }
        sendPlayerAction(userText = prompt)
    }

    // --- Custom Spells & Abilities Actions ---
    fun saveSpell(spell: CustomSpellEntity) {
        viewModelScope.launch {
            repository.saveSpell(spell)
            _snackbarEvents.tryEmit("Заклинание «${spell.name}» сохранено в гримуар")
        }
    }

    fun toggleSpellPrepared(spell: CustomSpellEntity) {
        viewModelScope.launch { repository.saveSpell(spell.copy(isPrepared = !spell.isPrepared)) }
    }

    fun deleteSpell(spell: CustomSpellEntity) {
        viewModelScope.launch { repository.deleteSpell(spell) }
    }

    fun castSpellInGame(spell: CustomSpellEntity, targetNote: String = "") {
        val roll = DiceRoller.rollFormulaOrNull(spell.diceFormula)?.formattedSummary
        val targetSuffix = if (targetNote.isNotBlank()) " Цель/условие: $targetNote." else ""
        _currentTab.value = MainTab.MASTER
        sendPlayerAction(
            userText = "Применяю способность «${spell.name}» [${spell.rankOrLevel}, цена: ${spell.resourceCost}]. Эффект: ${spell.description}$targetSuffix",
            diceRollInfo = roll
        )
    }

    fun generateSpellWithAi(concept: String) {
        val state = uiState.value
        val campaign = state.activeCampaign ?: return
        if (concept.isBlank()) return

        viewModelScope.launch {
            _spellAiState.value = SpellAiState.Loading
            try {
                val draft = geminiService.generateCustomSpell(
                    settings = state.settings,
                    campaign = campaign,
                    conceptPrompt = concept
                )
                _spellAiState.value = SpellAiState.Success(draft)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Spell AI architect failed", e)
                _spellAiState.value = SpellAiState.Error(geminiService.mapErrorToMessage(e))
            }
        }
    }

    // --- Session-level Knowledge Base ---
    fun saveKnowledgeChunk(chunk: KnowledgeChunkEntity) {
        viewModelScope.launch {
            repository.saveKnowledgeChunk(chunk)
            _snackbarEvents.tryEmit("Запись базы знаний сохранена")
        }
    }

    fun toggleKnowledgeAlwaysActive(chunk: KnowledgeChunkEntity) {
        viewModelScope.launch {
            repository.saveKnowledgeChunk(chunk.copy(isAlwaysActive = !chunk.isAlwaysActive))
        }
    }

    fun deleteKnowledgeChunk(chunk: KnowledgeChunkEntity) {
        viewModelScope.launch { repository.deleteKnowledgeChunk(chunk) }
    }

    fun importKnowledgeFromUri(context: Context, uri: Uri) {
        val campaignId = uiState.value.activeCampaign?.id ?: return
        viewModelScope.launch {
            try {
                val fileName = resolveFileName(context, uri) ?: "База_знаний.txt"
                val rawText = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
                if (rawText.isBlank()) {
                    _snackbarEvents.tryEmit("Выбранный файл пуст")
                    return@launch
                }
                val chunks = CampaignTransferManager.parseKnowledgeDocument(
                    fileName = fileName,
                    rawText = rawText,
                    campaignId = campaignId
                )
                if (chunks.isEmpty()) {
                    _snackbarEvents.tryEmit("Не удалось извлечь разделы из файла")
                } else {
                    repository.insertKnowledgeChunks(chunks)
                    _snackbarEvents.tryEmit("Импортировано разделов в текущую игру: ${chunks.size}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Knowledge import failed", e)
                _snackbarEvents.tryEmit("Ошибка чтения файла")
            }
        }
    }

    // --- 4. Out-of-Character Contextual Q&A («Чат с вопросами») & Hidden AI Plot («Закулисье») ---
    fun askAdvisorQuestion(question: String) {
        val state = uiState.value
        val campaign = state.activeCampaign ?: return
        val cleanQ = question.trim()
        if (cleanQ.isEmpty()) return
        if (state.qaTurnState is AiTurnState.Loading || state.qaTurnState is AiTurnState.Streaming) return

        viewModelScope.launch {
            _qaTurnState.value = AiTurnState.Loading
            try {
                val priorQa = state.qaMessages
                repository.addQaMessage(
                    campaignId = campaign.id,
                    senderRole = "USER",
                    content = cleanQ
                )

                val currentWorld = state.activeWorld
                val worldLorePool = if (currentWorld != null) {
                    state.allWorldLore.filter { it.worldId == currentWorld.id }
                } else emptyList()

                val matchedWorldLore = repository.selectRelevantWorldLore(
                    worldLoreList = worldLorePool,
                    queryText = cleanQ
                )
                val matchedSessionLore = repository.selectRelevantLore(
                    allChunks = state.knowledgeChunks,
                    queryText = cleanQ
                )

                val answer = geminiService.askContextualAdvisor(
                    settings = state.settings,
                    campaign = campaign,
                    activeWorld = currentWorld,
                    recentRpMessages = state.messages,
                    inventory = state.inventory,
                    journal = state.journal,
                    spells = state.spells,
                    relevantWorldLore = matchedWorldLore,
                    relevantLore = matchedSessionLore,
                    priorQaMessages = priorQa,
                    question = cleanQ,
                    useWebSearch = state.settings.webSearchEnabled,
                    onStreamingChunk = { partial ->
                        _qaTurnState.value = AiTurnState.Streaming(partial)
                    }
                )

                repository.addQaMessage(
                    campaignId = campaign.id,
                    senderRole = "ADVISOR",
                    content = answer.narrativeText,
                    groundingSourcesJson = serializeGroundingSources(answer.groundingSources)
                )
                _qaTurnState.value = AiTurnState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Contextual Q&A failed", e)
                _qaTurnState.value = AiTurnState.Error(geminiService.mapErrorToMessage(e))
            }
        }
    }

    fun dismissQaError() {
        if (_qaTurnState.value is AiTurnState.Error) {
            _qaTurnState.value = AiTurnState.Idle
        }
    }

    fun clearQaHistory() {
        val campaignId = uiState.value.activeCampaign?.id ?: return
        viewModelScope.launch {
            repository.clearQaHistory(campaignId)
            _snackbarEvents.tryEmit("История «Чата с вопросами» очищена")
        }
    }

    fun saveQaAnswerToJournal(qaMessage: QaMessageEntity) {
        val campaignId = uiState.value.activeCampaign?.id ?: return
        viewModelScope.launch {
            val shortHeadline = qaMessage.content
                .lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.replace(Regex("""[*#_`]+"""), "")
                ?.trim()
                ?.take(48)
                .orEmpty()
                .ifBlank { "Справка из вопросника" }
            repository.saveJournalEntry(
                JournalEntryEntity(
                    campaignId = campaignId,
                    title = shortHeadline,
                    entryType = "Заметка",
                    summary = qaMessage.content.take(260),
                    details = if (qaMessage.content.length > 260) qaMessage.content else "",
                    tagsCsv = "вопросник,справка",
                    statusOrDisposition = "Из Чата с вопросами"
                )
            )
            _snackbarEvents.tryEmit("Ответ Советника сохранён в Журнал приключений")
        }
    }

    fun generateHiddenPlotWithAi(authorDirectionHint: String = "") {
        val state = uiState.value
        val campaign = state.activeCampaign ?: return
        if (_isGeneratingHiddenPlot.value) return
        viewModelScope.launch {
            _isGeneratingHiddenPlot.value = true
            try {
                val plan = geminiService.generateOrRefreshHiddenPlotPlan(
                    settings = state.settings,
                    campaign = campaign,
                    activeWorld = state.activeWorld,
                    recentMessages = state.messages,
                    journal = state.journal,
                    authorDirectionHint = authorDirectionHint
                )
                repository.updateHiddenPlotPlan(campaign.id, plan)
                _snackbarEvents.tryEmit("Скрытый сюжетный план ИИ обновлён!")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Hidden plot generation failed", e)
                _snackbarEvents.tryEmit(geminiService.mapErrorToMessage(e))
            } finally {
                _isGeneratingHiddenPlot.value = false
            }
        }
    }

    fun saveHiddenPlotManual(plan: HiddenPlotPlan) {
        val campaignId = uiState.value.activeCampaign?.id ?: return
        viewModelScope.launch {
            repository.updateHiddenPlotPlan(campaignId, plan)
            _snackbarEvents.tryEmit("Закулисье ИИ-Мастера сохранено")
        }
    }

    // --- 5. Adventure Export & Standalone / Selective JSON Transfer ---
    private fun sanitizeExportFileName(raw: String, fallback: String): String =
        raw.replace(Regex("""[^\p{L}\p{N}_-]+"""), "_").trim('_').ifBlank { fallback }

    fun requestExport(format: String, shareDirectly: Boolean) {
        val campaignId = uiState.value.activeCampaign?.id ?: return
        viewModelScope.launch {
            val snapshot = repository.buildSnapshot(campaignId) ?: return@launch
            val safeTitle = sanitizeExportFileName(snapshot.campaign.title, "adventure")

            val payload = when (format) {
                "TXT" -> ExportPayload(
                    fileName = "${safeTitle}_история.txt",
                    mimeType = "text/plain",
                    content = CampaignTransferManager.exportToPlainText(snapshot),
                    shareDirectly = shareDirectly
                )
                "JSON" -> ExportPayload(
                    fileName = "${safeTitle}_бэкап.json",
                    mimeType = "application/json",
                    content = CampaignTransferManager.exportToJsonBackup(snapshot),
                    shareDirectly = shareDirectly
                )
                else -> ExportPayload(
                    fileName = "${safeTitle}_летопись.md",
                    mimeType = "text/markdown",
                    content = CampaignTransferManager.exportToMarkdown(snapshot),
                    shareDirectly = shareDirectly
                )
            }
            _exportEvents.tryEmit(payload)
        }
    }

    fun exportSelectiveBundleJson(selection: JsonTransferSelection, shareDirectly: Boolean) {
        val campaignId = uiState.value.activeCampaign?.id ?: return
        viewModelScope.launch {
            val snapshot = repository.buildSnapshot(campaignId) ?: return@launch
            val safeTitle = sanitizeExportFileName(snapshot.campaign.title, "пакет")
            _exportEvents.tryEmit(
                ExportPayload(
                    fileName = "${safeTitle}_пакет.json",
                    mimeType = "application/json",
                    content = CampaignTransferManager.exportSelectiveJson(snapshot, selection),
                    shareDirectly = shareDirectly
                )
            )
        }
    }

    fun exportStandaloneWorldJson(worldId: Long, shareDirectly: Boolean) {
        viewModelScope.launch {
            val world = repository.getWorldById(worldId) ?: return@launch
            val lore = repository.getWorldLoreOnce(worldId)
            val safeName = sanitizeExportFileName(world.name, "мир")
            _exportEvents.tryEmit(
                ExportPayload(
                    fileName = "Мир_${safeName}.json",
                    mimeType = "application/json",
                    content = CampaignTransferManager.exportWorldPackageJson(world, lore),
                    shareDirectly = shareDirectly
                )
            )
        }
    }

    fun exportStandaloneCharacterJson(
        includeInventory: Boolean,
        includeSpells: Boolean,
        shareDirectly: Boolean
    ) {
        val state = uiState.value
        val campaign = state.activeCampaign ?: return
        val safeHero = sanitizeExportFileName(campaign.heroName, "персонаж")
        val json = CampaignTransferManager.exportCharacterPackageJson(
            campaign = campaign,
            inventory = state.inventory,
            spells = state.spells,
            includeInventory = includeInventory,
            includeSpells = includeSpells
        )
        _exportEvents.tryEmit(
            ExportPayload(
                fileName = "Герой_${safeHero}.json",
                mimeType = "application/json",
                content = json,
                shareDirectly = shareDirectly
            )
        )
    }

    fun exportStandaloneChatJson(
        includeQaMessages: Boolean,
        includeHiddenPlot: Boolean,
        shareDirectly: Boolean
    ) {
        val state = uiState.value
        val campaign = state.activeCampaign ?: return
        val safeTitle = sanitizeExportFileName(campaign.title, "чат")
        val json = CampaignTransferManager.exportChatPackageJson(
            campaign = campaign,
            messages = state.messages,
            qaMessages = state.qaMessages,
            includeQaMessages = includeQaMessages,
            includeHiddenPlot = includeHiddenPlot
        )
        _exportEvents.tryEmit(
            ExportPayload(
                fileName = "Чат_${safeTitle}.json",
                mimeType = "application/json",
                content = json,
                shareDirectly = shareDirectly
            )
        )
    }

    fun inspectJsonFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val rawJson = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
                if (rawJson.isBlank()) {
                    _snackbarEvents.tryEmit("Выбранный JSON-файл пуст")
                    return@launch
                }
                val parsed = CampaignTransferManager.inspectJsonPackage(rawJson)
                _pendingJsonImportPreview.value = parsed
            } catch (e: Exception) {
                Log.e(TAG, "JSON package inspect failed", e)
                _snackbarEvents.tryEmit("Не удалось распознать JSON-пакет: проверьте формат файла")
            }
        }
    }

    fun dismissJsonImportPreview() {
        _pendingJsonImportPreview.value = null
    }

    fun confirmSelectiveJsonImport(
        selection: JsonTransferSelection,
        mergeIntoActiveCampaign: Boolean
    ) {
        val bundle = _pendingJsonImportPreview.value ?: return
        val targetMergeId = if (mergeIntoActiveCampaign) uiState.value.activeCampaign?.id else null
        viewModelScope.launch {
            try {
                val outcome = repository.applySelectiveJsonImport(
                    bundle = bundle,
                    selection = selection,
                    mergeIntoActiveCampaignId = targetMergeId
                )
                _pendingJsonImportPreview.value = null
                if (outcome.createdOrTargetCampaignId != null) {
                    settingsStore.setActiveCampaignId(outcome.createdOrTargetCampaignId)
                    _currentTab.value = MainTab.MASTER
                }
                _snackbarEvents.tryEmit(outcome.message)
            } catch (e: Exception) {
                Log.e(TAG, "Selective JSON import failed", e)
                _snackbarEvents.tryEmit("Ошибка импорта JSON: ${e.localizedMessage ?: "некорректные данные"}")
            }
        }
    }

    fun importBackupFromUri(context: Context, uri: Uri) {
        inspectJsonFromUri(context, uri)
    }

    private fun resolveFileName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull() ?: uri.lastPathSegment
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = RpgDatabase.getInstance(appContext)
                    val repo = RpgRepository(db.rpgDao())
                    val prefs = SettingsDataStore(appContext)
                    val vault = SecureCredentialVault(appContext)
                    val oauthHelper = GoogleOAuthHelper()
                    val gateway = AiProviderGateway(
                        appContext = appContext,
                        credentialVault = vault,
                        googleOAuthHelper = oauthHelper
                    )
                    return RpgMasterViewModel(
                        repository = repo,
                        settingsStore = prefs,
                        credentialVault = vault,
                        aiGateway = gateway,
                        googleOAuthHelper = oauthHelper
                    ) as T
                }
            }
        }
    }
}
