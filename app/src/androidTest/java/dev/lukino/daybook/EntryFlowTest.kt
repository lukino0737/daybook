package dev.lukino.daybook

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class EntryFlowTest {
    @get:Rule(order = 0) val returningUser = ReturningUserRule()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val title = "测试记录-${UUID.randomUUID().toString().take(8)}"
    private val repository get() = (compose.activity.application as DaybookApplication).repository

    @After fun removeOnlyThisTestsRecords() = runBlocking {
        repository.all().filter { it.title.startsWith(title) }.forEach { repository.delete(it.id) }
    }

    @Test fun draftSurvivesRecreationAndEditingKeepsId() {
        compose.onNodeWithTag("add").performClick()
        compose.onNodeWithTag("title").performTextInput(title)
        compose.onNodeWithTag("note").performTextInput("待补充的课程链接")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("title").assertTextContains(title)
        compose.onNodeWithTag("note").assertTextContains("待补充的课程链接")
        compose.onNodeWithTag("save").performClick()
        compose.waitUntil(5000) { runBlocking { repository.all().any { it.title == title } } }
        val first = runBlocking { repository.all().first { it.title == title } }
        compose.onNodeWithTag("entry-${first.id}").performScrollTo().performClick()
        compose.onNodeWithTag("title").performTextReplacement("$title 已修改")
        compose.onNodeWithTag("save").performClick()
        compose.waitUntil(5000) { runBlocking { repository.all().any { it.id == first.id && it.title.endsWith("已修改") } } }
        assertEquals(first.createdAt, runBlocking { repository.all().first { it.id == first.id }.createdAt })
    }
}
