package dev.lukino.daybook

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.lukino.daybook.data.*
import dev.lukino.daybook.media.BodyImageStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class BodyImageStorageTest {
    @get:org.junit.Rule val helper = androidx.room.testing.MigrationTestHelper(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(), DaybookDatabase::class.java)
    @Test fun v5MigrationPreservesMultilineMemoAndReminder() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "body-v5-migration"
        try {
            helper.createDatabase(name, 5).apply {
                execSQL("INSERT INTO memos VALUES ('00000000-0000-0000-0000-000000000607',?,NULL,NULL,NULL,10,20)", arrayOf("第一行\n\n🙂末行\n"))
                close()
            }
            helper.runMigrationsAndValidate(name, 6, true, DaybookDatabase.MIGRATION_5_6).close()
            val db = Room.databaseBuilder(context, DaybookDatabase::class.java, name).build()
            try {
                val memo = db.memos().all().single()
                assertEquals("第一行\n\n🙂末行\n", memo.body)
                assertEquals(listOf(BodyBlock(memo.body)), RichBody.effective(memo.body, memo.blocks))
                assertEquals(10L, memo.createdAt); assertEquals(20L, memo.updatedAt)
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun importsIndependentCopyPreservesTransparencyBoundsAndLeases() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "body-image-test-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = dir }
        val store = BodyImageStore(isolated)
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        val repo = EntryRepository(db, store)
        try {
            val source = File(dir, "source.png")
            Bitmap.createBitmap(3000, 1500, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.argb(100, 30, 90, 150))
                source.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }; recycle()
            }
            val image = repo.importImage(Uri.fromFile(source), "draft")
            assertEquals(2560, image.width); assertEquals(1280, image.height); assertEquals("image/png", image.mime)
            source.delete(); store.verify(image)
            store.ready(); repo.collectImages(); assertTrue(store.file(image).exists())
            val memo = Memo(blocks = RichBody.insert("", emptyList(), 0, 0, image))
            repo.saveMemo(memo); store.release("draft"); repo.collectImages()
            assertEquals(memo, repo.allMemos().single()); assertTrue(store.file(image).exists())
            repo.deleteMemo(memo.id); repo.collectImages(); assertFalse(store.file(image).exists())
        } finally { db.close(); dir.deleteRecursively() }
    }
    @Test fun invalidAndMissingImagesNeverReplaceSavedContent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "body-image-failure-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = dir }
        val store = BodyImageStore(isolated)
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        val repo = EntryRepository(db, store)
        try {
            val old = Memo(body = "保留原文"); repo.saveMemo(old)
            val invalid = File(dir, "bad.jpg").apply { writeText("not an image") }
            try { repo.importImage(Uri.fromFile(invalid), "draft"); fail() } catch (_: IllegalArgumentException) {}
            val missing = BodyImage("f".repeat(64), 100, 10, 10, "image/png")
            try { repo.saveMemo(old.copy(blocks = RichBody.insert(old.body, emptyList(), 0, 2, missing))); fail() } catch (_: IllegalArgumentException) {}
            assertEquals(old, repo.allMemos().single())
            assertTrue(store.directory.listFiles().orEmpty().isEmpty())
        } finally { db.close(); dir.deleteRecursively() }
    }
}
