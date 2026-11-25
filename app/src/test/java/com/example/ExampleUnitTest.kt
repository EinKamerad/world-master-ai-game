package com.example

import com.example.data.local.CampaignEntity
import com.example.data.local.ChatMessageEntity
import com.example.data.local.WorldEntity
import com.example.data.local.normalizeTagsCsv
import com.example.data.local.normalizeTagsList
import com.example.data.transfer.CampaignTransferManager
import com.example.data.transfer.DiceRoller
import com.example.data.transfer.FullCampaignSnapshot
import com.example.data.transfer.estimateTokenCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `dice roller parses standard and custom formulas`() {
        val roll = DiceRoller.rollFormulaOrNull("3d6 + 4")
        assertNotNull(roll)
        assertEquals(3, roll!!.rolls.size)
        assertEquals(4, roll.modifier)
        assertTrue(roll.total in 7..22)

        val d20 = DiceRoller.rollStandard(sides = 20, count = 1, modifier = -1)
        assertTrue(d20.total in 0..19)
    }

    @Test
    fun `tag normalization deduplicates and strips hashes`() {
        val csv = normalizeTagsCsv("#НПС, Таверна; #нпс, квест_активен")
        assertEquals("нпс,таверна,квест_активен", csv)
        assertEquals(listOf("нпс", "таверна", "квест_активен"), normalizeTagsList(csv))
    }

    @Test
    fun `knowledge base markdown parser extracts sections and keywords`() {
        val md = """
            # Орден Серебряного Пламени
            Древнее братство паладинов, охраняющее северные рубежи Эльдхейма от демонов.
            
            ## Цитадель Рассвета
            Горная крепость из белого гранита над Запретным Разломом.
        """.trimIndent()

        val chunks = CampaignTransferManager.parseKnowledgeDocument("codex.md", md, campaignId = 1L)
        assertEquals(2, chunks.size)
        assertEquals("Орден Серебряного Пламени", chunks[0].sectionTitle)
        assertEquals("Цитадель Рассвета", chunks[1].sectionTitle)
        assertTrue(chunks[0].isAlwaysActive)
        assertTrue(chunks[0].keywordsList().isNotEmpty())

        val worldLore = CampaignTransferManager.parseWorldLoreDocument("universe_canon.md", md, worldId = 42L)
        assertEquals(2, worldLore.size)
        assertEquals(42L, worldLore[0].worldId)
        assertEquals("Орден Серебряного Пламени", worldLore[0].sectionTitle)
    }

    @Test
    fun `world entity splits suggested archetypes cleanly`() {
        val world = WorldEntity(
            id = 7L,
            name = "Архипелаг Небесных Кораблей",
            originType = "AI_ORIGINAL",
            genre = "Эфирный стимпанк",
            worldDescription = "Летающие острова над штормовым океаном.",
            diceSystem = "2d6 + характеристика",
            magicOrTechSystem = "Эфирные ядра и паровые руны",
            narrativeTone = "Приключенческий драйв",
            customRules = "Падение за борт требует спасброска ловкости.",
            suggestedArchetypesCsv = "Капитан дирижабля | Эфирный механик | Охотник за штормами"
        )
        assertEquals(
            listOf("Капитан дирижабля", "Эфирный механик", "Охотник за штормами"),
            world.archetypesList()
        )
    }

    @Test
    fun `markdown export and token estimator work`() {
        val camp = CampaignEntity(
            id = 1L,
            worldId = 7L,
            title = "Тестовая кампания",
            heroName = "Рейвен",
            heroArchetype = "Маг",
            heroStatsSummary = "ОЗ 20/20",
            heroBackstory = "Бывший ученик обсерватории.",
            settingName = "Эльдхейм",
            settingGenre = "Фэнтези",
            diceSystem = "d20",
            magicOrTechSystem = "Аркана",
            narrativeTone = "Героический",
            customRules = "Мана восстанавливается у костра",
            memorySummary = "Герой прибыл в город."
        )
        val msg = ChatMessageEntity(
            id = 1L,
            campaignId = 1L,
            senderRole = "GM",
            content = "Добро пожаловать в Эльдхейм!",
            estimatedTokens = estimateTokenCount("Добро пожаловать в Эльдхейм!")
        )
        val snap = FullCampaignSnapshot(
            campaign = camp,
            messages = listOf(msg),
            inventory = emptyList(),
            journal = emptyList(),
            spells = emptyList(),
            knowledge = emptyList()
        )
        val md = CampaignTransferManager.exportToMarkdown(snap)
        assertTrue(md.contains("# Тестовая кампания"))
        assertTrue(md.contains("Добро пожаловать в Эльдхейм!"))
        assertTrue(msg.estimatedTokens > 0)
    }

    @Test
    fun `api key pool parser and endpoint validator enforce security rules`() {
        val pool = com.example.data.preferences.parseApiKeyPool("key_one\nkey_two, key_one;  key_three ")
        assertEquals(listOf("key_one", "key_two", "key_three"), pool)

        // Local Antigravity & Ollama HTTP allowed
        assertTrue(
            com.example.data.preferences.AiEndpointValidator
                .normalizeAndValidateBaseUrl("http://10.0.2.2:3000/v1/")
                .isSuccess
        )

        // Cloud hosts must use HTTPS
        assertTrue(
            com.example.data.preferences.AiEndpointValidator
                .normalizeAndValidateBaseUrl("http://openrouter.ai/api/v1")
                .isFailure
        )
        assertTrue(
            com.example.data.preferences.AiEndpointValidator
                .normalizeAndValidateBaseUrl("https://openrouter.ai/api/v1")
                .isSuccess
        )

        // Metadata / dangerous schemes blocked
        assertTrue(
            com.example.data.preferences.AiEndpointValidator
                .normalizeAndValidateBaseUrl("http://169.254.169.254/latest")
                .isFailure
        )
        assertTrue(
            com.example.data.preferences.AiEndpointValidator
                .normalizeAndValidateBaseUrl("javascript:alert(1)")
                .isFailure
        )

        // Clean JSON extractor strips <think> and markdown fences
        val messyOutput = """
            <think>Подбираю характеристики заклинания...</think>
            ```json
            {"name": "Клинок Бездны", "rankOrLevel": "2 круг"}
            ```
        """.trimIndent()
        assertEquals(
            """{"name": "Клинок Бездны", "rankOrLevel": "2 круг"}""",
            com.example.ai.AiProviderGateway.extractCleanJson(messyOutput)
        )
    }

    @Test
    fun `model discovery parser handles openai list, cloud code map, and string arrays`() {
        val openAiJson = """{"object":"list","data":[{"id":"gemini-2.5-flash"},{"id":"claude-sonnet-4-5"}]}"""
        assertEquals(
            listOf("gemini-2.5-flash", "claude-sonnet-4-5"),
            com.example.ai.AiProviderGateway.parseModelIdsFromJson(openAiJson)
        )

        val cloudCodeMapJson = """{"models":{"gemini-2.5-pro":{"quota":100},"claude-3-7-sonnet":{"quota":50}}}"""
        val mapParsed = com.example.ai.AiProviderGateway.parseModelIdsFromJson(cloudCodeMapJson)
        assertTrue(mapParsed.contains("gemini-2.5-pro"))
        assertTrue(mapParsed.contains("claude-3-7-sonnet"))

        val stringArrayJson = """{"models":["models/gemini-2.5-flash","claude-3-5-sonnet"]}"""
        assertEquals(
            listOf("gemini-2.5-flash", "claude-3-5-sonnet"),
            com.example.ai.AiProviderGateway.parseModelIdsFromJson(stringArrayJson)
        )

        val emptyAccountsJson = """{"object":"list","data":[]}"""
        assertTrue(com.example.ai.AiProviderGateway.parseModelIdsFromJson(emptyAccountsJson).isEmpty())
    }

    @Test
    fun `freeform non-dnd mode and hidden plot helpers work for local and online sessions`() {
        val freeformPreset = com.example.data.local.WorldSettingPresets.firstOrNull { it.id == "freeform_story" }
        assertNotNull(freeformPreset)
        assertEquals(false, freeformPreset!!.defaultUseDice)
        assertEquals(false, freeformPreset.defaultUseInventory)
        assertEquals(false, freeformPreset.defaultUseSpells)

        val storyCampaign = CampaignEntity(
            id = 10L,
            worldId = 2L,
            title = "Полуночный экспресс",
            heroName = "Элиас Морн",
            heroArchetype = "Попутчик",
            heroStatsSummary = "Наблюдательный",
            settingName = "Вальдштадт",
            settingGenre = "Словеска без ДнД",
            diceSystem = "Без кубиков",
            magicOrTechSystem = "Нет",
            narrativeTone = "Художественная проза",
            customRules = "",
            useDiceAndStats = false,
            useInventory = false,
            useSpells = false,
            hiddenPlotArc = "В купе №7 едет курьер тайной канцелярии.",
            hiddenNpcKnowledge = "Проводник не знает истинного имени курьера и не должен подозревать подмену."
        )
        assertTrue(storyCampaign.isPureFreeformMode())
        assertTrue(storyCampaign.hasHiddenPlot())

        val onlineRoom = com.example.data.remote.OnlineRoom(
            roomCode = "RPG777",
            title = "Ночной дозор",
            useDiceAndStats = false,
            useInventory = false,
            useSpells = false,
            hiddenNpcKnowledge = "Стражник у ворот не знает о заговоре."
        )
        assertTrue(onlineRoom.pureFreeformMode())
        assertTrue(onlineRoom.hasHiddenPlot())

        val plan = com.example.ai.HiddenPlotPlan(
            plotArc = "Акт 1: Тайная встреча",
            npcKnowledgeBoundaries = "Барон не знает о письме",
            triggersAndConsequences = "Если игрок покажет перстень — барон отступит"
        )
        assertTrue(plan.isNotEmpty())
    }
}


