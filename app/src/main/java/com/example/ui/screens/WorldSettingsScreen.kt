package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.ConnectionProbeResult
import com.example.ai.GeneratedWorldDraft
import com.example.data.local.CampaignEntity
import com.example.data.local.WorldEntity
import com.example.data.local.WorldLoreEntity
import com.example.data.preferences.AiCredentialsSnapshot
import com.example.data.preferences.AiProviderType
import com.example.data.preferences.AppSettings
import com.example.data.preferences.AppThemeMode
import com.example.data.repository.MemoryUsageStats
import com.example.ui.WorldGenUiState
import kotlin.math.roundToInt

@Composable
fun WorldSettingsScreen(
    subTabIndex: Int,
    onSelectSubTab: (Int) -> Unit,
    settings: AppSettings,
    credentials: AiCredentialsSnapshot,
    worlds: List<WorldEntity>,
    allWorldLore: List<WorldLoreEntity>,
    campaigns: List<CampaignEntity>,
    activeCampaign: CampaignEntity?,
    memoryStats: MemoryUsageStats,
    isOptimizingMemory: Boolean,
    scratchWorldGenState: WorldGenUiState,
    universeSearchState: WorldGenUiState,
    isTestingAiConnection: Boolean,
    connectionProbeResult: ConnectionProbeResult?,
    isFetchingProviderModels: Boolean,
    discoveredProviderModels: List<String>,
    isGoogleOAuthLoading: Boolean,
    onSelectAiProvider: (AiProviderType) -> Unit,
    onSetAutoFallbackFirebase: (Boolean) -> Unit,
    onSaveAntigravity: (baseUrl: String, model: String, token: String) -> Unit,
    onSignInGoogleOAuthClick: () -> Unit,
    onSignOutGoogleOAuthClick: () -> Unit,
    onSaveGoogleOAuthManual: (model: String, projectId: String, manualToken: String) -> Unit,
    onSaveGeminiKeys: (keysRaw: String, model: String) -> Unit,
    onSaveOpenRouter: (keysRaw: String, model: String) -> Unit,
    onSaveOpenAiCompatible: (baseUrl: String, model: String, keysRaw: String) -> Unit,
    onSaveAnthropic: (keysRaw: String, model: String) -> Unit,
    onSaveLocalServer: (baseUrl: String, model: String, apiKey: String) -> Unit,
    onFetchProviderModelsClick: () -> Unit,
    onTestAiConnectionClick: () -> Unit,
    onGenerateScratchWorld: (description: String, rulesHint: String) -> Unit,
    onSearchExistingUniverse: (universeTitle: String, eraHint: String) -> Unit,
    onResetScratchState: () -> Unit,
    onResetUniverseState: () -> Unit,
    onSaveGeneratedWorld: (draft: GeneratedWorldDraft, originType: String, franchiseSource: String, onSaved: ((WorldEntity) -> Unit)?) -> Unit,
    onSaveManualWorld: (WorldEntity) -> Unit,
    onDeleteWorld: (WorldEntity) -> Unit,
    onSaveWorldLore: (WorldLoreEntity) -> Unit,
    onToggleWorldLoreActive: (WorldLoreEntity) -> Unit,
    onDeleteWorldLore: (WorldLoreEntity) -> Unit,
    onPickWorldLoreFile: (worldId: Long) -> Unit,
    onOpenNewGameDialog: (preselectedWorldId: Long?) -> Unit,
    onSwitchCampaign: (Long) -> Unit,
    onDeleteCampaign: (CampaignEntity) -> Unit,
    onUpdateCampaignRules: (CampaignEntity) -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onSetModelName: (String) -> Unit,
    onSetWebSearch: (Boolean) -> Unit,
    onSetAutoTrack: (Boolean) -> Unit,
    onSetAutoCompress: (Boolean) -> Unit,
    onSetWarningPercent: (Int) -> Unit,
    onSetMaxTokens: (Int) -> Unit,
    onOptimizeMemoryNow: () -> Unit,
    onRequestExport: (format: String, shareDirectly: Boolean) -> Unit,
    onOpenJsonExportHub: () -> Unit = {},
    onExportStandaloneWorld: (worldId: Long, shareDirectly: Boolean) -> Unit = { _, _ -> },
    onPickBackupJsonToImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateManualWorld by remember { mutableStateOf(false) }
    var editingWorld by remember { mutableStateOf<WorldEntity?>(null) }
    var managingLoreForWorld by remember { mutableStateOf<WorldEntity?>(null) }
    var showCampaignRulesDialog by remember { mutableStateOf(false) }

    var maxTokensSlider by remember(activeCampaign?.maxContextTokens, settings.defaultMaxTokens) {
        mutableFloatStateOf((activeCampaign?.maxContextTokens ?: settings.defaultMaxTokens).toFloat())
    }
    var warningPercentSlider by remember(settings.memoryWarningPercent) {
        mutableFloatStateOf(settings.memoryWarningPercent.toFloat())
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Dedicated 5-Tab Bar: Worlds Library, AI Scratch World, Existing Universe Search, AI & Keys Hub, Games & Settings
        ScrollableTabRow(
            selectedTabIndex = subTabIndex,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Tab(
                selected = subTabIndex == 0,
                onClick = { onSelectSubTab(0) },
                text = { Text("Мои миры (${worlds.size})") },
                modifier = Modifier.testTag("world_subtab_library")
            )
            Tab(
                selected = subTabIndex == 1,
                onClick = { onSelectSubTab(1) },
                text = { Text("С нуля с ИИ") },
                modifier = Modifier.testTag("world_subtab_ai_scratch")
            )
            Tab(
                selected = subTabIndex == 2,
                onClick = { onSelectSubTab(2) },
                text = { Text("По вселенной") },
                modifier = Modifier.testTag("world_subtab_existing_universe")
            )
            Tab(
                selected = subTabIndex == 3,
                onClick = { onSelectSubTab(3) },
                text = { Text("ИИ и Ключи") },
                modifier = Modifier.testTag("world_subtab_ai_connections")
            )
            Tab(
                selected = subTabIndex == 4,
                onClick = { onSelectSubTab(4) },
                text = { Text("Игры и Настройки") },
                modifier = Modifier.testTag("world_subtab_games_settings")
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("world_settings_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (subTabIndex) {
                // --- SUBTAB 0: WORLDS LIBRARY ---
                0 -> {
                    item(key = "worlds_hero_banner") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(152.dp)
                                .clip(RoundedCornerShape(20.dp))
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_hero_world_portal_1795485411446),
                                contentDescription = stringResource(R.string.hero_banner_desc),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Black.copy(alpha = 0.25f),
                                                Color.Black.copy(alpha = 0.85f)
                                            )
                                        )
                                    )
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "Кузница Миров",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color(0xFFF6EEDF)
                                )
                                Text(
                                    text = "Создавайте миры отдельно, наполняйте их лором и запускайте в них разные игры и героев",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFDFD5C5)
                                )
                            }
                        }
                    }

                    item(key = "worlds_quick_create_row") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onSelectSubTab(1) },
                                modifier = Modifier.testTag("shortcut_ai_scratch_button")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Мир с нуля (ИИ)")
                            }
                            OutlinedButton(
                                onClick = { onSelectSubTab(2) },
                                modifier = Modifier.testTag("shortcut_universe_search_button")
                            ) {
                                Icon(Icons.Default.TravelExplore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("По вселенной")
                            }
                            OutlinedButton(
                                onClick = { onSelectSubTab(3) },
                                modifier = Modifier.testTag("shortcut_ai_connections_button")
                            ) {
                                Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ИИ: ${settings.aiProvider.shortBadge}")
                            }
                            OutlinedButton(
                                onClick = { showCreateManualWorld = true },
                                modifier = Modifier.testTag("create_manual_world_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Вручную")
                            }
                        }
                    }

                    items(worlds, key = { it.id }) { world ->
                        val loreCount = allWorldLore.count { it.worldId == world.id }
                        val isActiveInCurrentGame = activeCampaign?.worldId == world.id
                        WorldLibraryCard(
                            world = world,
                            loreCount = loreCount,
                            isActiveInCurrentGame = isActiveInCurrentGame,
                            canDelete = worlds.size > 1,
                            onStartGameInWorld = { onOpenNewGameDialog(world.id) },
                            onManageLore = { managingLoreForWorld = world },
                            onExportWorldJson = { onExportStandaloneWorld(world.id, false) },
                            onEditWorld = { editingWorld = world },
                            onDeleteWorld = { onDeleteWorld(world) }
                        )
                    }
                }

                // --- SUBTAB 1: GENERATE CUSTOM WORLD FROM DESCRIPTION (AI) ---
                1 -> {
                    item(key = "ai_scratch_world_section") {
                        AiScratchWorldTab(
                            genState = scratchWorldGenState,
                            onGenerateClick = onGenerateScratchWorld,
                            onSaveWorldOnly = { draft ->
                                onSaveGeneratedWorld(draft, "AI_ORIGINAL", "") { _ ->
                                    onSelectSubTab(0)
                                }
                            },
                            onSaveAndCreateHero = { draft ->
                                onSaveGeneratedWorld(draft, "AI_ORIGINAL", "") { savedWorld ->
                                    onOpenNewGameDialog(savedWorld.id)
                                }
                            },
                            onResetState = onResetScratchState
                        )
                    }
                }

                // --- SUBTAB 2: SEARCH EXISTING UNIVERSE (GAME, SERIES, ANIME, BOOK) ---
                2 -> {
                    item(key = "existing_universe_search_section") {
                        ExistingUniverseSearchTab(
                            searchState = universeSearchState,
                            onSearchUniverseClick = onSearchExistingUniverse,
                            onSaveWorldOnly = { draft, franchise ->
                                onSaveGeneratedWorld(draft, "EXISTING_UNIVERSE", franchise) { _ ->
                                    onSelectSubTab(0)
                                }
                            },
                            onSaveAndCreateHero = { draft, franchise ->
                                onSaveGeneratedWorld(draft, "EXISTING_UNIVERSE", franchise) { savedWorld ->
                                    onOpenNewGameDialog(savedWorld.id)
                                }
                            },
                            onResetState = onResetUniverseState
                        )
                    }
                }

                // --- SUBTAB 3: AI PROVIDERS, ANTIGRAVITY, GOOGLE OAUTH, BYOK KEYS & LOCAL SERVERS ---
                3 -> {
                    item(key = "ai_connections_hub_section") {
                        AiConnectionsTab(
                            settings = settings,
                            credentials = credentials,
                            isTestingConnection = isTestingAiConnection,
                            probeResult = connectionProbeResult,
                            isFetchingModels = isFetchingProviderModels,
                            discoveredModels = discoveredProviderModels,
                            isGoogleAuthLoading = isGoogleOAuthLoading,
                            onSelectProvider = onSelectAiProvider,
                            onSetAutoFallback = onSetAutoFallbackFirebase,
                            onSetFirebaseModel = onSetModelName,
                            onSaveAntigravity = onSaveAntigravity,
                            onSignInGoogleOAuthClick = onSignInGoogleOAuthClick,
                            onSignOutGoogleOAuthClick = onSignOutGoogleOAuthClick,
                            onSaveGoogleOAuthManual = onSaveGoogleOAuthManual,
                            onSaveGeminiKeys = onSaveGeminiKeys,
                            onSaveOpenRouter = onSaveOpenRouter,
                            onSaveOpenAiCompatible = onSaveOpenAiCompatible,
                            onSaveAnthropic = onSaveAnthropic,
                            onSaveLocalServer = onSaveLocalServer,
                            onFetchModelsClick = onFetchProviderModelsClick,
                            onTestConnectionClick = onTestAiConnectionClick
                        )
                    }
                }

                // --- SUBTAB 4: GAMES / CHARACTERS, AI MEMORY, THEME & EXPORT ---
                4 -> {
                    item(key = "privacy_notice") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Локальное хранение данных: все миры, персонажи, журнал и заклинания сохраняются приватно в Room SQLite на вашем устройстве.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    // Games & Characters Section
                    item(key = "games_and_characters_card") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Игры и персонажи (${campaigns.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Button(
                                        onClick = { onOpenNewGameDialog(null) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("new_game_character_button")
                                    ) {
                                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Новая игра")
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Создайте нового персонажа, выбрав любой из ${worlds.size} созданных миров вашей библиотеки.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                campaigns.forEach { camp ->
                                    val isSelected = camp.id == activeCampaign?.id
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        onClick = { onSwitchCampaign(camp.id) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = camp.title,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                                Text(
                                                    text = "Герой: ${camp.heroName} (${camp.heroArchetype}) • Мир: ${camp.settingName}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isSelected) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                            }
                                            Row {
                                                if (isSelected) {
                                                    IconButton(onClick = { showCampaignRulesDialog = true }) {
                                                        Icon(
                                                            imageVector = Icons.Default.Tune,
                                                            contentDescription = "Настроить правила текущей игры"
                                                        )
                                                    }
                                                }
                                                if (campaigns.size > 1) {
                                                    IconButton(onClick = { onDeleteCampaign(camp) }) {
                                                        Icon(
                                                            imageVector = Icons.Default.DeleteOutline,
                                                            contentDescription = "Удалить сохранение",
                                                            tint = MaterialTheme.colorScheme.error
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

                    // Theme, Active AI Shortcut, Web Search & Memory Settings
                    item(key = "theme_and_ai_settings_card") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Оформление, ИИ и Память",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text("Тема оформления:", style = MaterialTheme.typography.labelLarge)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AppThemeMode.entries.forEach { mode ->
                                        FilterChip(
                                            selected = settings.themeMode == mode,
                                            onClick = { onSetThemeMode(mode) },
                                            label = { Text(mode.label) },
                                            modifier = Modifier.testTag("theme_mode_${mode.name}")
                                        )
                                    }
                                }

                                HorizontalDivider()

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Текущее подключение ИИ:", style = MaterialTheme.typography.labelLarge)
                                        Text(
                                            text = "${settings.aiProvider.title} • ${settings.currentEffectiveModel()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    OutlinedButton(onClick = { onSelectSubTab(3) }) {
                                        Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Сменить ИИ")
                                    }
                                }

                                SettingToggleRow(
                                    title = "Веб-поиск Google для Мастера в чате",
                                    subtitle = if (settings.aiProvider.supportsNativeGoogleSearch) {
                                        "Актуализирует канон миров, правила НРИ и факты во время игры"
                                    } else {
                                        "Доступен для Firebase Gemini, Google OAuth и ключей Google AI Studio"
                                    },
                                    checked = settings.webSearchEnabled,
                                    onCheckedChange = onSetWebSearch
                                )

                                SettingToggleRow(
                                    title = "Авто-синхронизация снаряжения и журнала",
                                    subtitle = "ИИ автоматически записывает найденные предметы, НПС и локации с тегами",
                                    checked = settings.autoTrackWorldState,
                                    onCheckedChange = onSetAutoTrack
                                )

                                SettingToggleRow(
                                    title = "Авто-оптимизация при заполнении памяти",
                                    subtitle = "Автоматически сжимает ранние сцены в хронику при 90% заполнения",
                                    checked = settings.autoCompressMemory,
                                    onCheckedChange = onSetAutoCompress
                                )

                                HorizontalDivider()

                                Text(
                                    text = "Лимит памяти сессии: ${maxTokensSlider.roundToInt()} токенов (занято: ${memoryStats.usedTokens}, ${memoryStats.usagePercent}%)",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Slider(
                                    value = maxTokensSlider,
                                    onValueChange = { maxTokensSlider = it },
                                    onValueChangeFinished = {
                                        val snapped = ((maxTokensSlider / 500f).roundToInt() * 500).coerceIn(2000, 24000)
                                        onSetMaxTokens(snapped)
                                    },
                                    valueRange = 2000f..24000f
                                )

                                Text(
                                    text = "Предупреждение о заполнении памяти: с ${warningPercentSlider.roundToInt()}%",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Slider(
                                    value = warningPercentSlider,
                                    onValueChange = { warningPercentSlider = it },
                                    onValueChangeFinished = {
                                        onSetWarningPercent(warningPercentSlider.roundToInt())
                                    },
                                    valueRange = 55f..90f
                                )

                                OutlinedButton(
                                    onClick = onOptimizeMemoryNow,
                                    enabled = !isOptimizingMemory,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isOptimizingMemory) "Оптимизация памяти..." else "Оптимизировать память сейчас")
                                }
                            }
                        }
                    }

                    // Export & Backup
                    item(key = "export_and_backup_card") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Импорт и экспорт (JSON, MD, TXT)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Экспортируйте и импортируйте миры, героев и чаты по отдельности или выбирайте нужные разделы галочками.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = onOpenJsonExportHub,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("open_json_export_hub_button")
                                    ) {
                                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Экспорт .JSON")
                                    }
                                    OutlinedButton(
                                        onClick = onPickBackupJsonToImport,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("import_selective_json_button")
                                    ) {
                                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Импорт .JSON")
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onRequestExport("MD", false) },
                                        modifier = Modifier.testTag("export_markdown_button")
                                    ) { Text("Сохранить .MD") }
                                    OutlinedButton(
                                        onClick = { onRequestExport("TXT", false) },
                                        modifier = Modifier.testTag("export_txt_button")
                                    ) { Text("Сохранить .TXT") }
                                    AssistChip(
                                        onClick = { onRequestExport("MD", true) },
                                        label = { Text("Поделиться летописью") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateManualWorld) {
        ManualWorldEditorDialog(
            existing = null,
            onDismiss = { showCreateManualWorld = false },
            onSave = {
                onSaveManualWorld(it)
                showCreateManualWorld = false
            }
        )
    }

    editingWorld?.let { target ->
        ManualWorldEditorDialog(
            existing = target,
            onDismiss = { editingWorld = null },
            onSave = {
                onSaveManualWorld(it)
                editingWorld = null
            }
        )
    }

    managingLoreForWorld?.let { targetWorld ->
        val itemsForWorld = allWorldLore.filter { it.worldId == targetWorld.id }
        WorldLoreManagerDialog(
            world = targetWorld,
            loreItems = itemsForWorld,
            onDismiss = { managingLoreForWorld = null },
            onImportFileClick = { onPickWorldLoreFile(targetWorld.id) },
            onSaveLoreItem = onSaveWorldLore,
            onToggleAlwaysActive = onToggleWorldLoreActive,
            onDeleteLoreItem = onDeleteWorldLore
        )
    }

    if (showCampaignRulesDialog && activeCampaign != null) {
        CampaignRulesQuickDialog(
            campaign = activeCampaign,
            onDismiss = { showCampaignRulesDialog = false },
            onSave = {
                onUpdateCampaignRules(it)
                showCampaignRulesDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorldLibraryCard(
    world: WorldEntity,
    loreCount: Int,
    isActiveInCurrentGame: Boolean,
    canDelete: Boolean,
    onStartGameInWorld: () -> Unit,
    onManageLore: () -> Unit,
    onExportWorldJson: () -> Unit,
    onEditWorld: () -> Unit,
    onDeleteWorld: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val webSources = remember(world.groundingSourcesJson) { world.groundingSources() }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActiveInCurrentGame) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = world.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = worldOriginLabel(world.originType),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = world.genre,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Row {
                    IconButton(
                        onClick = onExportWorldJson,
                        modifier = Modifier.testTag("export_world_json_${world.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Экспортировать мир в JSON"
                        )
                    }
                    IconButton(onClick = onEditWorld) {
                        Icon(Icons.Default.Edit, contentDescription = "Редактировать мир")
                    }
                    if (canDelete) {
                        IconButton(onClick = onDeleteWorld) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Удалить мир",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            if (world.worldDescription.isNotBlank()) {
                Text(
                    text = world.worldDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("🎲 ${world.diceSystem}", style = MaterialTheme.typography.labelSmall)
                    Text("✨ ${world.magicOrTechSystem}", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (webSources.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    webSources.take(4).forEach { src ->
                        AssistChip(
                            onClick = { runCatching { uriHandler.openUri(src.uri) } },
                            label = {
                                Text(
                                    text = src.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            trailingIcon = {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(12.dp))
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartGameInWorld,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("start_game_in_world_${world.id}")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Создать героя")
                }
                OutlinedButton(
                    onClick = onManageLore,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("База ($loreCount)")
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
