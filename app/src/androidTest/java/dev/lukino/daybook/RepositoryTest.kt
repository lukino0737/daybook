package dev.lukino.daybook

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lukino.daybook.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    @Test fun persistsAcrossDatabaseReopenAndSupportsDeleteUndo() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "test-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, DaybookDatabase::class.java, name).build()
        var db = open()
        try {
            val entry = Entry(kind = EntryKind.TASK, title = "持久化测试", date = "2026-09-10")
            EntryRepository(db).save(entry)
            db.close()
            db = open()
            val repository = EntryRepository(db)
            assertEquals(listOf(entry), repository.all())
            repository.save(entry.copy(completed = true))
            assertTrue(repository.all().single().completed)
            repository.delete(entry.id)
            assertTrue(repository.all().isEmpty())
            repository.save(entry)
            assertEquals(entry, repository.all().single())
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun snapshotFailureAndSqlFailureNeverEraseExistingData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        try {
            val repository = EntryRepository(db)
            val original = Entry(title = "原记录", date = "2026-09-10")
            val incoming = Entry(title = "替换记录", date = "2026-09-11")
            repository.save(original)
            try { repository.replaceAll(listOf(incoming)) { error("磁盘已满") }; fail("expected failure") } catch (_: IllegalStateException) {}
            assertEquals(listOf(original), repository.all())
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_restore BEFORE INSERT ON entries BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            try { repository.replaceAll(listOf(incoming)) {}; fail("expected SQL failure") } catch (_: android.database.sqlite.SQLiteException) {}
            assertEquals(listOf(original), repository.all())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_restore")
            var snapshot: List<Entry> = emptyList()
            repository.replaceAll(listOf(incoming)) { snapshot = it }
            assertEquals(listOf(original), snapshot)
            assertEquals(listOf(incoming), repository.all())
        } finally { db.close() }
    }
}
