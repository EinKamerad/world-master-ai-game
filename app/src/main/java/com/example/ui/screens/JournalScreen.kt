package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.JournalEntryEntity

private val JournalTypes = listOf("Все", "НПС", "Локация", "Квест", "Фракция", "Заметка")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalScreen(
    campaignId: Long,
    entries: List<JournalEntryEntity>,
    onSaveEntry: (JournalEntryEntity) -> Unit,
    onTogglePin: (JournalEntryEntity) -> Unit,
    onDeleteEntry: (JournalEntryEntity) -> Unit,
    onInteractInChat: (JournalEntryEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedType by rememberSaveable { mutableStateOf("Все") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTags by rememberSaveable { mutableStateOf(listOf<String>()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<JournalEntryEntity?>(null) }

    // Aggregate all tags with counts for instant navigation
    val allTagsWithCount = remember(entries) {
        val counts = mutableMapOf<String, Int>()
        entries.forEach { entry ->
            entry.tagsList().forEach { tag ->
                counts[tag] = (counts[tag] ?: 0) + 1
            }
        }
        counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    }

    val filteredEntries = remember(entries, selectedType, searchQuery, selectedTags) {
        entries.filter { entry ->
            val typeOk = selectedType == "Все" || entry.entryType.equals(selectedType, ignoreCase = true)
            val entryTags = entry.tagsList()
            val tagsOk = selectedTags.isEmpty() || selectedTags.all { sel -> entryTags.any { it.equals(sel, ignoreCase = true) } }
            val q = searchQuery.trim().trimStart('#').lowercase()
            val searchOk = q.isEmpty() ||
                entry.title.lowercase().contains(q) ||
                entry.summary.lowercase().contains(q) ||
                entry.details.lowercase().contains(q) ||
                entry.statusOrDisposition.lowercase().contains(q) ||
                entryTags.any { it.lowercase().contains(q) }
            typeOk && tagsOk && searchOk
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("journal_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Search Bar & Type Filter
            item(key = "journal_search_and_types") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Поиск по НПС, локациям, заметкам или #тегу...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Очистить поиск")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("journal_search_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        JournalTypes.forEach { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(type) }
                            )
                        }
                    }
                }
            }

            // 2. Tag Cloud Navigation Panel
            if (allTagsWithCount.isNotEmpty()) {
                item(key = "journal_tag_cloud") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Tag,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Навигация по тегам (${allTagsWithCount.size})",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                if (selectedTags.isNotEmpty()) {
                                    TextButton(
                                        onClick = { selectedTags = emptyList() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text("Сбросить (${selectedTags.size})")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                allTagsWithCount.forEach { (tag, count) ->
                                    val isSelected = tag in selectedTags
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedTags = if (isSelected) {
                                                selectedTags - tag
                                            } else {
                                                selectedTags + tag
                                            }
                                        },
                                        label = { Text("#$tag ($count)", style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.testTag("journal_tag_chip_$tag")
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Entries List
            if (filteredEntries.isEmpty()) {
                item(key = "journal_empty") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Записей по текущему фильтру не найдено",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Встреченные НПС, локации и квесты добавляются автоматически ИИ-Мастером или вручную по кнопке «+».",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredEntries, key = { it.id }) { entry ->
                    JournalEntryCard(
                        entry = entry,
                        activeTags = selectedTags,
                        onTagClick = { clickedTag ->
                            selectedTags = if (clickedTag in selectedTags) {
                                selectedTags - clickedTag
                            } else {
                                selectedTags + clickedTag
                            }
                        },
                        onTogglePin = { onTogglePin(entry) },
                        onInteractInChat = { onInteractInChat(entry) },
                        onEdit = { editingEntry = entry },
                        onDelete = { onDeleteEntry(entry) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_journal_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Добавить запись в журнал")
        }
    }

    if (showCreateDialog && campaignId > 0L) {
        JournalEditDialog(
            campaignId = campaignId,
            existing = null,
            onDismiss = { showCreateDialog = false },
            onSave = {
                onSaveEntry(it)
                showCreateDialog = false
            }
        )
    }

    editingEntry?.let { current ->
        JournalEditDialog(
            campaignId = current.campaignId,
            existing = current,
            onDismiss = { editingEntry = null },
            onSave = {
                onSaveEntry(it)
                editingEntry = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JournalEntryCard(
    entry: JournalEntryEntity,
    activeTags: List<String>,
    onTagClick: (String) -> Unit,
    onTogglePin: () -> Unit,
    onInteractInChat: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }

    val typeIcon = when (entry.entryType) {
        "НПС" -> Icons.Default.Person
        "Локация" -> Icons.Default.Place
        "Квест" -> Icons.Default.Flag
        "Фракция" -> Icons.Default.Groups
        else -> Icons.Default.MenuBook
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (entry.isPinned) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                        imageVector = typeIcon,
                        contentDescription = entry.entryType,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = entry.entryType,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (entry.statusOrDisposition.isNotBlank()) {
                                Text(
                                    text = " • ${entry.statusOrDisposition}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }

                IconButton(onClick = onTogglePin) {
                    Icon(
                        imageVector = if (entry.isPinned) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (entry.isPinned) "Открепить запись" else "Закрепить запись",
                        tint = if (entry.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = entry.summary,
                style = MaterialTheme.typography.bodyMedium
            )

            if (expanded && entry.details.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = entry.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            val tags = entry.tagsList()
            if (tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.forEach { tag ->
                        val highlighted = tag in activeTags
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (highlighted) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            modifier = Modifier.clickable { onTagClick(tag) }
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (highlighted) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = onInteractInChat,
                    label = { Text("Вспомнить в чате") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Редактировать запись")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Удалить запись",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalEditDialog(
    campaignId: Long,
    existing: JournalEntryEntity?,
    onDismiss: () -> Unit,
    onSave: (JournalEntryEntity) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var entryType by remember { mutableStateOf(existing?.entryType ?: "НПС") }
    var status by remember { mutableStateOf(existing?.statusOrDisposition.orEmpty()) }
    var summary by remember { mutableStateOf(existing?.summary.orEmpty()) }
    var details by remember { mutableStateOf(existing?.details.orEmpty()) }
    var tagsRaw by remember { mutableStateOf(existing?.tagsCsv.orEmpty()) }

    val selectableTypes = listOf("НПС", "Локация", "Квест", "Фракция", "Заметка")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Новая запись журнала" else "Редактировать запись") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Имя НПС / Название локации или квеста") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    selectableTypes.forEach { t ->
                        FilterChip(
                            selected = entryType == t,
                            onClick = { entryType = t },
                            label = { Text(t) }
                        )
                    }
                }
                OutlinedTextField(
                    value = status,
                    onValueChange = { status = it },
                    label = { Text("Статус / Отношение (напр. Союзник, В процессе)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text("Краткое описание") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Подробные заметки, тайны и зацепки") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tagsRaw,
                    onValueChange = { tagsRaw = it },
                    label = { Text("Теги через запятую (напр. торговец, эльдхейм, квест)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(
                            JournalEntryEntity(
                                id = existing?.id ?: 0L,
                                campaignId = campaignId,
                                title = title.trim(),
                                entryType = entryType,
                                summary = summary.trim().ifBlank { "Без краткого описания" },
                                details = details.trim(),
                                tagsCsv = tagsRaw,
                                statusOrDisposition = status.trim(),
                                isPinned = existing?.isPinned ?: false
                            )
                        )
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
