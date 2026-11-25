package com.example.data.repository

import com.example.ai.AiJournalSuggestion
import com.example.ai.AiLootChange
import com.example.ai.AiWorldStateDelta
import com.example.ai.GeneratedWorldDraft
import com.example.ai.HiddenPlotPlan
import com.example.data.local.CampaignEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.CustomSpellEntity
import com.example.data.local.InventoryItemEntity
import com.example.data.local.JournalEntryEntity
import com.example.data.local.KnowledgeChunkEntity
import com.example.data.local.QaMessageEntity
import com.example.data.local.RpgDao
import com.example.data.local.SettingPreset
import com.example.data.local.WorldEntity
import com.example.data.local.WorldLoreEntity
import com.example.data.local.WorldSettingPresets
import com.example.data.local.normalizeTagsCsv
import com.example.data.local.serializeGroundingSources
import com.example.data.transfer.FullCampaignSnapshot
import com.example.data.transfer.JsonPackageKind
import com.example.data.transfer.JsonTransferSelection
import com.example.data.transfer.ParsedJsonTransferBundle
import com.example.data.transfer.estimateTokenCount
import kotlinx.coroutines.flow.Flow

enum class MemoryHealthLevel {
    NORMAL,
    WARNING,
    CRITICAL_FULL
}

data class MemoryUsageStats(
    val usedTokens: Int = 0,
    val maxTokens: Int = 8000,
    val usagePercent: Int = 0,
    val activeMessagesCount: Int = 0,
    val archivedMessagesCount: Int = 0,
    val compressedChaptersCount: Int = 0,
    val healthLevel: MemoryHealthLevel = MemoryHealthLevel.NORMAL
)

data class SelectiveImportOutcome(
    val message: String,
    val createdOrTargetCampaignId: Long? = null,
    val createdWorldId: Long? = null
)

class RpgRepository(private val dao: RpgDao) {

    // --- Worlds Library Flows ---
    fun observeWorlds(): Flow<List<WorldEntity>> = dao.observeWorlds()

    fun observeAllWorldLore(): Flow<List<WorldLoreEntity>> = dao.observeAllWorldLore()

    fun observeWorldLore(worldId: Long): Flow<List<WorldLoreEntity>> = dao.observeWorldLore(worldId)

    // --- Campaigns & Session Flows ---
    fun observeCampaigns(): Flow<List<CampaignEntity>> = dao.observeCampaigns()

    fun observeCampaign(id: Long): Flow<CampaignEntity?> = dao.observeCampaignById(id)

    fun observeMessages(campaignId: Long): Flow<List<ChatMessageEntity>> = dao.observeMessages(campaignId)

    fun observeQaMessages(campaignId: Long): Flow<List<QaMessageEntity>> = dao.observeQaMessages(campaignId)

    fun observeInventory(campaignId: Long): Flow<List<InventoryItemEntity>> = dao.observeInventory(campaignId)

    fun observeJournal(campaignId: Long): Flow<List<JournalEntryEntity>> = dao.observeJournal(campaignId)

    fun observeSpells(campaignId: Long): Flow<List<CustomSpellEntity>> = dao.observeSpells(campaignId)

    fun observeKnowledge(campaignId: Long): Flow<List<KnowledgeChunkEntity>> = dao.observeKnowledgeChunks(campaignId)

    suspend fun ensureStarterDataExists(defaultMaxTokens: Int): Long {
        var existingWorlds = dao.getAllWorldsOnce()
        if (existingWorlds.isEmpty()) {
            val now = System.currentTimeMillis()
            WorldSettingPresets.forEachIndexed { idx, preset ->
                val worldId = dao.insertWorld(
                    WorldEntity(
                        name = preset.title,
                        originType = "PRESET",
                        franchiseSource = "",
                        genre = preset.genre,
                        worldDescription = preset.worldDescription,
                        diceSystem = preset.diceSystem,
                        magicOrTechSystem = preset.magicOrTechSystem,
                        narrativeTone = preset.narrativeTone,
                        customRules = preset.customRules,
                        suggestedArchetypesCsv = preset.suggestedArchetypes.joinToString("|"),
                        defaultOpeningHook = preset.openingHook,
                        createdAt = now - idx * 1000L,
                        updatedAt = now - idx * 1000L
                    )
                )
                dao.insertWorldLore(
                    WorldLoreEntity(
                        worldId = worldId,
                        sourceTitle = "Базовый кодекс",
                        sectionTitle = "Устройство мира «${preset.title}»",
                        keywordsCsv = "мир,основы,правила",
                        content = "${preset.worldDescription}\nСистема способностей: ${preset.magicOrTechSystem}.\nОсобые законы: ${preset.customRules}",
                        isAlwaysActive = true
                    )
                )
            }
            existingWorlds = dao.getAllWorldsOnce()
        }

        val existingCampaigns = dao.getAllCampaignsOnce()
        if (existingCampaigns.isNotEmpty()) {
            return existingCampaigns.first().id
        }

        val starterWorld = existingWorlds.first()
        val firstPreset = WorldSettingPresets.first()
        return startNewGameInWorld(
            world = starterWorld,
            campaignTitle = "${starterWorld.name}: Первая глава",
            heroName = firstPreset.defaultHeroName,
            heroArchetype = firstPreset.defaultHeroArchetype,
            heroStatsSummary = firstPreset.defaultHeroStats,
            heroBackstory = "Странник, откликнувшийся на зов приключений.",
            customOpeningScene = starterWorld.defaultOpeningHook,
            maxTokens = defaultMaxTokens,
            useDiceAndStats = firstPreset.defaultUseDice,
            useInventory = firstPreset.defaultUseInventory,
            useSpells = firstPreset.defaultUseSpells,
            seedPresetId = firstPreset.id
        )
    }

