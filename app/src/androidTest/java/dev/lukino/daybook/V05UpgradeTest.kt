package dev.lukino.daybook

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.lukino.daybook.backup.BackupArchive
import dev.lukino.daybook.data.*
import dev.lukino.daybook.reminder.ReminderCoordinator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Explicit same-signature upgrade check on the dedicated upgrade AVD only. */
class V05UpgradeTest {
    @Test fun v04DataMemosAndChannelSurviveV05() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val mode = args.getString("daybookUpgrade")
        Assume.assumeTrue(mode == "seed" || mode == "verify")
        val app = ApplicationProvider.getApplicationContext<DaybookApplication>()
        assertEquals(args.getString("expectedVersion"), app.packageManager.getPackageInfo(app.packageName, 0).versionName)
        val data = File(app.filesDir, "v05-upgrade-data.json")
        val channel = File(app.filesDir, "v05-upgrade-channel.txt")
        val entry = Entry(id = "00000000-0000-0000-0000-000000000501", kind = EntryKind.TASK,
            title = "升级验收任务", note = "虚构测试样例", date = "2099-12-31", time = "18:30", completed = true,
            tags = listOf("验收"), reminderAt = "2099-12-30T09:00", reminderDeliveredFor = "2099-12-30T09:00", createdAt = 1000, updatedAt = 2000)
        val memo = Memo(id = "00000000-0000-0000-0000-000000000502", body = "升级验收便签\n虚构内容", date = "2099-12-31",
            reminderAt = "2099-12-31T09:00", createdAt = 3000, updatedAt = 4000)
        fun settings(): String {
            val value = app.getSystemService(NotificationManager::class.java).getNotificationChannel(ReminderCoordinator.CHANNEL)
            return listOf(value.id, value.importance, value.sound, value.shouldVibrate(), value.vibrationPattern?.joinToString(",")).joinToString("|")
        }
        if (mode == "seed") {
            assertFalse(data.exists())
            app.repository.save(entry); app.repository.saveMemo(memo)
            val (entries, memos) = app.repository.snapshotData()
            data.writeText(Json.encodeToString(BackupArchive(4, 0, entries, memos)))
            channel.writeText(settings())
        } else {
            val expected = Json.decodeFromString<BackupArchive>(data.readText())
            val (entries, memos) = app.repository.snapshotData()
            assertEquals(expected.entries.sortedBy { it.id }, entries.sortedBy { it.id })
            assertEquals(expected.memos.sortedBy { it.id }, memos.sortedBy { it.id })
            assertEquals(channel.readText(), settings())
            app.repository.delete(entry.id); app.repository.deleteMemo(memo.id)
            data.delete(); channel.delete()
        }
        Unit
    }
}
