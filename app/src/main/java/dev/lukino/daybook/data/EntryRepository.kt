package dev.lukino.daybook.data

import androidx.room.withTransaction
import dev.lukino.daybook.reminder.ReminderTarget
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serialize writes so a restore snapshot and its replacement cannot race with edits. */
class EntryRepository(private val database: DaybookDatabase) {
    private val writes = Mutex()
    private val dao get() = database.entries()
    val entries = dao.observeAll()
    private val memoDao get() = database.memos()
    val memos = memoDao.observeAll()

    // Scheduling and delivery share the write lock: stale notifications cannot race with edits/restores.
    suspend fun reconcileReminders(deliver: (ReminderTarget) -> Boolean, schedule: (List<ReminderTarget>) -> Unit) = writes.withLock {
        dao.all().forEach { entry ->
            if (deliver(ReminderTarget.from(entry))) dao.save(entry.copy(reminderDeliveredFor = entry.reminderAt))
        }
        memoDao.all().forEach { memo ->
            if (deliver(ReminderTarget.from(memo))) memoDao.save(memo.copy(reminderDeliveredFor = memo.reminderAt))
        }
        schedule(dao.all().map(ReminderTarget::from) + memoDao.all().map(ReminderTarget::from))
    }

    suspend fun allMemos(): List<Memo> = memoDao.all()
    suspend fun saveMemo(memo: Memo) = writes.withLock {
        memo.validate()
        val previous = memoDao.get(memo.id)
        // An editor's stale draft must not clear a delivery made while the editor was open.
        val value = memo.copy(reminderDeliveredFor = if (previous?.reminderAt == memo.reminderAt)
            previous?.reminderDeliveredFor ?: memo.reminderDeliveredFor else memo.reminderDeliveredFor)
        memoDao.save(value)
    }
    suspend fun deleteMemo(id: String) = writes.withLock { memoDao.delete(id) }
    suspend fun snapshotData(): Pair<List<Entry>, List<Memo>> = writes.withLock { dao.all() to memoDao.all() }
    suspend fun replaceData(entries: List<Entry>, memos: List<Memo>, beforeReplace: suspend (List<Entry>, List<Memo>) -> Unit) = writes.withLock {
        entries.forEach(Entry::validate); memos.forEach(Memo::validate)
        require(entries.map { it.id }.distinct().size == entries.size && memos.map { it.id }.distinct().size == memos.size) { "备份包含重复 ID" }
        beforeReplace(dao.all(), memoDao.all())
        database.withTransaction {
            dao.clear(); memoDao.clear()
            dao.insertAll(entries); memoDao.insertAll(memos)
        }
    }

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
