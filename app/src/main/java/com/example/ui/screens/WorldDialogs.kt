package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
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
import com.example.data.local.WorldLoreEntity

fun worldOriginLabel(originType: String): String = when (originType) {
    "AI_ORIGINAL" -> "ИИ с нуля"
    "EXISTING_UNIVERSE" -> "Вселенная"
    "PRESET" -> "Базовый"
    else -> "Авторский"
}

/**
 * Dedicated Game & Character Creation dialog:
 * Step 1: Pick one of the created Worlds from your Worlds Library.
 * Step 2: Customize your Character (name, archetype with world-specific suggestions, stats, backstory) and start playing!
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewGameCharacterDialog(
    worlds: List<WorldEntity>,
    preselectedWorldId: Long? = null,
    onDismiss: () -> Unit,
    onGoToCreateWorld: () -> Unit,
    onStartGame: (
        selectedWorld: WorldEntity,
        campaignTitle: String,
        heroName: String,
        heroArchetype: String,
        heroStatsSummary: String,
        heroBackstory: String,
        customOpeningScene: String,
        useDiceAndStats: Boolean,
        useInventory: Boolean,
        useSpells: Boolean
    ) -> Unit
) {
    val initialWorld = worlds.firstOrNull { it.id == preselectedWorldId } ?: worlds.firstOrNull()
    var selectedWorldId by remember(preselectedWorldId, worlds) {
        mutableLongStateOf(initialWorld?.id ?: 0L)
    }
    val chosenWorld = worlds.firstOrNull { it.id == selectedWorldId } ?: initialWorld
    val worldSuggestsFreeform = chosenWorld?.diceSystem?.contains("Без кубиков", ignoreCase = true) == true

    var useDiceAndStats by remember(chosenWorld?.id) { mutableStateOf(!worldSuggestsFreeform) }
    var useInventory by remember(chosenWorld?.id) { mutableStateOf(!worldSuggestsFreeform) }
    var useSpells by remember(chosenWorld?.id) { mutableStateOf(!worldSuggestsFreeform) }

    var heroName by remember { mutableStateOf("") }
    var heroArchetype by remember(chosenWorld?.id) {
        mutableStateOf(chosenWorld?.archetypesList()?.firstOrNull().orEmpty())
    }
    var heroStats by remember(chosenWorld?.id, useDiceAndStats) {
        mutableStateOf(
            if (useDiceAndStats) {
                "ОЗ 26/26 | Защита 14 | СИЛ +1, ЛОВ +2, ИНТ +2, ВОЛЯ +2"
            } else {
                "Наблюдательный, сдержанный в речах, хранит старую тайну"
            }
        )
    }
    var heroBackstory by remember { mutableStateOf("") }
    var campaignTitle by remember(chosenWorld?.id) {
        mutableStateOf(chosenWorld?.let { "${it.name}: Новая глава" }.orEmpty())
    }
    var openingScene by remember(chosenWorld?.id) {
        mutableStateOf(chosenWorld?.defaultOpeningHook.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая игра и создание персонажа") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "1. Выберите мир из библиотеки:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(
                        onClick = onGoToCreateWorld,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("+ Создать мир")
                    }
                }

                // Scrollable Worlds Selector List
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    worlds.forEach { world ->
                        val isSelected = world.id == chosenWorld?.id
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedWorldId = world.id }
                                .testTag("select_world_${world.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = world.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "[${worldOriginLabel(world.originType)}]",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.secondary
                                            }
                                        )
                                    }
                                    Text(
                                        text = world.genre,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Выбранный мир",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (chosenWorld != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "2. Формат ролевой (ДнД или чистая словеска):",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = useDiceAndStats && useInventory && useSpells,
                            onClick = {
                                useDiceAndStats = true
                                useInventory = true
                                useSpells = true
                            },
                            label = { Text("Полная РПГ") },
                            modifier = Modifier.testTag("preset_full_rpg_chip")
                        )
                        FilterChip(
                            selected = !useDiceAndStats && !useInventory && !useSpells,
                            onClick = {
                                useDiceAndStats = false
                                useInventory = false
                                useSpells = false
                            },
                            label = { Text("Ролевая без ДнД (словеска)") },
                            modifier = Modifier.testTag("preset_freeform_nodnd_chip")
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ModuleSwitchRow(
                                label = "Кубики и характеристики",
                                checked = useDiceAndStats,
                                onCheckedChange = { useDiceAndStats = it },
                                testTag = "switch_module_dice"
                            )
                            ModuleSwitchRow(
                                label = "Вкладка «Снаряжение»",
                                checked = useInventory,
                                onCheckedChange = { useInventory = it },
                                testTag = "switch_module_inventory"
                            )
                            ModuleSwitchRow(
                                label = "Вкладка «Заклинания и способности»",
                                checked = useSpells,
                                onCheckedChange = { useSpells = it },
                                testTag = "switch_module_spells"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "3. Создайте героя для мира «${chosenWorld.name}»:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = heroName,
                        onValueChange = { heroName = it },
                        label = { Text("Имя персонажа") },
                        placeholder = { Text("Например: Геральт, Ария, Корво...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_game_hero_name_input")
                    )

                    val archetypes = chosenWorld.archetypesList()
                    if (archetypes.isNotEmpty()) {
                        Text(
                            text = "Рекомендуемые роли в этом мире:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            archetypes.forEach { arch ->
                                FilterChip(
                                    selected = heroArchetype == arch,
                                    onClick = { heroArchetype = arch },
                                    label = { Text(arch, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = heroArchetype,
                        onValueChange = { heroArchetype = it },
                        label = { Text("Класс / Роль / Происхождение") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = heroStats,
                        onValueChange = { heroStats = it },
                        label = {
                            Text(
                                if (useDiceAndStats) "Стартовые характеристики (ОЗ, защита, навыки)"
                                else "Черты характера и внешность героя"
                            )
                        },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = heroBackstory,
                        onValueChange = { heroBackstory = it },
                        label = { Text("Предыстория и личная цель героя (необязательно)") },
                        placeholder = { Text("Кем был герой до начала игры и чего он ищет в этом мире...") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = campaignTitle,
                        onValueChange = { campaignTitle = it },
                        label = { Text("Название слота сохранения") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = openingScene,
                        onValueChange = { openingScene = it },
                        label = { Text("Стартовая сцена (можно оставить из шаблона мира)") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (chosenWorld != null) {
                        onStartGame(
                            chosenWorld,
                            campaignTitle,
                            heroName.ifBlank { "Странник" },
                            heroArchetype,
                            heroStats,
                            heroBackstory,
                            openingScene,
                            useDiceAndStats,
                            useInventory,
                            useSpells
                        )
                    }
                },
                enabled = chosenWorld != null,
                modifier = Modifier.testTag("confirm_start_new_game_button")
            ) {
                Text("Начать игру")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

/**
 * Manual World Creator / Editor for the reusable Worlds Library.
 */
