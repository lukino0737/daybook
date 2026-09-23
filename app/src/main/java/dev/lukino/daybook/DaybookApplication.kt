package dev.lukino.daybook

import android.app.Application
import androidx.room.Room
import dev.lukino.daybook.data.DaybookDatabase
import dev.lukino.daybook.data.EntryRepository
import dev.lukino.daybook.backup.BackupService

class DaybookApplication : Application() {
    private val aiScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    val ai by lazy { dev.lukino.daybook.ai.AiSession(dev.lukino.daybook.ai.AiSettingsStore(this), dev.lukino.daybook.ai.DeepSeekClient(), aiScope) }
    private val database by lazy { Room.databaseBuilder(this, DaybookDatabase::class.java, "daybook.db").addMigrations(DaybookDatabase.MIGRATION_1_2, DaybookDatabase.MIGRATION_2_3, DaybookDatabase.MIGRATION_3_4, DaybookDatabase.MIGRATION_4_5, DaybookDatabase.MIGRATION_5_6).build() }
    val appearance by lazy { dev.lukino.daybook.appearance.AppearanceStore(this) }
    val images by lazy { dev.lukino.daybook.media.BodyImageStore(this) }
    val repository by lazy { EntryRepository(database, images) }
    val reminders by lazy { dev.lukino.daybook.reminder.ReminderCoordinator(this, repository) }
    override fun onCreate() {
        super.onCreate()
        reminders.start()
        appearance
    }
    val backup by lazy { BackupService(this, repository) }
}
