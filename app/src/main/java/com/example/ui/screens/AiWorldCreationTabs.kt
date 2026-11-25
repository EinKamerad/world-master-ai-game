package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.ai.GeneratedWorldDraft
import com.example.ui.WorldGenUiState

private val ScratchWorldIdeas = listOf(
    "Океан вечных гроз с летающими островами, где охотники на дирижаблях добывают кристаллы молний",
    "Славянское тёмное фэнтези: княжества среди древних чащоб, заговоры ведунов и ожившие духи курганов",
    "Солнечный стимпанк-пустыня: кочевые города-шагоходы, алхимия эфира и руины стеклянных империй"
)

private val PopularUniverses = listOf(
    "Ведьмак (The Witcher)",
    "S.T.A.L.K.E.R.",
    "Cyberpunk 2077",
    "Warhammer 40,000",
    "Mass Effect",
    "Arcane / Runeterra",
    "Дюна (Dune)",
    "The Elder Scrolls: Skyrim"
)

/**
 * Tab 1 of AI Creation: Create a brand-new original RPG world from scratch by describing it to the AI!
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiScratchWorldTab(
    genState: WorldGenUiState,
    onGenerateClick: (description: String, rulesHint: String) -> Unit,
    onSaveWorldOnly: (GeneratedWorldDraft) -> Unit,
    onSaveAndCreateHero: (GeneratedWorldDraft) -> Unit,
    onResetState: () -> Unit
) {
    var description by rememberSaveable { mutableStateOf("") }
    var rulesHint by rememberSaveable { mutableStateOf("") }
    val isLoading = genState is WorldGenUiState.Loading

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Создание нового мира с нуля с ИИ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Опишите идею мира своими словами — ИИ разработает его историю, географию, фракции, уникальные правила, архетипы героев и статьи базы знаний.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Идеи для быстрого старта:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ScratchWorldIdeas.forEachIndexed { idx, sample ->
                        AssistChip(
                            onClick = { description = sample },
                            label = {
                                Text(
                                    text = sample.take(36) + "…",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.testTag("scratch_sample_chip_$idx")
                        )
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Опишите ваш будущий мир") },
                    placeholder = {
                        Text("Какая в нём эпоха, магия или технологии, в чём главный конфликт и атмосфера...")
                    },
                    minLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_scratch_world_description_input")
                )

                OutlinedTextField(
                    value = rulesHint,
                    onValueChange = { rulesHint = it },
                    label = { Text("Пожелания по механике или сложности (необязательно)") },
                    placeholder = { Text("Например: хардкорное выживание, система маны, кубик d20...") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { onGenerateClick(description, rulesHint) },
                    enabled = !isLoading && description.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generate_scratch_world_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Генерация мира...")
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сгенерировать мир с нуля")
                    }
                }
            }
        }

        WorldGenStatusAndPreview(
            state = genState,
            onSaveOnly = onSaveWorldOnly,
            onSaveAndPlay = onSaveAndCreateHero,
            onDiscard = onResetState
        )
    }
}

/**
 * Tab 2 of AI Creation: Dedicated tab for existing universes (games, TV series, anime, books, movies).
 * Enter the universe name -> AI searches via Google Search Grounding -> populates canon rules & lorebook!
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExistingUniverseSearchTab(
    searchState: WorldGenUiState,
    onSearchUniverseClick: (universeTitle: String, eraOrFocusHint: String) -> Unit,
    onSaveWorldOnly: (GeneratedWorldDraft, String) -> Unit,
    onSaveAndCreateHero: (GeneratedWorldDraft, String) -> Unit,
    onResetState: () -> Unit
) {
    var universeTitle by rememberSaveable { mutableStateOf("") }
    var eraOrFocusHint by rememberSaveable { mutableStateOf("") }
    val isLoading = searchState is WorldGenUiState.Loading

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TravelExplore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Мир по существующей вселенной",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Введите название любой игры, сериала, аниме, книги или кинофраншизы. ИИ найдёт в сети актуальный канон, фракции, локации и систему сил и соберёт готовый мир с базой знаний!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Популярные вселенные:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PopularUniverses.forEach { title ->
                        AssistChip(
                            onClick = { universeTitle = title },
                            label = { Text(title, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = universeTitle,
                    onValueChange = { universeTitle = it },
                    label = { Text("Название вселенной (игра, сериал, книга, аниме)") },
                    placeholder = { Text("Например: Ведьмак, Gothic II, Наруто, Fallout, Аркейн...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("existing_universe_name_input")
                )

                OutlinedTextField(
                    value = eraOrFocusHint,
                    onValueChange = { eraOrFocusHint = it },
                    label = { Text("Эпоха, часть или регион (необязательно)") },
                    placeholder = { Text("Например: Новиград во время Третьей Северной войны...") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { onSearchUniverseClick(universeTitle, eraOrFocusHint) },
                    enabled = !isLoading && universeTitle.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_existing_universe_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Поиск и сборка канона...")
                    } else {
                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Найти вселенную и собрать мир")
                    }
                }
            }
        }

        WorldGenStatusAndPreview(
            state = searchState,
            onSaveOnly = { draft ->
                val franchise = (searchState as? WorldGenUiState.Ready)?.franchiseSource ?: universeTitle
                onSaveWorldOnly(draft, franchise)
            },
            onSaveAndPlay = { draft ->
                val franchise = (searchState as? WorldGenUiState.Ready)?.franchiseSource ?: universeTitle
                onSaveAndCreateHero(draft, franchise)
            },
            onDiscard = onResetState
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorldGenStatusAndPreview(
    state: WorldGenUiState,
    onSaveOnly: (GeneratedWorldDraft) -> Unit,
    onSaveAndPlay: (GeneratedWorldDraft) -> Unit,
    onDiscard: () -> Unit
) {
    when (state) {
        WorldGenUiState.Idle -> Unit

        is WorldGenUiState.Loading -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = state.progressMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        is WorldGenUiState.Error -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Не удалось сгенерировать мир",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    TextButton(onClick = onDiscard) {
                        Text("Закрыть", color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        is WorldGenUiState.Ready -> {
            val uriHandler = LocalUriHandler.current
            var editableName by remember(state.draft) { mutableStateOf(state.draft.name) }
            var editableRules by remember(state.draft) { mutableStateOf(state.draft.customRules) }

            val updatedDraft = state.draft.copy(
                name = editableName.ifBlank { state.draft.name },
                customRules = editableRules
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("generated_world_preview_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Предпросмотр созданного мира",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    OutlinedTextField(
                        value = editableName,
                        onValueChange = { editableName = it },
                        label = { Text("Название мира") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Жанр: ${state.draft.genre}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = state.draft.worldDescription,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "🎲 Проверки: ${state.draft.diceSystem}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "✨ Способности / Технологии: ${state.draft.magicOrTechSystem}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "🎭 Тон: ${state.draft.narrativeTone}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    OutlinedTextField(
                        value = editableRules,
                        onValueChange = { editableRules = it },
                        label = { Text("Уникальные правила мира (можно скорректировать)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (state.draft.suggestedArchetypes.isNotEmpty()) {
                        Text(
                            text = "Доступные роли для создания героев: " + state.draft.suggestedArchetypes.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Text(
                        text = "Собранные разделы базы знаний (${state.draft.loreSections.size}):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    state.draft.loreSections.forEach { sec ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = sec.sectionTitle,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = sec.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (state.draft.groundingSources.isNotEmpty()) {
                        Text(
                            text = "Проверенные веб-источники по вселенной:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            state.draft.groundingSources.forEach { src ->
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
                                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(13.dp))
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSaveOnly(updatedDraft) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("В Мои миры")
                        }
                        Button(
                            onClick = { onSaveAndPlay(updatedDraft) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_world_and_create_hero_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Создать героя")
                        }
                    }

                    TextButton(onClick = onDiscard, modifier = Modifier.align(Alignment.End)) {
                        Text("Сбросить черновик")
                    }
                }
            }
        }
    }
}
