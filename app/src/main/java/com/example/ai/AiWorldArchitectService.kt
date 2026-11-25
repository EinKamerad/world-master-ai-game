package com.example.ai

import android.util.Log
import com.example.data.local.GroundingSource
import com.example.data.local.normalizeTagsCsv
import com.example.data.preferences.AppSettings
import com.google.firebase.ai.type.Schema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import org.json.JSONObject

private const val TAG = "AiWorldArchitect"

data class GeneratedLoreSection(
    val sectionTitle: String,
    val content: String,
    val keywordsCsv: String,
    val isAlwaysActive: Boolean = false
)

data class GeneratedWorldDraft(
    val name: String,
    val genre: String,
    val worldDescription: String,
    val diceSystem: String,
    val magicOrTechSystem: String,
    val narrativeTone: String,
    val customRules: String,
    val suggestedArchetypes: List<String>,
    val defaultOpeningHook: String,
    val loreSections: List<GeneratedLoreSection>,
    val groundingSources: List<GroundingSource> = emptyList()
)

class AiWorldArchitectService(
    private val gateway: AiProviderGateway = AiProviderGateway()
) {

    private val worldBlueprintSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "name" to Schema.string(description = "Выразительное название мира или вселенной на русском"),
                "genre" to Schema.string(description = "Жанр и стилистика (например: Тёмное славянское фэнтези, Постапокалиптический техно-триллер)"),
                "worldDescription" to Schema.string(description = "Ёмкое и атмосферное описание устройства мира, эпохи и главного конфликта (3-5 предложений)"),
                "diceSystem" to Schema.string(description = "Рекомендуемая механика проверок и кубиков под этот мир (например d20 + мод против КС, d10 пул, 2d6 и т.п.)"),
                "magicOrTechSystem" to Schema.string(description = "Как работают магия, сверхспособности, технологии или боевые искусства в этом мире и какие ресурсы расходуются"),
                "narrativeTone" to Schema.string(description = "Тон повествования, атмосфера и уровень опасности"),
                "customRules" to Schema.string(description = "Уникальные правила игры и законы этого мира, которые должен соблюдать Мастер"),
                "suggestedArchetypes" to Schema.array(
                    items = Schema.string(),
                    description = "4-6 характерных классов/ролей/архетипов персонажей для выбора игроком при старте игры"
                ),
                "defaultOpeningHook" to Schema.string(description = "Захватывающая стартовая сцена (1-2 абзаца), с которой может начаться приключение в этом мире"),
                "loreSections" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "sectionTitle" to Schema.string(description = "Название раздела базы знаний (например: Фракции и ордена, Ключевые регионы, Канон способностей, Опасности и существа)"),
                            "content" to Schema.string(description = "Подробное информативное содержание раздела для базы знаний ИИ-Мастера (4-8 предложений)"),
                            "keywords" to Schema.array(
                                items = Schema.string(),
                                description = "4-6 ключевых слов для поиска по лору"
                            )
                        )
                    ),
                    description = "4-5 насыщенных разделов базы знаний по этому миру"
                )
            )
        )
    }

    private val jsonFormatInstruction = """
        Верни строго валидный JSON-объект следующей структуры:
        {
          "name": "Название мира",
          "genre": "Жанр",
          "worldDescription": "Описание эпохи и конфликта",
          "diceSystem": "Механика проверок и кубиков",
          "magicOrTechSystem": "Система магии / технологий / ресурсов",
          "narrativeTone": "Тон повествования",
          "customRules": "Уникальные законы и правила мира",
          "suggestedArchetypes": ["Архетип 1", "Архетип 2", "Архетип 3", "Архетип 4"],
          "defaultOpeningHook": "Стартовая сцена (1-2 абзаца)",
          "loreSections": [
            {
              "sectionTitle": "Название раздела",
              "content": "Подробное содержание раздела (4-8 предложений)",
              "keywords": ["ключ1", "ключ2", "ключ3"]
            }
          ]
        }
    """.trimIndent()

    /**
     * Generates a brand-new original RPG world from scratch based on the player's description.
     */
    suspend fun generateWorldFromDescription(
        settings: AppSettings,
        userIdea: String,
        rulesOrToneHint: String = ""
    ): GeneratedWorldDraft {
        val prompt = buildString {
            appendLine("Ты — ведущий геймдизайнер и демиург настольных ролевых игр.")
            appendLine("Создай с нуля глубокий, целостный и готовый к игре мир по описанию пользователя.")
            appendLine()
            appendLine("Описание задумки пользователя: ${userIdea.trim()}")
            if (rulesOrToneHint.isNotBlank()) {
                appendLine("Дополнительные пожелания по тону или правилам: ${rulesOrToneHint.trim()}")
            }
            appendLine()
            appendLine("Продумай:")
            appendLine("- Уникальное название, жанр, описание эпохи и центрального конфликта;")
            appendLine("- Подходящую механику кубиков и систему магии / технологий / ресурсов;")
            appendLine("- Уникальные законы мира (customRules);")
            appendLine("- 4–6 колоритных архетипов/классов героев, за которых интересно играть;")
            appendLine("- Атмосферный стартовый хук и 4–5 подробных статей базы знаний (география, фракции, способности, угрозы).")
            appendLine("Весь ответ должен быть строго на русском языке.")
            appendLine()
            appendLine(jsonFormatInstruction)
        }

        val response = gateway.generateChatOrText(
            settings = settings,
            userPrompt = prompt,
            expectJson = true,
            firebaseSchema = worldBlueprintSchema
        )
        return parseWorldDraftJson(AiProviderGateway.extractCleanJson(response.text), emptyList())
    }

    /**
     * Researches an existing universe (video game, TV series, anime, book, movie, tabletop franchise)
     * using Google Search Grounding (when supported by the active provider) + structured lorebook synthesis.
     */
    suspend fun researchExistingUniverse(
        settings: AppSettings,
        universeName: String,
        eraOrFocusHint: String = "",
        onStageUpdate: (String) -> Unit = {}
    ): GeneratedWorldDraft {
        val cleanName = universeName.trim()
        val supportsSearch = settings.aiProvider.supportsNativeGoogleSearch
        onStageUpdate(
            if (supportsSearch) {
                "ИИ ищет канон вселенной «$cleanName» через Google Поиск (${settings.aiProvider.shortBadge})..."
            } else {
                "ИИ собирает каноничное досье вселенной «$cleanName» через ${settings.aiProvider.shortBadge}..."
            }
        )

        val researchPrompt = buildString {
            appendLine("Найди и систематизируй подробную, достоверную информацию о вымышленной вселенной (игра, сериал, аниме, книга или фильм): «$cleanName».")
            if (eraOrFocusHint.isNotBlank()) {
                appendLine("Уточнение эпохи, части или фокуса от игрока: ${eraOrFocusHint.trim()}")
            }
            appendLine("Собери справочное досье на русском языке для проведения настольной ролевой игры по этой вселенной:")
            appendLine("1. Суть мира, эпоха, ключевые законы реальности и главный конфликт.")
            appendLine("2. Главные локации, города, регионы или планеты.")
            appendLine("3. Ключевые фракции, гильдии, корпорации, ордена и знаковые персонажи.")
            appendLine("4. Как устроена боевая система, магия, технологии, мутации или сверхспособности (терминология и ограничения канона).")
            appendLine("5. Типичные враги, монстры или угрозы.")
            appendLine("6. Кем может играть игрок (типичные классы, профессии или роли героев в этой вселенной).")
        }

        val groundedResponse = gateway.generateChatOrText(
            settings = settings,
            userPrompt = researchPrompt,
            useWebSearch = supportsSearch,
            expectJson = false
        )
        val researchDossier = groundedResponse.text.trim()
        if (researchDossier.isBlank()) {
            throw IllegalStateException("Не удалось получить данные о вселенной «$cleanName».")
        }

        onStageUpdate("Адаптируем канон «$cleanName» в игровой мир и базу знаний...")
        val structurePrompt = buildString {
            appendLine("На основе собранного каноничного досье по вселенной «$cleanName» сформируй готовый ролевой сеттинг и базу знаний.")
            if (eraOrFocusHint.isNotBlank()) {
                appendLine("Учти фокус/эпоху: ${eraOrFocusHint.trim()}")
            }
            appendLine()
            appendLine("=== СОБРАННОЕ ДОСЬЕ ВСЕЛЕННОЙ ===")
            appendLine(researchDossier)
            appendLine()
            appendLine("Строго сохраняй каноничные названия, термины, фракции, географию и законы способностей этой вселенной.")
            appendLine("Создай 5 насыщенных разделов loreSections (География и локации, Фракции и силы, Система магии/технологий/боя, Угрозы и бестиарий, Каноничные детали и термины).")
            appendLine()
            appendLine(jsonFormatInstruction)
        }

        val structuredResponse = gateway.generateChatOrText(
            settings = settings,
            userPrompt = structurePrompt,
            expectJson = true,
            firebaseSchema = worldBlueprintSchema
        )

        return parseWorldDraftJson(
            AiProviderGateway.extractCleanJson(structuredResponse.text),
            groundedResponse.groundingSources
        )
    }

    private fun parseWorldDraftJson(
        rawJson: String,
        sources: List<GroundingSource>
    ): GeneratedWorldDraft {
        val json = JSONObject(rawJson)
        val archArr = json.optJSONArray("suggestedArchetypes")
        val archetypes = if (archArr != null) {
            (0 until archArr.length()).mapNotNull { archArr.optString(it)?.trim()?.takeIf { s -> s.isNotBlank() } }
        } else emptyList()

        val loreArr = json.optJSONArray("loreSections")
        val loreSections = buildList {
            if (loreArr != null) {
                for (i in 0 until loreArr.length()) {
                    val o = loreArr.optJSONObject(i) ?: continue
                    val title = o.optString("sectionTitle", "Раздел ${i + 1}").trim()
                    val body = o.optString("content", "").trim()
                    val kArr = o.optJSONArray("keywords")
                    val keysRaw = if (kArr != null) {
                        (0 until kArr.length()).joinToString(",") { kArr.optString(it) }
                    } else ""
                    if (body.isNotBlank()) {
                        add(
                            GeneratedLoreSection(
                                sectionTitle = title,
                                content = body,
                                keywordsCsv = normalizeTagsCsv(keysRaw),
                                isAlwaysActive = i < 2
                            )
                        )
                    }
                }
            }
        }

        return GeneratedWorldDraft(
            name = json.optString("name", "Новый мир").trim(),
            genre = json.optString("genre", "Авторский сеттинг").trim(),
            worldDescription = json.optString("worldDescription", "").trim(),
            diceSystem = json.optString("diceSystem", "d20 + модификатор против КС").trim(),
            magicOrTechSystem = json.optString("magicOrTechSystem", "Уникальная система способностей").trim(),
            narrativeTone = json.optString("narrativeTone", "Атмосферное приключение").trim(),
            customRules = json.optString("customRules", "").trim(),
            suggestedArchetypes = archetypes,
            defaultOpeningHook = json.optString("defaultOpeningHook", "").trim(),
            loreSections = loreSections,
            groundingSources = sources
        )
    }

    fun mapError(e: Throwable): String {
        if (e is CancellationException && e !is TimeoutCancellationException) throw e
        Log.e(TAG, "World generation error", e)
        return gateway.mapReadableError(e)
    }
}
