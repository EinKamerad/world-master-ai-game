package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CampaignEntity
import com.example.data.local.QaMessageEntity
import com.example.ui.AiTurnState

private val QuickAdvisorQuestions = listOf(
    "Какого цвета глаза и особые приметы у собеседника?",
    "Напомни ключевые детали текущей сцены",
    "Как работает передача власти и законы в этом сюжете?",
    "Что мой персонаж уже достоверно знает на данный момент?"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QaAdvisorBottomSheet(
    campaign: CampaignEntity?,
    qaMessages: List<QaMessageEntity>,
    qaTurnState: AiTurnState,
    webSearchEnabled: Boolean,
    onToggleWebSearch: (Boolean) -> Unit,
    onAskQuestion: (String) -> Unit,
    onClearHistory: () -> Unit,
    onSaveAnswerToJournal: (QaMessageEntity) -> Unit,
    onDismissError: () -> Unit,
    onDismissSheet: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissSheet,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        QaAdvisorPanelContent(
            campaign = campaign,
            qaMessages = qaMessages,
            qaTurnState = qaTurnState,
            webSearchEnabled = webSearchEnabled,
            onToggleWebSearch = onToggleWebSearch,
            onAskQuestion = onAskQuestion,
            onClearHistory = onClearHistory,
            onSaveAnswerToJournal = onSaveAnswerToJournal,
            onDismissError = onDismissError,
            onClosePanel = onDismissSheet,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QaAdvisorPanelContent(
    campaign: CampaignEntity?,
    qaMessages: List<QaMessageEntity>,
    qaTurnState: AiTurnState,
    webSearchEnabled: Boolean,
    onToggleWebSearch: (Boolean) -> Unit,
    onAskQuestion: (String) -> Unit,
    onClearHistory: () -> Unit,
    onSaveAnswerToJournal: (QaMessageEntity) -> Unit,
    onDismissError: () -> Unit,
    onClosePanel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var questionInput by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(qaMessages.size, qaTurnState) {
        val count = qaMessages.size + (if (qaTurnState is AiTurnState.Loading || qaTurnState is AiTurnState.Streaming) 1 else 0)
        if (count > 0) {
            listState.animateScrollToItem(count - 1)
        }
    }

    Column(
        modifier = modifier
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Чат с вопросами",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ИИ в контексте вашей ролевой + поиск в интернете (вне хода игры)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (qaMessages.isNotEmpty()) {
                    IconButton(
                        onClick = onClearHistory,
                        modifier = Modifier.testTag("qa_clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Очистить историю вопросов"
                        )
                    }
                }
                if (onClosePanel != null) {
                    IconButton(onClick = onClosePanel) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть чат с вопросами"
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = webSearchEnabled,
                onClick = { onToggleWebSearch(!webSearchEnabled) },
                label = { Text(if (webSearchEnabled) "Интернет-поиск: ВКЛ" else "Интернет-поиск") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.testTag("qa_toggle_web_search_chip")
            )

            if (campaign != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "Контекст: ${campaign.settingName} • ${campaign.heroName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Messages or Empty Prompt Suggestions
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("qa_messages_list"),
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (qaMessages.isEmpty() && qaTurnState is AiTurnState.Idle) {
                item(key = "qa_empty_hint") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Спросите что угодно по сюжету или миру",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Советник видит последние сцены вашего чата, лист героя, журнал и лор мира, а также может свериться с интернетом — не ломая сцену и не тратя игровой ход.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                QuickAdvisorQuestions.forEach { sample ->
                                    AssistChip(
                                        onClick = { onAskQuestion(sample) },
                                        label = {
                                            Text(
                                                text = sample,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(qaMessages, key = { it.id }) { qa ->
                val isUser = qa.senderRole == "USER"
                val sources = remember(qa.groundingSourcesJson) { qa.groundingSources() }

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUser) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ),
                        modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 0.96f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = if (isUser) "Ваш вопрос" else "Советник по контексту",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isUser) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = qa.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isUser) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )

                            if (sources.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                                    contentDescription = "Открыть источник",
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            if (!isUser) {
                                Spacer(modifier = Modifier.height(6.dp))
                                AssistChip(
                                    onClick = { onSaveAnswerToJournal(qa) },
                                    label = { Text("Сохранить в журнал", style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.BookmarkAdd,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            when (qaTurnState) {
                is AiTurnState.Loading -> {
                    item(key = "qa_loading") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Советник сверяет контекст чата и ищет ответ...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                is AiTurnState.Streaming -> {
                    item(key = "qa_streaming") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Советник печатает...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = qaTurnState.partialText, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                is AiTurnState.Error -> {
                    item(key = "qa_error") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = qaTurnState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onDismissError) {
                                    Text("ОК")
                                }
                            }
                        }
                    }
                }
                AiTurnState.Idle -> Unit
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val isBusy = qaTurnState is AiTurnState.Loading || qaTurnState is AiTurnState.Streaming
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = questionInput,
                onValueChange = { questionInput = it },
                placeholder = { Text("Например: какого цвета глаза у персонажа?") },
                maxLines = 3,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("qa_advisor_input")
            )
            FilledIconButton(
                onClick = {
                    val q = questionInput.trim()
                    if (q.isNotEmpty()) {
                        onAskQuestion(q)
                        questionInput = ""
                    }
                },
                enabled = !isBusy && questionInput.isNotBlank(),
                modifier = Modifier
                    .size(50.dp)
                    .testTag("qa_advisor_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Задать вопрос Советнику"
                )
            }
        }
    }
}
