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
import android.net.Uri
import dev.lukino.daybook.backup.BackupArchive
import dev.lukino.daybook.backup.BackupService

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
    val tagsText: String = "",
    val reminderAt: String? = null,
    val reminderDeliveredFor: String? = null,
) {
    fun entry(): Entry {
        val now = System.currentTimeMillis()
        val fresh = Entry(tags = ReviewRules.parseTags(tagsText), kind = kind, title = title.trim(), note = note, date = date, time = time,
            reminderAt = if (kind == EntryKind.NOTE) null else reminderAt,
            reminderDeliveredFor = reminderDeliveredFor.takeIf { kind != EntryKind.NOTE && it == reminderAt },
            completed = kind == EntryKind.TASK && completed, createdAt = createdAt ?: now, updatedAt = maxOf(now, createdAt ?: now))
        return if (id == null) fresh else fresh.copy(id = id)
    }
}

sealed interface UiNotice {
    data class Message(val text: String) : UiNotice
    data class Deleted(val entry: Entry, val historyVersion: Int) : UiNotice
}

class DaybookViewModel(private val repository: EntryRepository, private val saved: SavedStateHandle, private val backup: BackupService) : ViewModel() {
    val entries = repository.entries.catch { failure.value = "读取失败，请重新打开应用：${it.localizedMessage}" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val selected = saved.getStateFlow("selected", LocalDate.now().toString())
    val month = saved.getStateFlow("month", LocalDate.now().toString().take(7))
    private val reviewJson = saved.getStateFlow("review-selection", Json.encodeToString(ReviewSelection()))
    val reviewSelection = reviewJson.map { Json.decodeFromString<ReviewSelection>(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Json.decodeFromString(reviewJson.value))
    private val appliedJson = saved.getStateFlow<String?>("review-applied", null)
    val appliedReview = appliedJson.map { it?.let { Json.decodeFromString<ReviewSelection>(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val confirmAllReview = saved.getStateFlow("review-confirm-all", false)
    val reviewError = MutableStateFlow<String?>(null)
    fun setReview(value: ReviewSelection) {
        saved["review-selection"] = Json.encodeToString(value)
        saved["review-confirm-all"] = false
        reviewError.value = null
    }
    fun applyReview(confirmed: Boolean = false) {
        val selection = Json.decodeFromString<ReviewSelection>(reviewJson.value)
        try { selection.validate() } catch (e: Exception) { reviewError.value = e.message; return }
        if (selection.isUnrestricted() && !confirmed) { saved["review-confirm-all"] = true; return }
        saved["review-applied"] = Json.encodeToString(selection)
        saved["review-confirm-all"] = false
    }
    fun resetReview() {
        setReview(ReviewSelection())
        saved["review-applied"] = null
    }
    fun dismissReviewConfirmation() { saved["review-confirm-all"] = false }
    fun openTag(value: String) { setReview(ReviewSelection(tag = value)); saved["review-applied"] = null; setView("review") }
    init { if (saved.get<String>("view") == "undated") saved["view"] = "tasks" }
    val view = saved.getStateFlow("view", "day")
    private val draftJson = saved.getStateFlow<String?>("draft", null)
    val draft = draftJson.map { it?.let { Json.decodeFromString<Draft>(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val busy = MutableStateFlow(false)
    val failure = MutableStateFlow<String?>(null)
    val now = MutableStateFlow(LocalDateTime.now())
    val pendingRestore = MutableStateFlow<BackupArchive?>(null)
    val restoringSnapshot = MutableStateFlow(false)
    private val channel = Channel<UiNotice>(Channel.BUFFERED)
    val notices = channel.receiveAsFlow()

    fun refreshNow() { now.value = LocalDateTime.now() }
    fun select(date: LocalDate) { saved["selected"] = date.toString(); saved["view"] = "day" }
    fun setMonth(value: String) { saved["month"] = value }
    fun setView(value: String) { saved["view"] = value }
    fun today() { refreshNow(); select(now.value.toLocalDate()); setMonth(now.value.toLocalDate().toString().take(7)) }
    fun edit(entry: Entry? = null) {
        if (busy.value) return
        failure.value = null
        setDraft(entry?.let { Draft(it.id, it.title, it.note, it.kind, it.date, it.time, it.completed, it.createdAt, it.tags.joinToString("，"), it.reminderAt, it.reminderDeliveredFor) }
            ?: when (view.value) {
                "review" -> Draft(date = selected.value, kind = EntryKind.NOTE)
                "tasks" -> Draft(date = selected.value, kind = EntryKind.TASK)
                else -> Draft(date = selected.value)
            })
    }
    fun openEntry(id: String) {
        viewModelScope.launch {
            try {
                val entry = repository.all().firstOrNull { it.id == id }
                if (entry != null) edit(entry) else channel.send(UiNotice.Message("这条记录已删除"))
            } catch (_: Exception) { failure.value = "暂时无法打开记录，请重试" }
        }
    }
    fun setDraft(value: Draft) { saved["draft"] = Json.encodeToString(value) }
    fun dismissDraft() { if (!busy.value) { saved["draft"] = null; failure.value = null } }
    fun clearError() { failure.value = null }

    fun save() = runWrite {
        val draft = saved.get<String>("draft")?.let { Json.decodeFromString<Draft>(it) } ?: return@runWrite
        val entry = draft.entry()
        repository.save(entry)
        saved["draft"] = null
        if (view.value == "day") entry.date?.let { select(LocalDate.parse(it)); setMonth(it.take(7)) } ?: setView("tasks")
        channel.send(UiNotice.Message("已保存"))
    }
    fun toggle(entry: Entry) = runWrite {
        repository.save(entry.copy(completed = !entry.completed, updatedAt = maxOf(System.currentTimeMillis(), entry.createdAt)))
    }
    fun delete(entry: Entry) = runWrite {
        repository.delete(entry.id)
        saved["draft"] = null
        channel.send(UiNotice.Deleted(entry, historyVersion.value))
    }
    fun undoDelete(entry: Entry, expectedVersion: Int) = runWrite {
        if (expectedVersion != historyVersion.value) return@runWrite
        repository.save(entry)
        channel.send(UiNotice.Message("已恢复"))
    }

    fun export(uri: Uri) = runWrite { backup.export(uri); channel.send(UiNotice.Message("备份已导出")) }
    fun previewImport(uri: Uri) = runWrite {
        val archive = backup.preview(uri)
        restoringSnapshot.value = false
        pendingRestore.value = archive
    }
    fun previewSnapshot() = runWrite {
        val archive = backup.previewSnapshot()
        restoringSnapshot.value = true
        pendingRestore.value = archive
    }
    fun dismissRestore() { if (!busy.value) pendingRestore.value = null }
    fun confirmRestore() = runWrite {
        val archive = pendingRestore.value ?: return@runWrite
        backup.restore(archive)
        pendingRestore.value = null
        // Old undo notices must not reintroduce entries from a replaced database.
        historyVersion.value += 1
        channel.send(UiNotice.Message("已恢复 ${archive.entries.size} 条记录"))
    }
    val historyVersion = MutableStateFlow(0)

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
