package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RpgDao {
    // --- Worlds Library ---
    @Query("SELECT * FROM worlds ORDER BY updatedAt DESC")
    fun observeWorlds(): Flow<List<WorldEntity>>

    @Query("SELECT * FROM worlds ORDER BY updatedAt DESC")
    suspend fun getAllWorldsOnce(): List<WorldEntity>

    @Query("SELECT * FROM worlds WHERE id = :id LIMIT 1")
    suspend fun getWorldById(id: Long): WorldEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorld(world: WorldEntity): Long

    @Update
    suspend fun updateWorld(world: WorldEntity)

    @Delete
    suspend fun deleteWorld(world: WorldEntity)

    // --- World Lore (Reusable Knowledge per World) ---
    @Query("SELECT * FROM world_lore ORDER BY isAlwaysActive DESC, createdAt DESC")
    fun observeAllWorldLore(): Flow<List<WorldLoreEntity>>

    @Query("SELECT * FROM world_lore WHERE worldId = :worldId ORDER BY isAlwaysActive DESC, createdAt DESC")
    fun observeWorldLore(worldId: Long): Flow<List<WorldLoreEntity>>

    @Query("SELECT * FROM world_lore WHERE worldId = :worldId ORDER BY isAlwaysActive DESC, createdAt DESC")
    suspend fun getWorldLoreOnce(worldId: Long): List<WorldLoreEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorldLoreList(items: List<WorldLoreEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorldLore(item: WorldLoreEntity): Long

    @Update
    suspend fun updateWorldLore(item: WorldLoreEntity)

    @Delete
    suspend fun deleteWorldLore(item: WorldLoreEntity)

    // --- Campaigns (Games & Characters) ---
    @Query("SELECT * FROM campaigns ORDER BY updatedAt DESC")
    fun observeCampaigns(): Flow<List<CampaignEntity>>

    @Query("SELECT * FROM campaigns ORDER BY updatedAt DESC")
    suspend fun getAllCampaignsOnce(): List<CampaignEntity>

    @Query("SELECT * FROM campaigns WHERE id = :id LIMIT 1")
    fun observeCampaignById(id: Long): Flow<CampaignEntity?>

    @Query("SELECT * FROM campaigns WHERE id = :id LIMIT 1")
    suspend fun getCampaignById(id: Long): CampaignEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCampaign(campaign: CampaignEntity): Long

    @Update
    suspend fun updateCampaign(campaign: CampaignEntity)

    @Delete
    suspend fun deleteCampaign(campaign: CampaignEntity)

    // --- Chat Messages ---
    @Query("SELECT * FROM chat_messages WHERE campaignId = :campaignId ORDER BY timestamp ASC, id ASC")
    fun observeMessages(campaignId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE campaignId = :campaignId ORDER BY timestamp ASC, id ASC")
    suspend fun getMessagesOnce(campaignId: Long): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE campaignId = :campaignId AND isArchivedInSummary = 0 ORDER BY timestamp ASC, id ASC")
    suspend fun getActiveUnarchivedMessages(campaignId: Long): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET isArchivedInSummary = 1 WHERE id IN (:messageIds)")
    suspend fun markMessagesArchived(messageIds: List<Long>)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    // --- Q&A Side-Chat Messages («Чат с вопросами») ---
    @Query("SELECT * FROM qa_messages WHERE campaignId = :campaignId ORDER BY timestamp ASC, id ASC")
    fun observeQaMessages(campaignId: Long): Flow<List<QaMessageEntity>>

    @Query("SELECT * FROM qa_messages WHERE campaignId = :campaignId ORDER BY timestamp ASC, id ASC")
    suspend fun getQaMessagesOnce(campaignId: Long): List<QaMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQaMessage(message: QaMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQaMessages(messages: List<QaMessageEntity>)

    @Query("DELETE FROM qa_messages WHERE campaignId = :campaignId")
    suspend fun clearQaMessagesForCampaign(campaignId: Long)

    // --- Inventory ---
    @Query("SELECT * FROM inventory_items WHERE campaignId = :campaignId ORDER BY isEquipped DESC, category ASC, name ASC")
    fun observeInventory(campaignId: Long): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE campaignId = :campaignId ORDER BY isEquipped DESC, name ASC")
    suspend fun getInventoryOnce(campaignId: Long): List<InventoryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryItem(item: InventoryItemEntity): Long

    @Update
    suspend fun updateInventoryItem(item: InventoryItemEntity)

    @Delete
    suspend fun deleteInventoryItem(item: InventoryItemEntity)

    // --- Journal ---
    @Query("SELECT * FROM journal_entries WHERE campaignId = :campaignId ORDER BY isPinned DESC, updatedAt DESC")
    fun observeJournal(campaignId: Long): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE campaignId = :campaignId ORDER BY isPinned DESC, updatedAt DESC")
    suspend fun getJournalOnce(campaignId: Long): List<JournalEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalEntry(entry: JournalEntryEntity): Long

    @Update
    suspend fun updateJournalEntry(entry: JournalEntryEntity)

    @Delete
    suspend fun deleteJournalEntry(entry: JournalEntryEntity)

    // --- Custom Spells & Abilities ---
    @Query("SELECT * FROM custom_spells WHERE campaignId = :campaignId ORDER BY isPrepared DESC, name ASC")
    fun observeSpells(campaignId: Long): Flow<List<CustomSpellEntity>>

    @Query("SELECT * FROM custom_spells WHERE campaignId = :campaignId ORDER BY isPrepared DESC, name ASC")
    suspend fun getSpellsOnce(campaignId: Long): List<CustomSpellEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpell(spell: CustomSpellEntity): Long

    @Update
    suspend fun updateSpell(spell: CustomSpellEntity)

    @Delete
    suspend fun deleteSpell(spell: CustomSpellEntity)

    // --- Knowledge Base (Session Lorebook) ---
    @Query("SELECT * FROM knowledge_chunks WHERE campaignId = :campaignId ORDER BY isAlwaysActive DESC, importedAt DESC")
    fun observeKnowledgeChunks(campaignId: Long): Flow<List<KnowledgeChunkEntity>>

    @Query("SELECT * FROM knowledge_chunks WHERE campaignId = :campaignId ORDER BY isAlwaysActive DESC, importedAt DESC")
    suspend fun getKnowledgeChunksOnce(campaignId: Long): List<KnowledgeChunkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKnowledgeChunks(chunks: List<KnowledgeChunkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKnowledgeChunk(chunk: KnowledgeChunkEntity): Long

    @Update
    suspend fun updateKnowledgeChunk(chunk: KnowledgeChunkEntity)

    @Delete
    suspend fun deleteKnowledgeChunk(chunk: KnowledgeChunkEntity)
}
