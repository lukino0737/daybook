package dev.lukino.daybook

import android.app.NotificationManager
import dev.lukino.daybook.reminder.ReminderCoordinator
import java.io.File
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.lukino.daybook.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Run twice, with a same-signature package replacement between seed and verify. */
class UpgradeContinuityTest {
    @Test fun recordSurvivesPackageReplacement() = runBlocking {
        val mode = InstrumentationRegistry.getArguments().getString("daybookUpgrade")
        assumeTrue("Only runs as the explicit upgrade check", mode == "seed" || mode == "verify")
        val app = ApplicationProvider.getApplicationContext<DaybookApplication>()
        val fixture = Entry(id = "00000000-0000-0000-0000-000000000101", kind = EntryKind.TASK,
            title = "覆盖安装验收样例", note = "仅测试使用，验证后删除", date = "2099-12-31", time = "18:30",
            completed = true, createdAt = 1000, updatedAt = 2000)
        val args = InstrumentationRegistry.getArguments()
        val extended = args.getString("daybookUpgradeFields") == "extended"
        args.getString("expectedVersion")?.let { expected ->
            assertEquals(expected, app.packageManager.getPackageInfo(app.packageName, 0).versionName)
        }
        val samples = if (extended) listOf(fixture.copy(tags = listOf("学习", "验收"),
            reminderAt = "2099-12-30T09:00", reminderDeliveredFor = "2099-12-30T09:00"),
            Entry(id = "00000000-0000-0000-0000-000000000102", kind = EntryKind.EVENT,
                title = "旧版日程验收样例", date = "2099-12-31", tags = listOf("验收"),
                createdAt = 3000, updatedAt = 4000)) else listOf(fixture)
        val snapshot = File(app.filesDir, "upgrade-channel-test.txt")
        fun channelSettings(): String {
            val channel = app.getSystemService(NotificationManager::class.java).getNotificationChannel(ReminderCoordinator.CHANNEL)
            return listOf(channel.id, channel.importance, channel.sound, channel.shouldVibrate(),
                channel.vibrationPattern?.joinToString(",")).joinToString("|")
        }
        if (mode == "seed") {
            samples.forEach { app.repository.save(it) }
            if (extended) snapshot.writeText(channelSettings())
        } else {
            samples.forEach { assertEquals(it, app.repository.all().single { actual -> actual.id == it.id }) }
            if (extended) {
                assertEquals(snapshot.readText(), channelSettings())
                snapshot.delete()
            }
            samples.forEach { app.repository.delete(it.id) }
        }
    }
}
