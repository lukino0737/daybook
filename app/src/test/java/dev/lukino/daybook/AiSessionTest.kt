package dev.lukino.daybook

import dev.lukino.daybook.ai.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiSessionTest {
    private class Credentials : AiCredentials {
        var value = AiConfig(configured = true)
        override fun config() = value
        override fun key() = "fake-test-only"
        override fun save(key: String?, model: String, thinking: Boolean) { value = AiConfig(model, thinking, true) }
        override fun delete() { value = value.copy(configured = false) }
    }
    private class Client : AiClient {
        var failure = false
        var wait = false
        val inputs = mutableListOf<List<JsonObject>>()
        override suspend fun models(key: String) = AiProtocol.models
        override suspend fun complete(key: String, config: AiConfig, messages: List<JsonObject>, tools: JsonArray?): AiReply {
            inputs += messages
            if (failure) error("sensitive server response must not be shown")
            if (wait) withContext(NonCancellable) { delay(1000) }
            return AiReply(AiProtocol.message("assistant", "虚构回答"), 12)
        }
    }
    @Test fun conversationAndFailurePreserveInputWithoutLeakingErrors() = runTest {
        val client = Client(); val session = AiSession(Credentials(), client, backgroundScope)
        session.input("你好"); session.send(); runCurrent()
        assertEquals(2, session.state.value.lines.size)
        session.input("继续"); client.failure = true; session.send(); runCurrent()
        assertEquals("继续", session.state.value.input)
        assertEquals(2, session.state.value.lines.size)
        assertFalse(session.state.value.error!!.contains("sensitive"))
        client.failure = false; session.send(); runCurrent()
        assertEquals(4, session.state.value.lines.size)
        assertEquals(listOf("system", "user", "user", "assistant", "user"), client.inputs.last().map { it.getValue("role").jsonPrimitive.content })
    }
    @Test fun stopAndClearRejectLateReplyAndNewSessionHasNoHistory() = runTest {
        val credentials = Credentials(); val client = Client().apply { wait = true }
        val session = AiSession(credentials, client, backgroundScope)
        session.input("迟到请求"); session.send(); runCurrent(); session.clear()
        advanceTimeBy(1001); runCurrent()
        assertTrue(session.state.value.lines.isEmpty()); assertFalse(session.state.value.busy)
        client.wait = false; session.input("新对话"); session.send(); runCurrent()
        assertEquals(3, client.inputs.last().size)
        assertTrue(AiSession(credentials, client, backgroundScope).state.value.lines.isEmpty())
    }
    @Test fun unavailableKeyDoesNotCallAndModelThinkingAreExplicit() = runTest {
        val credentials = Credentials().apply { value = value.copy(configured = false) }
        val client = Client(); val session = AiSession(credentials, client, backgroundScope)
        session.input("你好"); session.send(); runCurrent()
        assertTrue(client.inputs.isEmpty()); assertEquals("你好", session.state.value.input)
        val body = Json.parseToJsonElement(AiProtocol.request(AiConfig(thinking = false), listOf(AiProtocol.message("user", "你好")), null)).jsonObject
        assertEquals("disabled", body.getValue("thinking").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("deepseek-flash", body.getValue("model").jsonPrimitive.content)
    }
    @Test fun truncatedAndEmptyRepliesAreRejected() {
        listOf("length", "stop").forEach { finish ->
            try { AiProtocol.reply("""{"choices":[{"finish_reason":"$finish","message":{"role":"assistant","content":""}}]}"""); fail() }
            catch (_: AiFailure) {}
        }
        assertFalse(AiProtocol.httpError(401).message!!.contains("Bearer"))
    }
}
