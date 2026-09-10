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

    @Upsert suspend fun save(entry: Entry)
    @Insert suspend fun insertAll(entries: List<Entry>)
    @Query("DELETE FROM entries WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM entries") suspend fun clear()
}

@Database(entities = [Entry::class], version = 3, exportSchema = true)
@TypeConverters(EntryConverters::class)
abstract class DaybookDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
    companion object {
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
    @TypeConverter fun encodeTags(tags: List<String>): String = kotlinx.serialization.json.Json.encodeToString(tags)
    @TypeConverter fun decodeTags(raw: String): List<String> = kotlinx.serialization.json.Json.decodeFromString(raw)
}
