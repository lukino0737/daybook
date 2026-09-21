package dev.lukino.daybook

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.lukino.daybook.data.*
import dev.lukino.daybook.backup.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class StandaloneStorageTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), DaybookDatabase::class.java)
    @Test fun v4MigrationPreservesBothTablesAndCreatesEmptyReminders() = runBlocking {
        val name = "v06-migration"
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            helper.createDatabase(name, 4).apply {
                execSQL("INSERT INTO memos VALUES ('00000000-0000-0000-0000-000000000606','正文','2026-09-21','2026-09-21T09:00','2026-09-21T09:00',10,20)")
                close()
            }
            helper.runMigrationsAndValidate(name, 5, true, DaybookDatabase.MIGRATION_4_5).close()
            val db = Room.databaseBuilder(context, DaybookDatabase::class.java, name).build()
            try {
                val repo = EntryRepository(db)
                assertEquals("正文", repo.allMemos().single().body)
                assertEquals("2026-09-21T09:00", repo.allMemos().single().reminderDeliveredFor)
                assertTrue(repo.allReminders().isEmpty())
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun oldBackupClearsRemindersAndSnapshotFailureAndTransactionFailurePreserveAll() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        val dir = File(context.cacheDir, "reminder-backup-${UUID.randomUUID()}").apply { mkdirs() }
        val backup = BackupService(object : android.content.ContextWrapper(context) { override fun getFilesDir() = dir }, EntryRepository(db))
        val repo = EntryRepository(db)
        try {
            val entry = Entry(title = "日程", date = "2026-09-21")
            val memo = Memo(body = "便签")
            repo.save(entry); repo.saveMemo(memo)
            repo.saveReminder(StandaloneReminder(title = "提醒", startDate = "2026-09-21", time = "09:00", repeat = RepeatKind.DAILY))
            val expected = repo.snapshotData()
            backup.restore(BackupArchive(4, 0, listOf(entry), listOf(memo)))
            assertTrue(repo.allReminders().isEmpty())
            backup.restore(backup.previewSnapshot())
            assertEquals(expected, repo.snapshotData())
            try { repo.replaceData(emptyList(), emptyList(), emptyList()) { _, _, _ -> error("snapshot failed") }; fail() } catch (_: IllegalStateException) {}
            assertEquals(expected, repo.snapshotData())
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_reminder BEFORE INSERT ON reminders BEGIN SELECT RAISE(ABORT, 'test'); END")
            try { backup.restore(BackupArchive(5, 0, emptyList(), emptyList(), expected.third)); fail() } catch (_: android.database.sqlite.SQLiteException) {}
            assertEquals(expected, repo.snapshotData())
            try { backup.restore(BackupArchive(5, 0, emptyList(), reminders = listOf(expected.third.single().copy(weekdays = 999)))); fail() } catch (_: IllegalArgumentException) {}
            assertEquals(expected, repo.snapshotData())
        } finally { db.close(); dir.deleteRecursively() }
    }
    @Test fun editPreservesScheduleStateAndResumeResetsEffectiveBoundary() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        val repo = EntryRepository(db)
        val now = Instant.parse("2026-09-21T00:00:00Z")
        try {
            repo.saveReminder(StandaloneReminder(title = "提醒", startDate = "2026-09-21", time = "09:00", repeat = RepeatKind.DAILY), now)
            val initial = repo.allReminders().single()
            db.reminders().save(initial.copy(deliveredFor = "2026-09-21T09:00"))
            repo.saveReminder(initial.copy(title = "改标题"), now.plusSeconds(7200), expectExisting = true)
            val renamed = repo.allReminders().single()
            assertEquals("2026-09-21T09:00", renamed.deliveredFor)
            assertEquals(initial.effectiveFrom, renamed.effectiveFrom)
            repo.saveReminder(renamed.copy(enabled = false), now.plusSeconds(8000))
            val paused = repo.allReminders().single()
            repo.saveReminder(paused.copy(enabled = true), now.plusSeconds(9000))
            val resumed = repo.allReminders().single()
            assertEquals(now.plusSeconds(9000).toEpochMilli(), resumed.effectiveFrom)
            assertEquals(initial.revision + 1, resumed.revision)
            repo.saveReminder(resumed.copy(time = "10:00"), now.plusSeconds(10000))
            assertNull(repo.allReminders().single().deliveredFor)
            try { repo.saveReminder(resumed.copy(title = "旧草稿"), now.plusSeconds(11000)); fail() } catch (_: IllegalArgumentException) {}
            try { repo.saveReminder(StandaloneReminder(title = "过期单次", startDate = "2026-09-20", time = "09:00"), now, ZoneId.of("Asia/Shanghai")); fail() } catch (_: IllegalArgumentException) {}
        } finally { db.close() }
    }
}
