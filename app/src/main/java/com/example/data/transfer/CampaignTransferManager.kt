package com.example.data.transfer

import com.example.data.local.CampaignEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.CustomSpellEntity
import com.example.data.local.InventoryItemEntity
import com.example.data.local.JournalEntryEntity
import com.example.data.local.KnowledgeChunkEntity
import com.example.data.local.QaMessageEntity
import com.example.data.local.WorldEntity
import com.example.data.local.WorldLoreEntity
import com.example.data.local.normalizeTagsCsv
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class FullCampaignSnapshot(
    val campaign: CampaignEntity,
    val messages: List<ChatMessageEntity>,
    val inventory: List<InventoryItemEntity>,
    val journal: List<JournalEntryEntity>,
    val spells: List<CustomSpellEntity>,
    val knowledge: List<KnowledgeChunkEntity>,
    val qaMessages: List<QaMessageEntity> = emptyList(),
    val world: WorldEntity? = null,
    val worldLore: List<WorldLoreEntity> = emptyList()
)

enum class JsonPackageKind(val formatCode: String, val displayLabel: String) {
    WORLD_ONLY("rpg_world_package_v2", "Отдельный мир и лор"),
    CHARACTER_ONLY("rpg_character_package_v2", "Отдельный персонаж"),
    CHAT_ONLY("rpg_chat_package_v2", "Отдельная история чата"),
    SELECTIVE_BUNDLE("rpg_selective_bundle_v2", "Сборный пакет (выбор разделов)")
}

data class JsonTransferSelection(
    val includeWorld: Boolean = true,
    val includeCharacter: Boolean = true,
    val includeMessages: Boolean = true,
    val includeQaMessages: Boolean = true,
    val includeInventory: Boolean = true,
    val includeJournal: Boolean = true,
    val includeSpells: Boolean = true,
    val includeKnowledge: Boolean = true,
    val includeHiddenPlot: Boolean = true
)

data class ParsedJsonTransferBundle(
    val kind: JsonPackageKind,
    val titleSummary: String,
    val world: WorldEntity? = null,
    val worldLore: List<WorldLoreEntity> = emptyList(),
    val campaign: CampaignEntity? = null,
    val messages: List<ChatMessageEntity> = emptyList(),
    val qaMessages: List<QaMessageEntity> = emptyList(),
    val inventory: List<InventoryItemEntity> = emptyList(),
    val journal: List<JournalEntryEntity> = emptyList(),
    val spells: List<CustomSpellEntity> = emptyList(),
    val knowledge: List<KnowledgeChunkEntity> = emptyList(),
    val hasCharacterSection: Boolean = false,
    val hasHiddenPlotSection: Boolean = false
) {
    fun defaultSelection(): JsonTransferSelection = JsonTransferSelection(
        includeWorld = world != null || worldLore.isNotEmpty(),
        includeCharacter = hasCharacterSection,
        includeMessages = messages.isNotEmpty(),
        includeQaMessages = qaMessages.isNotEmpty(),
        includeInventory = inventory.isNotEmpty(),
        includeJournal = journal.isNotEmpty(),
        includeSpells = spells.isNotEmpty(),
        includeKnowledge = knowledge.isNotEmpty(),
        includeHiddenPlot = hasHiddenPlotSection
    )
}

object CampaignTransferManager {

