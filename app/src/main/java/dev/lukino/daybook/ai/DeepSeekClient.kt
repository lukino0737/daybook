package dev.lukino.daybook.ai

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Fixed origin, no redirects, bounded responses; cancellation closes the active connection. */
class DeepSeekClient : AiClient {
    override suspend fun models(key: String): List<String> = try {
        AiProtocol.json.parseToJsonElement(request(key, "/models", null)).jsonObject
            .getValue("data").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
    } catch (e: AiFailure) { throw e }
    catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (_: Exception) { throw AiFailure("模型列表格式不正确，请稍后重试。") }

    override suspend fun complete(key: String, config: AiConfig, messages: List<JsonObject>, tools: JsonArray?): AiReply =
        AiProtocol.reply(request(key, "/chat/completions", AiProtocol.request(config, messages, tools)))

    private suspend fun request(key: String, path: String, body: String?): String = withTimeout(120_000) {
        if (key.isBlank() || key.any { it.isWhitespace() || it.code < 32 }) throw AiFailure("请先在AI设置中填写有效的API Key。")
        suspendCancellableCoroutine { continuation ->
            val active = AtomicReference<HttpsURLConnection?>()
            val future = executor.submit {
                try {
                    val connection = URL("https://api.deepseek.com$path").openConnection() as HttpsURLConnection
                    active.set(connection)
                    try {
                        if (!continuation.isActive) return@submit
                        connection.instanceFollowRedirects = false
                        connection.connectTimeout = 15_000
                        connection.readTimeout = 90_000
                        connection.requestMethod = if (body == null) "GET" else "POST"
                        connection.setRequestProperty("Authorization", "Bearer $key")
                        connection.setRequestProperty("Accept", "application/json")
                        if (body != null) {
                            connection.doOutput = true
                            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                        }
                        if (connection.responseCode != 200) throw AiProtocol.httpError(connection.responseCode)
                        val result = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                            val out = StringBuilder()
                            val buffer = CharArray(4096)
                            while (true) {
                                if (!continuation.isActive) return@submit
                                val count = reader.read(buffer)
                                if (count < 0) break
                                if (out.length + count > 256_000) throw AiFailure("响应过大，请缩小问题范围。")
                                out.append(buffer, 0, count)
                            }
                            out.toString()
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } finally { connection.disconnect(); active.compareAndSet(connection, null) }
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(when (e) {
                        is AiFailure -> e
                        is java.net.SocketTimeoutException -> AiFailure("连接超时，输入已保留，请稍后重试。")
                        else -> AiFailure("暂时无法连接DeepSeek，请检查网络后重试。")
                    })
                }
            }
            continuation.invokeOnCancellation { active.getAndSet(null)?.disconnect(); future.cancel(true) }
        }
    }
    companion object { private val executor = Executors.newFixedThreadPool(2) }
}