    suspend fun saveGeneratedWorldToLibrary(
        draft: GeneratedWorldDraft,
        originType: String,
        franchiseSource: String = ""
    ): Long {
        val now = System.currentTimeMillis()
        val worldId = dao.insertWorld(
            WorldEntity(
                name = draft.name.ifBlank { "Новый мир" },
                originType = originType,
                franchiseSource = franchiseSource.trim(),
                genre = draft.genre.ifBlank { "Ролевой сеттинг" },
                worldDescription = draft.worldDescription,
                diceSystem = draft.diceSystem.ifBlank { "d20 + модификатор против КС" },
                magicOrTechSystem = draft.magicOrTechSystem,
                narrativeTone = draft.narrativeTone,
                customRules = draft.customRules,
                suggestedArchetypesCsv = draft.suggestedArchetypes.joinToString("|"),
                defaultOpeningHook = draft.defaultOpeningHook,
                groundingSourcesJson = serializeGroundingSources(draft.groundingSources),
                createdAt = now,
                updatedAt = now
            )
        )

        if (draft.loreSections.isNotEmpty()) {
            val sourceLabel = if (originType == "EXISTING_UNIVERSE" && franchiseSource.isNotBlank()) {
                "Канон: $franchiseSource"
            } else {
                "ИИ-Демиург"
            }
            val loreEntities = draft.loreSections.mapIndexed { idx, sec ->
                WorldLoreEntity(
                    worldId = worldId,
                    sourceTitle = sourceLabel,
                    sectionTitle = sec.sectionTitle,
                    keywordsCsv = sec.keywordsCsv,
                    content = sec.content,
                    isAlwaysActive = sec.isAlwaysActive || idx == 0,
                    createdAt = now + idx
                )
            }
            dao.insertWorldLoreList(loreEntities)
        }
        return worldId
    }

    suspend fun saveManualWorld(world: WorldEntity): Long {
        val clean = world.copy(updatedAt = System.currentTimeMillis())
        return if (clean.id == 0L) {
            dao.insertWorld(clean)
        } else {
            dao.updateWorld(clean)
            clean.id
        }
    }

    suspend fun deleteWorld(world: WorldEntity) = dao.deleteWorld(world)

    suspend fun getWorldById(worldId: Long): WorldEntity? = dao.getWorldById(worldId)

    suspend fun getWorldLoreOnce(worldId: Long): List<WorldLoreEntity> = dao.getWorldLoreOnce(worldId)

    suspend fun saveWorldLore(item: WorldLoreEntity) {
        val clean = item.copy(keywordsCsv = normalizeTagsCsv(item.keywordsCsv))
        if (clean.id == 0L) dao.insertWorldLore(clean) else dao.updateWorldLore(clean)
    }

    suspend fun insertWorldLoreList(items: List<WorldLoreEntity>) = dao.insertWorldLoreList(items)

    suspend fun deleteWorldLore(item: WorldLoreEntity) = dao.deleteWorldLore(item)

    suspend fun startNewGameInWorld(
        world: WorldEntity,
        campaignTitle: String,
        heroName: String,
        heroArchetype: String,
        heroStatsSummary: String,
        heroBackstory: String,
        customOpeningScene: String,
        maxTokens: Int,
        useDiceAndStats: Boolean = true,
        useInventory: Boolean = true,
        useSpells: Boolean = true,
        seedPresetId: String? = null
    ): Long {
        val now = System.currentTimeMillis()
        val cleanHeroName = heroName.trim().ifBlank { "Странник" }
        val cleanTitle = campaignTitle.trim().ifBlank { "${world.name} — $cleanHeroName" }
        val defaultStatsFallback = if (useDiceAndStats) {
            "ОЗ 25/25 | Защита 14"
        } else {
            "Словесный образ: живой характер и личная мотивация"
        }
        val starterHiddenNpcNote = if (seedPresetId == "dnd_fantasy") {
            "Старый картограф Северин скрывает, что печать Разлома уже дала трещину по вине его бывшего ученика; наёмники у камина НЕ знают содержания карты в тубусе и не должны говорить о Шпиле, пока не подслушают разговор."
        } else ""
        val starterHiddenArc = if (seedPresetId == "dnd_fantasy") {
            "Глава 1: Переход через Туманный тракт к Внешнему Шпилю, попытка агентов Теневой Гильдии перехватить тубус ночью на привале, открытие древнего святилища у Разлома."
        } else ""

        val campaignId = dao.insertCampaign(
            CampaignEntity(
                worldId = world.id,
                title = cleanTitle,
                heroName = cleanHeroName,
                heroArchetype = heroArchetype.trim().ifBlank {
                    world.archetypesList().firstOrNull() ?: "Искатель приключений"
                },
                heroStatsSummary = heroStatsSummary.trim().ifBlank { defaultStatsFallback },
                heroBackstory = heroBackstory.trim(),
                settingName = world.name,
                settingGenre = world.genre,
                diceSystem = world.diceSystem,
                magicOrTechSystem = world.magicOrTechSystem,
                narrativeTone = world.narrativeTone,
                customRules = world.customRules,
                useDiceAndStats = useDiceAndStats,
                useInventory = useInventory,
                useSpells = useSpells,
                hiddenPlotArc = starterHiddenArc,
                hiddenNpcKnowledge = starterHiddenNpcNote,
                hiddenPlotTriggers = if (starterHiddenArc.isNotBlank()) {
                    "Если герой осмотрит сургуч или расспросит Северина об ожоге на ладони — картограф признается о провалившейся первой экспедиции."
                } else "",
                hiddenPlotUpdatedAt = if (starterHiddenArc.isNotBlank()) now else 0L,
                maxContextTokens = maxTokens,
                createdAt = now,
                updatedAt = now
            )
        )

        val openingText = customOpeningScene.trim().ifBlank {
            world.defaultOpeningHook.ifBlank {
                "Приключение героя $cleanHeroName в мире «${world.name}» начинается! ${world.worldDescription}\n\nОпишите вашу первую сцену или действие."
            }
        }

        dao.insertMessage(
            ChatMessageEntity(
                campaignId = campaignId,
                senderRole = "GM",
                content = openingText,
                estimatedTokens = estimateTokenCount(openingText),
                timestamp = now
            )
        )

        if (seedPresetId != null) {
            seedPresetStarterAssets(campaignId, seedPresetId, useInventory, useSpells)
        } else {
            dao.insertJournalEntry(
                JournalEntryEntity(
                    campaignId = campaignId,
                    title = world.name,
                    entryType = "Локация",
                    summary = world.worldDescription.ifBlank { "Мир вашего нового приключения (${world.genre})." },
                    details = if (heroBackstory.isNotBlank()) "Предыстория героя: $heroBackstory" else "",
                    tagsCsv = normalizeTagsCsv("мир,старт,${world.genre.substringBefore(" ")}"),
                    statusOrDisposition = "Активный мир",
                    isPinned = true
                )
            )
        }

        return campaignId
    }

