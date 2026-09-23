package dev.lukino.daybook.data

import androidx.room.withTransaction
import dev.lukino.daybook.reminder.ReminderTarget
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serialize writes so a restore snapshot and its replacement cannot race with edits. */
class EntryRepository(private val database: DaybookDatabase, val images: dev.lukino.daybook.media.BodyImageStore? = null) {
    private val writes = Mutex()
    @Volatile var restoreGeneration: Long = 0
        private set
    private val confirmedAiBatches = mutableSetOf<String>()

    /** Only preview confirmation may call this; validation and writes share the restore lock. */
    suspend fun saveAiBatch(token: String, generation: Long, entries: List<Entry>, memos: List<Memo>,
        replacement: Pair<Memo, Memo>? = null): Boolean = writes.withLock {
        if (token in confirmedAiBatches) return@withLock false
        require(generation == restoreGeneration) { "数据已从备份恢复，请重新生成预览" }
        require(entries.size + memos.size + (if (replacement == null) 0 else 1) in 1..20) { "每批保存1–20条内容" }
        entries.forEach(Entry::validate); memos.forEach(Memo::validate)
        require(entries.map { it.id }.distinct().size == entries.size && memos.map { it.id }.distinct().size == memos.size)
        (entries.flatMap { it.blocks } + memos.flatMap { it.blocks }).let { verifyImages(it) }
        database.withTransaction {
            replacement?.let { (expected, updated) ->
                val actual = memoDao.get(expected.id)
                require(actual != null && actual.copy(reminderDeliveredFor = null) == expected.copy(reminderDeliveredFor = null)) { "原便签已变化或被删除，请重新选择后整理" }
                require(updated.id == expected.id && updated.createdAt == expected.createdAt && updated.date == expected.date && updated.reminderAt == expected.reminderAt)
                require(RichBody.images(expected.blocks).isEmpty() && updated.blocks.isEmpty()) { "含图便签请将整理结果另存，原图文会保留" }
                updated.validate()
                memoDao.save(updated.copy(reminderDeliveredFor = actual.reminderDeliveredFor))
            }
            dao.insertAll(entries); memoDao.insertAll(memos)
        }
        confirmedAiBatches += token
        true
    }
    private val dao get() = database.entries()
    val entries = dao.observeAll()
    private val memoDao get() = database.memos()
    val memos = memoDao.observeAll()
    private val reminderDao get() = database.reminders()
    val reminders = reminderDao.observeAll()

    suspend fun allReminders(): List<StandaloneReminder> = reminderDao.all()
    suspend fun saveReminder(value: StandaloneReminder, now: java.time.Instant = java.time.Instant.now(),
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(), expectExisting: Boolean = false) = writes.withLock {
        // Delivery fields belong to persisted state, not an editor draft.
        value.copy(deliveredFor = null).validate()
        val old = reminderDao.get(value.id)
        require(!expectExisting || old != null) { "这条提醒已删除" }
        require(old == null || old.revision == value.revision) { "提醒计划已改变，请重新打开后编辑" }
        val changed = old == null || !old.sameSchedule(value)
        val resumed = old != null && !old.enabled && value.enabled
        if (value.repeat == RepeatKind.ONCE && (changed || resumed)) {
            require(java.time.LocalDate.parse(value.startDate).atTime(java.time.LocalTime.parse(value.time)).atZone(zone).toInstant() > now) { "请将单次提醒设为未来时间" }
        }
        val saved = value.copy(effectiveFrom = if (changed || resumed) now.toEpochMilli() else old!!.effectiveFrom,
            revision = if (old == null) 1 else if (changed || resumed) Math.addExact(old.revision, 1) else old.revision,
            deliveredFor = if (changed) null else old?.deliveredFor,
            createdAt = old?.createdAt ?: now.toEpochMilli(), updatedAt = now.toEpochMilli())
        saved.validate()
        reminderDao.save(saved)
    }
    suspend fun deleteReminder(id: String) = writes.withLock { reminderDao.delete(id) }

