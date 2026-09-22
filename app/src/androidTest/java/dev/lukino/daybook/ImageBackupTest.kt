package dev.lukino.daybook

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.lukino.daybook.backup.*
import dev.lukino.daybook.data.*
import dev.lukino.daybook.media.BodyImageStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ImageBackupTest {
    private fun fixture(block: suspend (File, BodyImageStore, EntryRepository, BackupService, DaybookDatabase, BodyImage) -> Unit) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "image-backup-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = dir }
        val store = BodyImageStore(isolated)
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        val repo = EntryRepository(db, store)
        val backup = BackupService(isolated, repo)
        try {
            val source = File(dir, "sample.png")
            Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.BLUE); source.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }; recycle()
            }
            val image = repo.importImage(Uri.fromFile(source), "sample")
            store.ready()
            block(dir, store, repo, backup, db, image)
        } finally { db.close(); dir.deleteRecursively() }
    }
    @Test fun fullRoundTripAndSnapshotSurviveLiveImageCleanup() = fixture { dir, store, repo, backup, _, image ->
        val blocks = RichBody.insert("前后", emptyList(), 0, 1, image)
        val entry = Entry(title = "图文", kind = EntryKind.NOTE, date = "2026-09-22", note = "前后", blocks = blocks)
        val memo = Memo(blocks = RichBody.insert("", emptyList(), 0, 0, image))
        repo.save(entry); repo.saveMemo(memo); store.release("sample")
        val output = File(dir, "backup.zip"); backup.export(Uri.fromFile(output))
        val preview = backup.preview(Uri.fromFile(output))
        assertEquals(2, preview.imageCount); assertEquals(image.bytes, preview.imageBytes)
        backup.restore(BackupArchive(5, 0, emptyList()))
        assertFalse(store.file(image).exists())
        val snapshot = backup.previewSnapshot()
        assertEquals(listOf(entry), snapshot.entries); assertEquals(listOf(memo), snapshot.memos)
        backup.restore(snapshot)
        assertEquals(listOf(entry), repo.all()); assertEquals(listOf(memo), repo.allMemos()); store.verify(image)
        backup.restore(preview); assertEquals(listOf(entry), repo.all())
        assertFalse(File(preview.imageDirectory!!).exists())
    }
    @Test fun missingCorruptAndUnsafeZipNeverAffectDatabase() = fixture { dir, store, repo, backup, _, image ->
        val old = Memo(body = "保留"); repo.saveMemo(old)
        val incoming = Memo(body = "图文", blocks = RichBody.insert("图文", emptyList(), 0, 1, image))
        val manifest = BackupCodec.encode(emptyList(), memos = listOf(incoming))
        for (mode in listOf("missing", "corrupt", "path")) {
            val file = File(dir, "$mode.zip")
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toByteArray()); zip.closeEntry()
                if (mode != "missing") {
                    zip.putNextEntry(ZipEntry(if (mode == "path") "../escape" else "images/${image.hash}"))
                    zip.write(ByteArray(image.bytes.toInt())); zip.closeEntry()
                }
            }
            try { backup.preview(Uri.fromFile(file)); fail("must reject $mode") } catch (_: IllegalArgumentException) {}
            assertEquals(listOf(old), repo.allMemos()); store.verify(image)
        }
        assertFalse(File(dir, "escape").exists())
        assertTrue(File(dir, "backup-staging").listFiles().orEmpty().isEmpty())
    }
    @Test fun snapshotWriteAndTransactionFailuresPreserveOriginalImages() = fixture { dir, store, repo, backup, db, image ->
        val old = Memo(body = "原图文", blocks = RichBody.insert("原图文", emptyList(), 0, 1, image))
        repo.saveMemo(old); store.release("sample")
        val blocked = File(dir, "before-restore.zip.new").apply { mkdirs(); File(this, "occupied").writeText("x") }
        try { backup.restore(BackupArchive(5, 0, emptyList())); fail() } catch (_: java.io.IOException) {}
        assertEquals(listOf(old), repo.allMemos()); store.verify(image)
        blocked.deleteRecursively()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_insert BEFORE INSERT ON memos BEGIN SELECT RAISE(ABORT, 'test'); END")
        try { backup.restore(BackupArchive(5, 0, emptyList(), listOf(Memo(body = "新内容")))); fail() } catch (_: android.database.sqlite.SQLiteException) {}
        assertEquals(listOf(old), repo.allMemos()); store.verify(image)
        assertEquals(listOf(old), backup.previewSnapshot().memos)
    }
    @Test fun changedPreviewAndMissingExportFailBeforeReplacement() = fixture { dir, store, repo, backup, _, image ->
        val old = Memo(body = "原图文", blocks = RichBody.insert("原图文", emptyList(), 0, 1, image))
        repo.saveMemo(old)
        val file = File(dir, "valid.zip"); backup.export(Uri.fromFile(file))
        val preview = backup.preview(Uri.fromFile(file))
        File(preview.imageDirectory!!, image.hash).writeText("damaged")
        try { backup.restore(preview); fail() } catch (_: IllegalArgumentException) {}
        assertEquals(listOf(old), repo.allMemos()); store.verify(image)
        backup.discard(preview)
        store.file(image).delete()
        val failed = File(dir, "failed.zip")
        try { backup.export(Uri.fromFile(failed)); fail() } catch (_: IllegalStateException) {}
        assertFalse(failed.exists()); assertEquals(listOf(old), repo.allMemos())
    }
}
