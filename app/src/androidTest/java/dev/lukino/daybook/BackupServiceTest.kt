package dev.lukino.daybook

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.lukino.daybook.backup.*
import dev.lukino.daybook.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BackupServiceTest {
    @Test fun exportRestoreSnapshotAndInvalidFile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        // Isolate snapshot storage as well as database; never replace application data in a test.
        val dir = File(context.cacheDir, "backup-test-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : android.content.ContextWrapper(context) { override fun getFilesDir() = dir }
        val repo = EntryRepository(db)
        val backup = BackupService(isolated, repo)
        try {
            val old = Entry(title = "原始记录", date = "2026-09-10")
            repo.save(old)
            val exported = File(dir, "export.json")
            backup.export(Uri.fromFile(exported))
            val preview = backup.preview(Uri.fromFile(exported))
            assertEquals(listOf(old), preview.entries)
            val incoming = Entry(title = "导入记录", date = "2026-09-11")
            backup.restore(BackupArchive(1, 1000, listOf(incoming)))
            assertEquals(listOf(incoming), repo.all())
            assertEquals(listOf(old), backup.previewSnapshot().entries)
            backup.restore(backup.previewSnapshot())
            assertEquals(listOf(old), repo.all())
            assertEquals(listOf(incoming), backup.previewSnapshot().entries)
            exported.writeText("broken json")
            try { backup.preview(Uri.fromFile(exported)); fail("expected invalid file") } catch (_: IllegalArgumentException) {}
            assertEquals(listOf(old), repo.all())
        } finally { db.close(); dir.deleteRecursively() }
    }
}
