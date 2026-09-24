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
    val blocks: List<BodyBlock> = emptyList(),
) {
    fun entry(): Entry {
        val now = System.currentTimeMillis()
        val fresh = Entry(blocks = blocks, tags = ReviewRules.parseTags(tagsText), kind = kind, title = title.trim(), note = note, date = date, time = time,
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
    val imageEditor = ImageEditorController(repository, viewModelScope)
    val memoEditor = MemoController(repository, saved, viewModelScope)
    val standaloneEditor = StandaloneReminderController(repository, saved, viewModelScope)
    val reminderListVisible = saved.getStateFlow("reminder-list-visible", false)
    val reminderListMessage = saved.getStateFlow<String?>("reminder-list-message", null)
    val pendingNotification = saved.getStateFlow<String?>("pending-notification", null)
    fun showReminderList() { saved["reminder-list-visible"] = true; saved["reminder-list-message"] = null }
    fun hideReminderList() { saved["reminder-list-visible"] = false }
    fun newReminder() { saved["entry-before-reminder"] = null; standaloneEditor.open() }
    fun reminderFromEntry() {
        val draft = saved.get<String>("draft")?.let { Json.decodeFromString<Draft>(it) } ?: return
        if (draft.id != null || busy.value || RichBody.images(draft.blocks).isNotEmpty()) return
        saved["entry-before-reminder"] = Json.encodeToString(draft)
        standaloneEditor.open(date = draft.date?.let(LocalDate::parse) ?: LocalDate.now(), title = draft.title, note = draft.note)
        dismissDraft()
    }
    fun entryFromReminder(kind: EntryKind) {
        if (!standaloneEditor.isNew || standaloneEditor.busy.value) return
        val value = standaloneEditor.current() ?: return
        val original = saved.get<String>("entry-before-reminder")?.let { Json.decodeFromString<Draft>(it) } ?: Draft()
        standaloneEditor.dismiss()
        setDraft(original.copy(kind = kind, title = value.title, note = value.note, blocks = emptyList(),
            date = if (kind == EntryKind.TASK) original.date.takeIf { original.kind == EntryKind.TASK } else value.startDate,
            time = if (kind == EntryKind.TASK) original.time.takeIf { original.kind == EntryKind.TASK } else value.time))
    }
    fun openReminder(id: String) {
        viewModelScope.launch {
            try {
                val value = repository.allReminders().firstOrNull { it.id == id }
                if (value != null) standaloneEditor.open(value)
                else { saved["reminder-list-visible"] = true; saved["reminder-list-message"] = "这条提醒已删除" }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { failure.value = "暂时无法打开提醒，请重试" }
        }
    }
    fun openReminderRow(row: dev.lukino.daybook.reminder.ReminderListItem) {
        when {
            row.key.startsWith("reminder:") -> openReminder(row.id)
            row.key.startsWith("memo:") -> openMemo(row.id)
            else -> openEntry(row.id)
        }
    }
    fun notification(host: String, id: String) {
        if (host !in setOf("entry", "memo", "reminder")) return
        // Preserve an open draft; follow the notification after the user saves or cancels it.
        saved["pending-notification"] = "$host/$id"
        consumeNotification()
    }
    private fun hasOpenDraft() = saved.get<String>("draft") != null ||
        saved.get<String>("memo-draft") != null || standaloneEditor.current() != null
    val detailTarget = saved.getStateFlow<String?>("notification-detail", null)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val notificationDetail = detailTarget.flatMapLatest { target ->
        if (target == null) flowOf(null)
        else combine(repository.entries, repository.memos, repository.reminders) { entries, memos, reminders ->
            val id = target.substringAfter('/')
            NotificationDetail(target, entry = entries.firstOrNull { target.startsWith("entry/") && it.id == id },
                memo = memos.firstOrNull { target.startsWith("memo/") && it.id == id },
                reminder = reminders.firstOrNull { target.startsWith("reminder/") && it.id == id })
        }.catch { emit(NotificationDetail(target, error = "暂时无法读取内容，请返回后重试")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun closeDetail() { saved["notification-detail"] = null }
    fun consumeNotification() {
        if (hasOpenDraft()) return
        val target = saved.get<String>("pending-notification") ?: return
        saved["pending-notification"] = null
        saved["notification-detail"] = target
    }
    fun editDetail() {
        val target = detailTarget.value ?: return
        if (hasOpenDraft() || busy.value) return
        viewModelScope.launch {
            try {
                val id = target.substringAfter('/')
                val entry = if (target.startsWith("entry/")) repository.all().firstOrNull { it.id == id } else null
                val memo = if (target.startsWith("memo/")) repository.allMemos().firstOrNull { it.id == id } else null
                val reminder = if (target.startsWith("reminder/")) repository.allReminders().firstOrNull { it.id == id } else null
                if (detailTarget.value != target || hasOpenDraft() || busy.value) return@launch
                when {
                    entry != null -> edit(entry)
                    memo != null -> memoEditor.open(memo)
                    reminder != null -> standaloneEditor.open(reminder)
                    else -> channel.send(UiNotice.Message("这条内容已删除"))
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { failure.value = "暂时无法打开编辑，请重试" }
        }
    }
    val reminders = repository.reminders.catch { failure.value = "读取提醒失败：${it.localizedMessage}" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
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
        setDraft(entry?.let { Draft(it.id, it.title, it.note, it.kind, it.date, it.time, it.completed, it.createdAt, it.tags.joinToString("，"), it.reminderAt, it.reminderDeliveredFor, it.blocks) }
            ?: when (view.value) {
                "review" -> Draft(date = selected.value, kind = EntryKind.NOTE)
                "tasks" -> Draft(kind = EntryKind.TASK)
                else -> Draft(date = selected.value, kind = defaultCalendarKind(LocalDate.parse(selected.value), LocalDate.now()))
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
    fun openMemo(id: String) {
        viewModelScope.launch {
            try {
                val memo = repository.allMemos().firstOrNull { it.id == id }
                if (memo == null) channel.send(UiNotice.Message("这条便签已删除"))
                else { saved["view"] = "memos"; memoEditor.open(memo) }
            } catch (_: Exception) { failure.value = "暂时无法打开便签，请重试" }
        }
    }
    fun setDraft(value: Draft) {
        repository.images?.pin("entry-draft", RichBody.images(value.blocks))
        saved["draft"] = Json.encodeToString(value)
    }
    private fun collectImages() { viewModelScope.launch { runCatching { repository.collectImages() } } }
    fun dismissDraft() { if (!busy.value && !imageEditor.busy.value) {
        saved["draft"] = null; failure.value = null; repository.images?.release("entry-draft"); collectImages()
    } }
    init {
        val restored = saved.get<String>("draft")?.let { Json.decodeFromString<Draft>(it) }
        repository.images?.pin("entry-draft", restored?.let { RichBody.images(it.blocks) }.orEmpty())
        repository.images?.ready()
        collectImages()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { backup.discardAbandonedPreviews() }
    }
    fun clearError() { failure.value = null }

    fun save() = runWrite {
        val draft = saved.get<String>("draft")?.let { Json.decodeFromString<Draft>(it) } ?: return@runWrite
        val entry = draft.entry()
        repository.save(entry)
        saved["draft"] = null
        repository.images?.release("entry-draft"); collectImages()
        if (view.value == "day") entry.date?.let { select(LocalDate.parse(it)); setMonth(it.take(7)) } ?: setView("tasks")
        channel.send(UiNotice.Message("已保存"))
    }
    val completing = MutableStateFlow<Set<String>>(emptySet())
    val toggling = MutableStateFlow<Set<String>>(emptySet())
    fun toggle(entry: Entry) {
        if (entry.kind != EntryKind.TASK || busy.value || imageEditor.busy.value || entry.id in toggling.value) return
        toggling.value += entry.id
        if (!entry.completed) completing.value += entry.id
        failure.value = null
        viewModelScope.launch {
            try {
                repository.toggleTask(entry)
                // The hold belongs to this row; unrelated cards remain enabled and unchanged.
                if (!entry.completed) kotlinx.coroutines.delay(400)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { failure.value = e.localizedMessage ?: "任务状态未修改，请重试" }
            finally { completing.value -= entry.id; toggling.value -= entry.id }
        }
    }
    fun deleteMemo(memo: Memo) = runWrite {
        repository.deleteMemo(memo.id)
        collectImages()
        channel.send(UiNotice.Message("已删除便签"))
    }
    fun delete(entry: Entry) = runWrite {
        repository.images?.pin("undo-${entry.id}", RichBody.images(entry.blocks))
        try { repository.delete(entry.id) }
        catch (e: Exception) { repository.images?.release("undo-${entry.id}"); throw e }
        saved["draft"] = null
        repository.images?.release("entry-draft"); collectImages()
        channel.send(UiNotice.Deleted(entry, historyVersion.value))
    }
    fun undoDelete(entry: Entry, expectedVersion: Int) = runWrite {
        try {
            if (expectedVersion != historyVersion.value) return@runWrite
            repository.save(entry)
            channel.send(UiNotice.Message("已恢复"))
        } finally { releaseUndo(entry.id) }
    }

    fun releaseUndo(id: String) { repository.images?.release("undo-$id"); collectImages() }

    fun export(uri: Uri) = runWrite { backup.export(uri); channel.send(UiNotice.Message("备份已导出")) }
    fun previewImport(uri: Uri) = runWrite {
        val archive = backup.preview(uri)
        restoringSnapshot.value = false
        backup.discard(pendingRestore.value)
        pendingRestore.value = archive
    }
    fun previewSnapshot() = runWrite {
        val archive = backup.previewSnapshot()
        restoringSnapshot.value = true
        backup.discard(pendingRestore.value)
        pendingRestore.value = archive
    }
    fun dismissRestore() { if (!busy.value) { backup.discard(pendingRestore.value); pendingRestore.value = null } }
    fun confirmRestore() = runWrite {
        val archive = pendingRestore.value ?: return@runWrite
        backup.restore(archive)
        pendingRestore.value = null
        // Old undo notices must not reintroduce entries from a replaced database.
        historyVersion.value += 1
        channel.send(UiNotice.Message("已恢复 ${archive.entries.size} 条记录、${archive.memos.size} 条便签"))
    }
    val historyVersion = MutableStateFlow(0)

    private fun runWrite(block: suspend () -> Unit) {
        if (busy.value || imageEditor.busy.value || memoEditor.images.busy.value) return
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

internal fun defaultCalendarKind(selected: LocalDate, today: LocalDate): EntryKind =
    if (selected.isBefore(today)) EntryKind.NOTE else EntryKind.EVENT
