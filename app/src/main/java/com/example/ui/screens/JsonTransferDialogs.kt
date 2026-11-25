package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.CampaignEntity
import com.example.data.local.WorldEntity
import com.example.data.transfer.JsonPackageKind
import com.example.data.transfer.JsonTransferSelection
import com.example.data.transfer.ParsedJsonTransferBundle

@Composable
fun JsonExportHubDialog(
    worlds: List<WorldEntity>,
    activeCampaign: CampaignEntity?,
    messagesCount: Int,
    qaMessagesCount: Int,
    inventoryCount: Int,
    journalCount: Int,
    spellsCount: Int,
    knowledgeCount: Int,
    onExportSelectiveBundle: (selection: JsonTransferSelection, shareDirectly: Boolean) -> Unit,
    onExportStandaloneWorld: (worldId: Long, shareDirectly: Boolean) -> Unit,
    onExportStandaloneCharacter: (includeInventory: Boolean, includeSpells: Boolean, shareDirectly: Boolean) -> Unit,
    onExportStandaloneChat: (includeQa: Boolean, includeHiddenPlot: Boolean, shareDirectly: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    // 0 = Сборный пакет с галочками, 1 = Отдельный мир, 2 = Отдельный герой, 3 = Отдельный чат
    var modeIndex by remember { mutableIntStateOf(0) }

    var selection by remember {
        mutableStateOf(
            JsonTransferSelection(
                includeWorld = true,
                includeCharacter = true,
                includeMessages = true,
                includeQaMessages = qaMessagesCount > 0,
                includeInventory = activeCampaign?.useInventory != false,
                includeJournal = true,
                includeSpells = activeCampaign?.useSpells != false,
                includeKnowledge = knowledgeCount > 0,
                includeHiddenPlot = activeCampaign?.hasHiddenPlot() == true
            )
        )
    }

    var selectedWorldId by remember(worlds, activeCampaign) {
        mutableLongStateOf(
            worlds.firstOrNull { it.id == activeCampaign?.worldId }?.id
                ?: worlds.firstOrNull()?.id
                ?: 0L
        )
    }

    var charIncludeInv by remember { mutableStateOf(activeCampaign?.useInventory != false) }
    var charIncludeSpells by remember { mutableStateOf(activeCampaign?.useSpells != false) }

    var chatIncludeQa by remember { mutableStateOf(true) }
    var chatIncludeHiddenPlot by remember { mutableStateOf(false) }

    val triggerExport: (Boolean) -> Unit = { shareDirectly ->
        when (modeIndex) {
            0 -> onExportSelectiveBundle(selection, shareDirectly)
            1 -> if (selectedWorldId > 0L) onExportStandaloneWorld(selectedWorldId, shareDirectly)
            2 -> onExportStandaloneCharacter(charIncludeInv, charIncludeSpells, shareDirectly)
            3 -> onExportStandaloneChat(chatIncludeQa, chatIncludeHiddenPlot, shareDirectly)
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("JSON-экспорт миров, героев и чатов")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = modeIndex == 0,
                        onClick = { modeIndex = 0 },
                        label = { Text("Выбор галочками") },
                        modifier = Modifier.testTag("json_export_mode_selective")
                    )
                    FilterChip(
                        selected = modeIndex == 1,
                        onClick = { modeIndex = 1 },
                        label = { Text("Отдельный мир") },
                        modifier = Modifier.testTag("json_export_mode_world")
                    )
                    FilterChip(
                        selected = modeIndex == 2,
                        onClick = { modeIndex = 2 },
                        label = { Text("Персонаж") },
                        modifier = Modifier.testTag("json_export_mode_character")
                    )
                    FilterChip(
                        selected = modeIndex == 3,
                        onClick = { modeIndex = 3 },
                        label = { Text("Чат") },
                        modifier = Modifier.testTag("json_export_mode_chat")
                    )
                }

                HorizontalDivider()

                when (modeIndex) {
                    0 -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Отметьте разделы для экспорта:",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Row {
                                TextButton(onClick = { selection = JsonTransferSelection() }) {
                                    Text("Все")
                                }
                                TextButton(
                                    onClick = {
                                        selection = JsonTransferSelection(
                                            includeWorld = false,
                                            includeCharacter = false,
                                            includeMessages = false,
                                            includeQaMessages = false,
                                            includeInventory = false,
                                            includeJournal = false,
                                            includeSpells = false,
                                            includeKnowledge = false,
                                            includeHiddenPlot = false
                                        )
                                    }
                                ) {
                                    Text("Сброс")
                                }
                            }
                        }

                        SelectionCheckboxRow(
                            title = "Мир, правила и статьи лора",
                            subtitle = activeCampaign?.settingName ?: "Текущий мир",
                            checked = selection.includeWorld,
                            onCheckedChange = { selection = selection.copy(includeWorld = it) }
                        )
                        SelectionCheckboxRow(
                            title = "Персонаж и режим модулей (ДнД / словеска)",
                            subtitle = activeCampaign?.let { "${it.heroName} (${it.heroArchetype})" } ?: "Герой",
                            checked = selection.includeCharacter,
                            onCheckedChange = { selection = selection.copy(includeCharacter = it) }
                        )
                        SelectionCheckboxRow(
                            title = "История ролевого чата и хроника памяти",
                            subtitle = "Сообщений: $messagesCount",
                            checked = selection.includeMessages,
                            onCheckedChange = { selection = selection.copy(includeMessages = it) }
                        )
                        SelectionCheckboxRow(
                            title = "Чат с вопросами (внеигровой советник)",
                            subtitle = "Сообщений: $qaMessagesCount",
                            checked = selection.includeQaMessages,
                            onCheckedChange = { selection = selection.copy(includeQaMessages = it) }
                        )
                        SelectionCheckboxRow(
                            title = "Снаряжение и предметы",
                            subtitle = "Предметов: $inventoryCount",
                            checked = selection.includeInventory,
                            onCheckedChange = { selection = selection.copy(includeInventory = it) }
                        )
                        SelectionCheckboxRow(
                            title = "Журнал приключений (НПС, квесты, локации)",
                            subtitle = "Записей: $journalCount",
                            checked = selection.includeJournal,
                            onCheckedChange = { selection = selection.copy(includeJournal = it) }
                        )
                        SelectionCheckboxRow(
                            title = "Заклинания и способности",
                            subtitle = "Способностей: $spellsCount",
                            checked = selection.includeSpells,
                            onCheckedChange = { selection = selection.copy(includeSpells = it) }
                        )
                        SelectionCheckboxRow(
                            title = "База знаний сессии",
                            subtitle = "Разделов: $knowledgeCount",
                            checked = selection.includeKnowledge,
                            onCheckedChange = { selection = selection.copy(includeKnowledge = it) }
                        )
                        SelectionCheckboxRow(
                            title = "Скрытый сюжетный план ИИ («Закулисье»)",
                            subtitle = if (activeCampaign?.hasHiddenPlot() == true) "Есть скрытый план" else "Пока пусто",
                            checked = selection.includeHiddenPlot,
                            onCheckedChange = { selection = selection.copy(includeHiddenPlot = it) }
                        )
                    }

                    1 -> {
                        Text(
                            text = "Экспортируйте любой мир вместе с его базой лора в отдельный .JSON файл для обмена с другими игроками:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        worlds.forEach { w ->
                            val selected = w.id == selectedWorldId
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedWorldId = w.id }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(w.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(w.genre, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (selected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        Text(
                            text = "Отдельный JSON-пакет вашего героя «${activeCampaign?.heroName ?: "Странник"}» (${activeCampaign?.heroArchetype.orEmpty()}). Его можно перенести в другую игру или передать другу.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Включить снаряжение ($inventoryCount)", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = charIncludeInv, onCheckedChange = { charIncludeInv = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Включить заклинания ($spellsCount)", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = charIncludeSpells, onCheckedChange = { charIncludeSpells = it })
                        }
                    }

                    3 -> {
                        Text(
                            text = "Отдельный JSON-пакет переписки «${activeCampaign?.title ?: "Сессия"}» ($messagesCount сообщ.) и сжатой хроники памяти.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Включить «Чат с вопросами» ($qaMessagesCount)", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = chatIncludeQa, onCheckedChange = { chatIncludeQa = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Включить скрытый план ИИ (спойлеры)", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = chatIncludeHiddenPlot, onCheckedChange = { chatIncludeHiddenPlot = it })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { triggerExport(true) },
                    modifier = Modifier.testTag("json_export_share_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Поделиться")
                }
                Button(
                    onClick = { triggerExport(false) },
                    modifier = Modifier.testTag("json_export_save_file_button")
                ) {
                    Text("Сохранить .JSON")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun JsonImportPreviewDialog(
    bundle: ParsedJsonTransferBundle,
    hasActiveCampaign: Boolean,
    onConfirmImport: (selection: JsonTransferSelection, mergeIntoActiveCampaign: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selection by remember(bundle) { mutableStateOf(bundle.defaultSelection()) }

    val hasWorldAvailable = bundle.world != null || bundle.worldLore.isNotEmpty()
    val onlyWorldSelected = selection.includeWorld &&
        !selection.includeCharacter &&
        !selection.includeMessages &&
        !selection.includeQaMessages &&
        !selection.includeInventory &&
        !selection.includeJournal &&
        !selection.includeSpells &&
        !selection.includeKnowledge &&
        !selection.includeHiddenPlot

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Импорт из JSON")
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = bundle.titleSummary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Тип пакета: ${bundle.kind.displayLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Text(
                    text = "Выберите галочками разделы, которые нужно импортировать:",
                    style = MaterialTheme.typography.labelLarge
                )

                if (hasWorldAvailable) {
                    SelectionCheckboxRow(
                        title = "Мир и кодекс лора",
                        subtitle = "${bundle.world?.name ?: "Мир"} (статей лора: ${bundle.worldLore.size})",
                        checked = selection.includeWorld,
                        onCheckedChange = { selection = selection.copy(includeWorld = it) }
                    )
                }
                if (bundle.hasCharacterSection && bundle.campaign != null) {
                    SelectionCheckboxRow(
                        title = "Персонаж и настройки режима",
                        subtitle = "${bundle.campaign.heroName} (${bundle.campaign.heroArchetype})",
                        checked = selection.includeCharacter,
                        onCheckedChange = { selection = selection.copy(includeCharacter = it) }
                    )
                }
                if (bundle.messages.isNotEmpty()) {
                    SelectionCheckboxRow(
                        title = "Сообщения ролевого чата и хроника",
                        subtitle = "Сообщений: ${bundle.messages.size}",
                        checked = selection.includeMessages,
                        onCheckedChange = { selection = selection.copy(includeMessages = it) }
                    )
                }
                if (bundle.qaMessages.isNotEmpty()) {
                    SelectionCheckboxRow(
                        title = "История «Чата с вопросами»",
                        subtitle = "Сообщений: ${bundle.qaMessages.size}",
                        checked = selection.includeQaMessages,
                        onCheckedChange = { selection = selection.copy(includeQaMessages = it) }
                    )
                }
                if (bundle.inventory.isNotEmpty()) {
                    SelectionCheckboxRow(
                        title = "Снаряжение",
                        subtitle = "Предметов: ${bundle.inventory.size}",
                        checked = selection.includeInventory,
                        onCheckedChange = { selection = selection.copy(includeInventory = it) }
                    )
                }
                if (bundle.journal.isNotEmpty()) {
                    SelectionCheckboxRow(
                        title = "Журнал приключений",
                        subtitle = "Записей: ${bundle.journal.size}",
                        checked = selection.includeJournal,
                        onCheckedChange = { selection = selection.copy(includeJournal = it) }
                    )
                }
                if (bundle.spells.isNotEmpty()) {
                    SelectionCheckboxRow(
                        title = "Заклинания и способности",
                        subtitle = "Способностей: ${bundle.spells.size}",
                        checked = selection.includeSpells,
                        onCheckedChange = { selection = selection.copy(includeSpells = it) }
                    )
                }
                if (bundle.knowledge.isNotEmpty()) {
                    SelectionCheckboxRow(
                        title = "База знаний сессии",
                        subtitle = "Разделов: ${bundle.knowledge.size}",
                        checked = selection.includeKnowledge,
                        onCheckedChange = { selection = selection.copy(includeKnowledge = it) }
                    )
                }
                if (bundle.hasHiddenPlotSection) {
                    SelectionCheckboxRow(
                        title = "Скрытый сюжетный план ИИ",
                        subtitle = "Арка, границы знаний НПС и триггеры",
                        checked = selection.includeHiddenPlot,
                        onCheckedChange = { selection = selection.copy(includeHiddenPlot = it) }
                    )
                }
            }
        },
        confirmButton = {
            if (bundle.kind == JsonPackageKind.WORLD_ONLY || onlyWorldSelected) {
                Button(
                    onClick = { onConfirmImport(selection, false) },
                    enabled = selection.includeWorld,
                    modifier = Modifier.testTag("confirm_import_world_only_button")
                ) {
                    Text("Добавить мир в библиотеку")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasActiveCampaign) {
                        OutlinedButton(
                            onClick = { onConfirmImport(selection, true) },
                            modifier = Modifier.testTag("confirm_import_merge_button")
                        ) {
                            Text("В текущую игру")
                        }
                    }
                    Button(
                        onClick = { onConfirmImport(selection, false) },
                        modifier = Modifier.testTag("confirm_import_new_game_button")
                    ) {
                        Text("Как новую игру")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun SelectionCheckboxRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
