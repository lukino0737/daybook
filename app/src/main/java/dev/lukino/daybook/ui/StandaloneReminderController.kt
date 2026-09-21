package dev.lukino.daybook.ui

import androidx.lifecycle.SavedStateHandle
import dev.lukino.daybook.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import java.time.*

class StandaloneReminderController(private val repository: EntryRepository, private val saved: SavedStateHandle, private val scope: CoroutineScope) {
    private val raw = saved.getStateFlow<String?>("standalone-draft", null)
    val draft = raw.map { it?.let { Json.decodeFromString<StandaloneReminder>(it) } }
        .stateIn(scope, SharingStarted.Eagerly, raw.value?.let { Json.decodeFromString<StandaloneReminder>(it) })
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val isNew get() = saved.get<Boolean>("standalone-new") == true
    fun current() = raw.value?.let { Json.decodeFromString<StandaloneReminder>(it) }
    fun open(value: StandaloneReminder? = null, date: LocalDate = LocalDate.now(), title: String = "", note: String = "") {
        if (busy.value) return
        val now = LocalDateTime.now()
        val initial = date.atTime(9, 0).takeIf { it > now } ?: now.plusHours(1).withSecond(0).withNano(0)
        saved["standalone-original"] = value?.let { Json.encodeToString(it) }
        saved["standalone-new"] = value == null
        saved["standalone-calendar-manual"] = value != null
        change(value ?: StandaloneReminder(title = title, note = note, startDate = initial.toLocalDate().toString(), time = initial.toLocalTime().toString()))
    }
    fun change(value: StandaloneReminder) {
        if (busy.value) return
        saved["standalone-draft"] = Json.encodeToString(value)
        error.value = null
    }
    fun schedulingPreview(value: StandaloneReminder, now: Instant): StandaloneReminder {
        val old = saved.get<String>("standalone-original")?.let { Json.decodeFromString<StandaloneReminder>(it) }
        val changed = old == null || !old.sameSchedule(value)
        val resumed = old != null && !old.enabled && value.enabled
        return value.copy(effectiveFrom = if (changed || resumed) now.toEpochMilli() else old!!.effectiveFrom,
            deliveredFor = if (changed) null else old?.deliveredFor)
    }
    fun setRepeat(kind: RepeatKind) {
        val value = current() ?: return
        change(value.copy(repeat = kind,
            weekdays = if (kind == RepeatKind.WEEKLY) value.weekdays.takeIf { it != 0 } ?: (1 shl (LocalDate.parse(value.startDate).dayOfWeek.value - 1)) else 0,
            intervalDays = if (kind == RepeatKind.INTERVAL) value.intervalDays else 1,
            showInCalendar = if (saved.get<Boolean>("standalone-calendar-manual") == true) value.showInCalendar else kind == RepeatKind.YEARLY))
    }
    fun setCalendar(value: Boolean) {
        saved["standalone-calendar-manual"] = true
        current()?.let { change(it.copy(showInCalendar = value)) }
    }
    fun dismiss() { if (!busy.value) { saved["standalone-draft"] = null; error.value = null } }
    fun save() = write("保存失败，请重试。当前输入已保留。") {
        val value = current() ?: return@write
        repository.saveReminder(value, expectExisting = !isNew)
        saved["standalone-draft"] = null
    }
    fun delete() = write("删除失败，请重试。提醒仍保留。") {
        current()?.let { repository.deleteReminder(it.id) }
        saved["standalone-draft"] = null
    }
    private fun write(failureMessage: String, action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        scope.launch {
            try { action(); error.value = null }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { error.value = e.localizedMessage ?: failureMessage }
            catch (_: Exception) { error.value = failureMessage }
            finally { busy.value = false }
        }
    }
}