    suspend fun createCampaignFromPreset(
        preset: SettingPreset,
        customTitle: String? = null,
        customHeroName: String? = null,
        customHeroArchetype: String? = null,
        customHeroStats: String? = null,
        customRulesOverride: String? = null,
        maxTokens: Int = 8000
    ): Long {
        val worldId = dao.insertWorld(
            WorldEntity(
                name = preset.title,
                originType = "PRESET",
                genre = preset.genre,
                worldDescription = preset.worldDescription,
                diceSystem = preset.diceSystem,
                magicOrTechSystem = preset.magicOrTechSystem,
                narrativeTone = preset.narrativeTone,
                customRules = customRulesOverride ?: preset.customRules,
                suggestedArchetypesCsv = preset.suggestedArchetypes.joinToString("|"),
                defaultOpeningHook = preset.openingHook
            )
        )
        val world = dao.getWorldById(worldId)!!
        return startNewGameInWorld(
            world = world,
            campaignTitle = customTitle ?: preset.title,
            heroName = customHeroName ?: preset.defaultHeroName,
            heroArchetype = customHeroArchetype ?: preset.defaultHeroArchetype,
            heroStatsSummary = customHeroStats ?: preset.defaultHeroStats,
            heroBackstory = "",
            customOpeningScene = preset.openingHook,
            maxTokens = maxTokens,
            useDiceAndStats = preset.defaultUseDice,
            useInventory = preset.defaultUseInventory,
            useSpells = preset.defaultUseSpells,
            seedPresetId = preset.id
        )
    }

    private suspend fun seedPresetStarterAssets(
        campaignId: Long,
        presetId: String,
        useInventory: Boolean,
        useSpells: Boolean
    ) {
        if (presetId == "dnd_fantasy") {
            if (useInventory) {
                dao.insertInventoryItem(
                    InventoryItemEntity(
                        campaignId = campaignId,
                        name = "Рунический клинок Пепельной Стражи",
                        category = "Оружие",
                        quantity = 1,
                        rarityOrTier = "Необычный",
                        bonusOrEffect = "1d8 + 3 рубящего урона (+1d4 огнём по нежити)",
                        description = "Выкованный в старом Эльдхейме полуторный меч с тлеющей руной у гарды.",
                        isEquipped = true
                    )
                )
                dao.insertInventoryItem(
                    InventoryItemEntity(
                        campaignId = campaignId,
                        name = "Эликсир багрового вереска",
                        category = "Расходники",
                        quantity = 2,
                        rarityOrTier = "Обычный",
                        bonusOrEffect = "Восстанавливает 2d4 + 2 ОЗ",
                        description = "Согревающее алхимическое зелье лечения.",
                        isEquipped = false
                    )
                )
                dao.insertInventoryItem(
                    InventoryItemEntity(
                        campaignId = campaignId,
                        name = "Серебряные кроны Эльдхейма",
                        category = "Ресурсы",
                        quantity = 45,
                        rarityOrTier = "Валюта",
                        bonusOrEffect = "Местная монета",
                        description = "Кошель с чеканными монетами для торговли и подкупа.",
                        isEquipped = false
                    )
                )
            }
            dao.insertJournalEntry(
                JournalEntryEntity(
                    campaignId = campaignId,
                    title = "Северин, старый картограф",
                    entryType = "НПС",
                    summary = "Хранитель карт приграничья, ищущий проводника к Запретному Разлому до новолуния.",
                    details = "Встречен в таверне «Расколотый Компас». Скрывает ожог от арканной печати на ладони.",
                    tagsCsv = "наниматель,картограф,квест_активен,эльдхейм",
                    statusOrDisposition = "Доверяет",
                    isPinned = true
                )
            )
            dao.insertJournalEntry(
                JournalEntryEntity(
                    campaignId = campaignId,
                    title = "Таверна «Расколотый Компас»",
                    entryType = "Локация",
                    summary = "Укреплённый постоялый двор на перекрёстке трактов у Туманных гор.",
                    details = "Здесь собираются охотники за реликвиями, наёмники и беглецы с севера.",
                    tagsCsv = "таверна,приграничье,безопасная_зона",
                    statusOrDisposition = "Текущая локация",
                    isPinned = false
                )
            )
            if (useSpells) {
                dao.insertSpell(
                    CustomSpellEntity(
                        campaignId = campaignId,
                        name = "Астральное копьё Зари",
                        rankOrLevel = "1 круг",
                        schoolOrType = "Воплощение",
                        resourceCost = "1 ячейка",
                        castTime = "1 действие",
                        rangeInfo = "24 метра",
                        durationInfo = "Мгновенно",
                        diceFormula = "2d8 + 3",
                        description = "Сгусток золотого звёздного света пронзает цель и подсвечивает её контуры в темноте до конца следующего хода.",
                        tagsCsv = "свет,урон,авторское",
                        isPrepared = true
                    )
                )
            }
        }
    }

