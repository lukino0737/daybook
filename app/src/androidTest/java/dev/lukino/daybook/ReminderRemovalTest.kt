package dev.lukino.daybook

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.lukino.daybook.data.*
import dev.lukino.daybook.reminder.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.*

class ReminderRemovalTest {
    private lateinit var db: DaybookDatabase
    private lateinit var repo: EntryRepository
    private val future = LocalDateTime.now().plusDays(2).withSecond(0).withNano(0)
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), DaybookDatabase::class.java).build(); repo = EntryRepository(db) }
    @After fun cleanup() { db.close() }
    private suspend fun rows() = ReminderListRules.items(repo.all(), repo.allMemos(), repo.allReminders(), Instant.now(), ZoneId.systemDefault())
    @Test fun attachedRemindersPreserveEveryBusinessFieldAndAreNotScheduled() = runBlocking {
        val entries = listOf(Entry(kind = EntryKind.TASK, title = "任务", note = "正文", tags = listOf("标签"), reminderAt = future.toString()),
            Entry(title = "日程", date = future.toLocalDate().toString(), reminderAt = future.toString()))
        val memo = Memo(body = "保留便签", date = future.toLocalDate().toString(), reminderAt = future.toString())
        entries.forEach { repo.save(it) }; repo.saveMemo(memo)
        rows().forEach { repo.removeListedReminder(it, repo.restoreGeneration) }
        repo.all().forEach { actual ->
            val original = entries.single { it.id == actual.id }
            assertEquals(original.copy(reminderAt = null, reminderDeliveredFor = null, updatedAt = actual.updatedAt), actual)
        }
        val actualMemo = repo.allMemos().single()
        assertEquals(memo.copy(reminderAt = null, reminderDeliveredFor = null, updatedAt = actualMemo.updatedAt), actualMemo)
        assertTrue(rows().isEmpty())
        repo.reconcileReminders({ assertFalse(it.pending); false }, { assertTrue(it.none { target -> target.pending }) })
    }
    @Test fun independentReminderIsDeletedWithoutChangingOtherRows() = runBlocking {
        val r = StandaloneReminder(title = "独立", startDate = future.toLocalDate().toString(), time = "09:00", repeat = RepeatKind.DAILY)
        val e = Entry(kind = EntryKind.TASK, title = "保留任务")
        repo.save(e); repo.saveReminder(r)
        repo.removeListedReminder(rows().single(), repo.restoreGeneration)
        assertTrue(repo.allReminders().isEmpty()); assertEquals(listOf(e), repo.all())
    }
    @Test fun staleConfirmationDoesNotRemoveEditedOrRestoredReminder() = runBlocking {
        val e = Entry(kind = EntryKind.TASK, title = "原任务", reminderAt = future.toString())
        repo.save(e); val old = rows().single()
        val edited = e.copy(title = "修改过", updatedAt = e.updatedAt + 1)
        repo.save(edited)
        try { repo.removeListedReminder(old, 0); fail() } catch (_: IllegalArgumentException) {}
        assertEquals(listOf(edited), repo.all())
        val current = rows().single()
        repo.replaceData(listOf(edited), emptyList()) { _, _, _ -> }
        try { repo.removeListedReminder(current, 0); fail() } catch (_: IllegalArgumentException) {}
        assertEquals(listOf(edited), repo.all())
    }
}
