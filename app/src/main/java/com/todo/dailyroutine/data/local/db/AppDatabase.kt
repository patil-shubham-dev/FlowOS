package com.todo.dailyroutine.data.local.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.todo.dailyroutine.data.local.dao.*
import com.todo.dailyroutine.data.local.entity.*

@Database(
    entities = [
        LocalTask::class, 
        LocalHabit::class, 
        LocalAiConfig::class, 
        LocalMessage::class, 
        LocalMemory::class, 
        ConversationSummary::class,
        LocalJournalEntry::class,
        LocalFlowScore::class,
        LocalJournalStreak::class,
        LocalBioData::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun habitDao(): HabitDao
    abstract fun aiConfigDao(): AiConfigDao
    abstract fun messageDao(): MessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun summaryDao(): SummaryDao
    abstract fun journalDao(): JournalDao
    abstract fun flowScoreDao(): FlowScoreDao
    abstract fun journalStreakDao(): JournalStreakDao
    abstract fun bioDataDao(): BioDataDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN scheduledTime TEXT")
                db.execSQL("ALTER TABLE habits ADD COLUMN scheduledTime TEXT")
                db.execSQL("ALTER TABLE ai_messages ADD COLUMN toolCalls TEXT")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS flow_scores (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        userId TEXT NOT NULL, 
                        date TEXT NOT NULL, 
                        score INTEGER NOT NULL, 
                        habitsCompleted INTEGER NOT NULL, 
                        totalHabits INTEGER NOT NULL, 
                        tasksCompleted INTEGER NOT NULL, 
                        totalTasks INTEGER NOT NULL, 
                        hasJournalEntry INTEGER NOT NULL, 
                        vibeRating INTEGER NOT NULL, 
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habits ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS journal_streaks (
                        userId TEXT PRIMARY KEY NOT NULL, 
                        currentStreak INTEGER NOT NULL DEFAULT 0, 
                        longestStreak INTEGER NOT NULL DEFAULT 0, 
                        lastEntryDate TEXT
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS bio_data (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        userId TEXT NOT NULL, 
                        date TEXT NOT NULL, 
                        steps INTEGER NOT NULL DEFAULT 0, 
                        sleepMinutes INTEGER NOT NULL DEFAULT 0, 
                        avgHeartRate INTEGER NOT NULL DEFAULT 0, 
                        hrvScore INTEGER NOT NULL DEFAULT 0, 
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "daily_routine_db"
                ).addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