@Composable
fun ManualWorldEditorDialog(
    existing: WorldEntity?,
    onDismiss: () -> Unit,
    onSave: (WorldEntity) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var genre by remember { mutableStateOf(existing?.genre ?: "Авторский сеттинг") }
    var description by remember { mutableStateOf(existing?.worldDescription.orEmpty()) }
    var diceSystem by remember { mutableStateOf(existing?.diceSystem ?: "d20 + модификатор против КС") }
    var magicTech by remember { mutableStateOf(existing?.magicOrTechSystem ?: "Авторская система способностей") }
    var tone by remember { mutableStateOf(existing?.narrativeTone ?: "Атмосферное приключение") }
    var customRules by remember { mutableStateOf(existing?.customRules.orEmpty()) }
    var archetypesRaw by remember {
        mutableStateOf(existing?.archetypesList()?.joinToString(", ").orEmpty())
    }
    var openingHook by remember { mutableStateOf(existing?.defaultOpeningHook.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Создать мир вручную" else "Редактировать мир") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название мира / сеттинга") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Жанр (Фэнтези, Стимпанк, Киберпанк, Постапокалипсис...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Общее описание мира и конфликта") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = diceSystem,
                    onValueChange = { diceSystem = it },
                    label = { Text("Механика кубиков и проверок") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = magicTech,
                    onValueChange = { magicTech = it },
                    label = { Text("Система магии / технологий / ресурсов") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tone,
                    onValueChange = { tone = it },
                    label = { Text("Тон и атмосфера") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = customRules,
                    onValueChange = { customRules = it },
                    label = { Text("Уникальные законы и правила мира") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = archetypesRaw,
                    onValueChange = { archetypesRaw = it },
                    label = { Text("Рекомендуемые классы/роли (через запятую)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = openingHook,
                    onValueChange = { openingHook = it },
                    label = { Text("Стартовая сцена по умолчанию") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val pipeArchetypes = archetypesRaw.split(",", "|")
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .joinToString("|")
                        onSave(
                            WorldEntity(
                                id = existing?.id ?: 0L,
                                name = name.trim(),
                                originType = existing?.originType ?: "CUSTOM",
                                franchiseSource = existing?.franchiseSource.orEmpty(),
                                genre = genre.trim().ifBlank { "Авторский сеттинг" },
                                worldDescription = description.trim(),
                                diceSystem = diceSystem.trim().ifBlank { "d20 + модификатор" },
                                magicOrTechSystem = magicTech.trim(),
                                narrativeTone = tone.trim(),
                                customRules = customRules.trim(),
                                suggestedArchetypesCsv = pipeArchetypes,
                                defaultOpeningHook = openingHook.trim(),
                                groundingSourcesJson = existing?.groundingSourcesJson ?: "[]",
                                createdAt = existing?.createdAt ?: System.currentTimeMillis()
                            )
                        )
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Сохранить мир")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

/**
 * Dialog to inspect, edit, and import files into a specific World's reusable Knowledge Base.
 */
@Composable
fun WorldLoreManagerDialog(
    world: WorldEntity,
    loreItems: List<WorldLoreEntity>,
    onDismiss: () -> Unit,
    onImportFileClick: () -> Unit,
    onSaveLoreItem: (WorldLoreEntity) -> Unit,
    onToggleAlwaysActive: (WorldLoreEntity) -> Unit,
    onDeleteLoreItem: (WorldLoreEntity) -> Unit
) {
    var editingLore by remember { mutableStateOf<WorldLoreEntity?>(null) }
    var showCreateLore by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("База знаний: ${world.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Все статьи этого мира автоматически доступны ИИ-Мастеру в любой игре, созданной в мире «${world.name}».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onImportFileClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Файл")
                    }
                    OutlinedButton(
                        onClick = { showCreateLore = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Статья")
                    }
                }

                if (loreItems.isEmpty()) {
                    Text(
                        text = "В базе знаний этого мира пока нет статей.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    loreItems.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.sectionTitle,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = item.sourceTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    FilterChip(
                                        selected = item.isAlwaysActive,
                                        onClick = { onToggleAlwaysActive(item) },
                                        label = { Text(if (item.isAlwaysActive) "Всегда" else "По ключам") }
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.content.take(220) + if (item.content.length > 220) "..." else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(onClick = { editingLore = item }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Редактировать статью")
                                    }
                                    IconButton(onClick = { onDeleteLoreItem(item) }) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Удалить статью",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Готово") }
        }
    )

    if (showCreateLore) {
        WorldLoreItemEditorDialog(
            worldId = world.id,
            existing = null,
            onDismiss = { showCreateLore = false },
            onSave = {
                onSaveLoreItem(it)
                showCreateLore = false
            }
        )
    }

    editingLore?.let { target ->
        WorldLoreItemEditorDialog(
            worldId = world.id,
            existing = target,
            onDismiss = { editingLore = null },
            onSave = {
                onSaveLoreItem(it)
                editingLore = null
            }
        )
    }
}

@Composable
fun WorldLoreItemEditorDialog(
    worldId: Long,
    existing: WorldLoreEntity?,
    onDismiss: () -> Unit,
    onSave: (WorldLoreEntity) -> Unit
) {
    var sectionTitle by remember { mutableStateOf(existing?.sectionTitle.orEmpty()) }
    var sourceTitle by remember { mutableStateOf(existing?.sourceTitle ?: "Кодекс мира") }
    var keywords by remember { mutableStateOf(existing?.keywordsCsv.orEmpty()) }
    var content by remember { mutableStateOf(existing?.content.orEmpty()) }
    var alwaysActive by remember { mutableStateOf(existing?.isAlwaysActive ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Новая статья лора мира" else "Изменить статью лора") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = sectionTitle,
                    onValueChange = { sectionTitle = it },
                    label = { Text("Название раздела (фракция, город, канон...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sourceTitle,
                    onValueChange = { sourceTitle = it },
                    label = { Text("Источник") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text("Ключевые слова через запятую") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Текст статьи базы знаний") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Всегда включать в контекст", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = alwaysActive, onCheckedChange = { alwaysActive = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (sectionTitle.isNotBlank() && content.isNotBlank()) {
                        onSave(
                            WorldLoreEntity(
                                id = existing?.id ?: 0L,
                                worldId = worldId,
                                sourceTitle = sourceTitle.trim().ifBlank { "Кодекс мира" },
                                sectionTitle = sectionTitle.trim(),
                                keywordsCsv = keywords,
                                content = content.trim(),
                                isAlwaysActive = alwaysActive
                            )
                        )
                    }
                },
                enabled = sectionTitle.isNotBlank() && content.isNotBlank()
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
fun CampaignRulesQuickDialog(
    campaign: CampaignEntity,
    onDismiss: () -> Unit,
    onSave: (CampaignEntity) -> Unit
) {
    var title by remember { mutableStateOf(campaign.title) }
    var diceSystem by remember { mutableStateOf(campaign.diceSystem) }
    var magicTech by remember { mutableStateOf(campaign.magicOrTechSystem) }
    var customRules by remember { mutableStateOf(campaign.customRules) }
    var useDiceAndStats by remember { mutableStateOf(campaign.useDiceAndStats) }
    var useInventory by remember { mutableStateOf(campaign.useInventory) }
    var useSpells by remember { mutableStateOf(campaign.useSpells) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Правила и модули текущей игры") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = useDiceAndStats && useInventory && useSpells,
                        onClick = {
                            useDiceAndStats = true
                            useInventory = true
                            useSpells = true
                        },
                        label = { Text("Полная РПГ") }
                    )
                    FilterChip(
                        selected = !useDiceAndStats && !useInventory && !useSpells,
                        onClick = {
                            useDiceAndStats = false
                            useInventory = false
                            useSpells = false
                        },
                        label = { Text("Без ДнД (чистая словеска)") }
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        ModuleSwitchRow(
                            label = "Кубики и числовые проверки",
                            checked = useDiceAndStats,
                            onCheckedChange = { useDiceAndStats = it },
                            testTag = "rules_switch_dice"
                        )
                        ModuleSwitchRow(
                            label = "Модуль «Снаряжение»",
                            checked = useInventory,
                            onCheckedChange = { useInventory = it },
                            testTag = "rules_switch_inventory"
                        )
                        ModuleSwitchRow(
                            label = "Модуль «Заклинания»",
                            checked = useSpells,
                            onCheckedChange = { useSpells = it },
                            testTag = "rules_switch_spells"
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название приключения") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (useDiceAndStats) {
                    OutlinedTextField(
                        value = diceSystem,
                        onValueChange = { diceSystem = it },
                        label = { Text("Механика кубиков") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = magicTech,
                    onValueChange = { magicTech = it },
                    label = { Text("Магия / Технологии / Законы мира") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = customRules,
                    onValueChange = { customRules = it },
                    label = { Text("Особые правила этой партии") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        campaign.copy(
                            title = title.ifBlank { campaign.title },
                            diceSystem = diceSystem.ifBlank { campaign.diceSystem },
                            magicOrTechSystem = magicTech.ifBlank { campaign.magicOrTechSystem },
                            customRules = customRules.trim(),
                            useDiceAndStats = useDiceAndStats,
                            useInventory = useInventory,
                            useSpells = useSpells
                        )
                    )
                }
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
private fun ModuleSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
