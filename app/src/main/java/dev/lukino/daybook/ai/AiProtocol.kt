package dev.lukino.daybook.ai

import kotlinx.serialization.json.*

data class AiConfig(val model: String = "deepseek-flash", val thinking: Boolean = false, val configured: Boolean = false)

interface AiCredentials {
    fun config(): AiConfig
    fun key(): String
    fun save(key: String?, model: String, thinking: Boolean)
    fun delete()
}

class AiFailure(message: String) : Exception(message)

data class AiReply(val message: JsonObject, val tokens: Int = 0) {
    val text: String get() = message["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val calls: JsonArray get() = message["tool_calls"] as? JsonArray ?: JsonArray(emptyList())
}

interface AiClient {
    suspend fun models(key: String): List<String>
    suspend fun complete(key: String, config: AiConfig, messages: List<JsonObject>, tools: JsonArray? = null): AiReply
}

object AiProtocol {
    val models = listOf("deepseek-flash", "deepseek-v4-pro")
    val json = Json { ignoreUnknownKeys = true }
    fun message(role: String, content: String) = buildJsonObject { put("role", role); put("content", content) }
    fun request(config: AiConfig, messages: List<JsonObject>, tools: JsonArray?): String {
        require(config.model in models)
        return buildJsonObject {
            put("model", config.model)
            put("messages", JsonArray(messages))
            put("thinking", buildJsonObject { put("type", if (config.thinking) "enabled" else "disabled") })
            if (config.thinking) put("reasoning_effort", "high")
            put("max_tokens", 8192)
            put("stream", false)
            if (tools != null && tools.isNotEmpty()) put("tools", tools)
        }.toString()
    }
    fun reply(raw: String): AiReply = try {
        val root = json.parseToJsonElement(raw).jsonObject
        val choice = root.getValue("choices").jsonArray.single().jsonObject
        val finish = choice.getValue("finish_reason").jsonPrimitive.content
        if (finish == "length") throw AiFailure("回答过长而被截断，请缩小问题范围后重试。")
        if (finish !in setOf("stop", "tool_calls")) throw AiFailure("模型没有完成回答，请调整输入后重试。")
        val source = choice.getValue("message").jsonObject
        if (source["role"]?.jsonPrimitive?.content != "assistant") throw AiFailure("模型响应格式不正确，请重试。")
        // Only retain fields used by the protocol; never reflect arbitrary provider metadata.
        val message = JsonObject(source.filterKeys { it in setOf("role", "content", "reasoning_content", "tool_calls") })
        val result = AiReply(message, root["usage"]?.jsonObject?.get("total_tokens")?.jsonPrimitive?.intOrNull ?: 0)
        if (result.text.isBlank() && result.calls.isEmpty()) throw AiFailure("模型返回了空内容，请重试。")
        if (result.calls.size > 4 || result.text.length > 32_000) throw AiFailure("本次回答超出处理范围，请拆成较小的问题。")
        result
    } catch (e: AiFailure) { throw e }
    catch (_: Exception) { throw AiFailure("模型响应格式不正确，请重试。") }

    fun httpError(code: Int): AiFailure = AiFailure(when (code) {
        401, 403 -> "API Key 无效或没有权限，请检查AI设置。"
        402 -> "DeepSeek 余额不足，请检查API账户。"
        429 -> "请求过于频繁，请稍后重试。"
        in 500..599 -> "DeepSeek 服务暂时不可用，请稍后重试。"
        else -> "请求未被接受（HTTP $code），请检查模型设置或调整输入。"
    })
}
