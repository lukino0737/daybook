package dev.lukino.daybook.ai

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.ZoneId

data class AiLine(val role: String, val text: String)
data class AiState(
    val config: AiConfig = AiConfig(), val input: String = "", val lines: List<AiLine> = emptyList(),
    val busy: Boolean = false, val error: String? = null, val status: String? = null, val tokens: Int = 0,
)

/** Process memory only. Never put conversation contents in SavedState, files or preferences. */
class AiSession(private val credentials: AiCredentials, private val client: AiClient, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(AiState(config = credentials.config()))
    val state = mutable.asStateFlow()
    private var history = emptyList<JsonObject>()
    private var job: Job? = null
    private var generation = 0
    fun input(value: String) { if (!state.value.busy) mutable.value = state.value.copy(input = value.take(20_000), error = null) }
    fun select(model: String, thinking: Boolean) {
        if (!state.value.busy && model in AiProtocol.models) mutable.value = state.value.copy(config = state.value.config.copy(model = model, thinking = thinking))
    }
    fun saveSettings(key: String?, model: String, thinking: Boolean): Boolean = try {
        check(!state.value.busy)
        credentials.save(key, model, thinking)
        mutable.value = state.value.copy(config = credentials.config(), error = null, status = "AI设置已保存")
        true
    } catch (_: Exception) { mutable.value = state.value.copy(error = "AI设置未保存，请检查Key格式或重试。"); false }
    fun deleteKey() {
        stop()
        try { credentials.delete(); mutable.value = state.value.copy(config = credentials.config(), error = null, status = "API Key 已删除") }
        catch (_: Exception) { mutable.value = state.value.copy(error = "密钥未删除，请重试。") }
    }
    fun stop() {
        generation++
        job?.cancel(); job = null
        mutable.value = state.value.copy(busy = false, status = "已停止；未发送或未完成的输入仍保留")
    }
    fun clear() {
        stop(); history = emptyList()
        mutable.value = AiState(config = state.value.config)
    }
    fun checkConnection() = launchRequest {
        val names = client.models(credentials.key())
        currentCoroutineContext().ensureActive()
        if (state.value.config.model !in names) throw AiFailure("此Key未返回所选模型，请选择其他模型或检查API账户。")
        mutable.value = state.value.copy(status = "已连接；所选模型在列表中。实际生成效果需另行验证。")
    }
    fun send() {
        val input = state.value.input.trim()
        if (input.isEmpty()) return
        launchRequest {
            if (history.sumOf { it.toString().length } + input.length > 60_000) throw AiFailure("本次对话已较长，请先新建对话；当前输入仍保留。")
            val config = state.value.config
            val pending = history + AiProtocol.message("user", input)
            val reply = client.complete(credentials.key(), config, listOf(AiProtocol.message("system",
                "你是Daybook助手，用中文清晰回答。当前本地日期${LocalDate.now()}，时区${ZoneId.systemDefault()}。当前没有数据查询或写入工具，不能声称读取或保存了用户的记录。不确定的事实应说明。")) + pending)
            currentCoroutineContext().ensureActive()
            if (reply.calls.isNotEmpty()) throw AiFailure("模型请求了当前不可用的操作，请重试。")
            history = pending + reply.message
            mutable.value = state.value.copy(input = "", lines = state.value.lines + AiLine("user", input) + AiLine("assistant", reply.text),
                tokens = state.value.tokens + reply.tokens, status = "本次使用 ${config.model}${if (config.thinking) " · 深度思考" else ""}")
        }
    }
    private fun launchRequest(block: suspend () -> Unit) {
        if (state.value.busy) return
        if (!state.value.config.configured) { mutable.value = state.value.copy(error = "请先打开AI设置，填写API Key。"); return }
        val current = ++generation
        mutable.value = state.value.copy(busy = true, error = null, status = "正在请求DeepSeek…")
        job = scope.launch {
            try { block() }
            catch (_: TimeoutCancellationException) { if (current == generation) mutable.value = state.value.copy(error = "请求超时，输入已保留，请稍后重试。") }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current == generation) mutable.value = state.value.copy(error = (e as? AiFailure)?.message ?: "操作未完成，输入已保留，请重试。") }
            finally { if (current == generation) mutable.value = state.value.copy(busy = false) }
        }
    }
}
