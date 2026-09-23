package dev.lukino.daybook.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries ORDER BY createdAt, id")
    fun observeAll(): Flow<List<Entry>>

    @Query("SELECT * FROM entries ORDER BY createdAt, id")
    suspend fun all(): List<Entry>

    @Query("SELECT * FROM entries WHERE id = :id") suspend fun get(id: String): Entry?

    @Upsert suspend fun save(entry: Entry)
    @Insert suspend fun insertAll(entries: List<Entry>)
    @Query("DELETE FROM entries WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM entries") suspend fun clear()
}

@Dao
interface MemoDao {
    @Query("SELECT * FROM memos ORDER BY updatedAt DESC, id") fun observeAll(): Flow<List<Memo>>
    @Query("SELECT * FROM memos ORDER BY updatedAt DESC, id") suspend fun all(): List<Memo>
    @Query("SELECT * FROM memos WHERE id = :id") suspend fun get(id: String): Memo?
    @Upsert suspend fun save(memo: Memo)
    @Insert suspend fun insertAll(memos: List<Memo>)
    @Query("DELETE FROM memos WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM memos") suspend fun clear()
}

@Dao
interface StandaloneReminderDao {
    @Query("SELECT * FROM reminders ORDER BY updatedAt DESC, id") fun observeAll(): Flow<List<StandaloneReminder>>
    @Query("SELECT * FROM reminders ORDER BY updatedAt DESC, id") suspend fun all(): List<StandaloneReminder>
    @Query("SELECT * FROM reminders WHERE id = :id") suspend fun get(id: String): StandaloneReminder?
    @Upsert suspend fun save(reminder: StandaloneReminder)
    @Insert suspend fun insertAll(reminders: List<StandaloneReminder>)
    @Query("DELETE FROM reminders WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM reminders") suspend fun clear()
}

@Database(entities = [Entry::class, Memo::class, StandaloneReminder::class], version = 6, exportSchema = true)
@TypeConverters(EntryConverters::class)
abstract class DaybookDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
    abstract fun memos(): MemoDao
    abstract fun reminders(): StandaloneReminderDao
    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN blocks TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE memos ADD COLUMN blocks TEXT NOT NULL DEFAULT '[]'")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS reminders (id TEXT NOT NULL, title TEXT NOT NULL, note TEXT NOT NULL, startDate TEXT NOT NULL, time TEXT NOT NULL, repeat TEXT NOT NULL, weekdays INTEGER NOT NULL, intervalDays INTEGER NOT NULL, enabled INTEGER NOT NULL, showInCalendar INTEGER NOT NULL, effectiveFrom INTEGER NOT NULL, revision INTEGER NOT NULL, deliveredFor TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS memos (id TEXT NOT NULL, body TEXT NOT NULL, date TEXT, reminderAt TEXT, reminderDeliveredFor TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN reminderAt TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE entries ADD COLUMN reminderDeliveredFor TEXT DEFAULT NULL")
            }
        }
    }
}

class EntryConverters {
    @TypeConverter fun encodeBlocks(blocks: List<BodyBlock>): String = kotlinx.serialization.json.Json.encodeToString(blocks)
    @TypeConverter fun decodeBlocks(raw: String): List<BodyBlock> = kotlinx.serialization.json.Json.decodeFromString(raw)
    @TypeConverter fun encodeTags(tags: List<String>): String = kotlinx.serialization.json.Json.encodeToString(tags)
    @TypeConverter fun decodeTags(raw: String): List<String> = kotlinx.serialization.json.Json.decodeFromString(raw)
}
