package dev.lukino.daybook

import dev.lukino.daybook.data.*
import dev.lukino.daybook.reminder.ReminderRules
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderRulesTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2026-09-10T01:00:00Z")
    private val task = Entry(kind = EntryKind.TASK, title = "提交材料", reminderAt = "2026-09-10T09:00")
    @Test fun independentOfDeadlineAndOnlyPendingItemsFire() {
        assertTrue(ReminderRules.due(task, now, zone))
        assertFalse(ReminderRules.due(task.copy(completed = true), now, zone))
        assertFalse(ReminderRules.due(task.copy(reminderDeliveredFor = task.reminderAt), now, zone))
        assertFalse(ReminderRules.due(task.copy(kind = EntryKind.NOTE), now, zone))
        assertFalse(ReminderRules.due(task.copy(reminderAt = null), now, zone))
        task.validate()
    }
    @Test fun catchUpHasAnExclusive24HourBoundaryAndNeverFiresEarly() {
        assertFalse(ReminderRules.due(task, now.minusMillis(1), zone))
        assertTrue(ReminderRules.due(task, now.plusSeconds(86399), zone))
        assertFalse(ReminderRules.due(task, now.plusSeconds(86400), zone))
    }
    @Test fun nextAlarmExcludesCompletedDeliveredAndPast() {
        val future = task.copy(reminderAt = "2027-01-01T00:00")
        assertEquals(Instant.parse("2026-12-31T16:00:00Z"), ReminderRules.next(listOf(task, future), now, zone))
        assertNull(ReminderRules.next(listOf(task, future.copy(completed = true)), now, zone))
    }
    @Test fun localDatesSurviveLeapDaysAndDst() {
        val leap = task.copy(reminderAt = "2028-02-29T09:00")
        leap.validate()
        val newYork = ZoneId.of("America/New_York")
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), ReminderRules.instant(task.copy(reminderAt = "2026-03-08T02:30"), newYork))
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), ReminderRules.instant(task.copy(reminderAt = "2026-11-01T01:30"), newYork))
    }
    @Test fun invalidReminderStatesAreRejected() {
        listOf(task.copy(reminderAt = "2026-02-30T09:00"), task.copy(kind = EntryKind.NOTE, date = "2026-09-10"),
            task.copy(reminderAt = "2026-09-10T09:00:30"), task.copy(reminderDeliveredFor = "2026-09-09T09:00"))
            .forEach { assertThrows(Exception::class.java) { it.validate() } }
    }
}