    fun calculateMemoryStats(
        campaign: CampaignEntity?,
        allMessages: List<ChatMessageEntity>,
        inventory: List<InventoryItemEntity>,
        journal: List<JournalEntryEntity>,
        spells: List<CustomSpellEntity>,
        worldLore: List<WorldLoreEntity>,
        knowledge: List<KnowledgeChunkEntity>,
        warningThresholdPercent: Int
    ): MemoryUsageStats {
        if (campaign == null) return MemoryUsageStats()

        val hiddenPlotTokens = estimateTokenCount(
            "${campaign.hiddenPlotArc} ${campaign.hiddenNpcKnowledge} ${campaign.hiddenPlotTriggers}"
        )
        val systemBaseTokens = estimateTokenCount(
            "${campaign.settingName} ${campaign.settingGenre} ${campaign.diceSystem} " +
                "${campaign.magicOrTechSystem} ${campaign.customRules} ${campaign.heroStatsSummary} ${campaign.heroBackstory}"
        ) + hiddenPlotTokens + 280

        val summaryTokens = estimateTokenCount(campaign.memorySummary)
        val activeMessages = allMessages.filter { !it.isArchivedInSummary }
        val archivedCount = allMessages.size - activeMessages.size

        val chatTokens = activeMessages.sumOf {
            if (it.estimatedTokens > 0) it.estimatedTokens else estimateTokenCount(it.content)
        }
        val invTokens = if (campaign.useInventory) {
            inventory.take(15).sumOf { estimateTokenCount("${it.name} ${it.bonusOrEffect}") }
        } else 0
        val journalTokens = journal.take(10).sumOf { estimateTokenCount("${it.title} ${it.summary}") }
        val spellTokens = if (campaign.useSpells) {
            spells.filter { it.isPrepared }.take(8).sumOf { estimateTokenCount("${it.name} ${it.diceFormula}") }
        } else 0
        val worldLoreTokens = worldLore.filter { it.isAlwaysActive }.take(4).sumOf { estimateTokenCount(it.content) }
        val loreTokens = knowledge.filter { it.isAlwaysActive }.take(4).sumOf { estimateTokenCount(it.content) }

        val totalUsed = systemBaseTokens + summaryTokens + chatTokens + invTokens +
            journalTokens + spellTokens + worldLoreTokens + loreTokens
        val maxBudget = campaign.maxContextTokens.coerceAtLeast(2000)
        val percent = ((totalUsed.toDouble() / maxBudget.toDouble()) * 100.0).toInt().coerceIn(0, 100)

        val health = when {
            percent >= 95 -> MemoryHealthLevel.CRITICAL_FULL
            percent >= warningThresholdPercent -> MemoryHealthLevel.WARNING
            else -> MemoryHealthLevel.NORMAL
        }

        return MemoryUsageStats(
            usedTokens = totalUsed,
            maxTokens = maxBudget,
            usagePercent = percent,
            activeMessagesCount = activeMessages.size,
            archivedMessagesCount = archivedCount,
            compressedChaptersCount = campaign.compressedChaptersCount,
            healthLevel = health
        )
    }

    fun selectRelevantWorldLore(
        worldLoreList: List<WorldLoreEntity>,
        queryText: String,
        maxChunks: Int = 5
    ): List<WorldLoreEntity> {
        if (worldLoreList.isEmpty()) return emptyList()
        val always = worldLoreList.filter { it.isAlwaysActive }
        val queryWords = Regex("""[\p{L}\p{N}_-]{4,}""")
            .findAll(queryText.lowercase())
            .map { it.value }
            .toSet()

        val scored = worldLoreList
            .filter { !it.isAlwaysActive }
            .map { item ->
                val keys = item.keywordsList().map { it.lowercase() }
                var score = 0
                for (qw in queryWords) {
                    if (keys.any { it.contains(qw) || qw.contains(it) }) score += 3
                    if (item.sectionTitle.lowercase().contains(qw)) score += 2
                    if (item.content.lowercase().contains(qw)) score += 1
                }
                item to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }

        return (always + scored).distinctBy { it.id }.take(maxChunks)
    }

