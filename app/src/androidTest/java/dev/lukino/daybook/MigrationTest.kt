package dev.lukino.daybook

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.lukino.daybook.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), DaybookDatabase::class.java)
    @Test fun v2MigrationKeepsDeliveredReminderAndAddsEmptyTags() = runBlocking {
        val name = "migration-v2-test"
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            helper.createDatabase(name, 2).apply {
                execSQL("INSERT INTO entries VALUES ('00000000-0000-0000-0000-000000000103','TASK','已提醒','正文',NULL,NULL,0,10,20,'2026-09-10T09:00','2026-09-10T09:00')")
                close()
            }
            helper.runMigrationsAndValidate(name, 4, true, DaybookDatabase.MIGRATION_2_3, DaybookDatabase.MIGRATION_3_4).close()
            val db = Room.databaseBuilder(context, DaybookDatabase::class.java, name).build()
            try {
                val entry = EntryRepository(db).all().single()
                assertEquals("2026-09-10T09:00", entry.reminderDeliveredFor)
                assertEquals(entry.reminderAt, entry.reminderDeliveredFor)
                assertTrue(entry.tags.isEmpty())
                assertEquals("正文", entry.note)
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun v1MigrationPreservesAllFieldsAndAllowsNewReminder() = runBlocking {
        val name = "migration-test"
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            helper.createDatabase(name, 1).apply {
                execSQL("INSERT INTO entries VALUES ('00000000-0000-0000-0000-000000000102','TASK','历史记录','保留正文','2028-02-29','23:59',1,10,20)")
                close()
            }
            helper.runMigrationsAndValidate(name, 4, true, DaybookDatabase.MIGRATION_1_2, DaybookDatabase.MIGRATION_2_3, DaybookDatabase.MIGRATION_3_4).close()
            val db = Room.databaseBuilder(context, DaybookDatabase::class.java, name).addMigrations(DaybookDatabase.MIGRATION_1_2, DaybookDatabase.MIGRATION_2_3).build()
            try {
                val repository = EntryRepository(db)
                val expected = Entry(id = "00000000-0000-0000-0000-000000000102", kind = EntryKind.TASK,
                    title = "历史记录", note = "保留正文", date = "2028-02-29", time = "23:59", completed = true, createdAt = 10, updatedAt = 20)
                assertEquals(expected, repository.all().single())
                repository.save(expected.copy(completed = false, tags = listOf("学习"), reminderAt = "2028-02-29T09:00"))
                assertEquals("2028-02-29T09:00", repository.all().single().reminderAt)
                assertEquals(listOf("学习"), repository.all().single().tags)
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
}
