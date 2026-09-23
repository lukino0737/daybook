package dev.lukino.daybook

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.lukino.daybook.ai.*
import dev.lukino.daybook.data.*
import dev.lukino.daybook.ui.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*

class AiReadUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun personalLookupHasRealSourceAndDenialPurgesDataFromLaterRequests() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        val repo = EntryRepository(db)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val secret = Entry(kind = EntryKind.TASK, title = "虚构的个人任务XYZ")
        val requests = mutableListOf<String>()
        val client = object : AiClient {
            override suspend fun models(key: String) = AiProtocol.models
            override suspend fun complete(key: String, config: AiConfig, messages: List<JsonObject>, tools: JsonArray?): AiReply {
                requests += messages.toString()
                if (messages.last()["role"]?.jsonPrimitive?.content == "user" && tools.toString().contains("query_records")) {
                    return AiReply(buildJsonObject {
                        put("role", "assistant"); put("content", JsonNull)
                        put("tool_calls", buildJsonArray { add(buildJsonObject {
                            put("id", "read-1"); put("type", "function")
                            put("function", buildJsonObject { put("name", "query_records"); put("arguments", """{"kind":"TASK","completed":false}""") })
                        }) })
                    })
                }
                return AiReply(AiProtocol.message("assistant", if (messages.toString().contains(secret.title)) "查到一条任务。" else "我们聊聊吧。"))
            }
        }
        val credentials = object : AiCredentials {
            override fun config() = AiConfig(configured = true)
            override fun key() = "fake-only"
            override fun save(key: String?, model: String, thinking: Boolean) {}
            override fun delete() {}
        }
        val session = AiSession(credentials, client, scope, repo)
        var opened: String? = null
        try {
            runBlocking { repo.save(secret) }
            compose.setContent { DaybookTheme { AiScreen(session, onSource = { opened = it.key }) {} } }
            compose.onNodeWithTag("ai-input").performTextInput("讲个笑话")
            compose.onNodeWithTag("ai-send").performClick()
            compose.waitUntil(5000) { session.state.value.lines.size == 2 }
            assertFalse(requests.any { it.contains(secret.title) })
            compose.onNodeWithTag("ai-input").performTextInput("我有哪些未完成任务")
            compose.onNodeWithTag("ai-send").performClick()
            compose.waitUntil(5000) { session.state.value.lines.lastOrNull()?.sources?.isNotEmpty() == true }
            val tag = "ai-source-entry:${secret.id}"
            compose.onNodeWithTag("ai-messages").performScrollToNode(hasTestTag(tag))
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(context.getExternalFilesDir(null), "ai-sources.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            compose.onNodeWithTag(tag).performClick()
            assertEquals("entry:${secret.id}", opened)
            compose.onNodeWithTag("ai-input").performTextInput("不要读取我的记录，只聊天")
            compose.onNodeWithTag("ai-send").performClick()
            compose.waitUntil(5000) { !session.state.value.busy && !session.state.value.readsEnabled }
            assertFalse(requests.last().contains(secret.title))
            assertTrue(session.state.value.lines.none { it.sources.isNotEmpty() })
            runBlocking { assertEquals(listOf(secret), repo.all()) }
        } finally { scope.cancel(); db.close() }
    }
}
