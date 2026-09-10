package dev.lukino.daybook.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.lukino.daybook.data.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime

@Serializable
data class Draft(
    val id: String? = null,
    val title: String = "",
    val note: String = "",
    val kind: EntryKind = EntryKind.EVENT,
    val date: String? = null,
    val time: String? = null,
    val completed: Boolean = false,
    val createdAt: Long? = null,
) {
    fun entry(): Entry {
        val now = System.currentTimeMillis()
        val fresh = Entry(kind = kind, title = title.trim(), note = note, date = date, time = time,
            completed = kind == EntryKind.TASK && completed, createdAt = createdAt ?: now, updatedAt = maxOf(now, createdAt ?: now))
        return if (id == null) fresh else fresh.copy(id = id)
    }
}

sealed interface UiNotice {
    data class Message(val text: String) : UiNotice
    data class Deleted(val entry: Entry) : UiNotice
}

class DaybookViewModel(private val repository: EntryRepository, private val saved: SavedStateHandle) : ViewModel() {
    val entries = repository.entries.catch { failure.value = "读取失败，请重新打开应用：${it.localizedMessage}" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val selected = saved.getStateFlow("selected", LocalDate.now().toString())
    val month = saved.getStateFlow("month", LocalDate.now().toString().take(7))
    val view = saved.getStateFlow("view", "day")
    private val draftJson = saved.getStateFlow<String?>("draft", null)
    val draft = draftJson.map { it?.let { Json.decodeFromString<Draft>(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val busy = MutableStateFlow(false)
    val failure = MutableStateFlow<String?>(null)
    val now = MutableStateFlow(LocalDateTime.now())
    private val channel = Channel<UiNotice>(Channel.BUFFERED)
    val notices = channel.receiveAsFlow()

    fun refreshNow() { now.value = LocalDateTime.now() }
    fun select(date: LocalDate) { saved["selected"] = date.toString(); saved["view"] = "day" }
    fun setMonth(value: String) { saved["month"] = value }
    fun setView(value: String) { saved["view"] = value }
    fun today() { refreshNow(); select(now.value.toLocalDate()); setMonth(now.value.toLocalDate().toString().take(7)) }
    fun edit(entry: Entry? = null) {
        failure.value = null
        setDraft(entry?.let { Draft(it.id, it.title, it.note, it.kind, it.date, it.time, it.completed, it.createdAt) }
            ?: Draft(date = selected.value))
    }
    fun setDraft(value: Draft) { saved["draft"] = Json.encodeToString(value) }
    fun dismissDraft() { if (!busy.value) { saved["draft"] = null; failure.value = null } }
    fun clearError() { failure.value = null }

    fun save() = runWrite {
        val draft = saved.get<String>("draft")?.let { Json.decodeFromString<Draft>(it) } ?: return@runWrite
        val entry = draft.entry()
        repository.save(entry)
        saved["draft"] = null
        entry.date?.let { select(LocalDate.parse(it)); setMonth(it.take(7)) } ?: setView("undated")
        channel.send(UiNotice.Message("已保存"))
    }
    fun toggle(entry: Entry) = runWrite {
        repository.save(entry.copy(completed = !entry.completed, updatedAt = maxOf(System.currentTimeMillis(), entry.createdAt)))
    }
    fun delete(entry: Entry) = runWrite {
        repository.delete(entry.id)
        saved["draft"] = null
        channel.send(UiNotice.Deleted(entry))
    }
    fun undoDelete(entry: Entry) = runWrite { repository.save(entry); channel.send(UiNotice.Message("已恢复")) }

    private fun runWrite(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        failure.value = null
        viewModelScope.launch {
            try { block() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { failure.value = e.localizedMessage ?: "操作失败，请重试" }
            finally { busy.value = false }
        }
    }
}
