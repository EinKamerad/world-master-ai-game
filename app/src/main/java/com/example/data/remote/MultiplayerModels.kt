package com.example.data.remote

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class OnlineRoom(
    @DocumentId val id: String = "",
    val roomCode: String = "",
    val title: String = "",
    val hostUid: String = "",
    val hostName: String = "",
    val memberUids: List<String> = emptyList(),
    val worldName: String = "",
    val worldGenre: String = "",
    val worldDescription: String = "",
    val diceSystem: String = "",
    val magicOrTechSystem: String = "",
    val narrativeTone: String = "",
    val customRules: String = "",
    val useDiceAndStats: Boolean = true,
    val useInventory: Boolean = true,
    val useSpells: Boolean = true,
    val currentRound: Long = 1L,
    val roundPhase: String = "COLLECTING",
    val hiddenPlotArc: String = "",
    val hiddenNpcKnowledge: String = "",
    val hiddenPlotTriggers: String = "",
    val memorySummary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun pureFreeformMode(): Boolean = !useDiceAndStats && !useInventory && !useSpells

    fun hasHiddenPlot(): Boolean =
        hiddenPlotArc.isNotBlank() || hiddenNpcKnowledge.isNotBlank() || hiddenPlotTriggers.isNotBlank()
}

@Keep
@IgnoreExtraProperties
data class OnlineRoomPlayer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val roomCode: String = "",
    val heroName: String = "",
    val heroArchetype: String = "",
    val heroStats: String = "",
    val pendingAction: String = "",
    val pendingDiceRoll: String = "",
    val submittedRound: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OnlineRoundEntry(
    @DocumentId val id: String = "",
    val roomCode: String = "",
    val roundNumber: Long = 0L,
    val authorUid: String = "",
    val partyActionsSummary: String = "",
    val gmNarrative: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
