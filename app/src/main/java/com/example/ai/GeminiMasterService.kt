package com.example.ai

import android.util.Log
import com.example.data.local.CampaignEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.CustomSpellEntity
import com.example.data.local.GroundingSource
import com.example.data.local.InventoryItemEntity
import com.example.data.local.JournalEntryEntity
import com.example.data.local.KnowledgeChunkEntity
import com.example.data.local.QaMessageEntity
import com.example.data.local.WorldEntity
import com.example.data.local.WorldLoreEntity
import com.example.data.preferences.AppSettings
import com.google.firebase.ai.type.Schema
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

private const val TAG = "GeminiMasterService"

data class GmTurnResponse(
    val narrativeText: String,
    val groundingSources: List<GroundingSource> = emptyList(),
    val fallbackNotice: String? = null
)

data class HiddenPlotPlan(
    val plotArc: String = "",
    val npcKnowledgeBoundaries: String = "",
    val triggersAndConsequences: String = ""
) {
    fun isNotEmpty(): Boolean =
        plotArc.isNotBlank() || npcKnowledgeBoundaries.isNotBlank() || triggersAndConsequences.isNotBlank()
}

data class PartyActionInput(
    val heroName: String,
    val heroArchetype: String,
    val heroStats: String,
    val actionText: String,
    val diceRoll: String = ""
)

data class MultiplayerRoundContext(
    val roomTitle: String,
    val worldName: String,
    val worldGenre: String,
    val worldDescription: String,
    val diceSystem: String,
    val magicOrTechSystem: String,
    val narrativeTone: String,
    val customRules: String,
    val useDiceAndStats: Boolean,
    val useInventory: Boolean,
    val useSpells: Boolean,
    val roundNumber: Int,
    val hiddenPlot: HiddenPlotPlan,
    val memorySummary: String,
    val previousRoundsTranscript: List<String>
)

data class AiLootChange(
    val action: String, // "ADD" or "REMOVE"
    val name: String,
    val category: String,
    val quantity: Int,
    val rarity: String,
    val effect: String,
    val description: String
)

data class AiJournalSuggestion(
    val title: String,
    val entryType: String, // "НПС", "Локация", "Квест", "Фракция", "Заметка"
    val summary: String,
    val status: String,
    val tags: List<String>
)

data class AiWorldStateDelta(
    val lootChanges: List<AiLootChange> = emptyList(),
    val journalUpdates: List<AiJournalSuggestion> = emptyList(),
    val updatedHiddenPlot: HiddenPlotPlan? = null
)

data class GeneratedSpellDraft(
    val name: String,
    val rankOrLevel: String,
    val schoolOrType: String,
    val resourceCost: String,
    val castTime: String,
    val rangeInfo: String,
    val durationInfo: String,
    val diceFormula: String,
    val description: String,
    val tagsCsv: String
)

