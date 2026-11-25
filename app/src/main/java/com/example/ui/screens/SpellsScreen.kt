package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CampaignEntity
import com.example.data.local.CustomSpellEntity
import com.example.ui.SpellAiState

@Composable
fun SpellsScreen(
    campaign: CampaignEntity?,
    spells: List<CustomSpellEntity>,
    spellAiState: SpellAiState,
    onSaveSpell: (CustomSpellEntity) -> Unit,
    onTogglePrepared: (CustomSpellEntity) -> Unit,
    onDeleteSpell: (CustomSpellEntity) -> Unit,
    onCastSpell: (CustomSpellEntity) -> Unit,
    onGenerateSpellWithAi: (String) -> Unit,
    onResetSpellAiState: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showCreateManual by remember { mutableStateOf(false) }
    var showAiArchitect by remember { mutableStateOf(false) }
    var editingSpell by remember { mutableStateOf<CustomSpellEntity?>(null) }

    // When AI finishes drafting a custom spell, open the editor pre-populated with the draft
    LaunchedEffect(spellAiState) {
        if (spellAiState is SpellAiState.Success && campaign != null) {
            val d = spellAiState.draft
            editingSpell = CustomSpellEntity(
                id = 0L,
                campaignId = campaign.id,
                name = d.name,
                rankOrLevel = d.rankOrLevel,
                schoolOrType = d.schoolOrType,
                resourceCost = d.resourceCost,
                castTime = d.castTime,
                rangeInfo = d.rangeInfo,
                durationInfo = d.durationInfo,
                diceFormula = d.diceFormula,
                description = d.description,
                tagsCsv = d.tagsCsv,
                isPrepared = true
            )
            showAiArchitect = false
            onResetSpellAiState()
        }
    }

    val filteredSpells = remember(spells, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) spells
        else spells.filter { sp ->
            sp.name.lowercase().contains(q) ||
                sp.schoolOrType.lowercase().contains(q) ||
                sp.description.lowercase().contains(q) ||
                sp.tagsCsv.lowercase().contains(q)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("spells_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Grimoire Illustrated Header Banner
            item(key = "grimoire_hero_header") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(152.dp)
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_grimoire_art_1795485409983),
                        contentDescription = stringResource(R.string.grimoire_banner_desc),
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
                                        Color.Black.copy(alpha = 0.82f)
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
                            text = "Гримуар и способности",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color(0xFFF6EEDF)
                        )
                        Text(
                            text = campaign?.magicOrTechSystem
                                ?: "Создавайте кастомные заклинания, ритуалы, псионику или кибер-модули",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFDFD5C5)
                        )
                    }
                }
            }

            // Action Buttons & Search
            item(key = "spells_controls") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showCreateManual = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("create_custom_spell_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Конструктор")
                        }
                        OutlinedButton(
                            onClick = { showAiArchitect = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ai_spell_architect_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("С ИИ под мир")
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Поиск заклинания, школы или #тега...") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (filteredSpells.isEmpty()) {
                item(key = "spells_empty") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Гримуар ждёт ваших творений",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Создайте уникальное заклинание вручную или поручите ИИ сбалансировать вашу идею под правила текущего мира.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredSpells, key = { it.id }) { spell ->
                    CustomSpellCard(
                        spell = spell,
                        onCast = { onCastSpell(spell) },
                        onTogglePrepared = { onTogglePrepared(spell) },
                        onEdit = { editingSpell = spell },
                        onDelete = { onDeleteSpell(spell) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateManual = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_spell_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Создать кастомное заклинание")
        }
    }

    if (showCreateManual && campaign != null) {
        SpellEditorDialog(
            campaignId = campaign.id,
            existing = null,
            onDismiss = { showCreateManual = false },
            onSave = {
                onSaveSpell(it)
                showCreateManual = false
            }
        )
    }

    editingSpell?.let { draftOrExisting ->
        SpellEditorDialog(
            campaignId = draftOrExisting.campaignId,
            existing = draftOrExisting,
            onDismiss = { editingSpell = null },
            onSave = {
                onSaveSpell(it)
                editingSpell = null
            }
        )
    }

    if (showAiArchitect) {
        AiSpellArchitectDialog(
            settingName = campaign?.settingName ?: "Текущий мир",
            spellAiState = spellAiState,
            onDismiss = {
                onResetSpellAiState()
                showAiArchitect = false
            },
            onGenerate = onGenerateSpellWithAi
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomSpellCard(
    spell: CustomSpellEntity,
    onCast: () -> Unit,
    onTogglePrepared: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (spell.isPrepared) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = spell.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${spell.rankOrLevel} • ${spell.schoolOrType}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                FilterChip(
                    selected = spell.isPrepared,
                    onClick = onTogglePrepared,
                    label = { Text(if (spell.isPrepared) "Активно" else "В резерве") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SpellStatBadge("Цена: ${spell.resourceCost}")
                SpellStatBadge("Время: ${spell.castTime}")
                SpellStatBadge("Дист.: ${spell.rangeInfo}")
                if (spell.diceFormula.isNotBlank()) {
                    SpellStatBadge("🎲 ${spell.diceFormula}", highlight = true)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = spell.description,
                style = MaterialTheme.typography.bodyMedium
            )

            val tags = spell.tagsList()
            if (tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = tags.joinToString("  ") { "#$it" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onCast,
                    modifier = Modifier.testTag("cast_spell_${spell.id}")
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Сотворить в чате")
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Редактировать заклинание")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Удалить заклинание",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpellStatBadge(text: String, highlight: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (highlight) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlight) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SpellEditorDialog(
    campaignId: Long,
    existing: CustomSpellEntity?,
    onDismiss: () -> Unit,
    onSave: (CustomSpellEntity) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var rank by remember { mutableStateOf(existing?.rankOrLevel ?: "1 круг") }
    var school by remember { mutableStateOf(existing?.schoolOrType ?: "Воплощение") }
    var cost by remember { mutableStateOf(existing?.resourceCost ?: "1 ячейка") }
    var castTime by remember { mutableStateOf(existing?.castTime ?: "1 действие") }
    var rangeInfo by remember { mutableStateOf(existing?.rangeInfo ?: "18 метров") }
    var duration by remember { mutableStateOf(existing?.durationInfo ?: "Мгновенно") }
    var diceFormula by remember { mutableStateOf(existing?.diceFormula.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var tagsRaw by remember { mutableStateOf(existing?.tagsCsv.orEmpty()) }
    var prepared by remember { mutableStateOf(existing?.isPrepared ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing == null || existing.id == 0L) {
                    "Конструктор кастомного заклинания"
                } else {
                    "Изменить заклинание"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название заклинания / способности") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rank,
                        onValueChange = { rank = it },
                        label = { Text("Круг / Ранг") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = school,
                        onValueChange = { school = it },
                        label = { Text("Школа / Тип") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cost,
                        onValueChange = { cost = it },
                        label = { Text("Стоимость") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = castTime,
                        onValueChange = { castTime = it },
                        label = { Text("Время наклад.") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = rangeInfo,
                        onValueChange = { rangeInfo = it },
                        label = { Text("Дистанция") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = duration,
                        onValueChange = { duration = it },
                        label = { Text("Длительность") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = diceFormula,
                    onValueChange = { diceFormula = it },
                    label = { Text("Формула броска (напр. 3d6 + 3)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание эффекта и правил") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tagsRaw,
                    onValueChange = { tagsRaw = it },
                    label = { Text("Теги через запятую") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Подготовлено к применению")
                    Switch(checked = prepared, onCheckedChange = { prepared = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && description.isNotBlank()) {
                        onSave(
                            CustomSpellEntity(
                                id = existing?.id ?: 0L,
                                campaignId = campaignId,
                                name = name.trim(),
                                rankOrLevel = rank.trim().ifBlank { "Особое" },
                                schoolOrType = school.trim().ifBlank { "Авторская" },
                                resourceCost = cost.trim().ifBlank { "Без затрат" },
                                castTime = castTime.trim().ifBlank { "1 действие" },
                                rangeInfo = rangeInfo.trim().ifBlank { "На себя" },
                                durationInfo = duration.trim().ifBlank { "Мгновенно" },
                                diceFormula = diceFormula.trim(),
                                description = description.trim(),
                                tagsCsv = tagsRaw,
                                isPrepared = prepared
                            )
                        )
                    }
                },
                enabled = name.isNotBlank() && description.isNotBlank()
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun AiSpellArchitectDialog(
    settingName: String,
    spellAiState: SpellAiState,
    onDismiss: () -> Unit,
    onGenerate: (String) -> Unit
) {
    var idea by rememberSaveable { mutableStateOf("") }
    val isLoading = spellAiState is SpellAiState.Loading

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("ИИ-Архитектор способностей") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Опишите идею своими словами — Gemini создаст сбалансированное заклинание или техно-способность специально под сеттинг «$settingName».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = idea,
                    onValueChange = { idea = it },
                    label = { Text("Идея заклинания или приёма") },
                    placeholder = { Text("Например: Цепь багровых молний, перескакивающая между врагами и дающая щит...") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                if (spellAiState is SpellAiState.Error) {
                    Text(
                        text = spellAiState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onGenerate(idea) },
                enabled = !isLoading && idea.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Создание...")
                } else {
                    Text("Сгенерировать")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Отмена")
            }
        }
    )
}
