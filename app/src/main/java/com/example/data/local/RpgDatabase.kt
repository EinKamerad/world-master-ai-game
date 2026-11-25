package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WorldEntity::class,
        WorldLoreEntity::class,
        CampaignEntity::class,
        ChatMessageEntity::class,
        QaMessageEntity::class,
        InventoryItemEntity::class,
        JournalEntryEntity::class,
        CustomSpellEntity::class,
        KnowledgeChunkEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class RpgDatabase : RoomDatabase() {
    abstract fun rpgDao(): RpgDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE campaigns ADD COLUMN useDiceAndStats INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE campaigns ADD COLUMN useInventory INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE campaigns ADD COLUMN useSpells INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE campaigns ADD COLUMN hiddenPlotArc TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE campaigns ADD COLUMN hiddenNpcKnowledge TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE campaigns ADD COLUMN hiddenPlotTriggers TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE campaigns ADD COLUMN hiddenPlotUpdatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `qa_messages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `campaignId` INTEGER NOT NULL,
                        `senderRole` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `groundingSourcesJson` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        FOREIGN KEY(`campaignId`) REFERENCES `campaigns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_qa_messages_campaignId` ON `qa_messages` (`campaignId`)")
            }
        }

        @Volatile
        private var INSTANCE: RpgDatabase? = null

        fun getInstance(context: Context): RpgDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RpgDatabase::class.java,
                    "rpg_worlds_local.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
