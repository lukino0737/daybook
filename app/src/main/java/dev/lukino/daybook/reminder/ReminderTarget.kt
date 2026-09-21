package dev.lukino.daybook.reminder

import dev.lukino.daybook.data.Entry
import dev.lukino.daybook.data.EntryKind
import dev.lukino.daybook.data.Memo
import dev.lukino.daybook.data.StandaloneReminder
import java.time.LocalDateTime
import java.time.ZoneId

/** Shared scheduling projection only. Source tables and notification destinations stay independent. */
data class ReminderTarget(val key: String, val uri: String, val title: String, val description: String,
    val at: String?, val delivered: String?, val updatedAt: Long, val enabled: Boolean, val revision: Long? = null) {
    val notificationTag get() = if (revision == null) key else "$key:$revision:$at"
    fun instant(zone: ZoneId) = LocalDateTime.parse(at).atZone(zone).toInstant()
    val pending get() = enabled && at != null && at != delivered
    companion object {
        fun from(reminder: StandaloneReminder, at: LocalDateTime?) = ReminderTarget("reminder:${reminder.id}",
            "daybook://reminder/${reminder.id}", reminder.title, "独立提醒 · ${RepeatRules.summary(reminder)}",
            at?.toString(), reminder.deliveredFor, reminder.updatedAt, reminder.enabled, reminder.revision)
        fun from(entry: Entry) = ReminderTarget(entry.id, "daybook://entry/${entry.id}", entry.title,
            "${entry.kind.label} · ${entry.date ?: "未设截止日期"}${entry.time?.let { " $it" }.orEmpty()}",
            entry.reminderAt, entry.reminderDeliveredFor, entry.updatedAt, entry.kind != EntryKind.NOTE && !entry.completed)
        fun from(memo: Memo) = ReminderTarget("memo:${memo.id}", "daybook://memo/${memo.id}", memo.summary,
            "便签${memo.date?.let { " · $it" }.orEmpty()}", memo.reminderAt, memo.reminderDeliveredFor, memo.updatedAt, true)
    }
}
