package dev.lukino.daybook

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.lukino.daybook.data.*
import dev.lukino.daybook.backup.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class MemoStorageTest {
    @Test fun oldRestoreClearsMemosAndSnapshotCanRecoverBothTables() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        val dir = File(context.cacheDir, "memo-backup-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : android.content.ContextWrapper(context) { override fun getFilesDir() = dir }
        val repo = EntryRepository(db)
        val backup = BackupService(isolated, repo)
        try {
            val entry = Entry(title = "原记录", date = "2026-09-13")
            val memo = Memo(body = "原便签", reminderAt = "2026-09-20T09:00")
            repo.save(entry); repo.saveMemo(memo)
            backup.restore(BackupArchive(3, 0, emptyList()))
            assertTrue(repo.all().isEmpty()); assertTrue(repo.allMemos().isEmpty())
            backup.restore(backup.previewSnapshot())
            assertEquals(listOf(entry), repo.all()); assertEquals(listOf(memo), repo.allMemos())
            try { repo.replaceData(emptyList(), emptyList()) { _, _, _ -> error("snapshot write failed") }; fail() } catch (_: IllegalStateException) {}
            assertEquals(listOf(entry), repo.all()); assertEquals(listOf(memo), repo.allMemos())
            // Failure after entries are deleted must roll back both tables.
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_memo BEFORE INSERT ON memos BEGIN SELECT RAISE(ABORT, 'test'); END")
            try { repo.replaceData(emptyList(), listOf(memo)) { _, _, _ -> }; fail() } catch (_: android.database.sqlite.SQLiteException) {}
            assertEquals(listOf(entry), repo.all()); assertEquals(listOf(memo), repo.allMemos())
        } finally { db.close(); dir.deleteRecursively() }
    }
    @Test fun editingDuringDeliveryPreservesSentStateAndReschedulingResetsIt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        val repo = EntryRepository(db)
        try {
            val memo = Memo(body = "草稿", reminderAt = "2026-09-13T09:00")
            repo.saveMemo(memo)
            repo.reconcileReminders({ it.pending }, {})
            repo.saveMemo(memo.copy(body = "继续写"))
            assertEquals(memo.reminderAt, repo.allMemos().single().reminderDeliveredFor)
            repo.saveMemo(memo.copy(reminderAt = "2026-09-14T09:00"))
            assertNull(repo.allMemos().single().reminderDeliveredFor)
        } finally { db.close() }
    }
}