    fun selectRelevantLore(
        allChunks: List<KnowledgeChunkEntity>,
        queryText: String,
        maxChunks: Int = 4
    ): List<KnowledgeChunkEntity> {
        if (allChunks.isEmpty()) return emptyList()
        val always = allChunks.filter { it.isAlwaysActive }
        val queryWords = Regex("""[\p{L}\p{N}_-]{4,}""")
            .findAll(queryText.lowercase())
            .map { it.value }
            .toSet()

        val scored = allChunks
            .filter { !it.isAlwaysActive }
            .map { chunk ->
                val keys = chunk.keywordsList().map { it.lowercase() }
                val titleWords = chunk.sectionTitle.lowercase()
                var score = 0
                for (qw in queryWords) {
                    if (keys.any { it.contains(qw) || qw.contains(it) }) score += 3
                    if (titleWords.contains(qw)) score += 2
                    if (chunk.content.lowercase().contains(qw)) score += 1
                }
                chunk to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }

        return (always + scored).distinctBy { it.id }.take(maxChunks)
    }

    suspend fun addMessage(
        campaignId: Long,
        senderRole: String,
        content: String,
        diceRollInfo: String? = null,
        groundingSourcesJson: String = "[]"
    ): Long {
        val now = System.currentTimeMillis()
        val id = dao.insertMessage(
            ChatMessageEntity(
                campaignId = campaignId,
                senderRole = senderRole,
                content = content,
                diceRollInfo = diceRollInfo,
                groundingSourcesJson = groundingSourcesJson,
                estimatedTokens = estimateTokenCount(content),
                timestamp = now
            )
        )
        dao.getCampaignById(campaignId)?.let { camp ->
            dao.updateCampaign(camp.copy(updatedAt = now))
        }
        return id
    }

