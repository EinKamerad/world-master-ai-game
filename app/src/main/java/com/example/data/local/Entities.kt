package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "worlds")
data class WorldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val originType: String = "CUSTOM", // "PRESET", "AI_ORIGINAL", "EXISTING_UNIVERSE", "CUSTOM"
    val franchiseSource: String = "",
    val genre: String,
    val worldDescription: String,
    val diceSystem: String,
    val magicOrTechSystem: String,
    val narrativeTone: String,
    val customRules: String,
    val suggestedArchetypesCsv: String = "",
    val defaultOpeningHook: String = "",
    val groundingSourcesJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun archetypesList(): List<String> {
        if (suggestedArchetypesCsv.isBlank()) return emptyList()
        return suggestedArchetypesCsv.split("|", ",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun groundingSources(): List<GroundingSource> = parseGroundingSources(groundingSourcesJson)
}

@Entity(
    tableName = "world_lore",
    foreignKeys = [
        ForeignKey(
            entity = WorldEntity::class,
            parentColumns = ["id"],
            childColumns = ["worldId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("worldId")]
)
data class WorldLoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val worldId: Long,
    val sourceTitle: String,
    val sectionTitle: String,
    val keywordsCsv: String,
    val content: String,
    val isAlwaysActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun keywordsList(): List<String> = normalizeTagsList(keywordsCsv)
}

@Entity(tableName = "campaigns")
data class CampaignEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val worldId: Long = 0,
    val title: String,
    val heroName: String,
    val heroArchetype: String,
    val heroStatsSummary: String,
    val heroBackstory: String = "",
    val settingName: String,
    val settingGenre: String,
    val diceSystem: String,
    val magicOrTechSystem: String,
    val narrativeTone: String,
    val customRules: String,
    val useDiceAndStats: Boolean = true,
    val useInventory: Boolean = true,
    val useSpells: Boolean = true,
    val hiddenPlotArc: String = "",
    val hiddenNpcKnowledge: String = "",
    val hiddenPlotTriggers: String = "",
    val hiddenPlotUpdatedAt: Long = 0L,
    val memorySummary: String = "",
    val compressedChaptersCount: Int = 0,
    val maxContextTokens: Int = 8000,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun isPureFreeformMode(): Boolean = !useDiceAndStats && !useInventory && !useSpells

    fun hasHiddenPlot(): Boolean =
        hiddenPlotArc.isNotBlank() || hiddenNpcKnowledge.isNotBlank() || hiddenPlotTriggers.isNotBlank()
}

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = CampaignEntity::class,
            parentColumns = ["id"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("campaignId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val senderRole: String, // "USER", "GM", "SYSTEM"
    val content: String,
    val diceRollInfo: String? = null,
    val groundingSourcesJson: String = "[]",
    val estimatedTokens: Int = 0,
    val isArchivedInSummary: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun groundingSources(): List<GroundingSource> = parseGroundingSources(groundingSourcesJson)
}

@Entity(
    tableName = "qa_messages",
    foreignKeys = [
        ForeignKey(
            entity = CampaignEntity::class,
            parentColumns = ["id"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("campaignId")]
)
data class QaMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val senderRole: String, // "USER" or "ADVISOR"
    val content: String,
    val groundingSourcesJson: String = "[]",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun groundingSources(): List<GroundingSource> = parseGroundingSources(groundingSourcesJson)
}

data class GroundingSource(
    val title: String,
    val uri: String
)

fun serializeGroundingSources(sources: List<GroundingSource>): String {
    val array = JSONArray()
    sources.forEach { src ->
        val obj = JSONObject()
        obj.put("title", src.title)
        obj.put("uri", src.uri)
        array.put(obj)
    }
    return array.toString()
}

fun parseGroundingSources(rawJson: String): List<GroundingSource> {
    if (rawJson.isBlank() || rawJson == "[]") return emptyList()
    return runCatching {
        val arr = JSONArray(rawJson)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val title = obj.optString("title").trim()
                val uri = obj.optString("uri").trim()
                if (uri.isNotEmpty()) {
                    add(GroundingSource(title = title.ifEmpty { uri }, uri = uri))
                }
            }
        }
    }.getOrDefault(emptyList())
}

@Entity(
    tableName = "inventory_items",
    foreignKeys = [
        ForeignKey(
            entity = CampaignEntity::class,
            parentColumns = ["id"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("campaignId")]
)
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val name: String,
    val category: String, // Оружие, Броня, Расходники, Артефакты, Ресурсы, Прочее
    val quantity: Int = 1,
    val rarityOrTier: String = "Обычный",
    val bonusOrEffect: String = "",
    val description: String = "",
    val isEquipped: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "journal_entries",
    foreignKeys = [
        ForeignKey(
            entity = CampaignEntity::class,
            parentColumns = ["id"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("campaignId")]
)
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val title: String,
    val entryType: String, // НПС, Локация, Квест, Фракция, Заметка
    val summary: String,
    val details: String = "",
    val tagsCsv: String = "",
    val statusOrDisposition: String = "",
    val isPinned: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun tagsList(): List<String> = normalizeTagsList(tagsCsv)
}

@Entity(
    tableName = "custom_spells",
    foreignKeys = [
        ForeignKey(
            entity = CampaignEntity::class,
            parentColumns = ["id"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("campaignId")]
)
data class CustomSpellEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val name: String,
    val rankOrLevel: String,
    val schoolOrType: String,
    val resourceCost: String,
    val castTime: String,
    val rangeInfo: String,
    val durationInfo: String,
    val diceFormula: String = "",
    val description: String,
    val tagsCsv: String = "",
    val isPrepared: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun tagsList(): List<String> = normalizeTagsList(tagsCsv)
}

@Entity(
    tableName = "knowledge_chunks",
    foreignKeys = [
        ForeignKey(
            entity = CampaignEntity::class,
            parentColumns = ["id"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("campaignId")]
)
data class KnowledgeChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val sourceTitle: String,
    val sectionTitle: String,
    val keywordsCsv: String,
    val content: String,
    val isAlwaysActive: Boolean = false,
    val importedAt: Long = System.currentTimeMillis()
) {
    fun keywordsList(): List<String> = normalizeTagsList(keywordsCsv)
}

fun normalizeTagsCsv(raw: String): String {
    return raw.split(",", ";", "\n")
        .map { it.trim().trimStart('#').lowercase() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(",")
}

fun normalizeTagsList(csv: String): List<String> {
    if (csv.isBlank()) return emptyList()
    return csv.split(",")
        .map { it.trim().trimStart('#') }
        .filter { it.isNotBlank() }
        .distinct()
}

data class SettingPreset(
    val id: String,
    val title: String,
    val genre: String,
    val worldDescription: String,
    val diceSystem: String,
    val magicOrTechSystem: String,
    val narrativeTone: String,
    val customRules: String,
    val suggestedArchetypes: List<String>,
    val defaultHeroName: String,
    val defaultHeroArchetype: String,
    val defaultHeroStats: String,
    val openingHook: String,
    val defaultUseDice: Boolean = true,
    val defaultUseInventory: Boolean = true,
    val defaultUseSpells: Boolean = true
)

val WorldSettingPresets = listOf(
    SettingPreset(
        id = "dnd_fantasy",
        title = "Хроники Эльдхейма",
        genre = "Тёмное и героическое фэнтези (D&D)",
        worldDescription = "Континент древних руин, пограничных крепостей и угасающих арканных печатей, сдерживающих Запретный Разлом.",
        diceSystem = "d20 + модификатор против КС (10 легко, 15 средне, 20 трудно)",
        magicOrTechSystem = "Арканное плетение, ячейки заклинаний и древние руны",
        narrativeTone = "Атмосферное приключение с живыми НПС и тактическими выборами",
        customRules = "Критический успех на 20, критический провал на 1. Короткий отдых восстанавливает часть сил, долгий — ячейки заклинаний.",
        suggestedArchetypes = listOf(
            "Следопыт-чародей • Ур. 3",
            "Паладин Пепельной Клятвы • Ур. 3",
            "Плут Теневых Гильдий • Ур. 3",
            "Боевой Маг Руин • Ур. 3"
        ),
        defaultHeroName = "Каэль Ветролом",
        defaultHeroArchetype = "Следопыт-чародей • Ур. 3",
        defaultHeroStats = "ОЗ 28/28 | КД 15 | СИЛ +1, ЛОВ +3, ТЕЛ +2, ИНТ +2, МДР +2, ХАР +0",
        openingHook = "Дождь барабанит по сланцевой крыше приграничной таверны «Расколотый Компас». Старый картограф Северин придвигает к вам обугленный тубус с картой Запретного Разлома и оглядывается на мрачных наёмников у камина: «Если мы не доберся до Шпиля до новолуния, печать рухнет. Ты в деле?»"
    ),
    SettingPreset(
        id = "freeform_story",
        title = "Полуночный Экспресс: Словеска",
        genre = "Чистая словесная ролевая (без ДнД)",
        worldDescription = "Атмосферная литературная ролевая без кубиков и цифр: глубокие диалоги, психология персонажей, тайны старинного города и живые отношения.",
        diceSystem = "Без кубиков — исход действий определяется логикой сцены, характером и отыгрышем",
        magicOrTechSystem = "Реализм с тонкими мистическими полутонами по желанию игрока",
        narrativeTone = "Художественная проза, внимание к эмоциям, деталям и диалогам",
        customRules = "Никаких числовых проверок КС, уровней и механики D&D: фокус на истории и живых характерах.",
        suggestedArchetypes = listOf(
            "Писатель в поисках утраченной рукописи",
            "Частный сыщик со старым долгом",
            "Аристократ инкогнито",
            "Таинственный попутчик из купе №7"
        ),
        defaultHeroName = "Элиас Морн",
        defaultHeroArchetype = "Попутчик с тайным прошлым",
        defaultHeroStats = "Словесный режим: наблюдательный, сдержанный, хранит старое письмо с сургучной печатью",
        openingHook = "Ночной экспресс мягко покачивается на стыках рельсов, рассекая осенний туман. В полупустом вагоне-ресторане за соседним столиком незнакомка в тёмно-зелёном пальто негромко произносит, не поднимая взгляда от чашки кофе: «Вы ведь тоже едете в Вальдштадт не ради горного воздуха?»",
        defaultUseDice = false,
        defaultUseInventory = false,
        defaultUseSpells = false
    ),
    SettingPreset(
        id = "cyberpunk_neon",
        title = "Неоновый Синдикат 2099",
        genre = "Киберпанк / Техно-триллер",
        worldDescription = "Многоуровневый мегаполис Нео-Киото, где мегакорпорации правят с орбитальных шпилей, а на залитых неоном улицах кипят войны синдикатов и нетраннеров.",
        diceSystem = "d10 + навык против сложности (12 штатно, 15 опасно, 18 грань возможного)",
        magicOrTechSystem = "Киберимпланты, нетраннинг-скрипты (ОЗУ) и боевые нейромоды",
        narrativeTone = "Нуар, высокие ставки, интриги мегакорпораций и уличный драйв",
        customRules = "Перегрузка нейросети при сложных взломах повышает уровень тревоги ICE. Репутация на улицах открывает доступ к чёрному рынку.",
        suggestedArchetypes = listOf(
            "Нетраннер-оперативник • Ранг 2",
            "Соло-наёмник с киберклинками • Ранг 2",
            "Техник дронов и имплантов • Ранг 2",
            "Корпоративный фиксер • Ранг 2"
        ),
        defaultHeroName = "Виктор «Спектр» Корсак",
        defaultHeroArchetype = "Нетраннер-оперативник • Ранг 2",
        defaultHeroStats = "ОЗ 24/24 | Броня 12 | ОЗУ 6/6 | РЕФ +3, ТЕХ +3, ИНТ +4, ХЛАД +2",
        openingHook = "Голографическая реклама «Арасака-Биотех» отражается в лужах нижнего сектора Нео-Киото. Ваш нейролинк ловит зашифрованный сигнал бедствия от связной Миры прямо из закрытой лаборатории на 88-м этаже башни «Оникс»."
    ),
    SettingPreset(
        id = "space_odyssey",
        title = "Звёздный Рубеж: Эхо Пустоты",
        genre = "Космическая опера и исследование",
        worldDescription = "Дальний сектор Галактики с вратами Предтеч, независимыми колониями, космическими пиратами и загадочными псионическими аномалиями.",
        diceSystem = "2d6 + характеристика (7–9 успех с ценой, 10+ чистый успех)",
        magicOrTechSystem = "Псионический резонанс, квантовые модули и корабельные системы",
        narrativeTone = "Научно-фантастическая драма, дипломатия фракций и древние тайны космоса",
        customRules = "Запас энергии реактора и щитов критичен при высадках на аномальные миры.",
        suggestedArchetypes = listOf(
            "Навигатор-псионик",
            "Штурмовик Звёздного Флота",
            "Вольный контрабандист",
            "Ксеноархеолог"
        ),
        defaultHeroName = "Капитан Ария Вэнс",
        defaultHeroArchetype = "Навигатор-псионик",
        defaultHeroStats = "ОЗ 22/22 | Щиты 14 | Пси-заряд 5/5 | РАЗУМ +3, МАНЁВР +2, ВОЛЯ +3",
        openingHook = "Сканеры фрегата «Аврора-7» фиксируют дрейфующий дредноут Предтеч на орбите умирающей звезды Кеплер-442. Шлюзы корабля-призрака внезапно открываются, посылая импульс на частоте вашего пси-импланта."
    ),
    SettingPreset(
        id = "eldritch_noir",
        title = "Туманный Аркхэм 1926",
        genre = "Готический детектив и мистический хоррор",
        worldDescription = "Дождливая Новая Англия 1920-х годов: закрытые университетские архивы, портовые легенды, тайные ложи и древние силы за гранью понимания.",
        diceSystem = "d100 процентная проверка навыка (меньше или равно значению навыка — успех)",
        magicOrTechSystem = "Оккультные ритуалы, запретные фолианты и цена Рассудка",
        narrativeTone = "Напряжённое расследование, густая атмосфера тайны и хрупкость разума",
        customRules = "Столкновение с необъяснимым требует проверки Рассудка. Ритуалы расходуют Волю и Рассудок.",
        suggestedArchetypes = listOf(
            "Частный детектив",
            "Профессор оккультной истории",
            "Журналист-расследователь",
            "Медиум-спиритуалист"
        ),
        defaultHeroName = "Артур Блэквуд",
        defaultHeroArchetype = "Частный детектив",
        defaultHeroStats = "ОЗ 14/14 | Рассудок 72/80 | Внимание 65%, Оккультизм 55%, Убеждение 60%",
        openingHook = "В полночь к вам в кабинет врывается промокший смотритель маяка Иннсмута. На столе остаётся бронзовый астролябиум, покрытый нетающим инеем, и страница дневника с вашим именем."
    )
)
