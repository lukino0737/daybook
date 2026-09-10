package dev.lukino.daybook.reminder

import dev.lukino.daybook.data.Entry
import dev.lukino.daybook.data.EntryKind
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object ReminderRules {
    fun pending(entry: Entry): Boolean = entry.kind != EntryKind.NOTE && !entry.completed &&
        entry.reminderAt != null && entry.reminderDeliveredFor != entry.reminderAt

    // Follow the local wall clock when the user changes time zones. DST gaps shift forward;
    // overlaps use the earlier offset, matching java.time's documented atZone behavior.
    fun instant(entry: Entry, zone: ZoneId): Instant = LocalDateTime.parse(entry.reminderAt).atZone(zone).toInstant()
    fun due(entry: Entry, now: Instant, zone: ZoneId): Boolean = pending(entry) &&
        instant(entry, zone) <= now && instant(entry, zone) > now.minus(Duration.ofHours(24))
    fun next(entries: List<Entry>, now: Instant, zone: ZoneId): Instant? = entries.asSequence()
        .filter(::pending).map { instant(it, zone) }.filter { it > now }.minOrNull()
}
