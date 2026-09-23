package dev.lukino.daybook

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.lukino.daybook.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AiStorageTest {
    @Test fun batchRollsBackOnSqlFailureAndConfirmationIsIdempotent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        try {
            val repo = EntryRepository(db)
            val original = Entry(kind = EntryKind.TASK, title = "原始任务")
            repo.save(original)
            val created = Entry(kind = EntryKind.TASK, title = "新任务")
            val memo = Memo(body = "新便签")
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_ai BEFORE INSERT ON memos BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            try { repo.saveAiBatch("batch", 0, listOf(created), listOf(memo)); fail() } catch (_: android.database.sqlite.SQLiteException) {}
            assertEquals(listOf(original), repo.all()); assertTrue(repo.allMemos().isEmpty())
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_ai")
            assertTrue(repo.saveAiBatch("batch", 0, listOf(created), listOf(memo)))
            assertFalse(repo.saveAiBatch("batch", 0, listOf(created), listOf(memo)))
            assertEquals(2, repo.all().size); assertEquals(listOf(memo), repo.allMemos())
        } finally { db.close() }
    }
    @Test fun staleMemoOrRestoreRejectsEntireBatch() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        try {
            val repo = EntryRepository(db)
            val original = Memo(body = "原文")
            repo.saveMemo(original)
            val changed = original.copy(body = "用户刚改过", updatedAt = original.updatedAt + 1)
            repo.saveMemo(changed)
            val entry = Entry(kind = EntryKind.TASK, title = "不能留下半批")
            try { repo.saveAiBatch("stale", 0, listOf(entry), emptyList(), original to original.copy(body = "整理后")); fail() } catch (_: IllegalArgumentException) {}
            assertTrue(repo.all().isEmpty()); assertEquals(listOf(changed), repo.allMemos())
            repo.replaceData(emptyList(), listOf(changed)) { _, _, _ -> }
            assertEquals(1L, repo.restoreGeneration)
            try { repo.saveAiBatch("old-preview", 0, listOf(entry), emptyList()); fail() } catch (_: IllegalArgumentException) {}
            assertTrue(repo.all().isEmpty())
            assertTrue(repo.saveAiBatch("fresh", 1, emptyList(), emptyList(), changed to changed.copy(body = "确认整理", updatedAt = changed.updatedAt + 1)))
            assertEquals("确认整理", repo.allMemos().single().body)
        } finally { db.close() }
    }
    @Test fun imageMemoCannotBeReplacedByTextOnlyResult() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        try {
            val repo = EntryRepository(db)
            val blocks = listOf(BodyBlock("原文"), BodyBlock(image = BodyImage("a".repeat(64), 1, 1, 1, "image/png")), BodyBlock())
            val original = Memo(body = "原文", blocks = blocks)
            db.memos().save(original)
            try { repo.saveAiBatch("image", 0, emptyList(), emptyList(), original to original.copy(body = "仅文字", blocks = emptyList())); fail() } catch (_: IllegalArgumentException) {}
            assertEquals(listOf(original), repo.allMemos())
            repo.saveAiBatch("copy", 0, emptyList(), listOf(Memo(body = "仅文字")))
            assertTrue(repo.allMemos().contains(original))
        } finally { db.close() }
    }
}
