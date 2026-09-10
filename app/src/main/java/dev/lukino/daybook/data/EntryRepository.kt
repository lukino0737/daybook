package dev.lukino.daybook.data

import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serialize writes so a restore snapshot and its replacement cannot race with edits. */
class EntryRepository(private val database: DaybookDatabase) {
    private val writes = Mutex()
    private val dao get() = database.entries()
    val entries = dao.observeAll()

    suspend fun all(): List<Entry> = dao.all()
    suspend fun save(entry: Entry) = writes.withLock { entry.validate(); dao.save(entry) }
    suspend fun delete(id: String) = writes.withLock { dao.delete(id) }

    suspend fun replaceAll(entries: List<Entry>, beforeReplace: suspend (List<Entry>) -> Unit) = writes.withLock {
        entries.forEach(Entry::validate)
        require(entries.map { it.id }.toSet().size == entries.size) { "备份包含重复 ID" }
        beforeReplace(dao.all())
        database.withTransaction {
            dao.clear()
            dao.insertAll(entries)
        }
    }
}
