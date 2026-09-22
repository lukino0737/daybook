package dev.lukino.daybook.ui

import androidx.lifecycle.SavedStateHandle
import dev.lukino.daybook.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class MemoController(private val repository: EntryRepository, private val saved: SavedStateHandle, private val scope: CoroutineScope) {
    val images = ImageEditorController(repository, scope)
    private val raw = saved.getStateFlow<String?>("memo-draft", null)
    val draft = raw.map { it?.let { Json.decodeFromString<Memo>(it) } }
        .stateIn(scope, SharingStarted.Eagerly, raw.value?.let { Json.decodeFromString<Memo>(it) })
    val items = repository.memos.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    val busy = MutableStateFlow(false)
    val status = MutableStateFlow("已保存")
    val error = MutableStateFlow<String?>(null)
    val confirmBlankExit = MutableStateFlow(false)
    private val writer = Mutex()
    private var job: Job? = null
    private fun current() = raw.value?.let { Json.decodeFromString<Memo>(it) }
    private fun hasContent(value: Memo) = value.body.isNotBlank() || RichBody.images(value.blocks).isNotEmpty()
    private fun pin(value: Memo?) { repository.images?.pin("memo-draft", value?.let { RichBody.images(it.blocks) }.orEmpty()) }
    private fun collectImages() { scope.launch { runCatching { repository.collectImages() } } }
    init { pin(current()) }
    fun open(memo: Memo? = null) {
        if (busy.value || images.busy.value) return
        scope.launch {
            if (current() != null && !finish(false)) return@launch
            pin(memo)
            saved["memo-new"] = memo == null
            saved["memo-draft"] = Json.encodeToString(memo ?: Memo())
            status.value = if (memo == null) "输入后自动保存" else "已保存"
            error.value = null
            confirmBlankExit.value = false
        }
    }
    fun change(value: Memo) {
        if (busy.value) return
        val old = current() ?: return
        pin(value)
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
            if (!hasContent(value)) {
                if (leaving && saved.get<Boolean>("memo-new") == true) repository.deleteMemo(value.id)
                else if (leaving) {
                    error.value = null
                    confirmBlankExit.value = true
                    return false
                }
                status.value = if (saved.get<Boolean>("memo-new") == true) "输入后自动保存" else "正文已清空，上次保存的内容仍保留"
            } else {
                repository.saveMemo(value)
                if (current() == value) status.value = "已保存"
                collectImages()
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
    fun cancelBlankExit() { confirmBlankExit.value = false }
    fun keepSavedAndClose() {
        if (busy.value || images.busy.value) return
        busy.value = true
        scope.launch {
            try {
                job?.cancelAndJoin()
                writer.withLock {
                    if (current()?.let { !hasContent(it) } == true && saved.get<Boolean>("memo-new") != true) {
                        saved["memo-draft"] = null
                        pin(null); collectImages()
                        confirmBlankExit.value = false
                        error.value = null
                    }
                }
            } finally { busy.value = false }
        }
    }
    fun close(remove: Boolean = false) {
        if (busy.value || images.busy.value) return
        busy.value = true
        scope.launch {
            try { if (finish(remove)) { saved["memo-draft"] = null; pin(null); collectImages(); confirmBlankExit.value = false } }
            finally { busy.value = false }
        }
    }
}
