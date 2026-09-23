package dev.lukino.daybook

import dev.lukino.daybook.ai.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AiDraftTest {
    @Test fun undatedTaskIsValidButEventsAndTimeNeedDate() {
        val task = AiDraft("a", title = "整理书桌")
        assertNull(task.validationError()); assertNull(task.date)
        assertNotNull(task.copy(kind = "EVENT").validationError())
        assertNotNull(task.copy(time = "12:00").validationError())
        assertNull(task.copy(date = "2028-02-29", time = "12:00").validationError())
        assertNotNull(task.copy(date = "2027-02-29").validationError())
        assertNotNull(task.copy(kind = "NOTE", date = "2026-12-31", reminderAt = "2027-01-01T12:00").validationError())
    }
    @Test fun malformedUnknownOrDuplicateDraftsNeverBecomeWritable() {
        listOf("""{"items":[{"id":"a","completed":true}]}""",
            """{"items":[{"id":"a","date":"2026-02-30"}]}""",
            """{"items":[{"id":"a"},{"id":"a"}]}""").forEach { raw ->
            try { AiDrafts.parse(raw); fail() } catch (_: AiFailure) {}
        }
        assertNull(AiDraft("m", kind = "MEMO", body = "整理后的文字").validationError())
    }
    @Test fun proposalsStayInMemoryAndFollowupReceivesEditedDraftAndFixedDate() = runTest {
        var turns = 0
        val requests = mutableListOf<List<JsonObject>>()
        val client = object : AiClient {
            override suspend fun models(key: String) = AiProtocol.models
            override suspend fun complete(key: String, config: AiConfig, messages: List<JsonObject>, tools: JsonArray?): AiReply {
                requests += messages
                turns++
                if (turns == 1) return AiReply(buildJsonObject {
                    put("role", "assistant"); put("content", JsonNull)
                    put("tool_calls", buildJsonArray {
                        add(buildJsonObject {
                            put("id", "call-1"); put("type", "function")
                            put("function", buildJsonObject {
                                put("name", "propose_items")
                                put("arguments", AiDrafts.encode(listOf(AiDraft("a", title = "买书"))))
                            })
                        })
                    })
                })
                return AiReply(AiProtocol.message("assistant", "草稿已生成，请确认。"))
            }
        }
        val credentials = object : AiCredentials {
            override fun config() = AiConfig(configured = true)
            override fun key() = "fake-test-only"
            override fun save(key: String?, model: String, thinking: Boolean) {}
            override fun delete() {}
        }
        val clock = Clock.fixed(Instant.parse("2026-12-31T16:30:00Z"), ZoneId.of("Asia/Shanghai"))
        val session = AiSession(credentials, client, backgroundScope, clock = clock)
        session.input("记个任务买书"); session.send(); runCurrent()
        assertEquals(1, session.state.value.drafts.size)
        assertTrue(requests.first().first().toString().contains("2027-01-01"))
        assertNull(session.state.value.drafts.single().date)
        session.editDraft(session.state.value.drafts.single().copy(title = "买两本书"))
        session.input("请保留这个标题"); session.send(); runCurrent()
        assertTrue(requests.last()[1].toString().contains("买两本书"))
        session.clear(); assertTrue(session.state.value.drafts.isEmpty())
    }
}
