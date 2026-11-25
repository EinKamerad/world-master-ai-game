package com.example.data.transfer

import kotlin.random.Random

data class DiceRollResult(
    val formula: String,
    val rolls: List<Int>,
    val modifier: Int,
    val total: Int,
    val formattedSummary: String
)

object DiceRoller {
    private val diceRegex = Regex("""^\s*(\d*)d(\d+)\s*([+-]\s*\d+)?\s*$""", RegexOption.IGNORE_CASE)

    fun rollStandard(sides: Int, count: Int = 1, modifier: Int = 0): DiceRollResult {
        val safeCount = count.coerceIn(1, 20)
        val safeSides = sides.coerceIn(2, 1000)
        val rolls = List(safeCount) { Random.nextInt(1, safeSides + 1) }
        val sum = rolls.sum() + modifier
        val modStr = when {
            modifier > 0 -> " + $modifier"
            modifier < 0 -> " - ${-modifier}"
            else -> ""
        }
        val formula = "${safeCount}d${safeSides}$modStr"
        val rollsStr = if (safeCount == 1) "${rolls.first()}" else rolls.joinToString(", ", "[", "]")
        val critNote = when {
            safeCount == 1 && safeSides == 20 && rolls.first() == 20 -> " (КРИТ. УСПЕХ!)"
            safeCount == 1 && safeSides == 20 && rolls.first() == 1 -> " (КРИТ. ПРОВАЛ!)"
            else -> ""
        }
        val summary = "🎲 $formula → $rollsStr$modStr = $sum$critNote"
        return DiceRollResult(
            formula = formula,
            rolls = rolls,
            modifier = modifier,
            total = sum,
            formattedSummary = summary
        )
    }

    fun rollFormulaOrNull(rawFormula: String): DiceRollResult? {
        val cleaned = rawFormula.trim()
        if (cleaned.isEmpty()) return null
        // Extract first NdM +/- K pattern even if text like "+ МОД" follows
        val embeddedRegex = Regex("""(\d*)d(\d+)(\s*[+-]\s*\d+)?""", RegexOption.IGNORE_CASE)
        val match = diceRegex.matchEntire(cleaned) ?: embeddedRegex.find(cleaned) ?: return null
        val countStr = match.groupValues[1]
        val sidesStr = match.groupValues[2]
        val modRaw = match.groupValues[3].replace(" ", "")
        val count = if (countStr.isBlank()) 1 else (countStr.toIntOrNull() ?: 1)
        val sides = sidesStr.toIntOrNull() ?: return null
        val modifier = if (modRaw.isBlank()) 0 else (modRaw.toIntOrNull() ?: 0)
        return rollStandard(sides = sides, count = count, modifier = modifier)
    }
}
