package dev.lukino.daybook

import android.app.NotificationManager
import android.media.AudioAttributes
import android.provider.Settings
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.lukino.daybook.reminder.ReminderCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ReminderSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun settingsAreASeparatePageAndReturningKeepsUnsavedDraft() {
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("title").performTextInput("提醒设置草稿")
        compose.onNodeWithTag("reminder-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("reminder-settings-entry").performScrollTo().performClick()
        compose.onNodeWithTag("reminder-settings-screen").assertIsDisplayed()
        compose.onNodeWithTag("reminder-settings-back").assertIsDisplayed()
        compose.onNodeWithText("请开启以下两项").assertIsDisplayed()
        compose.onNodeWithText("重新检查提醒").assertDoesNotExist()
        compose.onNodeWithText("提醒权限已开启", substring = true).assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("reminder-settings-screen").assertIsDisplayed()
        val folder = File(compose.activity.getExternalFilesDir(null), "qa").apply { mkdirs() }
        compose.onNodeWithTag("reminder-settings-screen").captureToImage().asAndroidBitmap().let { bitmap ->
            File(folder, "reminder-settings.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithTag("reminder-settings-back").performClick()
        compose.onNodeWithTag("title").performScrollTo().assertTextContains("提醒设置草稿")
        compose.onNodeWithTag("reminder-toggle").performScrollTo().assertIsOn()
        compose.onNodeWithText("取消", substring = false).performClick()
        val app = compose.activity.application as DaybookApplication
        assertFalse(runBlocking { app.repository.all().any { it.title == "提醒设置草稿" } })
        compose.onNodeWithTag("backup-menu").performClick()
        compose.onNodeWithText("提醒设置").performClick()
        compose.onNodeWithTag("reminder-settings-screen").assertIsDisplayed()
        compose.onNodeWithTag("reminder-settings-back").performClick()
        compose.onNodeWithTag("bottom-navigation").assertIsDisplayed()
    }
    @Test fun defaultChannelAlertsAndExistingChannelIsPreserved() {
        val defaults = ReminderCoordinator.defaultChannel()
        assertEquals("daybook.reminders", defaults.id)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, defaults.importance)
        assertEquals(Settings.System.DEFAULT_NOTIFICATION_URI, defaults.sound)
        assertEquals(AudioAttributes.USAGE_NOTIFICATION, defaults.audioAttributes.usage)
        assertTrue(defaults.shouldVibrate())
        assertArrayEquals(longArrayOf(0, 250, 150, 250), defaults.vibrationPattern)
        val app = compose.activity.application as DaybookApplication
        val manager = app.getSystemService(NotificationManager::class.java)
        val before = manager.getNotificationChannel(ReminderCoordinator.CHANNEL)
        ReminderCoordinator(app, app.repository)
        val after = manager.getNotificationChannel(ReminderCoordinator.CHANNEL)
        assertEquals(before.importance, after.importance)
        assertEquals(before.sound, after.sound)
        assertEquals(before.shouldVibrate(), after.shouldVibrate())
        assertArrayEquals(before.vibrationPattern, after.vibrationPattern)
    }
}