    private fun formatDate(ts: Long): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag("ru"))
        return sdf.format(Date(ts))
    }

    fun exportToMarkdown(snapshot: FullCampaignSnapshot): String = buildString {
        val c = snapshot.campaign
        appendLine("# ${c.title}")
        appendLine()
        appendLine("- **Сеттинг:** ${c.settingName} (${c.settingGenre})")
        appendLine("- **Герой:** ${c.heroName} — ${c.heroArchetype}")
        appendLine("- **Характеристики / Образ:** ${c.heroStatsSummary}")
        appendLine(
            "- **Режим партии:** " + if (c.isPureFreeformMode()) {
                "Чистая словеска (без ДнД)"
            } else {
                buildList {
                    if (c.useDiceAndStats) add("Кубики и статы")
                    if (c.useInventory) add("Снаряжение")
                    if (c.useSpells) add("Заклинания")
                }.joinToString(", ")
            }
        )
        if (c.useDiceAndStats) {
            appendLine("- **Механика проверок:** ${c.diceSystem}")
        }
        appendLine("- **Система магии / технологий:** ${c.magicOrTechSystem}")
        appendLine("- **Тон игры:** ${c.narrativeTone}")
        if (c.customRules.isNotBlank()) {
            appendLine("- **Уникальные правила мира:** ${c.customRules}")
        }
        appendLine("- **Дата экспорта:** ${formatDate(System.currentTimeMillis())}")
        appendLine()

        if (c.memorySummary.isNotBlank()) {
            appendLine("## Сжатая хроника прошлых глав")
            appendLine(c.memorySummary)
            appendLine()
        }

        if (c.useInventory && snapshot.inventory.isNotEmpty()) {
            appendLine("## Снаряжение (${snapshot.inventory.size})")
            snapshot.inventory.forEach { item ->
                val eq = if (item.isEquipped) "[Экипировано] " else ""
                val effect = if (item.bonusOrEffect.isNotBlank()) " — ${item.bonusOrEffect}" else ""
                appendLine("- $eq**${item.name}** ×${item.quantity} (${item.category}, ${item.rarityOrTier})$effect")
                if (item.description.isNotBlank()) {
                    appendLine("  - ${item.description}")
                }
            }
            appendLine()
        }

        if (c.useSpells && snapshot.spells.isNotEmpty()) {
            appendLine("## Заклинания и способности (${snapshot.spells.size})")
            snapshot.spells.forEach { sp ->
                val formula = if (sp.diceFormula.isNotBlank()) " | Бросок: `${sp.diceFormula}`" else ""
                appendLine("- **${sp.name}** (${sp.rankOrLevel}, ${sp.schoolOrType}, Цена: ${sp.resourceCost}$formula)")
                appendLine("  - ${sp.description}")
            }
            appendLine()
        }

        if (snapshot.journal.isNotEmpty()) {
            appendLine("## Журнал приключений (${snapshot.journal.size})")
            snapshot.journal.forEach { entry ->
                val tags = entry.tagsList().joinToString(" ") { "#$it" }
                val status = if (entry.statusOrDisposition.isNotBlank()) " [${entry.statusOrDisposition}]" else ""
                appendLine("### [${entry.entryType}] ${entry.title}$status")
                if (tags.isNotBlank()) appendLine("*Теги:* $tags")
                appendLine(entry.summary)
                if (entry.details.isNotBlank()) {
                    appendLine()
                    appendLine(entry.details)
                }
                appendLine()
            }
        }

        appendLine("## История приключения (${snapshot.messages.size} сообщ.)")
        appendLine()
        snapshot.messages.forEach { msg ->
            val speaker = when (msg.senderRole) {
                "USER" -> "🧑 **${c.heroName}**"
                "GM" -> "🐉 **Мастер Миров**"
                else -> "⚙️ **Система**"
            }
            appendLine("$speaker _(${formatDate(msg.timestamp)})_")
            if (!msg.diceRollInfo.isNullOrBlank()) {
                appendLine("> ${msg.diceRollInfo}")
            }
            appendLine()
            appendLine(msg.content)
            val sources = msg.groundingSources()
            if (sources.isNotEmpty()) {
                appendLine()
                appendLine("*Источники веб-поиска:* " + sources.joinToString(", ") { "[${it.title}](${it.uri})" })
            }
            appendLine()
            appendLine("---")
            appendLine()
        }
    }

    fun exportToPlainText(snapshot: FullCampaignSnapshot): String = buildString {
        val c = snapshot.campaign
        appendLine("=== ${c.title.uppercase()} ===")
        appendLine("Сеттинг: ${c.settingName} (${c.settingGenre})")
        appendLine("Герой: ${c.heroName} (${c.heroArchetype}) | ${c.heroStatsSummary}")
        appendLine("Правила: ${c.diceSystem} | ${c.magicOrTechSystem}")
        if (c.customRules.isNotBlank()) appendLine("Особые правила: ${c.customRules}")
        appendLine("Экспортировано: ${formatDate(System.currentTimeMillis())}")
        appendLine()

        if (c.memorySummary.isNotBlank()) {
            appendLine("--- ХРОНИКА ПАМЯТИ ---")
            appendLine(c.memorySummary)
            appendLine()
        }

        appendLine("--- ИСТОРИЯ СЕССИИ ---")
        snapshot.messages.forEach { msg ->
            val role = when (msg.senderRole) {
                "USER" -> c.heroName
                "GM" -> "Мастер"
                else -> "Система"
            }
            appendLine("[${formatDate(msg.timestamp)}] $role:")
            if (!msg.diceRollInfo.isNullOrBlank()) appendLine("  (${msg.diceRollInfo})")
            appendLine(msg.content)
            appendLine()
        }
    }

    /**
     * Standalone JSON export for a reusable World + its WorldLore sections.
     */
    fun exportWorldPackageJson(world: WorldEntity, lore: List<WorldLoreEntity>): String {
        val root = JSONObject()
        root.put("format", JsonPackageKind.WORLD_ONLY.formatCode)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("world", serializeWorld(world))
        root.put("worldLore", serializeWorldLoreArray(lore))
        return root.toString(2)
    }

    /**
     * Standalone JSON export for a Character sheet (with optional inventory & spells).
     */
    fun exportCharacterPackageJson(
        campaign: CampaignEntity,
        inventory: List<InventoryItemEntity> = emptyList(),
        spells: List<CustomSpellEntity> = emptyList(),
        includeInventory: Boolean = true,
        includeSpells: Boolean = true
    ): String {
        val root = JSONObject()
        root.put("format", JsonPackageKind.CHARACTER_ONLY.formatCode)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("character", serializeCharacterFromCampaign(campaign))
        if (includeInventory) {
            root.put("inventory", serializeInventoryArray(inventory))
        }
        if (includeSpells) {
            root.put("spells", serializeSpellsArray(spells))
        }
        return root.toString(2)
    }

    /**
     * Standalone JSON export for Chat history + Memory Summary + optional Q&A side-chat.
     */
    fun exportChatPackageJson(
        campaign: CampaignEntity,
        messages: List<ChatMessageEntity>,
        qaMessages: List<QaMessageEntity> = emptyList(),
        includeQaMessages: Boolean = true,
        includeHiddenPlot: Boolean = false
    ): String {
        val root = JSONObject()
        root.put("format", JsonPackageKind.CHAT_ONLY.formatCode)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("campaignHeader", JSONObject().apply {
            put("title", campaign.title)
            put("heroName", campaign.heroName)
            put("heroArchetype", campaign.heroArchetype)
            put("settingName", campaign.settingName)
            put("settingGenre", campaign.settingGenre)
            put("memorySummary", campaign.memorySummary)
            put("compressedChaptersCount", campaign.compressedChaptersCount)
            put("useDiceAndStats", campaign.useDiceAndStats)
            put("useInventory", campaign.useInventory)
            put("useSpells", campaign.useSpells)
        })
        root.put("messages", serializeMessagesArray(messages))
        if (includeQaMessages && qaMessages.isNotEmpty()) {
            root.put("qaMessages", serializeQaMessagesArray(qaMessages))
        }
        if (includeHiddenPlot && campaign.hasHiddenPlot()) {
            root.put("hiddenPlot", serializeHiddenPlot(campaign))
        }
        return root.toString(2)
    }

    /**
     * Selective multi-section JSON export controlled by user checkboxes.
     */
    fun exportSelectiveJson(
        snapshot: FullCampaignSnapshot,
        selection: JsonTransferSelection
    ): String {
        val root = JSONObject()
        root.put("format", JsonPackageKind.SELECTIVE_BUNDLE.formatCode)
        root.put("exportedAt", System.currentTimeMillis())

        val c = snapshot.campaign
        root.put("selectionMeta", JSONObject().apply {
            put("includeWorld", selection.includeWorld)
            put("includeCharacter", selection.includeCharacter)
            put("includeMessages", selection.includeMessages)
            put("includeQaMessages", selection.includeQaMessages)
            put("includeInventory", selection.includeInventory)
            put("includeJournal", selection.includeJournal)
            put("includeSpells", selection.includeSpells)
            put("includeKnowledge", selection.includeKnowledge)
            put("includeHiddenPlot", selection.includeHiddenPlot)
        })

        if (selection.includeWorld) {
            val worldEntity = snapshot.world ?: WorldEntity(
                name = c.settingName,
                originType = "CUSTOM",
                genre = c.settingGenre,
                worldDescription = "",
                diceSystem = c.diceSystem,
                magicOrTechSystem = c.magicOrTechSystem,
                narrativeTone = c.narrativeTone,
                customRules = c.customRules
            )
            root.put("world", serializeWorld(worldEntity))
            root.put("worldLore", serializeWorldLoreArray(snapshot.worldLore))
        }

        if (selection.includeCharacter) {
            root.put("character", serializeCharacterFromCampaign(c))
        }

        root.put("campaign", serializeCampaignCore(c, includeHiddenPlot = selection.includeHiddenPlot))

        if (selection.includeHiddenPlot && c.hasHiddenPlot()) {
            root.put("hiddenPlot", serializeHiddenPlot(c))
        }
        if (selection.includeMessages) {
            root.put("messages", serializeMessagesArray(snapshot.messages))
        }
        if (selection.includeQaMessages) {
            root.put("qaMessages", serializeQaMessagesArray(snapshot.qaMessages))
        }
        if (selection.includeInventory) {
            root.put("inventory", serializeInventoryArray(snapshot.inventory))
        }
        if (selection.includeJournal) {
            root.put("journal", serializeJournalArray(snapshot.journal))
        }
        if (selection.includeSpells) {
            root.put("spells", serializeSpellsArray(snapshot.spells))
        }
        if (selection.includeKnowledge) {
            root.put("knowledge", serializeKnowledgeArray(snapshot.knowledge))
        }

        return root.toString(2)
    }

    /**
     * Full campaign JSON backup (preserves backwards compatibility with v1 format while adding v2 fields).
     */
    fun exportToJsonBackup(snapshot: FullCampaignSnapshot): String {
        val root = JSONObject()
        root.put("format", "rpg_world_master_backup_v1")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("campaign", serializeCampaignCore(snapshot.campaign, includeHiddenPlot = true))
        if (snapshot.world != null) {
            root.put("world", serializeWorld(snapshot.world))
        }
        if (snapshot.worldLore.isNotEmpty()) {
            root.put("worldLore", serializeWorldLoreArray(snapshot.worldLore))
        }
        root.put("messages", serializeMessagesArray(snapshot.messages))
        if (snapshot.qaMessages.isNotEmpty()) {
            root.put("qaMessages", serializeQaMessagesArray(snapshot.qaMessages))
        }
        root.put("inventory", serializeInventoryArray(snapshot.inventory))
        root.put("journal", serializeJournalArray(snapshot.journal))
        root.put("spells", serializeSpellsArray(snapshot.spells))
        root.put("knowledge", serializeKnowledgeArray(snapshot.knowledge))
        return root.toString(2)
    }

    /**
     * Universal JSON inspector & parser supporting World-only, Character-only, Chat-only,
     * Selective v2 bundles, and v1 full backups.
     */
    fun inspectJsonPackage(rawJson: String): ParsedJsonTransferBundle {
        val root = JSONObject(rawJson.trim())
        val format = root.optString("format", "")
        val now = System.currentTimeMillis()

        val worldObj = root.optJSONObject("world")
        val parsedWorld = worldObj?.let { parseWorldObject(it, now) }
        val parsedWorldLore = parseWorldLoreArray(root.optJSONArray("worldLore"), now)

        val charObj = root.optJSONObject("character")
        val campObj = root.optJSONObject("campaign")
        val headerObj = root.optJSONObject("campaignHeader")
        val hiddenObj = root.optJSONObject("hiddenPlot")

        val parsedMessages = parseMessagesArray(root.optJSONArray("messages"), now)
        val parsedQaMessages = parseQaMessagesArray(root.optJSONArray("qaMessages"), now)
        val parsedInventory = parseInventoryArray(root.optJSONArray("inventory"))
        val parsedJournal = parseJournalArray(root.optJSONArray("journal"))
        val parsedSpells = parseSpellsArray(root.optJSONArray("spells"))
        val parsedKnowledge = parseKnowledgeArray(root.optJSONArray("knowledge"))

        val kind = when {
            format == JsonPackageKind.WORLD_ONLY.formatCode || (parsedWorld != null && campObj == null && charObj == null && headerObj == null) ->
                JsonPackageKind.WORLD_ONLY
            format == JsonPackageKind.CHARACTER_ONLY.formatCode || (charObj != null && campObj == null && parsedMessages.isEmpty()) ->
                JsonPackageKind.CHARACTER_ONLY
            format == JsonPackageKind.CHAT_ONLY.formatCode || (headerObj != null && campObj == null) ->
                JsonPackageKind.CHAT_ONLY
            else -> JsonPackageKind.SELECTIVE_BUNDLE
        }

        val mergedCampaign: CampaignEntity? = when {
            campObj != null -> {
                val base = parseCampaignCore(campObj, now)
                val withChar = if (charObj != null) applyCharacterObject(base, charObj) else base
                if (hiddenObj != null) applyHiddenPlotObject(withChar, hiddenObj, now) else withChar
            }
            charObj != null -> {
                val base = CampaignEntity(
                    title = "${charObj.optString("settingName", "Приключение")} — ${charObj.optString("heroName", "Герой")}",
                    heroName = charObj.optString("heroName", "Странник"),
                    heroArchetype = charObj.optString("heroArchetype", "Искатель приключений"),
                    heroStatsSummary = charObj.optString("heroStatsSummary", "ОЗ 25/25"),
                    heroBackstory = charObj.optString("heroBackstory", ""),
                    settingName = charObj.optString("settingName", parsedWorld?.name ?: "Авторский мир"),
                    settingGenre = charObj.optString("settingGenre", parsedWorld?.genre ?: "Свободный сеттинг"),
                    diceSystem = parsedWorld?.diceSystem ?: "d20 + модификатор",
                    magicOrTechSystem = parsedWorld?.magicOrTechSystem ?: "Свободная система",
                    narrativeTone = parsedWorld?.narrativeTone ?: "Атмосферное приключение",
                    customRules = parsedWorld?.customRules ?: "",
                    useDiceAndStats = charObj.optBoolean("useDiceAndStats", true),
                    useInventory = charObj.optBoolean("useInventory", true),
                    useSpells = charObj.optBoolean("useSpells", true),
                    createdAt = now,
                    updatedAt = now
                )
                if (hiddenObj != null) applyHiddenPlotObject(base, hiddenObj, now) else base
            }
            headerObj != null -> {
                val base = CampaignEntity(
                    title = headerObj.optString("title", "Импортированный чат"),
                    heroName = headerObj.optString("heroName", "Странник"),
                    heroArchetype = headerObj.optString("heroArchetype", "Герой"),
                    heroStatsSummary = "ОЗ 25/25",
                    settingName = headerObj.optString("settingName", "Авторский мир"),
                    settingGenre = headerObj.optString("settingGenre", "Ролевая история"),
                    diceSystem = "d20 + модификатор",
                    magicOrTechSystem = "Свободная система",
                    narrativeTone = "Атмосферное приключение",
                    customRules = "",
                    memorySummary = headerObj.optString("memorySummary", ""),
                    compressedChaptersCount = headerObj.optInt("compressedChaptersCount", 0),
                    useDiceAndStats = headerObj.optBoolean("useDiceAndStats", true),
                    useInventory = headerObj.optBoolean("useInventory", true),
                    useSpells = headerObj.optBoolean("useSpells", true),
                    createdAt = now,
                    updatedAt = now
                )
                if (hiddenObj != null) applyHiddenPlotObject(base, hiddenObj, now) else base
            }
            else -> null
        }

        val hasCharacter = charObj != null || campObj != null
        val hasHiddenPlot = hiddenObj != null || (mergedCampaign?.hasHiddenPlot() == true)

        val titleSummary = when (kind) {
            JsonPackageKind.WORLD_ONLY -> "Мир «${parsedWorld?.name ?: "Без названия"}»"
            JsonPackageKind.CHARACTER_ONLY -> "Персонаж «${mergedCampaign?.heroName ?: "Герой"}»"
            JsonPackageKind.CHAT_ONLY -> "Чат «${mergedCampaign?.title ?: "Сессия"}»"
            JsonPackageKind.SELECTIVE_BUNDLE -> mergedCampaign?.title ?: parsedWorld?.name ?: "Пакет данных"
        }

        return ParsedJsonTransferBundle(
            kind = kind,
            titleSummary = titleSummary,
            world = parsedWorld,
            worldLore = parsedWorldLore,
            campaign = mergedCampaign,
            messages = parsedMessages,
            qaMessages = parsedQaMessages,
            inventory = parsedInventory,
            journal = parsedJournal,
            spells = parsedSpells,
            knowledge = parsedKnowledge,
            hasCharacterSection = hasCharacter,
            hasHiddenPlotSection = hasHiddenPlot
        )
    }

    fun parseBackupJson(rawJson: String): FullCampaignSnapshot {
        val bundle = inspectJsonPackage(rawJson)
        val now = System.currentTimeMillis()
        val campaign = bundle.campaign ?: CampaignEntity(
            title = bundle.world?.name ?: "Импортированная кампания",
            heroName = "Странник",
            heroArchetype = "Искатель приключений",
            heroStatsSummary = "ОЗ 20/20",
            settingName = bundle.world?.name ?: "Авторский мир",
            settingGenre = bundle.world?.genre ?: "Свободный сеттинг",
            diceSystem = bundle.world?.diceSystem ?: "d20 + модификатор",
            magicOrTechSystem = bundle.world?.magicOrTechSystem ?: "Свободная система",
            narrativeTone = bundle.world?.narrativeTone ?: "Атмосферное приключение",
            customRules = bundle.world?.customRules ?: "",
            createdAt = now,
            updatedAt = now
        )
        return FullCampaignSnapshot(
            campaign = campaign,
            messages = bundle.messages,
            inventory = bundle.inventory,
            journal = bundle.journal,
            spells = bundle.spells,
            knowledge = bundle.knowledge,
            qaMessages = bundle.qaMessages,
            world = bundle.world,
            worldLore = bundle.worldLore
        )
    }

    // --- Serialization Helpers ---

    private fun serializeWorld(w: WorldEntity): JSONObject = JSONObject().apply {
        put("name", w.name)
        put("originType", w.originType)
        put("franchiseSource", w.franchiseSource)
        put("genre", w.genre)
        put("worldDescription", w.worldDescription)
        put("diceSystem", w.diceSystem)
        put("magicOrTechSystem", w.magicOrTechSystem)
        put("narrativeTone", w.narrativeTone)
        put("customRules", w.customRules)
        put("suggestedArchetypesCsv", w.suggestedArchetypesCsv)
        put("defaultOpeningHook", w.defaultOpeningHook)
        put("groundingSourcesJson", w.groundingSourcesJson)
    }

    private fun serializeWorldLoreArray(lore: List<WorldLoreEntity>): JSONArray {
        val arr = JSONArray()
        lore.forEach { l ->
            arr.put(JSONObject().apply {
                put("sourceTitle", l.sourceTitle)
                put("sectionTitle", l.sectionTitle)
                put("keywordsCsv", l.keywordsCsv)
                put("content", l.content)
                put("isAlwaysActive", l.isAlwaysActive)
            })
        }
        return arr
    }

    private fun serializeCharacterFromCampaign(c: CampaignEntity): JSONObject = JSONObject().apply {
        put("heroName", c.heroName)
        put("heroArchetype", c.heroArchetype)
        put("heroStatsSummary", c.heroStatsSummary)
        put("heroBackstory", c.heroBackstory)
        put("settingName", c.settingName)
        put("settingGenre", c.settingGenre)
        put("useDiceAndStats", c.useDiceAndStats)
        put("useInventory", c.useInventory)
        put("useSpells", c.useSpells)
    }

    private fun serializeHiddenPlot(c: CampaignEntity): JSONObject = JSONObject().apply {
        put("hiddenPlotArc", c.hiddenPlotArc)
        put("hiddenNpcKnowledge", c.hiddenNpcKnowledge)
        put("hiddenPlotTriggers", c.hiddenPlotTriggers)
        put("hiddenPlotUpdatedAt", c.hiddenPlotUpdatedAt)
    }

    private fun serializeCampaignCore(c: CampaignEntity, includeHiddenPlot: Boolean): JSONObject = JSONObject().apply {
        put("title", c.title)
        put("heroName", c.heroName)
        put("heroArchetype", c.heroArchetype)
        put("heroStatsSummary", c.heroStatsSummary)
        put("heroBackstory", c.heroBackstory)
        put("settingName", c.settingName)
        put("settingGenre", c.settingGenre)
        put("diceSystem", c.diceSystem)
        put("magicOrTechSystem", c.magicOrTechSystem)
        put("narrativeTone", c.narrativeTone)
        put("customRules", c.customRules)
        put("useDiceAndStats", c.useDiceAndStats)
        put("useInventory", c.useInventory)
        put("useSpells", c.useSpells)
        if (includeHiddenPlot) {
            put("hiddenPlotArc", c.hiddenPlotArc)
            put("hiddenNpcKnowledge", c.hiddenNpcKnowledge)
            put("hiddenPlotTriggers", c.hiddenPlotTriggers)
            put("hiddenPlotUpdatedAt", c.hiddenPlotUpdatedAt)
        }
        put("memorySummary", c.memorySummary)
        put("compressedChaptersCount", c.compressedChaptersCount)
        put("maxContextTokens", c.maxContextTokens)
    }

    private fun serializeMessagesArray(messages: List<ChatMessageEntity>): JSONArray {
        val arr = JSONArray()
        messages.forEach { m ->
            arr.put(JSONObject().apply {
                put("senderRole", m.senderRole)
                put("content", m.content)
                put("diceRollInfo", m.diceRollInfo ?: "")
                put("groundingSourcesJson", m.groundingSourcesJson)
                put("estimatedTokens", m.estimatedTokens)
                put("isArchivedInSummary", m.isArchivedInSummary)
                put("timestamp", m.timestamp)
            })
        }
        return arr
    }

    private fun serializeQaMessagesArray(qaMessages: List<QaMessageEntity>): JSONArray {
        val arr = JSONArray()
        qaMessages.forEach { q ->
            arr.put(JSONObject().apply {
                put("senderRole", q.senderRole)
                put("content", q.content)
                put("groundingSourcesJson", q.groundingSourcesJson)
                put("timestamp", q.timestamp)
            })
        }
        return arr
    }

    private fun serializeInventoryArray(inventory: List<InventoryItemEntity>): JSONArray {
        val arr = JSONArray()
        inventory.forEach { i ->
            arr.put(JSONObject().apply {
                put("name", i.name)
                put("category", i.category)
                put("quantity", i.quantity)
                put("rarityOrTier", i.rarityOrTier)
                put("bonusOrEffect", i.bonusOrEffect)
                put("description", i.description)
                put("isEquipped", i.isEquipped)
            })
        }
        return arr
    }

    private fun serializeJournalArray(journal: List<JournalEntryEntity>): JSONArray {
        val arr = JSONArray()
        journal.forEach { j ->
            arr.put(JSONObject().apply {
                put("title", j.title)
                put("entryType", j.entryType)
                put("summary", j.summary)
                put("details", j.details)
                put("tagsCsv", j.tagsCsv)
                put("statusOrDisposition", j.statusOrDisposition)
                put("isPinned", j.isPinned)
            })
        }
        return arr
    }

    private fun serializeSpellsArray(spells: List<CustomSpellEntity>): JSONArray {
        val arr = JSONArray()
        spells.forEach { s ->
            arr.put(JSONObject().apply {
                put("name", s.name)
                put("rankOrLevel", s.rankOrLevel)
                put("schoolOrType", s.schoolOrType)
                put("resourceCost", s.resourceCost)
                put("castTime", s.castTime)
                put("rangeInfo", s.rangeInfo)
                put("durationInfo", s.durationInfo)
                put("diceFormula", s.diceFormula)
                put("description", s.description)
                put("tagsCsv", s.tagsCsv)
                put("isPrepared", s.isPrepared)
            })
        }
        return arr
    }

    private fun serializeKnowledgeArray(knowledge: List<KnowledgeChunkEntity>): JSONArray {
        val arr = JSONArray()
        knowledge.forEach { k ->
            arr.put(JSONObject().apply {
                put("sourceTitle", k.sourceTitle)
                put("sectionTitle", k.sectionTitle)
                put("keywordsCsv", k.keywordsCsv)
                put("content", k.content)
                put("isAlwaysActive", k.isAlwaysActive)
            })
        }
        return arr
    }

    // --- Deserialization Helpers ---

    private fun parseWorldObject(o: JSONObject, now: Long): WorldEntity = WorldEntity(
        name = o.optString("name", "Импортированный мир"),
        originType = o.optString("originType", "CUSTOM"),
        franchiseSource = o.optString("franchiseSource", ""),
        genre = o.optString("genre", "Авторский сеттинг"),
        worldDescription = o.optString("worldDescription", ""),
        diceSystem = o.optString("diceSystem", "d20 + модификатор"),
        magicOrTechSystem = o.optString("magicOrTechSystem", "Свободная система"),
        narrativeTone = o.optString("narrativeTone", "Атмосферное приключение"),
        customRules = o.optString("customRules", ""),
        suggestedArchetypesCsv = o.optString("suggestedArchetypesCsv", ""),
        defaultOpeningHook = o.optString("defaultOpeningHook", ""),
        groundingSourcesJson = o.optString("groundingSourcesJson", "[]"),
        createdAt = now,
        updatedAt = now
    )

    private fun parseWorldLoreArray(arr: JSONArray?, now: Long): List<WorldLoreEntity> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    WorldLoreEntity(
                        worldId = 0L,
                        sourceTitle = o.optString("sourceTitle", "Кодекс мира"),
                        sectionTitle = o.optString("sectionTitle", "Раздел ${i + 1}"),
                        keywordsCsv = normalizeTagsCsv(o.optString("keywordsCsv", "")),
                        content = o.optString("content", ""),
                        isAlwaysActive = o.optBoolean("isAlwaysActive", true),
                        createdAt = now + i
                    )
                )
            }
        }
    }

    private fun parseCampaignCore(cObj: JSONObject, now: Long): CampaignEntity = CampaignEntity(
        title = cObj.optString("title", "Импортированная кампания"),
        heroName = cObj.optString("heroName", "Странник"),
        heroArchetype = cObj.optString("heroArchetype", "Искатель приключений"),
        heroStatsSummary = cObj.optString("heroStatsSummary", "ОЗ 20/20"),
        heroBackstory = cObj.optString("heroBackstory", ""),
        settingName = cObj.optString("settingName", "Авторский мир"),
        settingGenre = cObj.optString("settingGenre", "Свободный сеттинг"),
        diceSystem = cObj.optString("diceSystem", "d20 + модификатор"),
        magicOrTechSystem = cObj.optString("magicOrTechSystem", "Свободная система"),
        narrativeTone = cObj.optString("narrativeTone", "Атмосферное приключение"),
        customRules = cObj.optString("customRules", ""),
        useDiceAndStats = cObj.optBoolean("useDiceAndStats", true),
        useInventory = cObj.optBoolean("useInventory", true),
        useSpells = cObj.optBoolean("useSpells", true),
        hiddenPlotArc = cObj.optString("hiddenPlotArc", ""),
        hiddenNpcKnowledge = cObj.optString("hiddenNpcKnowledge", ""),
        hiddenPlotTriggers = cObj.optString("hiddenPlotTriggers", ""),
        hiddenPlotUpdatedAt = cObj.optLong("hiddenPlotUpdatedAt", 0L),
        memorySummary = cObj.optString("memorySummary", ""),
        compressedChaptersCount = cObj.optInt("compressedChaptersCount", 0),
        maxContextTokens = cObj.optInt("maxContextTokens", 8000),
        createdAt = now,
        updatedAt = now
    )

    private fun applyCharacterObject(base: CampaignEntity, charObj: JSONObject): CampaignEntity = base.copy(
        heroName = charObj.optString("heroName", base.heroName),
        heroArchetype = charObj.optString("heroArchetype", base.heroArchetype),
        heroStatsSummary = charObj.optString("heroStatsSummary", base.heroStatsSummary),
        heroBackstory = charObj.optString("heroBackstory", base.heroBackstory),
        useDiceAndStats = charObj.optBoolean("useDiceAndStats", base.useDiceAndStats),
        useInventory = charObj.optBoolean("useInventory", base.useInventory),
        useSpells = charObj.optBoolean("useSpells", base.useSpells)
    )

    private fun applyHiddenPlotObject(base: CampaignEntity, hiddenObj: JSONObject, now: Long): CampaignEntity = base.copy(
        hiddenPlotArc = hiddenObj.optString("hiddenPlotArc", base.hiddenPlotArc),
        hiddenNpcKnowledge = hiddenObj.optString("hiddenNpcKnowledge", base.hiddenNpcKnowledge),
        hiddenPlotTriggers = hiddenObj.optString("hiddenPlotTriggers", base.hiddenPlotTriggers),
        hiddenPlotUpdatedAt = hiddenObj.optLong("hiddenPlotUpdatedAt", now)
    )

    private fun parseMessagesArray(arr: JSONArray?, now: Long): List<ChatMessageEntity> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val dice = o.optString("diceRollInfo", "").ifBlank { null }
                val content = o.optString("content", "")
                add(
                    ChatMessageEntity(
                        campaignId = 0,
                        senderRole = o.optString("senderRole", "GM"),
                        content = content,
                        diceRollInfo = dice,
                        groundingSourcesJson = o.optString("groundingSourcesJson", "[]"),
                        estimatedTokens = o.optInt("estimatedTokens", estimateTokenCount(content)),
                        isArchivedInSummary = o.optBoolean("isArchivedInSummary", false),
                        timestamp = o.optLong("timestamp", now + i)
                    )
                )
            }
        }
    }

    private fun parseQaMessagesArray(arr: JSONArray?, now: Long): List<QaMessageEntity> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    QaMessageEntity(
                        campaignId = 0,
                        senderRole = o.optString("senderRole", "ADVISOR"),
                        content = o.optString("content", ""),
                        groundingSourcesJson = o.optString("groundingSourcesJson", "[]"),
                        timestamp = o.optLong("timestamp", now + i)
                    )
                )
            }
        }
    }

    private fun parseInventoryArray(arr: JSONArray?): List<InventoryItemEntity> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    InventoryItemEntity(
                        campaignId = 0,
                        name = o.optString("name", "Предмет"),
                        category = o.optString("category", "Прочее"),
                        quantity = o.optInt("quantity", 1),
                        rarityOrTier = o.optString("rarityOrTier", "Обычный"),
                        bonusOrEffect = o.optString("bonusOrEffect", ""),
                        description = o.optString("description", ""),
                        isEquipped = o.optBoolean("isEquipped", false)
                    )
                )
            }
        }
    }

    private fun parseJournalArray(arr: JSONArray?): List<JournalEntryEntity> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    JournalEntryEntity(
                        campaignId = 0,
                        title = o.optString("title", "Запись"),
                        entryType = o.optString("entryType", "Заметка"),
                        summary = o.optString("summary", ""),
                        details = o.optString("details", ""),
                        tagsCsv = normalizeTagsCsv(o.optString("tagsCsv", "")),
                        statusOrDisposition = o.optString("statusOrDisposition", ""),
                        isPinned = o.optBoolean("isPinned", false)
                    )
                )
            }
        }
    }

    private fun parseSpellsArray(arr: JSONArray?): List<CustomSpellEntity> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    CustomSpellEntity(
                        campaignId = 0,
                        name = o.optString("name", "Способность"),
                        rankOrLevel = o.optString("rankOrLevel", "1 круг"),
                        schoolOrType = o.optString("schoolOrType", "Универсальная"),
                        resourceCost = o.optString("resourceCost", "1 ячейка"),
                        castTime = o.optString("castTime", "1 действие"),
                        rangeInfo = o.optString("rangeInfo", "18 м"),
                        durationInfo = o.optString("durationInfo", "Мгновенно"),
                        diceFormula = o.optString("diceFormula", ""),
                        description = o.optString("description", ""),
                        tagsCsv = normalizeTagsCsv(o.optString("tagsCsv", "")),
                        isPrepared = o.optBoolean("isPrepared", true)
                    )
                )
            }
        }
    }

    private fun parseKnowledgeArray(arr: JSONArray?): List<KnowledgeChunkEntity> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    KnowledgeChunkEntity(
                        campaignId = 0,
                        sourceTitle = o.optString("sourceTitle", "Импорт"),
                        sectionTitle = o.optString("sectionTitle", "Раздел ${i + 1}"),
                        keywordsCsv = normalizeTagsCsv(o.optString("keywordsCsv", "")),
                        content = o.optString("content", ""),
                        isAlwaysActive = o.optBoolean("isAlwaysActive", false)
                    )
                )
            }
        }
    }

    /**
     * Parses user-imported Knowledge Base files (.txt, .md, .json) into indexed lore chunks.
     */
    fun parseKnowledgeDocument(
        fileName: String,
        rawText: String,
        campaignId: Long
    ): List<KnowledgeChunkEntity> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()
        val cleanSource = fileName.substringBeforeLast('.').ifBlank { "База знаний" }

        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            val parsedJson = runCatching {
                val arr = if (trimmed.startsWith("[")) {
                    JSONArray(trimmed)
                } else {
                    val obj = JSONObject(trimmed)
                    obj.optJSONArray("entries")
                        ?: obj.optJSONArray("lore")
                        ?: obj.optJSONArray("knowledge")
                        ?: obj.optJSONArray("items")
                }
                if (arr != null && arr.length() > 0) {
                    buildList {
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            val title = item.optString("title")
                                .ifBlank { item.optString("name") }
                                .ifBlank { item.optString("key") }
                                .ifBlank { "Запись ${i + 1}" }
                            val body = item.optString("content")
                                .ifBlank { item.optString("description") }
                                .ifBlank { item.optString("text") }
                            val keysRaw = when {
                                item.optJSONArray("keywords") != null -> {
                                    val kArr = item.getJSONArray("keywords")
                                    (0 until kArr.length()).joinToString(",") { kArr.optString(it) }
                                }
                                item.optJSONArray("tags") != null -> {
                                    val kArr = item.getJSONArray("tags")
                                    (0 until kArr.length()).joinToString(",") { kArr.optString(it) }
                                }
                                else -> item.optString("keywords", item.optString("tags", ""))
                            }
                            if (body.isNotBlank()) {
                                val autoKeys = if (keysRaw.isBlank()) extractAutoKeywords("$title $body") else normalizeTagsCsv(keysRaw)
                                add(
                                    KnowledgeChunkEntity(
                                        campaignId = campaignId,
                                        sourceTitle = cleanSource,
                                        sectionTitle = title,
                                        keywordsCsv = autoKeys,
                                        content = body.take(2400),
                                        isAlwaysActive = item.optBoolean("alwaysActive", i == 0)
                                    )
                                )
                            }
                        }
                    }
                } else emptyList()
            }.getOrDefault(emptyList())

            if (parsedJson.isNotEmpty()) return parsedJson
        }

        val sections = mutableListOf<Pair<String, StringBuilder>>()
        var currentHeader = cleanSource
        var currentBody = StringBuilder()

        trimmed.lines().forEach { line ->
            val headingMatch = Regex("""^(#{1,3})\s+(.+)$""").find(line.trim())
            if (headingMatch != null) {
                if (currentBody.isNotBlank()) {
                    sections.add(currentHeader to currentBody)
                    currentBody = StringBuilder()
                }
                currentHeader = headingMatch.groupValues[2].trim()
            } else {
                currentBody.appendLine(line)
            }
        }
        if (currentBody.isNotBlank()) {
            sections.add(currentHeader to currentBody)
        }

        val chunks = mutableListOf<KnowledgeChunkEntity>()
        sections.forEach { (header, bodyBuilder) ->
            val body = bodyBuilder.toString().trim()
            if (body.length <= 1200) {
                if (body.isNotBlank()) {
                    chunks.add(
                        KnowledgeChunkEntity(
                            campaignId = campaignId,
                            sourceTitle = cleanSource,
                            sectionTitle = header,
                            keywordsCsv = extractAutoKeywords("$header $body"),
                            content = body,
                            isAlwaysActive = chunks.isEmpty()
                        )
                    )
                }
            } else {
                val paragraphs = body.split(Regex("""\n\s*\n"""))
                var acc = StringBuilder()
                var partIdx = 1
                for (p in paragraphs) {
                    if (acc.length + p.length > 1100 && acc.isNotBlank()) {
                        val partText = acc.toString().trim()
                        chunks.add(
                            KnowledgeChunkEntity(
                                campaignId = campaignId,
                                sourceTitle = cleanSource,
                                sectionTitle = "$header (ч. $partIdx)",
                                keywordsCsv = extractAutoKeywords("$header $partText"),
                                content = partText,
                                isAlwaysActive = chunks.isEmpty()
                            )
                        )
                        partIdx++
                        acc = StringBuilder()
                    }
                    acc.appendLine(p).appendLine()
                }
                if (acc.isNotBlank()) {
                    val partText = acc.toString().trim()
                    chunks.add(
                        KnowledgeChunkEntity(
                            campaignId = campaignId,
                            sourceTitle = cleanSource,
                            sectionTitle = if (partIdx > 1) "$header (ч. $partIdx)" else header,
                            keywordsCsv = extractAutoKeywords("$header $partText"),
                            content = partText,
                            isAlwaysActive = chunks.isEmpty()
                        )
                    )
                }
            }
        }
        return chunks.take(60)
    }

    fun parseWorldLoreDocument(
        fileName: String,
        rawText: String,
        worldId: Long
    ): List<WorldLoreEntity> {
        return parseKnowledgeDocument(fileName = fileName, rawText = rawText, campaignId = 0L)
            .map { chunk ->
                WorldLoreEntity(
                    worldId = worldId,
                    sourceTitle = chunk.sourceTitle,
                    sectionTitle = chunk.sectionTitle,
                    keywordsCsv = chunk.keywordsCsv,
                    content = chunk.content,
                    isAlwaysActive = chunk.isAlwaysActive
                )
            }
    }

    private val stopWords = setOf(
        "это", "как", "так", "и", "в", "над", "к", "до", "не", "на", "но", "за", "то", "с", "ли",
        "а", "во", "от", "со", "для", "о", "же", "ну", "вы", "бы", "что", "кто", "он", "она",
        "они", "мы", "его", "ее", "их", "или", "при", "из", "по", "все", "был", "была", "были",
        "the", "and", "for", "with", "that", "this", "from", "into", "are", "was", "were"
    )

    fun extractAutoKeywords(text: String): String {
        val freq = mutableMapOf<String, Int>()
        Regex("""[\p{L}\p{N}_-]{4,}""").findAll(text.lowercase()).forEach { m ->
            val w = m.value
            if (w !in stopWords) {
                freq[w] = (freq[w] ?: 0) + 1
            }
        }
        return freq.entries
            .sortedByDescending { it.value }
            .take(8)
            .joinToString(",") { it.key }
    }
}

fun estimateTokenCount(text: String): Int {
    if (text.isBlank()) return 0
    return ((text.length / 3.2).toInt()).coerceAtLeast(1)
}
