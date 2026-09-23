package dev.lukino.daybook

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.lukino.daybook.ai.*
import dev.lukino.daybook.data.*
import dev.lukino.daybook.ui.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class AiDraftUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun mixedDraftsRequireExplicitConfirmationBeforeAnyWrite() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        val repo = EntryRepository(db)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val credentials = object : AiCredentials {
            override fun config() = AiConfig(configured = true)
            override fun key() = "fake-only"
            override fun save(key: String?, model: String, thinking: Boolean) {}
            override fun delete() {}
        }
        var calls = 0
        val client = object : AiClient {
            override suspend fun models(key: String) = AiProtocol.models
            override suspend fun complete(key: String, config: AiConfig, messages: List<JsonObject>, tools: JsonArray?): AiReply {
                calls++
                if (calls == 1) return AiReply(buildJsonObject {
                    put("role", "assistant"); put("content", JsonNull)
                    put("tool_calls", buildJsonArray { add(buildJsonObject {
                        put("id", "draft-call"); put("type", "function")
                        put("function", buildJsonObject { put("name", "propose_items"); put("arguments", AiDrafts.encode(listOf(
                            AiDraft("task1", title = "整理书桌"), AiDraft("memo1", kind = "MEMO", body = "周末留一点时间读书。")))) })
                    }) })
                })
                return AiReply(AiProtocol.message("assistant", "已生成两条草稿，请检查后保存。"))
            }
        }
        val session = AiSession(credentials, client, scope, repo)
        try {
            compose.setContent { DaybookTheme { AiScreen(session) {} } }
            compose.onNodeWithTag("ai-input").performTextInput("帮我记录两件事")
            compose.onNodeWithTag("ai-send").performClick()
            compose.waitUntil(5000) { session.state.value.drafts.size == 2 }
            runBlocking { assertTrue(repo.all().isEmpty()); assertTrue(repo.allMemos().isEmpty()) }
            compose.onNodeWithTag("ai-messages").performScrollToNode(hasTestTag("ai-draft-task1"))
            compose.onNodeWithText("不设截止日期").assertExists()
            compose.onNodeWithTag("ai-messages").performScrollToNode(hasTestTag("ai-save-preview"))
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { screenshot ->
                File(context.getExternalFilesDir(null), "m2-drafts.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                screenshot.recycle()
            }
            compose.onNodeWithTag("ai-save-preview").performClick()
            runBlocking { assertTrue(repo.all().isEmpty()) }
            compose.onNodeWithTag("ai-confirm-save").performClick()
            compose.waitUntil(5000) { !session.state.value.busy && session.state.value.drafts.isEmpty() }
            runBlocking { assertEquals(1, repo.all().size); assertNull(repo.all().single().date); assertEquals(1, repo.allMemos().size) }
            compose.runOnIdle { session.saveDrafts() }
            runBlocking { assertEquals(1, repo.all().size) }
        } finally { scope.cancel(); db.close() }
    }
}
