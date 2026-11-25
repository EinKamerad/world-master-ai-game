package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CampaignEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.repository.MemoryHealthLevel
import com.example.data.repository.MemoryUsageStats
import com.example.data.transfer.DiceRoller
import com.example.ui.AiTurnState

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.VerticalDivider
import com.example.ai.HiddenPlotPlan
import com.example.data.local.QaMessageEntity

@Composable
fun MasterChatScreen(
    campaign: CampaignEntity?,
    messages: List<ChatMessageEntity>,
    qaMessages: List<QaMessageEntity> = emptyList(),
    memoryStats: MemoryUsageStats,
    aiTurnState: AiTurnState,
    qaTurnState: AiTurnState = AiTurnState.Idle,
    isGeneratingHiddenPlot: Boolean = false,
    isOptimizingMemory: Boolean,
    webSearchEnabled: Boolean,
    onToggleWebSearch: (Boolean) -> Unit,
    onSendAction: (String, String?) -> Unit,
    onRetryTurn: () -> Unit,
    onDismissError: () -> Unit,
    onOptimizeMemory: () -> Unit,
    onSaveMemorySummaryManual: (String) -> Unit,
    onAskAdvisorQuestion: (String) -> Unit = {},
    onClearQaHistory: () -> Unit = {},
    onSaveQaAnswerToJournal: (QaMessageEntity) -> Unit = {},
    onDismissQaError: () -> Unit = {},
    onGenerateHiddenPlotAi: (String) -> Unit = {},
    onSaveHiddenPlotManual: (HiddenPlotPlan) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val diceEnabled = campaign?.useDiceAndStats != false
    var inputText by rememberSaveable { mutableStateOf("") }
    var attachedDiceRoll by rememberSaveable { mutableStateOf<String?>(null) }
    var showDiceDrawer by rememberSaveable { mutableStateOf(false) }
    var diceModifier by rememberSaveable { mutableIntStateOf(0) }
    var showChronicleDialog by remember { mutableStateOf(false) }
    var showQaAdvisor by rememberSaveable { mutableStateOf(false) }
    var showHiddenPlotDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, aiTurnState) {
        val totalItems = messages.size + (if (aiTurnState is AiTurnState.Loading || aiTurnState is AiTurnState.Streaming) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    LaunchedEffect(diceEnabled) {
        if (!diceEnabled) {
            showDiceDrawer = false
            attachedDiceRoll = null
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useSplitQaPane = maxWidth >= 900.dp

        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .imePadding()
            ) {
        // 1. Memory Status & Full-Memory Warning Header
        MemoryStatusHeader(
            memoryStats = memoryStats,
            isOptimizing = isOptimizingMemory,
            hasChronicle = !campaign?.memorySummary.isNullOrBlank(),
            onOptimizeClick = onOptimizeMemory,
            onViewChronicleClick = { showChronicleDialog = true }
        )

        // 2. Chat History List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("master_chat_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                ChatMessageBubble(
                    message = message,
                    heroName = campaign?.heroName ?: "Герой"
                )
            }

            when (aiTurnState) {
                is AiTurnState.Loading -> {
                    item(key = "streaming_loading") {
                        StreamingGmCard(
                            text = if (webSearchEnabled) {
                                "Мастер сверяется с источниками в сети и плетёт повествование..."
                            } else {
                                "Мастер обдумывает последствия вашего хода..."
                            },
                            isPlaceholder = true
                        )
                    }
                }
                is AiTurnState.Streaming -> {
                    item(key = "streaming_active") {
                        StreamingGmCard(
                            text = aiTurnState.partialText,
                            isPlaceholder = false
                        )
                    }
                }
                is AiTurnState.Error -> {
                    item(key = "turn_error") {
                        AiErrorBanner(
                            message = aiTurnState.message,
                            canRetry = aiTurnState.lastPrompt != null,
                            onRetry = onRetryTurn,
                            onDismiss = onDismissError
                        )
                    }
                }
                AiTurnState.Idle -> Unit
            }
        }

        // 3. Expandable Polyhedral Dice Tray (shown only when Dice & Stats module is enabled)
        AnimatedVisibility(visible = diceEnabled && showDiceDrawer) {
            DiceRollerTray(
                modifierValue = diceModifier,
                onModifierChange = { diceModifier = it.coerceIn(-20, 30) },
                onRollDie = { sides ->
                    val result = DiceRoller.rollStandard(sides = sides, count = 1, modifier = diceModifier)
                    attachedDiceRoll = result.formattedSummary
                }
            )
        }

        // 4. Input Composer & Quick Action Chips
        Surface(
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Quick Controls Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (diceEnabled) {
                        FilterChip(
                            selected = showDiceDrawer,
                            onClick = { showDiceDrawer = !showDiceDrawer },
                            label = { Text("Кубики") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Casino,
                                    contentDescription = "Открыть панель кубиков",
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.testTag("toggle_dice_tray_chip")
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "Словеска без ДнД",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    FilterChip(
                        selected = showQaAdvisor,
                        onClick = { showQaAdvisor = !showQaAdvisor },
                        label = {
                            Text(
                                if (qaMessages.isNotEmpty()) "Вопросник (${qaMessages.size})"
                                else "Вопросник"
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = "Открыть чат с вопросами по сюжету",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("open_qa_advisor_chip")
                    )

                    AssistChip(
                        onClick = { showHiddenPlotDialog = true },
                        label = {
                            Text(
                                if (campaign?.hasHiddenPlot() == true) "Закулисье ИИ •"
                                else "Закулисье ИИ"
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = "Открыть скрытый план ИИ-Мастера",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("open_hidden_plot_chip")
                    )

                    FilterChip(
                        selected = webSearchEnabled,
                        onClick = { onToggleWebSearch(!webSearchEnabled) },
                        label = { Text(if (webSearchEnabled) "Веб-поиск: ВКЛ" else "Веб-поиск") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Переключить веб-поиск для нейросети",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("toggle_web_search_chip")
                    )

                    AssistChip(
                        onClick = { showChronicleDialog = true },
                        label = { Text("Память (${memoryStats.compressedChaptersCount})") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.HistoryEdu,
                                contentDescription = "Открыть хронику памяти",
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("open_chronicle_chip")
                    )
                }

                // Attached Dice Roll Preview Pill
                if (diceEnabled && !attachedDiceRoll.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = attachedDiceRoll.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { attachedDiceRoll = null },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Убрать результат броска",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                val isBusy = aiTurnState is AiTurnState.Loading || aiTurnState is AiTurnState.Streaming
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = "Ваше действие, реплика или заявка...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        maxLines = 4,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_action_input")
                    )

                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank() || !attachedDiceRoll.isNullOrBlank()) {
                                onSendAction(inputText, attachedDiceRoll)
                                inputText = ""
                                attachedDiceRoll = null
                            }
                        },
                        enabled = !isBusy && (inputText.isNotBlank() || !attachedDiceRoll.isNullOrBlank()),
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("send_chat_action_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Отправить действие Мастеру"
                            )
                        }
                    }
                }
            }
        }
            }

            // Supporting Side Pane for Contextual Q&A on Wide Tablets / Desktop
            if (useSplitQaPane && showQaAdvisor) {
                VerticalDivider()
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .width(380.dp)
                        .fillMaxHeight()
                ) {
                    QaAdvisorPanelContent(
                        campaign = campaign,
                        qaMessages = qaMessages,
                        qaTurnState = qaTurnState,
                        webSearchEnabled = webSearchEnabled,
                        onToggleWebSearch = onToggleWebSearch,
                        onAskQuestion = onAskAdvisorQuestion,
                        onClearHistory = onClearQaHistory,
                        onSaveAnswerToJournal = onSaveQaAnswerToJournal,
                        onDismissError = onDismissQaError,
                        onClosePanel = { showQaAdvisor = false },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (!useSplitQaPane && showQaAdvisor) {
            QaAdvisorBottomSheet(
                campaign = campaign,
                qaMessages = qaMessages,
                qaTurnState = qaTurnState,
                webSearchEnabled = webSearchEnabled,
                onToggleWebSearch = onToggleWebSearch,
                onAskQuestion = onAskAdvisorQuestion,
                onClearHistory = onClearQaHistory,
                onSaveAnswerToJournal = onSaveQaAnswerToJournal,
                onDismissError = onDismissQaError,
                onDismissSheet = { showQaAdvisor = false }
            )
        }
    }

    if (showHiddenPlotDialog && campaign != null) {
        HiddenPlotSpoilerDialog(
            initialPlan = HiddenPlotPlan(
                plotArc = campaign.hiddenPlotArc,
                npcKnowledgeBoundaries = campaign.hiddenNpcKnowledge,
                triggersAndConsequences = campaign.hiddenPlotTriggers
            ),
            isGeneratingAi = isGeneratingHiddenPlot,
            onGenerateWithAi = onGenerateHiddenPlotAi,
            onSaveManual = onSaveHiddenPlotManual,
            onDismiss = { showHiddenPlotDialog = false }
        )
    }

    if (showChronicleDialog && campaign != null) {
        ChronicleMemoryDialog(
            campaign = campaign,
            memoryStats = memoryStats,
            isOptimizing = isOptimizingMemory,
            onDismiss = { showChronicleDialog = false },
            onOptimizeNow = onOptimizeMemory,
            onSaveManual = { edited ->
                onSaveMemorySummaryManual(edited)
                showChronicleDialog = false
            }
        )
    }
}

@Composable
private fun MemoryStatusHeader(
    memoryStats: MemoryUsageStats,
    isOptimizing: Boolean,
    hasChronicle: Boolean,
    onOptimizeClick: () -> Unit,
    onViewChronicleClick: () -> Unit
) {
    val barColor = when (memoryStats.healthLevel) {
        MemoryHealthLevel.CRITICAL_FULL -> MaterialTheme.colorScheme.error
        MemoryHealthLevel.WARNING -> MaterialTheme.colorScheme.primary
        MemoryHealthLevel.NORMAL -> MaterialTheme.colorScheme.secondary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = "Индикатор памяти ИИ",
                    tint = barColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Память ИИ: ${memoryStats.usagePercent}% (${memoryStats.usedTokens}/${memoryStats.maxTokens} ток.)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isOptimizing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Сжатие...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (memoryStats.activeMessagesCount >= 4 || hasChronicle) {
                Text(
                    text = "Оптимизировать",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onOptimizeClick() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("quick_optimize_memory_link")
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (memoryStats.usagePercent / 100f).coerceIn(0.02f, 1f) },
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape)
        )

        // Prominent Warning / Full Memory Banner
        if (memoryStats.healthLevel != MemoryHealthLevel.NORMAL) {
            Spacer(modifier = Modifier.height(8.dp))
            val isFull = memoryStats.healthLevel == MemoryHealthLevel.CRITICAL_FULL
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isFull) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("memory_warning_banner")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Предупреждение о заполнении памяти",
                            tint = if (isFull) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isFull) {
                                "Память ИИ заполнена полностью (${memoryStats.usagePercent}%)!"
                            } else {
                                "Память сессии почти заполнена (${memoryStats.usagePercent}%)"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isFull) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isFull) {
                            "Контекст достиг предела: чат-бот начнёт забывать ранние события. Сожмите старые сцены в хронику — переписка в чате полностью сохранится!"
                        } else {
                            "Рекомендуем выполнить оптимизацию памяти, чтобы Мастер помнил все сюжетные решения и НПС без перегрузки контекста."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isFull) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onOptimizeClick,
                            enabled = !isOptimizing,
                            colors = if (isFull) {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                            modifier = Modifier.testTag("banner_optimize_memory_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Compress,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Сжать память")
                        }
                        TextButton(onClick = onViewChronicleClick) {
                            Text("Хроника")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatMessageBubble(
    message: ChatMessageEntity,
    heroName: String
) {
    val isUser = message.senderRole == "USER"
    val uriHandler = LocalUriHandler.current
    val sources = remember(message.groundingSourcesJson) { message.groundingSources() }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        val bubbleShape = if (isUser) {
            RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
        } else {
            RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
        }

        Card(
            shape = bubbleShape,
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ),
            modifier = Modifier
                .widthIn(max = 600.dp)
                .then(
                    if (!isUser) {
                        Modifier.border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )
                            ),
                            shape = bubbleShape
                        )
                    } else Modifier
                )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isUser) heroName else "Мастер Миров",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    if (message.isArchivedInSummary) {
                        Text(
                            text = "в хронике",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                if (!message.diceRollInfo.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = message.diceRollInfo,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                if (sources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Источники из веб-поиска Google:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        sources.forEach { src ->
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
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = "Открыть веб-источник",
                                        modifier = Modifier.size(13.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingGmCard(
    text: String,
    isPlaceholder: Boolean
) {
    Card(
        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Мастер Миров печатает...",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isPlaceholder) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun AiErrorBanner(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_error_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Ошибка ИИ",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Не удалось получить ответ Мастера",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canRetry) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Повторить")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Скрыть", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@Composable
private fun DiceRollerTray(
    modifierValue: Int,
    onModifierChange: (Int) -> Unit,
    onRollDie: (Int) -> Unit
) {
    val diceSides = listOf(4, 6, 8, 10, 12, 20, 100)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Бросок кубика (прикрепится к ходу)",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { onModifierChange(modifierValue - 1) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(34.dp)
                    ) { Text("-") }
                    Text(
                        text = if (modifierValue >= 0) " +$modifierValue " else " $modifierValue ",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    OutlinedButton(
                        onClick = { onModifierChange(modifierValue + 1) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(34.dp)
                    ) { Text("+") }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                diceSides.forEach { sides ->
                    Button(
                        onClick = { onRollDie(sides) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("roll_d$sides")
                    ) {
                        Text("d$sides", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChronicleMemoryDialog(
    campaign: CampaignEntity,
    memoryStats: MemoryUsageStats,
    isOptimizing: Boolean,
    onDismiss: () -> Unit,
    onOptimizeNow: () -> Unit,
    onSaveManual: (String) -> Unit
) {
    var draftSummary by remember(campaign.memorySummary) { mutableStateOf(campaign.memorySummary) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Долговременная память Мастера") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Активных сообщений в контексте: ${memoryStats.activeMessagesCount} | В архиве хроники: ${memoryStats.archivedMessagesCount} (глав сжато: ${memoryStats.compressedChaptersCount}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = draftSummary,
                    onValueChange = { draftSummary = it },
                    label = { Text("Сжатая хроника событий (можно дополнить вручную)") },
                    placeholder = { Text("Нажмите «Сжать с ИИ» или запишите ключевые факты приключения, которые Мастер обязан помнить всегда...") },
                    minLines = 6,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = onOptimizeNow,
                    enabled = !isOptimizing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isOptimizing) "Оптимизация..." else "Сжать старые сообщения с ИИ")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSaveManual(draftSummary) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}