    suspend fun addQaMessage(
        campaignId: Long,
        senderRole: String,
        content: String,
        groundingSourcesJson: String = "[]"
    ): Long {
        return dao.insertQaMessage(
            QaMessageEntity(
                campaignId = campaignId,
                senderRole = senderRole,
                content = content,
                groundingSourcesJson = groundingSourcesJson,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearQaHistory(campaignId: Long) = dao.clearQaMessagesForCampaign(campaignId)

    suspend fun updateHiddenPlotPlan(campaignId: Long, plan: HiddenPlotPlan) {
        val camp = dao.getCampaignById(campaignId) ?: return
        val now = System.currentTimeMillis()
        dao.updateCampaign(
            camp.copy(
                hiddenPlotArc = plan.plotArc.trim(),
                hiddenNpcKnowledge = plan.npcKnowledgeBoundaries.trim(),
                hiddenPlotTriggers = plan.triggersAndConsequences.trim(),
                hiddenPlotUpdatedAt = if (plan.isNotEmpty()) now else 0L,
                updatedAt = now
            )
        )
    }

    suspend fun applyMemoryCompression(
        campaignId: Long,
        newSummary: String,
        archivedMessageIds: List<Long>
    ) {
        if (archivedMessageIds.isNotEmpty()) {
            dao.markMessagesArchived(archivedMessageIds)
        }
        val camp = dao.getCampaignById(campaignId) ?: return
        dao.updateCampaign(
            camp.copy(
                memorySummary = newSummary.trim(),
                compressedChaptersCount = camp.compressedChaptersCount + 1,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun applyAiWorldDelta(campaignId: Long, delta: AiWorldStateDelta): Int {
        var changesApplied = 0
        val existingInv = dao.getInventoryOnce(campaignId).toMutableList()
        val existingJournal = dao.getJournalOnce(campaignId).toMutableList()

        for (loot: AiLootChange in delta.lootChanges) {
            val cleanName = loot.name.trim()
            if (cleanName.isEmpty()) continue
            val match = existingInv.firstOrNull { it.name.equals(cleanName, ignoreCase = true) }
            when (loot.action.uppercase()) {
                "REMOVE", "CONSUME" -> {
                    if (match != null) {
                        val removeQty = loot.quantity.coerceAtLeast(1)
                        val remaining = match.quantity - removeQty
                        if (remaining <= 0) {
                            dao.deleteInventoryItem(match)
                            existingInv.remove(match)
                        } else {
                            dao.updateInventoryItem(match.copy(quantity = remaining, updatedAt = System.currentTimeMillis()))
                        }
                        changesApplied++
                    }
                }
                else -> {
                    val addQty = loot.quantity.coerceAtLeast(1)
                    if (match != null) {
                        dao.updateInventoryItem(
                            match.copy(
                                quantity = match.quantity + addQty,
                                bonusOrEffect = loot.effect.ifBlank { match.bonusOrEffect },
                                description = loot.description.ifBlank { match.description },
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    } else {
                        dao.insertInventoryItem(
                            InventoryItemEntity(
                                campaignId = campaignId,
                                name = cleanName,
                                category = loot.category.ifBlank { "Прочее" },
                                quantity = addQty,
                                rarityOrTier = loot.rarity.ifBlank { "Обычный" },
                                bonusOrEffect = loot.effect,
                                description = loot.description,
                                isEquipped = false
                            )
                        )
                    }
                    changesApplied++
                }
            }
        }

        for (j: AiJournalSuggestion in delta.journalUpdates) {
            val cleanTitle = j.title.trim()
            if (cleanTitle.isEmpty()) continue
            val match = existingJournal.firstOrNull { it.title.equals(cleanTitle, ignoreCase = true) }
            val normalizedNewTags = normalizeTagsCsv(j.tags.joinToString(","))
            if (match != null) {
                val mergedTags = normalizeTagsCsv("${match.tagsCsv},$normalizedNewTags")
                val updatedDetails = if (j.summary.isNotBlank() && !match.details.contains(j.summary)) {
                    listOf(match.details, j.summary).filter { it.isNotBlank() }.joinToString("\n\n")
                } else match.details
                dao.updateJournalEntry(
                    match.copy(
                        summary = j.summary.ifBlank { match.summary },
                        details = updatedDetails,
                        tagsCsv = mergedTags,
                        statusOrDisposition = j.status.ifBlank { match.statusOrDisposition },
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else {
                dao.insertJournalEntry(
                    JournalEntryEntity(
                        campaignId = campaignId,
                        title = cleanTitle,
                        entryType = j.entryType.ifBlank { "Заметка" },
                        summary = j.summary,
                        details = "",
                        tagsCsv = normalizedNewTags,
                        statusOrDisposition = j.status
                    )
                )
            }
            changesApplied++
        }

        delta.updatedHiddenPlot?.let { newPlan ->
            if (newPlan.isNotEmpty()) {
                updateHiddenPlotPlan(campaignId, newPlan)
            }
        }

        return changesApplied
    }

    suspend fun updateCampaign(campaign: CampaignEntity) =
        dao.updateCampaign(campaign.copy(updatedAt = System.currentTimeMillis()))

    suspend fun deleteCampaign(campaign: CampaignEntity) = dao.deleteCampaign(campaign)

    suspend fun getAllCampaignsOnce(): List<CampaignEntity> = dao.getAllCampaignsOnce()

    suspend fun saveInventoryItem(item: InventoryItemEntity) {
        if (item.id == 0L) dao.insertInventoryItem(item) else dao.updateInventoryItem(item)
    }

    suspend fun deleteInventoryItem(item: InventoryItemEntity) = dao.deleteInventoryItem(item)

    suspend fun saveJournalEntry(entry: JournalEntryEntity) {
        val clean = entry.copy(tagsCsv = normalizeTagsCsv(entry.tagsCsv), updatedAt = System.currentTimeMillis())
        if (clean.id == 0L) dao.insertJournalEntry(clean) else dao.updateJournalEntry(clean)
    }

    suspend fun deleteJournalEntry(entry: JournalEntryEntity) = dao.deleteJournalEntry(entry)

    suspend fun saveSpell(spell: CustomSpellEntity) {
        val clean = spell.copy(tagsCsv = normalizeTagsCsv(spell.tagsCsv))
        if (clean.id == 0L) dao.insertSpell(clean) else dao.updateSpell(clean)
    }

    suspend fun deleteSpell(spell: CustomSpellEntity) = dao.deleteSpell(spell)

    suspend fun insertKnowledgeChunks(chunks: List<KnowledgeChunkEntity>) = dao.insertKnowledgeChunks(chunks)

    suspend fun saveKnowledgeChunk(chunk: KnowledgeChunkEntity) {
        val clean = chunk.copy(keywordsCsv = normalizeTagsCsv(chunk.keywordsCsv))
        if (clean.id == 0L) dao.insertKnowledgeChunk(clean) else dao.updateKnowledgeChunk(clean)
    }

    suspend fun deleteKnowledgeChunk(chunk: KnowledgeChunkEntity) = dao.deleteKnowledgeChunk(chunk)

    suspend fun buildSnapshot(campaignId: Long): FullCampaignSnapshot? {
        val camp = dao.getCampaignById(campaignId) ?: return null
        val world = if (camp.worldId > 0L) dao.getWorldById(camp.worldId) else null
        val worldLore = if (world != null) dao.getWorldLoreOnce(world.id) else emptyList()
        return FullCampaignSnapshot(
            campaign = camp,
            messages = dao.getMessagesOnce(campaignId),
            inventory = dao.getInventoryOnce(campaignId),
            journal = dao.getJournalOnce(campaignId),
            spells = dao.getSpellsOnce(campaignId),
            knowledge = dao.getKnowledgeChunksOnce(campaignId),
            qaMessages = dao.getQaMessagesOnce(campaignId),
            world = world,
            worldLore = worldLore
        )
    }

    suspend fun importCampaignSnapshot(snapshot: FullCampaignSnapshot): Long {
        var linkedWorldId = snapshot.campaign.worldId
        if (snapshot.world != null) {
            linkedWorldId = dao.insertWorld(snapshot.world.copy(id = 0))
            if (snapshot.worldLore.isNotEmpty()) {
                dao.insertWorldLoreList(snapshot.worldLore.map { it.copy(id = 0, worldId = linkedWorldId) })
            }
        }
        val newCampaignId = dao.insertCampaign(snapshot.campaign.copy(id = 0, worldId = linkedWorldId))
        snapshot.messages.forEach { dao.insertMessage(it.copy(id = 0, campaignId = newCampaignId)) }
        if (snapshot.qaMessages.isNotEmpty()) {
            dao.insertQaMessages(snapshot.qaMessages.map { it.copy(id = 0, campaignId = newCampaignId) })
        }
        snapshot.inventory.forEach { dao.insertInventoryItem(it.copy(id = 0, campaignId = newCampaignId)) }
        snapshot.journal.forEach { dao.insertJournalEntry(it.copy(id = 0, campaignId = newCampaignId)) }
        snapshot.spells.forEach { dao.insertSpell(it.copy(id = 0, campaignId = newCampaignId)) }
        if (snapshot.knowledge.isNotEmpty()) {
            dao.insertKnowledgeChunks(snapshot.knowledge.map { it.copy(id = 0, campaignId = newCampaignId) })
        }
        return newCampaignId
    }

    /**
     * Applies a parsed JSON transfer bundle using the user's selected checkboxes,
     * either creating a standalone World / new Campaign OR merging into the active Campaign.
     */
    suspend fun applySelectiveJsonImport(
        bundle: ParsedJsonTransferBundle,
        selection: JsonTransferSelection,
        mergeIntoActiveCampaignId: Long? = null
    ): SelectiveImportOutcome {
        val now = System.currentTimeMillis()
        var savedWorldId: Long? = null

        if (selection.includeWorld && bundle.world != null) {
            val wId = dao.insertWorld(bundle.world.copy(id = 0, createdAt = now, updatedAt = now))
            if (bundle.worldLore.isNotEmpty()) {
                dao.insertWorldLoreList(bundle.worldLore.map { it.copy(id = 0, worldId = wId) })
            }
            savedWorldId = wId
        }

        // If user only wanted to import a World into the Worlds Library (and no campaign/hero/chat selected)
        val hasAnyCampaignContentSelected =
            (selection.includeCharacter && bundle.hasCharacterSection) ||
                (selection.includeMessages && bundle.messages.isNotEmpty()) ||
                (selection.includeQaMessages && bundle.qaMessages.isNotEmpty()) ||
                (selection.includeInventory && bundle.inventory.isNotEmpty()) ||
                (selection.includeJournal && bundle.journal.isNotEmpty()) ||
                (selection.includeSpells && bundle.spells.isNotEmpty()) ||
                (selection.includeKnowledge && bundle.knowledge.isNotEmpty()) ||
                (selection.includeHiddenPlot && bundle.hasHiddenPlotSection)

        if (!hasAnyCampaignContentSelected || bundle.kind == JsonPackageKind.WORLD_ONLY) {
            val wName = bundle.world?.name ?: "Импортированный мир"
            return SelectiveImportOutcome(
                message = "Мир «$wName» добавлен в вашу библиотеку миров!",
                createdWorldId = savedWorldId
            )
        }

        // Mode A: Merge selected sections into currently active campaign
        if (mergeIntoActiveCampaignId != null && mergeIntoActiveCampaignId > 0L) {
            val current = dao.getCampaignById(mergeIntoActiveCampaignId)
            if (current != null) {
                var updatedCamp = current
                val srcCamp = bundle.campaign
                if (selection.includeWorld && bundle.world != null && savedWorldId != null) {
                    updatedCamp = updatedCamp.copy(
                        worldId = savedWorldId,
                        settingName = bundle.world.name,
                        settingGenre = bundle.world.genre,
                        diceSystem = bundle.world.diceSystem,
                        magicOrTechSystem = bundle.world.magicOrTechSystem,
                        narrativeTone = bundle.world.narrativeTone,
                        customRules = bundle.world.customRules
                    )
                }
                if (selection.includeCharacter && srcCamp != null) {
                    updatedCamp = updatedCamp.copy(
                        heroName = srcCamp.heroName,
                        heroArchetype = srcCamp.heroArchetype,
                        heroStatsSummary = srcCamp.heroStatsSummary,
                        heroBackstory = srcCamp.heroBackstory,
                        useDiceAndStats = srcCamp.useDiceAndStats,
                        useInventory = srcCamp.useInventory,
                        useSpells = srcCamp.useSpells
                    )
                }
                if (selection.includeHiddenPlot && srcCamp != null) {
                    updatedCamp = updatedCamp.copy(
                        hiddenPlotArc = srcCamp.hiddenPlotArc,
                        hiddenNpcKnowledge = srcCamp.hiddenNpcKnowledge,
                        hiddenPlotTriggers = srcCamp.hiddenPlotTriggers,
                        hiddenPlotUpdatedAt = now
                    )
                }
                if (selection.includeMessages && srcCamp != null && srcCamp.memorySummary.isNotBlank()) {
                    updatedCamp = updatedCamp.copy(
                        memorySummary = listOf(updatedCamp.memorySummary, srcCamp.memorySummary)
                            .filter { it.isNotBlank() }
                            .joinToString("\n\n")
                    )
                }
                dao.updateCampaign(updatedCamp.copy(updatedAt = now))

                if (selection.includeMessages) {
                    bundle.messages.forEach { dao.insertMessage(it.copy(id = 0, campaignId = mergeIntoActiveCampaignId)) }
                }
                if (selection.includeQaMessages && bundle.qaMessages.isNotEmpty()) {
                    dao.insertQaMessages(bundle.qaMessages.map { it.copy(id = 0, campaignId = mergeIntoActiveCampaignId) })
                }
                if (selection.includeInventory) {
                    bundle.inventory.forEach { dao.insertInventoryItem(it.copy(id = 0, campaignId = mergeIntoActiveCampaignId)) }
                }
                if (selection.includeJournal) {
                    bundle.journal.forEach { dao.insertJournalEntry(it.copy(id = 0, campaignId = mergeIntoActiveCampaignId)) }
                }
                if (selection.includeSpells) {
                    bundle.spells.forEach { dao.insertSpell(it.copy(id = 0, campaignId = mergeIntoActiveCampaignId)) }
                }
                if (selection.includeKnowledge && bundle.knowledge.isNotEmpty()) {
                    dao.insertKnowledgeChunks(bundle.knowledge.map { it.copy(id = 0, campaignId = mergeIntoActiveCampaignId) })
                }

                return SelectiveImportOutcome(
                    message = "Выбранные разделы импортированы в текущую игру «${updatedCamp.title}»",
                    createdOrTargetCampaignId = mergeIntoActiveCampaignId,
                    createdWorldId = savedWorldId
                )
            }
        }

        // Mode B: Create a new Campaign from the selected sections
        val fallbackWorld = savedWorldId?.let { dao.getWorldById(it) } ?: dao.getAllWorldsOnce().firstOrNull()
        val src = bundle.campaign
        val baseCampaign = CampaignEntity(
            worldId = savedWorldId ?: fallbackWorld?.id ?: 0L,
            title = src?.title ?: "${fallbackWorld?.name ?: "Новая партия"} — ${src?.heroName ?: "Герой"}",
            heroName = if (selection.includeCharacter && src != null) src.heroName else "Странник",
            heroArchetype = if (selection.includeCharacter && src != null) src.heroArchetype else "Искатель приключений",
            heroStatsSummary = if (selection.includeCharacter && src != null) src.heroStatsSummary else "ОЗ 25/25",
            heroBackstory = if (selection.includeCharacter && src != null) src.heroBackstory else "",
            settingName = bundle.world?.name ?: src?.settingName ?: fallbackWorld?.name ?: "Авторский мир",
            settingGenre = bundle.world?.genre ?: src?.settingGenre ?: fallbackWorld?.genre ?: "Ролевой сеттинг",
            diceSystem = bundle.world?.diceSystem ?: src?.diceSystem ?: fallbackWorld?.diceSystem ?: "d20 + модификатор",
            magicOrTechSystem = bundle.world?.magicOrTechSystem ?: src?.magicOrTechSystem ?: fallbackWorld?.magicOrTechSystem ?: "Свободная система",
            narrativeTone = bundle.world?.narrativeTone ?: src?.narrativeTone ?: fallbackWorld?.narrativeTone ?: "Атмосферное приключение",
            customRules = bundle.world?.customRules ?: src?.customRules ?: fallbackWorld?.customRules ?: "",
            useDiceAndStats = src?.useDiceAndStats ?: true,
            useInventory = src?.useInventory ?: true,
            useSpells = src?.useSpells ?: true,
            hiddenPlotArc = if (selection.includeHiddenPlot && src != null) src.hiddenPlotArc else "",
            hiddenNpcKnowledge = if (selection.includeHiddenPlot && src != null) src.hiddenNpcKnowledge else "",
            hiddenPlotTriggers = if (selection.includeHiddenPlot && src != null) src.hiddenPlotTriggers else "",
            hiddenPlotUpdatedAt = if (selection.includeHiddenPlot && src?.hasHiddenPlot() == true) now else 0L,
            memorySummary = if (selection.includeMessages && src != null) src.memorySummary else "",
            compressedChaptersCount = if (selection.includeMessages && src != null) src.compressedChaptersCount else 0,
            maxContextTokens = src?.maxContextTokens ?: 8000,
            createdAt = now,
            updatedAt = now
        )
        val newCampaignId = dao.insertCampaign(baseCampaign)

        if (selection.includeMessages && bundle.messages.isNotEmpty()) {
            bundle.messages.forEach { dao.insertMessage(it.copy(id = 0, campaignId = newCampaignId)) }
        } else {
            val hook = fallbackWorld?.defaultOpeningHook?.ifBlank { null }
                ?: "Герой ${baseCampaign.heroName} вступает в мир «${baseCampaign.settingName}». Опишите ваше первое действие!"
            dao.insertMessage(
                ChatMessageEntity(
                    campaignId = newCampaignId,
                    senderRole = "GM",
                    content = hook,
                    estimatedTokens = estimateTokenCount(hook),
                    timestamp = now
                )
            )
        }

        if (selection.includeQaMessages && bundle.qaMessages.isNotEmpty()) {
            dao.insertQaMessages(bundle.qaMessages.map { it.copy(id = 0, campaignId = newCampaignId) })
        }
        if (selection.includeInventory) {
            bundle.inventory.forEach { dao.insertInventoryItem(it.copy(id = 0, campaignId = newCampaignId)) }
        }
        if (selection.includeJournal) {
            bundle.journal.forEach { dao.insertJournalEntry(it.copy(id = 0, campaignId = newCampaignId)) }
        }
        if (selection.includeSpells) {
            bundle.spells.forEach { dao.insertSpell(it.copy(id = 0, campaignId = newCampaignId)) }
        }
        if (selection.includeKnowledge && bundle.knowledge.isNotEmpty()) {
            dao.insertKnowledgeChunks(bundle.knowledge.map { it.copy(id = 0, campaignId = newCampaignId) })
        }

        return SelectiveImportOutcome(
            message = "Импортирована игра «${baseCampaign.title}»",
            createdOrTargetCampaignId = newCampaignId,
            createdWorldId = savedWorldId
        )
    }

    suspend fun getActiveUnarchivedMessages(campaignId: Long): List<ChatMessageEntity> =
        dao.getActiveUnarchivedMessages(campaignId)
}
