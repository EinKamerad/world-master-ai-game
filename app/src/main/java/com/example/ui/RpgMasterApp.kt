package com.example.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.AppThemeMode
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.JournalScreen
import com.example.ui.screens.JsonExportHubDialog
import com.example.ui.screens.JsonImportPreviewDialog
import com.example.ui.screens.MasterChatScreen
import com.example.ui.screens.NewGameCharacterDialog
import com.example.ui.screens.OnlineMultiplayerScreen
import com.example.ui.screens.SpellsScreen
import com.example.ui.screens.WorldSettingsScreen
import com.example.ui.theme.RpgMasterTheme
import kotlinx.coroutines.launch

private data class NavTabSpec(
    val tab: MainTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val NavigationTabs = listOf(
    NavTabSpec(MainTab.MASTER, "Мастер", Icons.Filled.Forum, Icons.Outlined.Forum, "nav_tab_master"),
    NavTabSpec(MainTab.INVENTORY, "Снаряжение", Icons.Filled.Backpack, Icons.Outlined.Backpack, "nav_tab_inventory"),
    NavTabSpec(MainTab.JOURNAL, "Журнал", Icons.Filled.MenuBook, Icons.Outlined.MenuBook, "nav_tab_journal"),
    NavTabSpec(MainTab.SPELLS, "Заклинания", Icons.Filled.AutoFixHigh, Icons.Outlined.AutoFixHigh, "nav_tab_spells"),
    NavTabSpec(MainTab.ONLINE, "Онлайн", Icons.Filled.Groups, Icons.Outlined.Groups, "nav_tab_online"),
    NavTabSpec(MainTab.WORLD_SETTINGS, "Миры", Icons.Filled.Public, Icons.Outlined.Public, "nav_tab_world")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RpgMasterApp(viewModel: RpgMasterViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var pendingExportPayload by remember { mutableStateOf<ExportPayload?>(null) }
    var pendingWorldIdForLoreImport by remember { mutableLongStateOf(0L) }
    var showCampaignDropdown by remember { mutableStateOf(false) }

    var showNewGameDialog by remember { mutableStateOf(false) }
    var preselectedWorldForNewGame by remember { mutableStateOf<Long?>(null) }
    var showJsonExportHub by remember { mutableStateOf(false) }

    val visibleTabs = remember(state.activeCampaign?.useInventory, state.activeCampaign?.useSpells) {
        NavigationTabs.filter { spec ->
            when (spec.tab) {
                MainTab.INVENTORY -> state.activeCampaign?.useInventory != false
                MainTab.SPELLS -> state.activeCampaign?.useSpells != false
                else -> true
            }
        }
    }

    if (state.currentTab != MainTab.MASTER) {
        BackHandler {
            viewModel.selectTab(MainTab.MASTER)
        }
    }

    // Google OAuth 2.0 Consent IntentSender Launcher
    val googleOAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        viewModel.finishGoogleOAuthFromIntent(context, activityResult.data)
    }

    // SAF Launcher: Import lore document (.txt, .md, .json) directly into a reusable WorldEntity
    val worldLorePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val targetWorldId = pendingWorldIdForLoreImport
        if (uri != null && targetWorldId > 0L) {
            viewModel.importWorldLoreFromUri(context, uri, targetWorldId)
        }
        pendingWorldIdForLoreImport = 0L
    }

    // SAF Launcher: Import full JSON campaign backup
    val backupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackupFromUri(context, uri)
        }
    }

    // SAF Launcher: Save exported adventure (.md / .txt / .json)
    val saveExportDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val payload = pendingExportPayload
        if (uri != null && payload != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                    it.write(payload.content)
                }
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Файл «${payload.fileName}» сохранён")
                }
            }.onFailure {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Не удалось сохранить файл")
                }
            }
        }
        pendingExportPayload = null
    }

    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.googleOAuthConsentEvents.collect { intentSender ->
            googleOAuthLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.exportEvents.collect { payload ->
            if (payload.shareDirectly) {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, payload.fileName)
                    putExtra(Intent.EXTRA_TEXT, payload.content)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Поделиться историей приключения"))
            } else {
                pendingExportPayload = payload
                saveExportDocumentLauncher.launch(payload.fileName)
            }
        }
    }

    RpgMasterTheme(themeMode = state.settings.themeMode) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 680.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Box {
                                    TextButton(
                                        onClick = { showCampaignDropdown = true },
                                        modifier = Modifier.testTag("campaign_switcher_button")
                                    ) {
                                        Text(
                                            text = state.activeCampaign?.title ?: "Мастер Миров",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showCampaignDropdown,
                                        onDismissRequest = { showCampaignDropdown = false }
                                    ) {
                                        state.campaigns.forEach { camp ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = "${camp.title} (${camp.heroName})",
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.switchCampaign(camp.id)
                                                    showCampaignDropdown = false
                                                }
                                            )
                                        }
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text("+ Новая игра / Персонаж (выбрать мир)") },
                                            leadingIcon = {
                                                Icon(Icons.Default.PersonAdd, contentDescription = null)
                                            },
                                            onClick = {
                                                showCampaignDropdown = false
                                                preselectedWorldForNewGame = null
                                                showNewGameDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Подключения ИИ и Ключи (${state.settings.aiProvider.shortBadge})") },
                                            leadingIcon = {
                                                Icon(Icons.Default.Hub, contentDescription = null)
                                            },
                                            onClick = {
                                                showCampaignDropdown = false
                                                viewModel.openAiConnectionsHub()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Кузница Миров (ИИ / По вселенной)") },
                                            leadingIcon = {
                                                Icon(Icons.Default.Public, contentDescription = null)
                                            },
                                            onClick = {
                                                showCampaignDropdown = false
                                                viewModel.selectWorldHubSubTab(0)
                                                viewModel.selectTab(MainTab.WORLD_SETTINGS)
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        actions = {
                            AssistChip(
                                onClick = { viewModel.openAiConnectionsHub() },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Hub,
                                        contentDescription = "Выбрать провайдера ИИ",
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = state.settings.aiProvider.shortBadge,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                },
                                modifier = Modifier.testTag("top_ai_provider_chip")
                            )
                            IconButton(
                                onClick = {
                                    preselectedWorldForNewGame = null
                                    showNewGameDialog = true
                                },
                                modifier = Modifier.testTag("top_new_game_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = "Новая игра и создание персонажа"
                                )
                            }
                            IconButton(
                                onClick = { viewModel.toggleDarkLightQuick() },
                                modifier = Modifier.testTag("quick_theme_toggle_button")
                            ) {
                                val isDark = state.settings.themeMode == AppThemeMode.DARK
                                Icon(
                                    imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = if (isDark) "Переключить на светлую тему" else "Переключить на тёмную тему"
                                )
                            }
                            IconButton(
                                onClick = { showJsonExportHub = true },
                                modifier = Modifier.testTag("quick_share_export_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.IosShare,
                                    contentDescription = "Экспорт и импорт JSON / истории"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar {
                            visibleTabs.forEach { item ->
                                val selected = state.currentTab == item.tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(item.tab) },
                                    icon = {
                                        TabIconWithCount(
                                            spec = item,
                                            selected = selected,
                                            badgeCount = when (item.tab) {
                                                MainTab.INVENTORY -> state.inventory.size
                                                MainTab.JOURNAL -> state.journal.size
                                                MainTab.SPELLS -> state.spells.size
                                                MainTab.WORLD_SETTINGS -> state.worlds.size
                                                else -> 0
                                            }
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = item.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        NavigationRail(modifier = Modifier.fillMaxHeight()) {
                            visibleTabs.forEach { item ->
                                val selected = state.currentTab == item.tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { viewModel.selectTab(item.tab) },
                                    icon = {
                                        TabIconWithCount(
                                            spec = item,
                                            selected = selected,
                                            badgeCount = when (item.tab) {
                                                MainTab.INVENTORY -> state.inventory.size
                                                MainTab.JOURNAL -> state.journal.size
                                                MainTab.SPELLS -> state.spells.size
                                                MainTab.WORLD_SETTINGS -> state.worlds.size
                                                else -> 0
                                            }
                                        )
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (state.currentTab) {
                            MainTab.MASTER -> MasterChatScreen(
                                campaign = state.activeCampaign,
                                messages = state.messages,
                                qaMessages = state.qaMessages,
                                memoryStats = state.memoryStats,
                                aiTurnState = state.aiTurnState,
                                qaTurnState = state.qaTurnState,
                                isGeneratingHiddenPlot = state.isGeneratingHiddenPlot,
                                isOptimizingMemory = state.isOptimizingMemory,
                                webSearchEnabled = state.settings.webSearchEnabled,
                                onToggleWebSearch = viewModel::setWebSearchEnabled,
                                onSendAction = { prompt, dice -> viewModel.sendPlayerAction(prompt, dice) },
                                onRetryTurn = viewModel::retryFailedTurn,
                                onDismissError = viewModel::dismissAiError,
                                onOptimizeMemory = viewModel::optimizeMemoryNow,
                                onSaveMemorySummaryManual = { edited ->
                                    state.activeCampaign?.let { camp ->
                                        viewModel.updateActiveCampaignDetails(camp.copy(memorySummary = edited))
                                    }
                                },
                                onAskAdvisorQuestion = viewModel::askAdvisorQuestion,
                                onClearQaHistory = viewModel::clearQaHistory,
                                onSaveQaAnswerToJournal = viewModel::saveQaAnswerToJournal,
                                onDismissQaError = viewModel::dismissQaError,
                                onGenerateHiddenPlotAi = viewModel::generateHiddenPlotWithAi,
                                onSaveHiddenPlotManual = viewModel::saveHiddenPlotManual
                            )

                            MainTab.INVENTORY -> InventoryScreen(
                                campaign = state.activeCampaign,
                                items = state.inventory,
                                onUpdateHeroSheet = viewModel::updateActiveCampaignDetails,
                                onSaveItem = viewModel::saveInventoryItem,
                                onToggleEquip = viewModel::toggleEquipItem,
                                onAdjustQty = viewModel::adjustItemQuantity,
                                onUseInGame = viewModel::useItemInGame,
                                onInspectWithMaster = viewModel::inspectItemWithMaster,
                                onDeleteItem = viewModel::deleteInventoryItem
                            )

                            MainTab.JOURNAL -> JournalScreen(
                                campaignId = state.activeCampaign?.id ?: 0L,
                                entries = state.journal,
                                onSaveEntry = viewModel::saveJournalEntry,
                                onTogglePin = viewModel::togglePinJournalEntry,
                                onDeleteEntry = viewModel::deleteJournalEntry,
                                onInteractInChat = viewModel::interactWithJournalInChat
                            )

                            MainTab.SPELLS -> SpellsScreen(
                                campaign = state.activeCampaign,
                                spells = state.spells,
                                spellAiState = state.spellAiState,
                                onSaveSpell = viewModel::saveSpell,
                                onTogglePrepared = viewModel::toggleSpellPrepared,
                                onDeleteSpell = viewModel::deleteSpell,
                                onCastSpell = { viewModel.castSpellInGame(it) },
                                onGenerateSpellWithAi = viewModel::generateSpellWithAi,
                                onResetSpellAiState = viewModel::resetSpellAiState
                            )

                            MainTab.ONLINE -> OnlineMultiplayerScreen(
                                worlds = state.worlds,
                                activeSoloCampaign = state.activeCampaign,
                                onShowSnackbar = { msg ->
                                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )

                            MainTab.WORLD_SETTINGS -> WorldSettingsScreen(
                                subTabIndex = state.worldHubSubTab,
                                onSelectSubTab = viewModel::selectWorldHubSubTab,
                                settings = state.settings,
                                credentials = state.credentials,
                                worlds = state.worlds,
                                allWorldLore = state.allWorldLore,
                                campaigns = state.campaigns,
                                activeCampaign = state.activeCampaign,
                                memoryStats = state.memoryStats,
                                isOptimizingMemory = state.isOptimizingMemory,
                                scratchWorldGenState = state.scratchWorldGenState,
                                universeSearchState = state.universeSearchState,
                                isTestingAiConnection = state.isTestingAiConnection,
                                connectionProbeResult = state.connectionProbeResult,
                                isFetchingProviderModels = state.isFetchingProviderModels,
                                discoveredProviderModels = state.discoveredProviderModels,
                                isGoogleOAuthLoading = state.isGoogleOAuthLoading,
                                onSelectAiProvider = viewModel::setAiProvider,
                                onSetAutoFallbackFirebase = viewModel::setAutoFallbackToFirebase,
                                onSaveAntigravity = viewModel::saveAntigravitySettings,
                                onSignInGoogleOAuthClick = { viewModel.startGoogleOAuthSignIn(context) },
                                onSignOutGoogleOAuthClick = viewModel::signOutGoogleOAuth,
                                onSaveGoogleOAuthManual = viewModel::saveGoogleOAuthManualSettings,
                                onSaveGeminiKeys = viewModel::saveGeminiApiKeysSettings,
                                onSaveOpenRouter = viewModel::saveOpenRouterSettings,
                                onSaveOpenAiCompatible = viewModel::saveOpenAiCompatibleSettings,
                                onSaveAnthropic = viewModel::saveAnthropicSettings,
                                onSaveLocalServer = viewModel::saveLocalServerSettings,
                                onFetchProviderModelsClick = viewModel::fetchModelsForCurrentProvider,
                                onTestAiConnectionClick = viewModel::testCurrentAiConnection,
                                onGenerateScratchWorld = viewModel::generateWorldFromScratchAi,
                                onSearchExistingUniverse = viewModel::searchAndBuildExistingUniverseAi,
                                onResetScratchState = viewModel::resetScratchWorldGenState,
                                onResetUniverseState = viewModel::resetUniverseSearchState,
                                onSaveGeneratedWorld = viewModel::saveWorldDraftToLibrary,
                                onSaveManualWorld = viewModel::saveManualWorld,
                                onDeleteWorld = viewModel::deleteWorld,
                                onSaveWorldLore = viewModel::saveWorldLore,
                                onToggleWorldLoreActive = viewModel::toggleWorldLoreAlwaysActive,
                                onDeleteWorldLore = viewModel::deleteWorldLore,
                                onPickWorldLoreFile = { targetWorldId ->
                                    pendingWorldIdForLoreImport = targetWorldId
                                    worldLorePickerLauncher.launch(
                                        arrayOf("text/*", "application/json", "application/octet-stream")
                                    )
                                },
                                onOpenNewGameDialog = { preselectedWorldId ->
                                    preselectedWorldForNewGame = preselectedWorldId
                                    showNewGameDialog = true
                                },
                                onSwitchCampaign = viewModel::switchCampaign,
                                onDeleteCampaign = viewModel::deleteCampaign,
                                onUpdateCampaignRules = viewModel::updateActiveCampaignDetails,
                                onSetThemeMode = viewModel::setThemeMode,
                                onSetModelName = viewModel::setModelName,
                                onSetWebSearch = viewModel::setWebSearchEnabled,
                                onSetAutoTrack = viewModel::setAutoTrackWorldState,
                                onSetAutoCompress = viewModel::setAutoCompressMemory,
                                onSetWarningPercent = viewModel::setMemoryWarningPercent,
                                onSetMaxTokens = viewModel::setCampaignMaxTokens,
                                onOptimizeMemoryNow = viewModel::optimizeMemoryNow,
                                onRequestExport = viewModel::requestExport,
                                onOpenJsonExportHub = { showJsonExportHub = true },
                                onExportStandaloneWorld = viewModel::exportStandaloneWorldJson,
                                onPickBackupJsonToImport = {
                                    backupImportLauncher.launch(
                                        arrayOf("application/json", "text/*")
                                    )
                                }
                            )
                        }
                    }
                }
            }

            if (showNewGameDialog && state.worlds.isNotEmpty()) {
                NewGameCharacterDialog(
                    worlds = state.worlds,
                    preselectedWorldId = preselectedWorldForNewGame,
                    onDismiss = { showNewGameDialog = false },
                    onGoToCreateWorld = {
                        showNewGameDialog = false
                        viewModel.selectWorldHubSubTab(0)
                        viewModel.selectTab(MainTab.WORLD_SETTINGS)
                    },
                    onStartGame = { world, title, heroName, heroArch, heroStats, backstory, opening, useDice, useInv, useSpells ->
                        viewModel.createNewGameWithCharacter(
                            selectedWorld = world,
                            campaignTitle = title,
                            heroName = heroName,
                            heroArchetype = heroArch,
                            heroStatsSummary = heroStats,
                            heroBackstory = backstory,
                            customOpeningScene = opening,
                            useDiceAndStats = useDice,
                            useInventory = useInv,
                            useSpells = useSpells
                        )
                        showNewGameDialog = false
                    }
                )
            }

            if (showJsonExportHub) {
                JsonExportHubDialog(
                    worlds = state.worlds,
                    activeCampaign = state.activeCampaign,
                    messagesCount = state.messages.size,
                    qaMessagesCount = state.qaMessages.size,
                    inventoryCount = state.inventory.size,
                    journalCount = state.journal.size,
                    spellsCount = state.spells.size,
                    knowledgeCount = state.knowledgeChunks.size,
                    onExportSelectiveBundle = viewModel::exportSelectiveBundleJson,
                    onExportStandaloneWorld = viewModel::exportStandaloneWorldJson,
                    onExportStandaloneCharacter = viewModel::exportStandaloneCharacterJson,
                    onExportStandaloneChat = viewModel::exportStandaloneChatJson,
                    onDismiss = { showJsonExportHub = false }
                )
            }

            state.pendingJsonImportPreview?.let { importBundle ->
                JsonImportPreviewDialog(
                    bundle = importBundle,
                    hasActiveCampaign = state.activeCampaign != null,
                    onConfirmImport = viewModel::confirmSelectiveJsonImport,
                    onDismiss = viewModel::dismissJsonImportPreview
                )
            }
        }
    }
}

@Composable
private fun TabIconWithCount(
    spec: NavTabSpec,
    selected: Boolean,
    badgeCount: Int
) {
    if (badgeCount > 0) {
        BadgedBox(
            badge = {
                Badge {
                    Text(if (badgeCount > 99) "99+" else badgeCount.toString())
                }
            }
        ) {
            Icon(
                imageVector = if (selected) spec.selectedIcon else spec.unselectedIcon,
                contentDescription = spec.label
            )
        }
    } else {
        Icon(
            imageVector = if (selected) spec.selectedIcon else spec.unselectedIcon,
            contentDescription = spec.label
        )
    }
}