class GeminiMasterService(
    private val gateway: AiProviderGateway = AiProviderGateway()
) {

    private val worldDeltaSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "lootChanges" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "action" to Schema.enumeration(
                                values = listOf("ADD", "REMOVE"),
                                description = "ADD если герой получил предмет/ресурс, REMOVE если потратил или потерял"
                            ),
                            "name" to Schema.string(description = "Точное название предмета на русском"),
                            "category" to Schema.enumeration(
                                values = listOf("Оружие", "Броня", "Расходники", "Артефакты", "Ресурсы", "Прочее")
                            ),
                            "quantity" to Schema.integer(description = "Количество (минимум 1)"),
                            "rarity" to Schema.string(description = "Редкость или ранг предмета"),
                            "effect" to Schema.string(description = "Краткий игровой бонус или урон"),
                            "description" to Schema.string(description = "Короткое описание в 1 предложение")
                        )
                    ),
                    description = "Изменения инвентаря за этот ход (пустой список, если ничего не менялось или инвентарь выключен)"
                ),
                "journalUpdates" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "title" to Schema.string(description = "Имя НПС, название локации, квеста или фракции"),
                            "entryType" to Schema.enumeration(
                                values = listOf("НПС", "Локация", "Квест", "Фракция", "Заметка")
                            ),
                            "summary" to Schema.string(description = "Краткая суть того, что игрок открыто узнал за сцену"),
                            "status" to Schema.string(description = "Статус: Дружелюбный, Враждебный, Исследовано, В процессе, Выполнено и т.п."),
                            "tags" to Schema.array(
                                items = Schema.string(),
                                description = "2-4 коротких тега в нижнем регистре без пробелов"
                            )
                        )
                    ),
                    description = "Новые или существенно обновлённые НПС, локации, квесты за этот ход"
                ),
                "hiddenPlotArc" to Schema.string(
                    description = "Актуальный скрытый режиссёрский план ИИ на 2-4 шага вперёд (назревающие события, истинные мотивы антагонистов и НПС)"
                ),
                "hiddenNpcKnowledge" to Schema.string(
                    description = "Чёткий реестр границ знаний персонажей: кто из НПС НЕ знает о каком факте X и потому НЕ должен преждевременно говорить о нём или подозревать без улик"
                ),
                "hiddenPlotTriggers" to Schema.string(
                    description = "Скрытые условия и последствия: что произойдёт, если игрок сделает или упустит конкретный шаг"
                )
            )
        )
    }

    private val hiddenPlotSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "hiddenPlotArc" to Schema.string(
                    description = "Скрытая сюжетная арка и план ближайших глав (закулисный замысел Мастера)"
                ),
                "hiddenNpcKnowledge" to Schema.string(
                    description = "Границы знаний и тайны НПС: конкретные факты вида «Персонаж А не знает о факте X и не должен вести себя подозрительно раньше времени»"
                ),
                "hiddenPlotTriggers" to Schema.string(
                    description = "Скрытые триггеры, улики и последствия выбора героя"
                )
            )
        )
    }

    private val spellArchitectSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "name" to Schema.string(description = "Выразительное название заклинания или способности"),
                "rankOrLevel" to Schema.string(description = "Круг, ранг или уровень по правилам сеттинга"),
                "schoolOrType" to Schema.string(description = "Школа магии, дисциплина псионики или тип модуля"),
                "resourceCost" to Schema.string(description = "Стоимость применения (мана, ячейка, ОЗУ, рассудок)"),
                "castTime" to Schema.string(description = "Время сотворения (например: 1 действие, бонусное действие)"),
                "rangeInfo" to Schema.string(description = "Дистанция (например: 18 м, Касание, На себя)"),
                "durationInfo" to Schema.string(description = "Длительность (например: Мгновенно, Концентрация до 1 мин)"),
                "diceFormula" to Schema.string(description = "Формула броска в формате NdM + K (например 3d6 + 3) или пустая строка"),
                "description" to Schema.string(description = "Подробное художественное и механическое описание эффекта"),
                "tags" to Schema.array(
                    items = Schema.string(),
                    description = "3-4 тега способности (например: огонь, урон, контроль)"
                )
            )
        )
    }

    private fun buildSystemPrompt(
        campaign: CampaignEntity,
        activeWorld: WorldEntity?,
        inventory: List<InventoryItemEntity>,
        journal: List<JournalEntryEntity>,
        spells: List<CustomSpellEntity>,
        relevantWorldLore: List<WorldLoreEntity>,
        relevantLore: List<KnowledgeChunkEntity>,
        webSearchActive: Boolean
    ): String = buildString {
        if (campaign.useDiceAndStats) {
            appendLine("Ты — выдающийся Мастер настольных ролевых игр (Game Master). Веди игру живо, атмосферно и на русском языке.")
        } else {
            appendLine("Ты — выдающийся ведущий литературной текстовой ролевой игры (словески без механики D&D). Веди историю художественно, глубоко и на русском языке.")
        }
        appendLine()
        appendLine("=== ПАРАМЕТРЫ МИРА И ФОРМАТА ===")
        appendLine("Кампания: ${campaign.title}")
        appendLine("Сеттинг: ${campaign.settingName} (${campaign.settingGenre})")
        if (activeWorld != null) {
            if (activeWorld.franchiseSource.isNotBlank()) {
                appendLine("Каноничная вселенная-источник: ${activeWorld.franchiseSource}")
            }
            if (activeWorld.worldDescription.isNotBlank()) {
                appendLine("Описание мира: ${activeWorld.worldDescription}")
            }
        }
        if (campaign.useDiceAndStats) {
            appendLine("Механика проверок и кубиков: ${campaign.diceSystem}")
        } else {
            appendLine("Формат проверок: ЧИСТАЯ СЛОВЕСКА БЕЗ КУБИКОВ И D&D. Числовые броски, КС и уровни отключены.")
        }
        appendLine("Система магии / технологий / мира: ${campaign.magicOrTechSystem}")
        appendLine("Тон повествования: ${campaign.narrativeTone}")
        if (campaign.customRules.isNotBlank()) {
            appendLine("Уникальные правила партии (соблюдай строго): ${campaign.customRules}")
        }
        appendLine()
        appendLine("=== ГЕРОЙ ИГРОКА ===")
        appendLine("Имя: ${campaign.heroName} (${campaign.heroArchetype})")
        if (campaign.heroStatsSummary.isNotBlank()) {
            val label = if (campaign.useDiceAndStats) "Характеристики и состояние" else "Черты образа и состояние"
            appendLine("$label: ${campaign.heroStatsSummary}")
        }
        if (campaign.heroBackstory.isNotBlank()) {
            appendLine("Предыстория и личная цель героя: ${campaign.heroBackstory}")
        }

        if (campaign.useInventory && inventory.isNotEmpty()) {
            appendLine("Снаряжение героя:")
            inventory.take(20).forEach { item ->
                val eq = if (item.isEquipped) "[Экипировано] " else ""
                val bonus = if (item.bonusOrEffect.isNotBlank()) " (${item.bonusOrEffect})" else ""
                appendLine("- $eq${item.name} x${item.quantity}$bonus")
            }
        }

        if (campaign.useSpells) {
            val preparedSpells = spells.filter { it.isPrepared }
            if (preparedSpells.isNotEmpty()) {
                appendLine("Доступные заклинания / способности героя:")
                preparedSpells.take(12).forEach { sp ->
                    val roll = if (campaign.useDiceAndStats && sp.diceFormula.isNotBlank()) ", бросок ${sp.diceFormula}" else ""
                    appendLine("- ${sp.name} [${sp.rankOrLevel}, цена: ${sp.resourceCost}$roll]: ${sp.description.take(140)}")
                }
            }
        }

        if (journal.isNotEmpty()) {
            appendLine()
            appendLine("=== ИЗВЕСТНЫЕ НПС, ЛОКАЦИИ И КВЕСТЫ ===")
            journal.take(14).forEach { j ->
                val status = if (j.statusOrDisposition.isNotBlank()) " (${j.statusOrDisposition})" else ""
                appendLine("- [${j.entryType}] ${j.title}$status: ${j.summary.take(150)}")
            }
        }

        if (relevantWorldLore.isNotEmpty() || relevantLore.isNotEmpty()) {
            appendLine()
            appendLine("=== БАЗА ЗНАНИЙ И КАНОН МИРА ===")
            relevantWorldLore.forEach { wLore ->
                appendLine("### ${wLore.sectionTitle} (${wLore.sourceTitle})")
                appendLine(wLore.content)
            }
            relevantLore.forEach { chunk ->
                appendLine("### ${chunk.sectionTitle} (${chunk.sourceTitle})")
                appendLine(chunk.content)
            }
        }

        if (campaign.memorySummary.isNotBlank()) {
            appendLine()
            appendLine("=== СЖАТАЯ ХРОНИКА ПРОШЛЫХ ГЛАВ (ДОЛГОВРЕМЕННАЯ ПАМЯТЬ) ===")
            appendLine(campaign.memorySummary)
        }

        if (campaign.hasHiddenPlot()) {
            appendLine()
            appendLine("=== СКРЫТЫЙ РЕЖИССЁРСКИЙ ПЛАН И ГРАНИЦЫ ЗНАНИЙ НПС (ТАЙНОЕ ЗАКУЛИСЬЕ ИИ) ===")
            if (campaign.hiddenPlotArc.isNotBlank()) {
                appendLine("Скрытая арка и план событий наперёд: ${campaign.hiddenPlotArc}")
            }
            if (campaign.hiddenNpcKnowledge.isNotBlank()) {
                appendLine("Границы знаний персонажей (КРИТИЧНО): ${campaign.hiddenNpcKnowledge}")
            }
            if (campaign.hiddenPlotTriggers.isNotBlank()) {
                appendLine("Скрытые триггеры и последствия: ${campaign.hiddenPlotTriggers}")
            }
            appendLine("ВАЖНЕЙШЕЕ ПРАВИЛО ЗАКУЛИСЬЯ: Никогда не вываливай этот скрытый план игроку прямым текстом. Если в границах знаний записано, что персонаж НЕ знает о факте X, он НИ ПРИ КАКИХ ОБСТОЯТЕЛЬСТВАХ не должен говорить об этом факте, намекать на него или внезапно беспричинно подозревать, пока не получит явную улику прямо в ходе сцены!")
        }

        appendLine()
        appendLine("=== ИНСТРУКЦИИ ВЕДЕНИЯ ИГРЫ ===")
        if (campaign.useDiceAndStats) {
            appendLine("1. Реагируй на действия игрока, учитывая его лист героя, активные модули, броски кубиков и прошлые события.")
            appendLine("2. Если в сообщении игрока указан результат броска кубика — честно интерпретируй его. Если требуется проверка — попроси сделать бросок (${campaign.diceSystem}) или предложи 2-3 интересных варианта действий.")
        } else {
            appendLine("1. РЕЖИМ РОЛКИ БЕЗ ДНД: Никогда не проси бросать кубики (d20 и др.), не упоминай КС, числовые модификаторы, уровни или очки здоровья в цифрах.")
            appendLine("2. Развивай сцену через живую художественную прозу, психологию персонажей, достоверные диалоги и логичные последствия поступков героя.")
        }
        appendLine("3. Держи объём ответа комфортным для чтения с экрана смартфона (2–4 выразительных абзаца с прямой речью НПС и атмосферой сцены).")
        if (webSearchActive) {
            appendLine("4. При необходимости используй подключённый веб-поиск Google для сверки канона вселенной, исторических или научных фактов.")
        }
    }

    suspend fun streamOrGenerateMasterTurn(
        settings: AppSettings,
        campaign: CampaignEntity,
        activeWorld: WorldEntity? = null,
        recentMessages: List<ChatMessageEntity>,
        userPromptWithDice: String,
        inventory: List<InventoryItemEntity>,
        journal: List<JournalEntryEntity>,
        spells: List<CustomSpellEntity>,
        relevantWorldLore: List<WorldLoreEntity> = emptyList(),
        relevantLore: List<KnowledgeChunkEntity>,
        useWebSearch: Boolean,
        onStreamingChunk: (String) -> Unit
    ): GmTurnResponse {
        val effectiveWebSearch = useWebSearch && settings.aiProvider.supportsNativeGoogleSearch
        val sysText = buildSystemPrompt(
            campaign = campaign,
            activeWorld = activeWorld,
            inventory = inventory,
            journal = journal,
            spells = spells,
            relevantWorldLore = relevantWorldLore,
            relevantLore = relevantLore,
            webSearchActive = effectiveWebSearch
        )

        val history = recentMessages
            .filter { it.senderRole == "USER" || it.senderRole == "GM" }
            .takeLast(18)
            .map { msg ->
                val textBody = if (campaign.useDiceAndStats && !msg.diceRollInfo.isNullOrBlank()) {
                    "[${msg.diceRollInfo}]\n${msg.content}"
                } else {
                    msg.content
                }
                GatewayChatMessage(
                    isUser = msg.senderRole == "USER",
                    text = textBody
                )
            }

        val response = gateway.generateChatOrText(
            settings = settings,
            systemInstruction = sysText,
            history = history,
            userPrompt = userPromptWithDice,
            useWebSearch = effectiveWebSearch,
            expectJson = false,
            onStreamPartial = onStreamingChunk
        )

        return GmTurnResponse(
            narrativeText = response.text,
            groundingSources = response.groundingSources,
            fallbackNotice = response.fallbackNotice
        )
    }

    /**
     * Out-of-character Contextual Q&A Side-Chat («Чат с вопросами»):
     * Immersed in the active RP chat, journal, character, and lore + optional Google Search grounding,
     * answering sudden questions without disrupting the RP chat or spoiling hidden plot twists.
     */
    suspend fun askContextualAdvisor(
        settings: AppSettings,
        campaign: CampaignEntity,
        activeWorld: WorldEntity?,
        recentRpMessages: List<ChatMessageEntity>,
        inventory: List<InventoryItemEntity>,
        journal: List<JournalEntryEntity>,
        spells: List<CustomSpellEntity>,
        relevantWorldLore: List<WorldLoreEntity>,
        relevantLore: List<KnowledgeChunkEntity>,
        priorQaMessages: List<QaMessageEntity>,
        question: String,
        useWebSearch: Boolean,
        onStreamingChunk: (String) -> Unit
    ): GmTurnResponse {
        val effectiveWebSearch = useWebSearch && settings.aiProvider.supportsNativeGoogleSearch

        val recentSceneTranscript = recentRpMessages
            .filter { it.senderRole == "USER" || it.senderRole == "GM" }
            .takeLast(16)
            .joinToString("\n\n") { m ->
                val speaker = if (m.senderRole == "USER") campaign.heroName else "Мастер"
                "$speaker: ${m.content.take(900)}"
            }

        val advisorSystemPrompt = buildString {
            appendLine("Ты — Внеигровой Советник и Хранитель Контекста («Чат с вопросами») для ролевой партии «${campaign.title}».")
            appendLine("Твоя задача — чётко, дружелюбно и по делу отвечать на внезапные вопросы игрока о текущей сцене, внешности и деталях персонажей, законах мира, механике сюжета или смежных реальных/канонических фактах.")
            appendLine("ВАЖНО: Ты отвечаешь ВНЕ ролевого отыгрыша (как знающий энциклопедист и помощник за игровым столом), не продвигая сюжет вперёд вместо Мастера и не раскрывая будущие нераскрытые сюжетные спойлеры.")
            appendLine()
            appendLine("=== КОНТЕКСТ МИРА И ГЕРОЯ ===")
            appendLine("Мир: ${campaign.settingName} (${campaign.settingGenre})")
            if (activeWorld != null) {
                if (activeWorld.franchiseSource.isNotBlank()) {
                    appendLine("Канон / Первоисточник: ${activeWorld.franchiseSource}")
                }
                if (activeWorld.worldDescription.isNotBlank()) {
                    appendLine("Описание мира: ${activeWorld.worldDescription}")
                }
            }
            appendLine("Правила и магия/технологии: ${campaign.diceSystem} | ${campaign.magicOrTechSystem}")
            if (campaign.customRules.isNotBlank()) {
                appendLine("Особые правила: ${campaign.customRules}")
            }
            appendLine("Герой игрока: ${campaign.heroName} (${campaign.heroArchetype}) — ${campaign.heroStatsSummary}")
            if (campaign.heroBackstory.isNotBlank()) {
                appendLine("Предыстория героя: ${campaign.heroBackstory}")
            }
            if (campaign.useInventory && inventory.isNotEmpty()) {
                appendLine("Предметы героя: " + inventory.take(15).joinToString(", ") { "${it.name} x${it.quantity}" })
            }
            if (campaign.useSpells && spells.isNotEmpty()) {
                appendLine("Способности героя: " + spells.take(10).joinToString(", ") { it.name })
            }
            if (journal.isNotEmpty()) {
                appendLine()
                appendLine("=== ЗАПИСИ ЖУРНАЛА (НПС, ЛОКАЦИИ, КВЕСТЫ) ===")
                journal.take(16).forEach { j ->
                    appendLine("- [${j.entryType}] ${j.title}: ${j.summary} ${j.details}".trim())
                }
            }
            if (relevantWorldLore.isNotEmpty() || relevantLore.isNotEmpty()) {
                appendLine()
                appendLine("=== ЛОР И БАЗА ЗНАНИЙ МИРА ===")
                relevantWorldLore.forEach { l -> appendLine("- ${l.sectionTitle}: ${l.content}") }
                relevantLore.forEach { c -> appendLine("- ${c.sectionTitle}: ${c.content}") }
            }
            if (campaign.memorySummary.isNotBlank()) {
                appendLine()
                appendLine("=== СЖАТАЯ ХРОНИКА ПРОШЛЫХ СОБЫТИЙ ===")
                appendLine(campaign.memorySummary)
            }
            if (recentSceneTranscript.isNotBlank()) {
                appendLine()
                appendLine("=== ПОСЛЕДНИЕ СООБЩЕНИЯ РОЛЕВОГО ЧАТА ===")
                appendLine(recentSceneTranscript)
            }
            appendLine()
            appendLine("Правила ответа: если деталь уже упоминалась в чате, журнале или лоре — сошлись на неё точно. Если деталь ещё не была задана в тексте (например, цвет глаз эпизодического персонажа или тонкость устройства мира) — дай органичный, каноничный для этого сеттинга ответ или справку из интернета (если включён веб-поиск).")
        }

        val qaHistory = priorQaMessages.takeLast(10).map { msg ->
            GatewayChatMessage(
                isUser = msg.senderRole == "USER",
                text = msg.content
            )
        }

        val response = gateway.generateChatOrText(
            settings = settings,
            systemInstruction = advisorSystemPrompt,
            history = qaHistory,
            userPrompt = question,
            useWebSearch = effectiveWebSearch,
            expectJson = false,
            onStreamPartial = onStreamingChunk
        )

        return GmTurnResponse(
            narrativeText = response.text,
            groundingSources = response.groundingSources,
            fallbackNotice = response.fallbackNotice
        )
    }

    /**
     * Explicitly builds or refreshes the Hidden AI Plot Plan («Закулисье Мастера») on demand.
     */
    suspend fun generateOrRefreshHiddenPlotPlan(
        settings: AppSettings,
        campaign: CampaignEntity,
        activeWorld: WorldEntity?,
        recentMessages: List<ChatMessageEntity>,
        journal: List<JournalEntryEntity>,
        authorDirectionHint: String = ""
    ): HiddenPlotPlan {
        val recentTranscript = recentMessages
            .filter { it.senderRole == "USER" || it.senderRole == "GM" }
            .takeLast(12)
            .joinToString("\n\n") { m ->
                val who = if (m.senderRole == "USER") campaign.heroName else "Мастер"
                "$who: ${m.content.take(700)}"
            }

        val prompt = buildString {
            appendLine("Ты — Главный Сценарист ролевой кампании «${campaign.title}» в мире «${campaign.settingName}» (${campaign.settingGenre}).")
            if (activeWorld != null && activeWorld.worldDescription.isNotBlank()) {
                appendLine("Описание мира: ${activeWorld.worldDescription}")
            }
            appendLine("Герой: ${campaign.heroName} (${campaign.heroArchetype}). ${campaign.heroBackstory}")
            if (campaign.memorySummary.isNotBlank()) {
                appendLine("Хроника прошлых глав: ${campaign.memorySummary}")
            }
            if (journal.isNotEmpty()) {
                appendLine("Известные НПС и квесты: " + journal.take(12).joinToString("; ") { "[${it.entryType}] ${it.title}: ${it.summary}" })
            }
            if (campaign.hasHiddenPlot()) {
                appendLine("Предыдущий скрытый план:")
                appendLine("- Арка: ${campaign.hiddenPlotArc}")
                appendLine("- Границы знаний НПС: ${campaign.hiddenNpcKnowledge}")
                appendLine("- Триггеры: ${campaign.hiddenPlotTriggers}")
            }
            if (recentTranscript.isNotBlank()) {
                appendLine("Последние сцены чата:\n$recentTranscript")
            }
            if (authorDirectionHint.isNotBlank()) {
                appendLine("Пожелание автора / ведущего к плану: $authorDirectionHint")
            }
            appendLine()
            appendLine("Продумай или обнови СКРЫТЫЙ СЮЖЕТНЫЙ ПЛАН ИИ (невидимый игроку во время обычной игры), чтобы история развивалась логично, с интригой и без галлюцинаций:")
            appendLine("1. hiddenPlotArc: план на 3-4 сюжетных шага вперёд, скрытые мотивы действующих лиц и что происходит за кулисами.")
            appendLine("2. hiddenNpcKnowledge: чёткие факты асимметрии информации — кто из персонажей НЕ знает важный факт X и почему не должен внезапно говорить о нём или подозревать без улик.")
            appendLine("3. hiddenPlotTriggers: какие действия героя раскроют тайны или запустят важные последствия.")
            appendLine("Верни ответ строго на русском языке в JSON:")
            appendLine("""{"hiddenPlotArc":"...","hiddenNpcKnowledge":"...","hiddenPlotTriggers":"..."}""")
        }

        val resp = gateway.generateChatOrText(
            settings = settings,
            userPrompt = prompt,
            expectJson = true,
            firebaseSchema = hiddenPlotSchema
        )
        val root = JSONObject(AiProviderGateway.extractCleanJson(resp.text))
        return HiddenPlotPlan(
            plotArc = root.optString("hiddenPlotArc", "").trim(),
            npcKnowledgeBoundaries = root.optString("hiddenNpcKnowledge", "").trim(),
            triggersAndConsequences = root.optString("hiddenPlotTriggers", "").trim()
        )
    }

    /**
     * Synthesizes a unified multiplayer scene from all party members' round submissions
     * and updates the room's hidden AI plot plan.
     */
    suspend fun generateMultiplayerRoundTurn(
        settings: AppSettings,
        context: MultiplayerRoundContext,
        partyActions: List<PartyActionInput>,
        onStreamingChunk: (String) -> Unit = {}
    ): Pair<String, HiddenPlotPlan> {
        val partyBlock = partyActions.joinToString("\n\n") { p ->
            val rollPart = if (context.useDiceAndStats && p.diceRoll.isNotBlank()) " [${p.diceRoll}]" else ""
            val statsPart = if (p.heroStats.isNotBlank()) " (${p.heroStats})" else ""
            "• Герой «${p.heroName}» (${p.heroArchetype})$statsPart$rollPart:\n${p.actionText}"
        }

        val sysPrompt = buildString {
            appendLine("Ты — Мастер совместной онлайн-ролевой игры «${context.roomTitle}» для группы живых игроков. Веди сцену ярко, слаженно и на русском языке.")
            appendLine("Мир: ${context.worldName} (${context.worldGenre}). ${context.worldDescription}")
            if (context.useDiceAndStats) {
                appendLine("Механика проверок: ${context.diceSystem} | Способности: ${context.magicOrTechSystem}")
            } else {
                appendLine("РЕЖИМ ЧИСТОЙ СЛОВЕСКИ БЕЗ ДНД: Кубики и числовые проверки отключены. Не проси бросков d20 и не пиши цифры урона/КС — опирайся на логику сцены и совместный отыгрыш героев.")
            }
            appendLine("Тон: ${context.narrativeTone}")
            if (context.customRules.isNotBlank()) {
                appendLine("Правила комнаты: ${context.customRules}")
            }
            if (context.memorySummary.isNotBlank()) {
                appendLine("Хроника прошлых раундов: ${context.memorySummary}")
            }
            if (context.hiddenPlot.isNotEmpty()) {
                appendLine("=== СКРЫТЫЙ ПЛАН И ГРАНИЦЫ ЗНАНИЙ НПС (НЕ СПОЙЛЕРИТЬ ИГРОКАМ НАПРЯМУЮ) ===")
                appendLine("Арка: ${context.hiddenPlot.plotArc}")
                appendLine("Границы знаний НПС: ${context.hiddenPlot.npcKnowledgeBoundaries}")
                appendLine("Триггеры: ${context.hiddenPlot.triggersAndConsequences}")
                appendLine("Соблюдай границы знаний НПС: персонаж, не знающий факт X, не должен вести себя так, будто знает его!")
            }
            appendLine("ИНСТРУКЦИЯ РАУНДА №${context.roundNumber}: Органично объедини заявки ВСЕХ героев партии в единую динамичную сцену (3–5 абзацев), покажи вклад и последствия действий каждого персонажа и заверши сцену крючком для следующего совместного раунда.")
        }

        val historyMessages = context.previousRoundsTranscript.takeLast(8).map { roundText ->
            GatewayChatMessage(isUser = false, text = roundText)
        }

        val userPrompt = "Заявки игроков на Раунд №${context.roundNumber}:\n\n$partyBlock"

        val narrativeResp = gateway.generateChatOrText(
            settings = settings,
            systemInstruction = sysPrompt,
            history = historyMessages,
            userPrompt = userPrompt,
            useWebSearch = false,
            expectJson = false,
            onStreamPartial = onStreamingChunk
        )

        val updatedHiddenPlot = runCatching {
            val plotPrompt = buildString {
                appendLine("Обнови скрытый сюжетный план онлайн-комнаты «${context.roomTitle}» после Раунда №${context.roundNumber}.")
                if (context.hiddenPlot.isNotEmpty()) {
                    appendLine("Текущий план: ${context.hiddenPlot.plotArc}")
                    appendLine("Текущие границы знаний НПС: ${context.hiddenPlot.npcKnowledgeBoundaries}")
                    appendLine("Текущие триггеры: ${context.hiddenPlot.triggersAndConsequences}")
                }
                appendLine("Заявки партии:\n$partyBlock")
                appendLine("Ответ Мастера:\n${narrativeResp.text}")
                appendLine("""Верни строго JSON: {"hiddenPlotArc":"...","hiddenNpcKnowledge":"...","hiddenPlotTriggers":"..."}""")
            }
            val plotResp = gateway.generateChatOrText(
                settings = settings,
                userPrompt = plotPrompt,
                expectJson = true,
                firebaseSchema = hiddenPlotSchema
            )
            val json = JSONObject(AiProviderGateway.extractCleanJson(plotResp.text))
            HiddenPlotPlan(
                plotArc = json.optString("hiddenPlotArc", context.hiddenPlot.plotArc).trim(),
                npcKnowledgeBoundaries = json.optString("hiddenNpcKnowledge", context.hiddenPlot.npcKnowledgeBoundaries).trim(),
                triggersAndConsequences = json.optString("hiddenPlotTriggers", context.hiddenPlot.triggersAndConsequences).trim()
            )
        }.getOrDefault(context.hiddenPlot)

        return narrativeResp.text.trim() to updatedHiddenPlot
    }

    suspend fun extractWorldStateDelta(
        settings: AppSettings,
        campaign: CampaignEntity,
        playerAction: String,
        gmReply: String,
        currentInventory: List<InventoryItemEntity>,
        currentJournal: List<JournalEntryEntity>
    ): AiWorldStateDelta {
        return try {
            val invNames = if (campaign.useInventory) {
                currentInventory.joinToString(", ") { "${it.name} (x${it.quantity})" }.ifBlank { "пусто" }
            } else {
                "модуль инвентаря выключен (оставь lootChanges пустым [])"
            }
            val jrnNames = currentJournal.take(20).joinToString(", ") { "[${it.entryType}] ${it.title}" }.ifBlank { "пусто" }

            val prompt = buildString {
                appendLine("Проанализируй последний ход ролевой игры в сеттинге «${campaign.settingName}».")
                appendLine("Текущий инвентарь героя: $invNames")
                appendLine("Уже записано в журнале: $jrnNames")
                if (campaign.hasHiddenPlot()) {
                    appendLine("Текущий скрытый план ИИ:")
                    appendLine("- Арка: ${campaign.hiddenPlotArc}")
                    appendLine("- Границы знаний НПС: ${campaign.hiddenNpcKnowledge}")
                    appendLine("- Триггеры: ${campaign.hiddenPlotTriggers}")
                } else {
                    appendLine("Скрытый план ИИ ещё пуст — обязательно создай продуманный стартовый план на несколько шагов вперёд и укажи границы знаний НПС!")
                }
                appendLine()
                appendLine("Действие игрока: $playerAction")
                appendLine("Ответ Мастера: $gmReply")
                appendLine()
                appendLine("Верни изменения инвентаря/журнала И обновлённый скрытый сюжетный план ИИ строго в формате JSON:")
                appendLine("""{"lootChanges":[],"journalUpdates":[],"hiddenPlotArc":"...","hiddenNpcKnowledge":"кто чего не знает и не должен преждевременно подозревать...","hiddenPlotTriggers":"..."}""")
            }

            val resp = gateway.generateChatOrText(
                settings = settings,
                userPrompt = prompt,
                expectJson = true,
                firebaseSchema = worldDeltaSchema
            )
            val root = JSONObject(AiProviderGateway.extractCleanJson(resp.text))

            val lootList = if (!campaign.useInventory) {
                emptyList()
            } else {
                buildList {
                    val arr = root.optJSONArray("lootChanges")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            add(
                                AiLootChange(
                                    action = o.optString("action", "ADD"),
                                    name = o.optString("name", ""),
                                    category = o.optString("category", "Прочее"),
                                    quantity = o.optInt("quantity", 1).coerceAtLeast(1),
                                    rarity = o.optString("rarity", "Обычный"),
                                    effect = o.optString("effect", ""),
                                    description = o.optString("description", "")
                                )
                            )
                        }
                    }
                }
            }

            val journalList = buildList {
                val arr = root.optJSONArray("journalUpdates")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val tArr = o.optJSONArray("tags")
                        val tags = if (tArr != null) {
                            (0 until tArr.length()).mapNotNull { idx ->
                                tArr.optString(idx)?.trim()?.takeIf { it.isNotBlank() }
                            }
                        } else emptyList()
                        add(
                            AiJournalSuggestion(
                                title = o.optString("title", ""),
                                entryType = o.optString("entryType", "Заметка"),
                                summary = o.optString("summary", ""),
                                status = o.optString("status", ""),
                                tags = tags
                            )
                        )
                    }
                }
            }

            val newArc = root.optString("hiddenPlotArc", "").trim()
            val newKnowledge = root.optString("hiddenNpcKnowledge", "").trim()
            val newTriggers = root.optString("hiddenPlotTriggers", "").trim()
            val hiddenPlot = if (newArc.isNotBlank() || newKnowledge.isNotBlank() || newTriggers.isNotBlank()) {
                HiddenPlotPlan(
                    plotArc = newArc.ifBlank { campaign.hiddenPlotArc },
                    npcKnowledgeBoundaries = newKnowledge.ifBlank { campaign.hiddenNpcKnowledge },
                    triggersAndConsequences = newTriggers.ifBlank { campaign.hiddenPlotTriggers }
                )
            } else null

            AiWorldStateDelta(
                lootChanges = lootList,
                journalUpdates = journalList,
                updatedHiddenPlot = hiddenPlot
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "World state delta extraction skipped: ${e.message}")
            AiWorldStateDelta()
        }
    }

    suspend fun compressMemoryChronicle(
        settings: AppSettings,
        campaign: CampaignEntity,
        messagesToArchive: List<ChatMessageEntity>
    ): String {
        val dialogueDump = messagesToArchive.joinToString("\n\n") { m ->
            val who = if (m.senderRole == "USER") campaign.heroName else "Мастер"
            val roll = if (campaign.useDiceAndStats && !m.diceRollInfo.isNullOrBlank()) " [${m.diceRollInfo}]" else ""
            "$who$roll: ${m.content}"
        }

        val prompt = buildString {
            appendLine("Ты — Хранитель Памяти ролевой кампании «${campaign.title}» (${campaign.settingName}).")
            appendLine("Сожми предыдущую хронику и новые сцены в плотную, структурированную сводку памяти (до 450 слов) на русском языке, чтобы ИИ-Мастер НИЧЕГО важного не забыл.")
            appendLine("Сохрани все имена НПС, обещания, последствия решений игрока, полученные улики, изменения мира и незавершённые цели.")
            appendLine()
            if (campaign.memorySummary.isNotBlank()) {
                appendLine("=== ПРЕДЫДУЩАЯ ХРОНИКА ===")
                appendLine(campaign.memorySummary)
                appendLine()
            }
            appendLine("=== НОВЫЕ СЦЕНЫ ДЛЯ СЖАТИЯ ===")
            appendLine(dialogueDump)
            appendLine()
            appendLine("Сформируй обновлённую хронику по разделам:")
            appendLine("1. Ключевые события и сюжет")
            appendLine("2. Важные решения и отношения с НПС")
            appendLine("3. Открытые тайны и активные цели")
        }

        val resp = gateway.generateChatOrText(
            settings = settings,
            userPrompt = prompt
        )
        return resp.text.trim()
    }

    suspend fun generateCustomSpell(
        settings: AppSettings,
        campaign: CampaignEntity,
        conceptPrompt: String
    ): GeneratedSpellDraft {
        val prompt = buildString {
            appendLine("Создай сбалансированное кастомное заклинание / способность / особый приём для ролевой игры.")
            appendLine("Сеттинг: ${campaign.settingName} (${campaign.settingGenre})")
            appendLine("Механика: ${if (campaign.useDiceAndStats) campaign.diceSystem else "Словесная ролевая без бросков кубиков"}")
            appendLine("Система магии/технологий: ${campaign.magicOrTechSystem}")
            if (campaign.customRules.isNotBlank()) {
                appendLine("Особые правила: ${campaign.customRules}")
            }
            appendLine("Идея игрока: $conceptPrompt")
            appendLine("Ответ дай строго на русском языке в формате JSON:")
            appendLine("""{"name":"...","rankOrLevel":"...","schoolOrType":"...","resourceCost":"...","castTime":"...","rangeInfo":"...","durationInfo":"...","diceFormula":"2d8 + 3","description":"...","tags":["тег1","тег2"]}""")
        }

        val resp = gateway.generateChatOrText(
            settings = settings,
            userPrompt = prompt,
            expectJson = true,
            firebaseSchema = spellArchitectSchema
        )
        val json = JSONObject(AiProviderGateway.extractCleanJson(resp.text))
        val tagsArr = json.optJSONArray("tags")
        val tagsCsv = if (tagsArr != null) {
            (0 until tagsArr.length()).mapNotNull { tagsArr.optString(it)?.trim() }.joinToString(",")
        } else ""

        return GeneratedSpellDraft(
            name = json.optString("name", "Новая способность"),
            rankOrLevel = json.optString("rankOrLevel", "1 ранг"),
            schoolOrType = json.optString("schoolOrType", "Универсальная"),
            resourceCost = json.optString("resourceCost", "1 ячейка"),
            castTime = json.optString("castTime", "1 действие"),
            rangeInfo = json.optString("rangeInfo", "18 метров"),
            durationInfo = json.optString("durationInfo", "Мгновенно"),
            diceFormula = if (campaign.useDiceAndStats) json.optString("diceFormula", "") else "",
            description = json.optString("description", ""),
            tagsCsv = tagsCsv
        )
    }

    fun mapErrorToMessage(error: Throwable): String = gateway.mapReadableError(error)
}
