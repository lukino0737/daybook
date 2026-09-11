package dev.lukino.daybook

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.lukino.daybook.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

class CalendarUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val ownEntries = mutableListOf<Entry>()
    private val repository get() = (compose.activity.application as DaybookApplication).repository
    @After fun cleanup() = runBlocking { ownEntries.forEach { repository.delete(it.id) } }

    @Test fun monthNavigationAndSampleCalendar() {
        val today = LocalDate.now()
        val samples = listOf(
            Entry(title = "产品发布会", date = today.toString(), time = "13:00"),
            Entry(title = "游戏更新", date = today.toString()),
            Entry(kind = EntryKind.TASK, title = "提交课程材料", date = today.toString(), time = "18:00"),
            Entry(title = "周末见面", date = today.plusDays(2).toString(), time = "14:30"),
            Entry(title = "计算机网络 · 线上课", date = today.plusDays(7).toString(), time = "10:00"),
            Entry(kind = EntryKind.NOTE, title = "奶茶 🧋", date = today.minusDays(1).toString()),
        )
        ownEntries += samples
        runBlocking { samples.forEach { repository.save(it) } }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("marker-$today", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("day-$today").assertExists()
        compose.onNodeWithTag("today").assertDoesNotExist()
        compose.onNodeWithTag("month-calendar").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("today").assertExists()
        compose.onNodeWithText("今天").performClick()
        compose.onNodeWithTag("day-$today").assertExists()
        val folder = File(compose.activity.getExternalFilesDir(null), "qa").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(folder, "calendar.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithTag("day-$today").performClick()
        compose.onNodeWithTag("calendar-list").performScrollToNode(hasText("产品发布会", substring = false))
        compose.onNodeWithText("产品发布会").assertIsDisplayed()
        compose.onNodeWithTag("bottom-navigation").assertIsDisplayed()
        compose.onNodeWithTag("nav-tasks").assertIsDisplayed()
    }
}
