package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CustomSpellEntity
import com.example.data.local.JournalEntryEntity
import com.example.data.local.RpgDatabase
import com.example.data.local.WorldSettingPresets
import com.example.data.local.normalizeTagsList
import com.example.data.repository.MemoryHealthLevel
import com.example.data.repository.RpgRepository
import com.example.data.transfer.CampaignTransferManager
import com.example.data.transfer.DiceRoller
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: RpgDatabase
    private lateinit var repo: RpgRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, RpgDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RpgRepository(db.rpgDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Мастер Миров", appName)
    }

    @Test
    fun `room persistence journal tags spells and memory full warning work`() = runTest {
        val campId = repo.createCampaignFromPreset(
            preset = WorldSettingPresets.first(),
            maxTokens = 2000
        )
        assertTrue(campId > 0L)

        // Add journal entry with tags
        repo.saveJournalEntry(
            JournalEntryEntity(
                campaignId = campId,
                title = "Архимаг Вейлон",
                entryType = "НПС",
                summary = "Хранитель Обсидиановой Башни",
                tagsCsv = "#маг, Башня, #Квест_Активен"
            )
        )

        // Add custom spell
        repo.saveSpell(
            CustomSpellEntity(
                campaignId = campId,
                name = "Пепельный Вихрь",
                rankOrLevel = "3 круг",
                schoolOrType = "Воплощение",
                resourceCost = "1 ячейка",
                castTime = "1 действие",
                rangeInfo = "18 м",
                durationInfo = "Мгновенно",
                diceFormula = "3d8 + 4",
                description = "Огненный вихрь с искрами",
                tagsCsv = "огонь,урон"
            )
        )

        val snapshot = repo.buildSnapshot(campId)
        assertNotNull(snapshot)
        assertTrue(snapshot!!.journal.any { "маг" in it.tagsList() && "башня" in it.tagsList() })
        assertTrue(snapshot.spells.any { it.name == "Пепельный Вихрь" })

        // Fill messages to trigger CRITICAL_FULL memory alert
        val longBlock = "Сцена в древнем подземелье Эльдхейма. ".repeat(180)
        val m1 = repo.addMessage(campId, "USER", longBlock)
        val m2 = repo.addMessage(campId, "GM", longBlock)

        val floodedSnapshot = repo.buildSnapshot(campId)!!
        val fullStats = repo.calculateMemoryStats(
            campaign = floodedSnapshot.campaign,
            allMessages = floodedSnapshot.messages,
            inventory = floodedSnapshot.inventory,
            journal = floodedSnapshot.journal,
            spells = floodedSnapshot.spells,
            worldLore = emptyList(),
            knowledge = floodedSnapshot.knowledge,
            warningThresholdPercent = 75
        )
        assertEquals(MemoryHealthLevel.CRITICAL_FULL, fullStats.healthLevel)

        // Compress older messages into chronicle and verify memory drops back to NORMAL
        repo.applyMemoryCompression(
            campaignId = campId,
            newSummary = "Краткая сводка спуска в подземелье Эльдхейма.",
            archivedMessageIds = listOf(m1, m2)
        )
        val optimizedSnapshot = repo.buildSnapshot(campId)!!
        val afterStats = repo.calculateMemoryStats(
            campaign = optimizedSnapshot.campaign,
            allMessages = optimizedSnapshot.messages,
            inventory = optimizedSnapshot.inventory,
            journal = optimizedSnapshot.journal,
            spells = optimizedSnapshot.spells,
            worldLore = emptyList(),
            knowledge = optimizedSnapshot.knowledge,
            warningThresholdPercent = 75
        )
        assertEquals(MemoryHealthLevel.NORMAL, afterStats.healthLevel)
        // Messages remain preserved for reading & export!
        assertEquals(floodedSnapshot.messages.size, optimizedSnapshot.messages.size)
    }

    @Test
    fun `worlds library separates world creation from starting new game with character`() = runTest {
        val draft = com.example.ai.GeneratedWorldDraft(
            name = "Неверленд Ведьмака",
            genre = "Тёмное славянское фэнтези",
            worldDescription = "Континент после Сопряжения Сфер, полный чудовищ и интриг чародеев.",
            diceSystem = "d10 + навык",
            magicOrTechSystem = "Ведьмачьи Знаки, эликсиры и Первозданный Хаос",
            narrativeTone = "Мрачный моральный выбор",
            customRules = "Серебро против чудовищ, сталь против людей.",
            suggestedArchetypes = listOf("Ведьмак Школы Волка", "Чародейка Аретузы"),
            defaultOpeningHook = "На доске объявлений в Вызиме висит контракт на грифона.",
            loreSections = listOf(
                com.example.ai.GeneratedLoreSection(
                    sectionTitle = "Школы ведьмаков",
                    content = "Цехи охотников на чудовищ: Волк, Кот, Грифон, Медведь и Змея.",
                    keywordsCsv = "ведьмак,школа,чудовища",
                    isAlwaysActive = true
                )
            )
        )

        val worldId = repo.saveGeneratedWorldToLibrary(
            draft = draft,
            originType = "EXISTING_UNIVERSE",
            franchiseSource = "Ведьмак"
        )
        val savedWorld = repo.getWorldById(worldId)
        assertNotNull(savedWorld)
        assertEquals("EXISTING_UNIVERSE", savedWorld!!.originType)
        assertEquals(2, savedWorld.archetypesList().size)

        val newCampId = repo.startNewGameInWorld(
            world = savedWorld,
            campaignTitle = "Контракт у Вызимы",
            heroName = "Эскель Северный",
            heroArchetype = savedWorld.archetypesList().first(),
            heroStatsSummary = "ОЗ 30/30 | Интоксикация 0/100",
            heroBackstory = "Прошёл Испытание Травами в Каэр Морхене.",
            customOpeningScene = "",
            maxTokens = 8000
        )
        val campSnap = repo.buildSnapshot(newCampId)
        assertNotNull(campSnap)
        assertEquals(worldId, campSnap!!.campaign.worldId)
        assertEquals("Неверленд Ведьмака", campSnap.campaign.settingName)
        assertEquals("Эскель Северный", campSnap.campaign.heroName)
    }

    @Test
    fun `knowledge base import and json backup roundtrip`() = runTest {
        val campId = repo.createCampaignFromPreset(WorldSettingPresets[1])
        val mdLore = """
            # Мегакорпорация Оникс
            Контролирует орбитальные лифты и биочипы Нео-Киото.
            
            ## Сектор 9
            Подземный рынок редких кибердек и нелегальных ИИ-ядер.
        """.trimIndent()

        val chunks = CampaignTransferManager.parseKnowledgeDocument("lore.md", mdLore, campId)
        assertEquals(2, chunks.size)
        repo.insertKnowledgeChunks(chunks)

        val snap = repo.buildSnapshot(campId)!!
        val exportedJson = CampaignTransferManager.exportToJsonBackup(snap)
        val restoredSnap = CampaignTransferManager.parseBackupJson(exportedJson)
        assertEquals(snap.campaign.title, restoredSnap.campaign.title)
        assertEquals(2, restoredSnap.knowledge.size)

        val roll = DiceRoller.rollFormulaOrNull("2d8 + 3")
        assertNotNull(roll)
        assertTrue(roll!!.total in 5..19)
        assertEquals(listOf("нпс", "квест"), normalizeTagsList("#НПС, квест, нпс"))
    }
}
