package dev.lukino.daybook.reminder

import dev.lukino.daybook.data.*
import java.time.*

enum class ReminderGroup(val label: String) { PENDING("待提醒"), PAUSED("已暂停"), FINISHED("已结束") }
data class ReminderListItem(val key: String, val id: String, val source: String, val title: String, val rule: String,
    val group: ReminderGroup, val status: String, val next: Instant?, val updatedAt: Long)

object ReminderListRules {
    fun items(entries: List<Entry>, memos: List<Memo>, reminders: List<StandaloneReminder>, now: Instant, zone: ZoneId): List<ReminderListItem> {
        val attached = entries.filter { it.reminderAt != null }.map { attached(ReminderTarget.from(it), it.id, it.kind.label, now, zone) } +
            memos.filter { it.reminderAt != null }.map { attached(ReminderTarget.from(it), it.id, "便签", now, zone) }
        val independent = reminders.map { r ->
            val due = RepeatRules.due(r, now, zone)
            val next = due ?: RepeatRules.next(r, now, zone)
            val group = if (!r.enabled) ReminderGroup.PAUSED else if (next == null) ReminderGroup.FINISHED else ReminderGroup.PENDING
            val status = when {
                !r.enabled -> "已暂停"
                due != null -> "等待补发 · $due"
                next != null -> "下一次 · $next"
                r.deliveredFor != null -> "已发出 · ${r.deliveredFor}"
                else -> "已错过或无后续日期"
            }
            ReminderListItem("reminder:${r.id}", r.id, "独立提醒", r.title, RepeatRules.summary(r), group, status.replace('T', ' '), next?.atZone(zone)?.toInstant(), r.updatedAt)
        }
        return (attached + independent).sortedWith(compareBy<ReminderListItem> { it.group.ordinal }
            .thenBy { if (it.group == ReminderGroup.PENDING) it.next else null }
            .thenByDescending { if (it.group != ReminderGroup.PENDING) it.updatedAt else 0 }.thenBy { it.key })
    }
    private fun attached(t: ReminderTarget, id: String, source: String, now: Instant, zone: ZoneId): ReminderListItem {
        val at = t.instant(zone)
        val ended = !t.enabled || !t.pending || at <= now.minusSeconds(86400)
        val status = when {
            !t.enabled -> "任务已完成"
            !t.pending -> "已发出 · ${t.at}"
            ended -> "已错过 · ${t.at}"
            at <= now -> "等待补发 · ${t.at}"
            else -> "下一次 · ${t.at}"
        }
        return ReminderListItem(t.key, id, source, t.title, "单次", if (ended) ReminderGroup.FINISHED else ReminderGroup.PENDING,
            status.replace('T', ' '), at.takeUnless { ended }, t.updatedAt)
    }
}
