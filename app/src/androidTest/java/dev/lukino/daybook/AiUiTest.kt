package dev.lukino.daybook

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.lukino.daybook.ai.*
import dev.lukino.daybook.ui.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*

class AiUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun chatInputAndConversationSurvivePageReturnAndClearExplicitly() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val credentials = object : AiCredentials {
            override fun config() = AiConfig(configured = true)
            override fun key() = "fake-only"
            override fun save(key: String?, model: String, thinking: Boolean) {}
            override fun delete() {}
        }
        val client = object : AiClient {
            override suspend fun models(key: String) = AiProtocol.models
            override suspend fun complete(key: String, config: AiConfig, messages: List<JsonObject>, tools: JsonArray?) =
                AiReply(AiProtocol.message("assistant", "这是虚构的测试回答。"))
        }
        val session = AiSession(credentials, client, scope)
        var visible by mutableStateOf(true)
        try {
            compose.setContent { DaybookTheme { if (visible) AiScreen(session) { visible = false } } }
            compose.onNodeWithTag("ai-input").performTextInput("你好")
            compose.onNodeWithTag("ai-send").performClick()
            compose.waitUntil(5000) { session.state.value.lines.size == 2 }
            compose.onNodeWithText("这是虚构的测试回答。").assertExists()
            compose.onNodeWithTag("ai-input").performTextInput("未发送的下一句")
            compose.onNodeWithText("返回").performClick()
            compose.runOnIdle { visible = true }
            compose.onNodeWithTag("ai-input").assertTextContains("未发送的下一句")
            compose.onNodeWithTag("ai-clear").performClick()
            compose.onNodeWithText("清空并开始").performClick()
            compose.runOnIdle { assertTrue(session.state.value.lines.isEmpty()); assertEquals("", session.state.value.input) }
        } finally { scope.cancel() }
    }
}