    // Scheduling and delivery share the write lock: stale notifications cannot race with edits/restores.
    suspend fun reconcileReminders(deliver: (ReminderTarget) -> Boolean, schedule: (List<ReminderTarget>) -> Unit, now: java.time.Instant = java.time.Instant.now(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()) = writes.withLock {
        dao.all().forEach { entry ->
            if (deliver(ReminderTarget.from(entry))) dao.save(entry.copy(reminderDeliveredFor = entry.reminderAt))
        }
        memoDao.all().forEach { memo ->
            if (deliver(ReminderTarget.from(memo))) memoDao.save(memo.copy(reminderDeliveredFor = memo.reminderAt))
        }
        reminderDao.all().forEach { reminder ->
            dev.lukino.daybook.reminder.RepeatRules.due(reminder, now, zone)?.let { due ->
                if (deliver(ReminderTarget.from(reminder, due))) reminderDao.save(reminder.copy(deliveredFor = due.toString()))
            }
        }
        schedule(dao.all().map(ReminderTarget::from) + memoDao.all().map(ReminderTarget::from) +
            reminderDao.all().map { ReminderTarget.from(it, dev.lukino.daybook.reminder.RepeatRules.next(it, now, zone)) })
    }

    suspend fun allMemos(): List<Memo> = memoDao.all()
    suspend fun saveMemo(memo: Memo) = writes.withLock {
        memo.validate()
        verifyImages(memo.blocks)
        val previous = memoDao.get(memo.id)
        // An editor's stale draft must not clear a delivery made while the editor was open.
        val value = memo.copy(reminderDeliveredFor = if (previous?.reminderAt == memo.reminderAt)
            previous?.reminderDeliveredFor ?: memo.reminderDeliveredFor else memo.reminderDeliveredFor)
        memoDao.save(value)
    }
    suspend fun deleteMemo(id: String) = writes.withLock { memoDao.delete(id) }
    suspend fun snapshotData(): Triple<List<Entry>, List<Memo>, List<StandaloneReminder>> = writes.withLock { Triple(dao.all(), memoDao.all(), reminderDao.all()) }
    suspend fun replaceData(entries: List<Entry>, memos: List<Memo>, reminders: List<StandaloneReminder> = emptyList(), beforeReplace: suspend (List<Entry>, List<Memo>, List<StandaloneReminder>) -> Unit) = writes.withLock {
        entries.forEach(Entry::validate); memos.forEach(Memo::validate); reminders.forEach(StandaloneReminder::validate)
        require(reminders.map { it.id }.distinct().size == reminders.size) { "备份包含重复提醒 ID" }
        require(entries.map { it.id }.distinct().size == entries.size && memos.map { it.id }.distinct().size == memos.size) { "备份包含重复 ID" }
        beforeReplace(dao.all(), memoDao.all(), reminderDao.all())
        (entries.flatMap { it.blocks } + memos.flatMap { it.blocks }).let { verifyImages(it) }
        database.withTransaction {
            dao.clear(); memoDao.clear(); reminderDao.clear()
            dao.insertAll(entries); memoDao.insertAll(memos); reminderDao.insertAll(reminders)
        }
        restoreGeneration++
    }

    private suspend fun verifyImages(blocks: List<BodyBlock>) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        RichBody.images(blocks).distinctBy { it.hash }.forEach { image ->
            requireNotNull(images) { "图片存储不可用" }.verify(image)
        }
    }
    suspend fun importImage(uri: android.net.Uri, owner: String): BodyImage = writes.withLock {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { requireNotNull(images).import(uri, owner) }
    }
    suspend fun collectImages() = writes.withLock {
        val referenced = dao.all().flatMap { RichBody.images(it.blocks) } + memoDao.all().flatMap { RichBody.images(it.blocks) }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { images?.collect(referenced) }
    }
    suspend fun <T> readSnapshot(block: suspend (List<Entry>, List<Memo>, List<StandaloneReminder>) -> T): T = writes.withLock {
        block(dao.all(), memoDao.all(), reminderDao.all())
    }

    suspend fun all(): List<Entry> = dao.all()
    suspend fun save(entry: Entry) = writes.withLock { entry.validate(); verifyImages(entry.blocks); dao.save(entry) }
    suspend fun delete(id: String) = writes.withLock { dao.delete(id) }

    suspend fun replaceAll(entries: List<Entry>, beforeReplace: suspend (List<Entry>) -> Unit) = writes.withLock {
        entries.forEach(Entry::validate)
        require(entries.map { it.id }.toSet().size == entries.size) { "备份包含重复 ID" }
        beforeReplace(dao.all())
        database.withTransaction {
            dao.clear()
            dao.insertAll(entries)
        }
        restoreGeneration++
    }
}
