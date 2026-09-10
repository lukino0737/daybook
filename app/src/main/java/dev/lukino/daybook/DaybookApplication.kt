package dev.lukino.daybook

import android.app.Application
import androidx.room.Room
import dev.lukino.daybook.data.DaybookDatabase
import dev.lukino.daybook.data.EntryRepository
import dev.lukino.daybook.backup.BackupService

class DaybookApplication : Application() {
    private val database by lazy { Room.databaseBuilder(this, DaybookDatabase::class.java, "daybook.db").addMigrations(DaybookDatabase.MIGRATION_1_2, DaybookDatabase.MIGRATION_2_3).build() }
    val repository by lazy { EntryRepository(database) }
    val reminders by lazy { dev.lukino.daybook.reminder.ReminderCoordinator(this, repository) }
    override fun onCreate() {
        super.onCreate()
        reminders.start()
    }
    val backup by lazy { BackupService(this, repository) }
}
