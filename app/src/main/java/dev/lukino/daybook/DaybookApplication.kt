package dev.lukino.daybook

import android.app.Application
import androidx.room.Room
import dev.lukino.daybook.data.DaybookDatabase
import dev.lukino.daybook.data.EntryRepository
import dev.lukino.daybook.backup.BackupService

class DaybookApplication : Application() {
    private val database by lazy { Room.databaseBuilder(this, DaybookDatabase::class.java, "daybook.db").build() }
    val repository by lazy { EntryRepository(database) }
    val backup by lazy { BackupService(this, repository) }
}
