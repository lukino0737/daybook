package dev.lukino.daybook

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.lukino.daybook.ai.*
import dev.lukino.daybook.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AiReviewTest {
    @Test fun reviewReadsBoundedTextThenCreatesUnsavedMemoUntilUserConfirms() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DaybookDatabase::class.java).build()
        val repo = EntryRepository(db)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val original = Memo(body = "本周整理了书房，准备周末读书。", date = "2026-09-23")
        val seen = mutableListOf<String>()
        var step = 0
        val client = object : AiClient {
            override suspend fun models(key: String) = AiProtocol.models
            override suspend fun complete(key: String, config: AiConfig, messages: List<JsonObject>, tools: JsonArray?): AiReply {
                seen += messages.toString(); step++
                val (name, args) = when (step) {
                    1 -> "query_records" to """{"kind":"MEMO","from":"2026-09-21","through":"2026-09-27"}"""
                    2 -> "read_records" to """{"sources":["memo:${original.id}"]}"""
                    3 -> "propose_items" to AiDrafts.encode(listOf(AiDraft("review", kind = "MEMO", body = "本周回顾：整理书房，计划周末读书。")))
                    else -> return AiReply(AiProtocol.message("assistant", "已根据所查便签生成回顾草稿，请确认保存。"))
                }
                return AiReply(buildJsonObject {
                    put("role", "assistant"); put("content", JsonNull)
                    put("tool_calls", buildJsonArray { add(buildJsonObject {
                        put("id", "call-$step"); put("type", "function")
                        put("function", buildJsonObject { put("name", name); put("arguments", args) })
                    }) })
                })
            }
        }
        val credentials = object : AiCredentials {
            override fun config() = AiConfig(configured = true)
            override fun key() = "fake-only"
            override fun save(key: String?, model: String, thinking: Boolean) {}
            override fun delete() {}
        }
        val session = AiSession(credentials, client, scope, repo)
        try {
            repo.saveMemo(original)
            withContext(Dispatchers.Main) { session.input("请总结我本周的便签并生成回顾"); session.send() }
            withTimeout(5000) { session.state.first { !it.busy } }
            assertNull(session.state.value.error)
            assertEquals(1, session.state.value.drafts.size)
            assertEquals("memo:${original.id}", session.state.value.lines.last().sources.single().key)
            assertFalse(seen.first().contains(original.body))
            assertTrue(seen.last().contains(original.body))
            assertEquals(listOf(original), repo.allMemos())
            withContext(Dispatchers.Main) { session.saveDrafts() }
            withTimeout(5000) { session.state.first { !it.busy } }
            assertEquals(2, repo.allMemos().size)
        } finally { scope.cancel(); db.close() }
    }
}
