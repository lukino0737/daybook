package dev.lukino.daybook.reminder

import dev.lukino.daybook.data.RepeatKind
import dev.lukino.daybook.data.StandaloneReminder
import java.time.*
import java.time.temporal.ChronoUnit

/** Pure calendar arithmetic. Never derives a new anchor from a previous delivery. */
object RepeatRules {
    fun occursOn(r: StandaloneReminder, date: LocalDate): Boolean {
        val start = LocalDate.parse(r.startDate)
        if (date < start || date.year !in 1..9999) return false
        return when (r.repeat) {
            RepeatKind.ONCE -> date == start
            RepeatKind.DAILY -> true
            RepeatKind.WEEKLY -> r.weekdays and (1 shl (date.dayOfWeek.value - 1)) != 0
            RepeatKind.MONTHLY -> date.dayOfMonth == start.dayOfMonth
            RepeatKind.YEARLY -> date.month == start.month && date.dayOfMonth == start.dayOfMonth
            RepeatKind.INTERVAL -> ChronoUnit.DAYS.between(start, date) % r.intervalDays == 0L
        }
    }

    fun dates(r: StandaloneReminder, from: LocalDate, through: LocalDate): List<LocalDate> =
        if (through < from) emptyList() else generateSequence(from) { if (it < through) it.plusDays(1) else null }
            .filter { occursOn(r, it) }.toList()

    private fun dateAt(r: StandaloneReminder, from: LocalDate, forward: Boolean): LocalDate? {
        val anchor = LocalDate.parse(r.startDate)
        var date = if (forward) maxOf(from, anchor) else from
        if (date < anchor || date.year !in 1..9999) return null
        if (r.repeat == RepeatKind.ONCE) return anchor.takeIf { if (forward) it >= date else it <= date }
        if (r.repeat == RepeatKind.INTERVAL) {
            val remainder = ChronoUnit.DAYS.between(anchor, date) % r.intervalDays
            date = if (forward && remainder != 0L) date.plusDays(r.intervalDays - remainder) else date.minusDays(remainder)
            return date.takeIf { it >= anchor && it.year in 1..9999 }
        }
        // At most one Gregorian leap-year gap (including non-leap century years).
        repeat(2923) {
            if (occursOn(r, date)) return date
            if ((!forward && date <= anchor) || (forward && date == LocalDate.of(9999, 12, 31))) return null
            date = date.plusDays(if (forward) 1 else -1)
        }
        return null
    }

    fun next(r: StandaloneReminder, after: Instant, zone: ZoneId): LocalDateTime? {
        if (!r.enabled) return null
        val boundary = maxOf(after, Instant.ofEpochMilli(r.effectiveFrom))
        var date = boundary.atZone(zone).toLocalDate().minusDays(1)
        repeat(4) {
            val found = dateAt(r, date, true) ?: return null
            val at = found.atTime(LocalTime.parse(r.time))
            if (at.atZone(zone).toInstant() > boundary && (r.deliveredFor == null || at > LocalDateTime.parse(r.deliveredFor))) return at
            if (found == LocalDate.of(9999, 12, 31)) return null
            date = maxOf(found.plusDays(1), r.deliveredFor?.let { LocalDateTime.parse(it).toLocalDate() } ?: found)
        }
        return null
    }

    fun latest(r: StandaloneReminder, now: Instant, zone: ZoneId): LocalDateTime? {
        var date = now.atZone(zone).toLocalDate()
        repeat(3) {
            val found = dateAt(r, date, false) ?: return null
            val at = found.atTime(LocalTime.parse(r.time))
            if (at.atZone(zone).toInstant() <= now) return at
            date = found.minusDays(1)
        }
        return null
    }

    fun due(r: StandaloneReminder, now: Instant, zone: ZoneId): LocalDateTime? {
        if (!r.enabled) return null
        val at = latest(r, now, zone) ?: return null
        val instant = at.atZone(zone).toInstant()
        return at.takeIf { instant > Instant.ofEpochMilli(r.effectiveFrom) && instant > now.minusSeconds(86400) &&
            (r.deliveredFor == null || at > LocalDateTime.parse(r.deliveredFor)) }
    }

    fun summary(r: StandaloneReminder): String = when (r.repeat) {
        RepeatKind.WEEKLY -> "每周" + (1..7).filter { r.weekdays and (1 shl (it - 1)) != 0 }.joinToString("、") { listOf("一", "二", "三", "四", "五", "六", "日")[it - 1] }
        RepeatKind.INTERVAL -> "每隔 ${r.intervalDays} 天"
        RepeatKind.MONTHLY -> "每月 ${LocalDate.parse(r.startDate).dayOfMonth} 日（缺失日期跳过）"
        RepeatKind.YEARLY -> LocalDate.parse(r.startDate).let { "每年 ${it.monthValue} 月 ${it.dayOfMonth} 日（公历，缺失日期跳过）" }
        else -> r.repeat.label
    }
}
