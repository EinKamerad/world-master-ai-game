package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.data.local.CampaignEntity
import com.example.data.local.InventoryItemEntity

private val InventoryCategories = listOf(
    "Все",
    "Экипировано",
    "Оружие",
    "Броня",
    "Расходники",
    "Артефакты",
    "Ресурсы",
    "Прочее"
)

@Composable
fun InventoryScreen(
    campaign: CampaignEntity?,
    items: List<InventoryItemEntity>,
    onUpdateHeroSheet: (CampaignEntity) -> Unit,
    onSaveItem: (InventoryItemEntity) -> Unit,
    onToggleEquip: (InventoryItemEntity) -> Unit,
    onAdjustQty: (InventoryItemEntity, Int) -> Unit,
    onUseInGame: (InventoryItemEntity) -> Unit,
    onInspectWithMaster: (InventoryItemEntity) -> Unit,
    onDeleteItem: (InventoryItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by rememberSaveable { mutableStateOf("Все") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var editingItem by remember { mutableStateOf<InventoryItemEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showHeroEditDialog by remember { mutableStateOf(false) }

    val filteredItems = remember(items, selectedCategory, searchQuery) {
        items.filter { item ->
            val catMatch = when (selectedCategory) {
                "Все" -> true
                "Экипировано" -> item.isEquipped
                else -> item.category.equals(selectedCategory, ignoreCase = true)
            }
            val q = searchQuery.trim().lowercase()
            val queryMatch = q.isEmpty() ||
                item.name.lowercase().contains(q) ||
                item.description.lowercase().contains(q) ||
                item.bonusOrEffect.lowercase().contains(q)
            catMatch && queryMatch
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("inventory_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero Quick Sheet Card
            if (campaign != null) {
                item(key = "hero_sheet_card") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = campaign.heroName,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = campaign.heroArchetype,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { showHeroEditDialog = true },
                                    modifier = Modifier.testTag("edit_hero_stats_button")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Параметры")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = campaign.heroStatsSummary,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Search & Filter
            item(key = "inventory_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Поиск предмета или бонуса...") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inventory_search_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InventoryCategories.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category) }
                            )
                        }
                    }
                }
            }

            if (filteredItems.isEmpty()) {
                item(key = "inventory_empty") {
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
                                imageVector = Icons.Default.Backpack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "В этой категории пока пусто",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Добавьте снаряжение вручную кнопкой «+» или находите трофеи по ходу игры с Мастером!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    InventoryItemCard(
                        item = item,
                        onToggleEquip = { onToggleEquip(item) },
                        onAdjustQty = { delta -> onAdjustQty(item, delta) },
                        onUseInGame = { onUseInGame(item) },
                        onInspect = { onInspectWithMaster(item) },
                        onEdit = { editingItem = item },
                        onDelete = { onDeleteItem(item) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_inventory_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Добавить предмет в снаряжение")
        }
    }

    if (showAddDialog && campaign != null) {
        InventoryEditDialog(
            campaignId = campaign.id,
            existing = null,
            onDismiss = { showAddDialog = false },
            onSave = {
                onSaveItem(it)
                showAddDialog = false
            }
        )
    }

    editingItem?.let { current ->
        InventoryEditDialog(
            campaignId = current.campaignId,
            existing = current,
            onDismiss = { editingItem = null },
            onSave = {
                onSaveItem(it)
                editingItem = null
            }
        )
    }

    if (showHeroEditDialog && campaign != null) {
        HeroSheetEditDialog(
            campaign = campaign,
            onDismiss = { showHeroEditDialog = false },
            onSave = {
                onUpdateHeroSheet(it)
                showHeroEditDialog = false
            }
        )
    }
}

@Composable
private fun InventoryItemCard(
    item: InventoryItemEntity,
    onToggleEquip: () -> Unit,
    onAdjustQty: (Int) -> Unit,
    onUseInGame: () -> Unit,
    onInspect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (item.isEquipped) {
                MaterialTheme.colorScheme.surfaceContainerHighest
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (item.isEquipped) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Экипировано",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = "${item.category} • ${item.rarityOrTier}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Quantity Stepper
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onAdjustQty(-1) }, modifier = Modifier.size(36.dp)) {
                        Text("−", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "×${item.quantity}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(onClick = { onAdjustQty(1) }, modifier = Modifier.size(36.dp)) {
                        Text("+", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            if (item.bonusOrEffect.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = item.bonusOrEffect,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (item.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onUseInGame,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("use_item_${item.id}")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (item.category == "Расходники") "Использовать" else "Применить")
                }

                AssistChip(
                    onClick = onToggleEquip,
                    label = { Text(if (item.isEquipped) "Снять" else "Экипировать") },
                    leadingIcon = {
                        Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )

                AssistChip(
                    onClick = onInspect,
                    label = { Text("Осмотреть") },
                    leadingIcon = {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )

                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Редактировать предмет")
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Удалить предмет",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun InventoryEditDialog(
    campaignId: Long,
    existing: InventoryItemEntity?,
    onDismiss: () -> Unit,
    onSave: (InventoryItemEntity) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var category by remember { mutableStateOf(existing?.category ?: "Оружие") }
    var quantityStr by remember { mutableStateOf((existing?.quantity ?: 1).toString()) }
    var rarity by remember { mutableStateOf(existing?.rarityOrTier ?: "Обычный") }
    var bonus by remember { mutableStateOf(existing?.bonusOrEffect.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    var equipped by remember { mutableStateOf(existing?.isEquipped ?: false) }

    val selectableCats = listOf("Оружие", "Броня", "Расходники", "Артефакты", "Ресурсы", "Прочее")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Новый предмет" else "Редактировать предмет") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название предмета") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    selectableCats.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantityStr,
                        onValueChange = { quantityStr = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Кол-во") },
                        singleLine = true,
                        modifier = Modifier.weight(0.4f)
                    )
                    OutlinedTextField(
                        value = rarity,
                        onValueChange = { rarity = it },
                        label = { Text("Редкость / Ранг") },
                        singleLine = true,
                        modifier = Modifier.weight(0.6f)
                    )
                }
                OutlinedTextField(
                    value = bonus,
                    onValueChange = { bonus = it },
                    label = { Text("Урон / бонус (напр. 1d8 + 2)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание и свойства") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Экипировано сейчас", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = equipped, onCheckedChange = { equipped = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            InventoryItemEntity(
                                id = existing?.id ?: 0L,
                                campaignId = campaignId,
                                name = name.trim(),
                                category = category,
                                quantity = (quantityStr.toIntOrNull() ?: 1).coerceAtLeast(1),
                                rarityOrTier = rarity.ifBlank { "Обычный" },
                                bonusOrEffect = bonus.trim(),
                                description = description.trim(),
                                isEquipped = equipped
                            )
                        )
                    }
                },
                enabled = name.isNotBlank()
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
private fun HeroSheetEditDialog(
    campaign: CampaignEntity,
    onDismiss: () -> Unit,
    onSave: (CampaignEntity) -> Unit
) {
    var heroName by remember { mutableStateOf(campaign.heroName) }
    var archetype by remember { mutableStateOf(campaign.heroArchetype) }
    var stats by remember { mutableStateOf(campaign.heroStatsSummary) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Лист персонажа") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = heroName,
                    onValueChange = { heroName = it },
                    label = { Text("Имя героя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = archetype,
                    onValueChange = { archetype = it },
                    label = { Text("Класс / Архетип / Уровень") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = stats,
                    onValueChange = { stats = it },
                    label = { Text("Здоровье, защита и характеристики") },
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
                            heroName = heroName.ifBlank { campaign.heroName },
                            heroArchetype = archetype.ifBlank { campaign.heroArchetype },
                            heroStatsSummary = stats.ifBlank { campaign.heroStatsSummary }
                        )
                    )
                }
            ) {
                Text("Обновить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
