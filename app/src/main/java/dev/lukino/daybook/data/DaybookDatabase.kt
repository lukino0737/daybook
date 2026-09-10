package dev.lukino.daybook.data

import androidx.room.*
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

@Database(entities = [Entry::class], version = 1, exportSchema = true)
abstract class DaybookDatabase : RoomDatabase() {
    abstract fun entries(): EntryDao
}
