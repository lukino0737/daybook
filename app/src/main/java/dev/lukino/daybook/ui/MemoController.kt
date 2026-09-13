package dev.lukino.daybook.ui

import androidx.lifecycle.SavedStateHandle
import dev.lukino.daybook.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class MemoController(private val repository: EntryRepository, private val saved: SavedStateHandle, private val scope: CoroutineScope) {
    private val raw = saved.getStateFlow<String?>("memo-draft", null)
    val draft = raw.map { it?.let { Json.decodeFromString<Memo>(it) } }
        .stateIn(scope, SharingStarted.Eagerly, raw.value?.let { Json.decodeFromString<Memo>(it) })
    val items = repository.memos.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val busy = MutableStateFlow(false)
    val status = MutableStateFlow("已保存")
    val error = MutableStateFlow<String?>(null)
    private val writer = Mutex()
    private var job: Job? = null
    private fun current() = raw.value?.let { Json.decodeFromString<Memo>(it) }
    fun open(memo: Memo? = null) {
        if (busy.value) return
        scope.launch {
            if (current() != null && !finish(false)) return@launch
            saved["memo-new"] = memo == null
            saved["memo-draft"] = Json.encodeToString(memo ?: Memo())
            status.value = if (memo == null) "输入后自动保存" else "已保存"
            error.value = null
        }
    }
    fun change(value: Memo) {
        if (busy.value) return
        val old = current() ?: return
        saved["memo-draft"] = Json.encodeToString(value.copy(updatedAt = maxOf(System.currentTimeMillis(), old.updatedAt + 1),
            reminderDeliveredFor = old.reminderDeliveredFor.takeIf { old.reminderAt == value.reminderAt }))
        flush(300)
    }
    fun flush(delayMillis: Long = 0) {
        if (busy.value || current() == null) return
        job?.cancel()
        status.value = "正在保存…"
        job = scope.launch { delay(delayMillis); writer.withLock { persist(false) } }
    }
    private suspend fun persist(leaving: Boolean): Boolean {
        val value = current() ?: return true
        return try {
            if (value.body.isBlank()) {
                if (leaving && saved.get<Boolean>("memo-new") == true) repository.deleteMemo(value.id)
                else if (leaving) error("正文不能为空；不需要时请选择删除")
                status.value = "输入后自动保存"
            } else {
                repository.saveMemo(value)
                if (current() == value) status.value = "已保存"
            }
            error.value = null
            true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { status.value = "未保存"; error.value = e.localizedMessage ?: "保存失败，请重试"; false }
    }
    private suspend fun finish(remove: Boolean): Boolean {
        job?.cancelAndJoin()
        return writer.withLock {
            if (remove) {
                try { current()?.let { repository.deleteMemo(it.id) }; true }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error.value = e.localizedMessage ?: "删除失败"; false }
            } else persist(true)
        }
    }
    fun close(remove: Boolean = false) {
        if (busy.value) return
        busy.value = true
        scope.launch {
            try { if (finish(remove)) saved["memo-draft"] = null }
            finally { busy.value = false }
        }
    }
}
