package dev.lukino.daybook

import dev.lukino.daybook.data.*
import dev.lukino.daybook.reminder.RepeatRules
import dev.lukino.daybook.backup.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class RepeatRulesTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(s: String) = LocalDateTime.parse(s).atZone(zone).toInstant()
    private val base = StandaloneReminder(title = "提醒", startDate = "2026-01-31", time = "09:00", effectiveFrom = 0)
    @Test fun missingMonthDatesDoNotDrift() {
        val r = base.copy(repeat = RepeatKind.MONTHLY)
        assertEquals("2026-03-31T09:00", RepeatRules.next(r, at("2026-02-01T00:00"), zone).toString())
        assertEquals("2026-01-31T09:00", RepeatRules.latest(r, at("2026-03-01T00:00"), zone).toString())
        assertTrue(RepeatRules.dates(r, LocalDate.parse("2026-02-01"), LocalDate.parse("2026-02-28")).isEmpty())
    }
    @Test fun leapYearsIncludeNonLeapCenturiesAndCalendarIgnoresPause() {
        val r = base.copy(startDate = "2096-02-29", repeat = RepeatKind.YEARLY)
        assertEquals("2104-02-29T09:00", RepeatRules.next(r, at("2096-03-01T00:00"), zone).toString())
        assertEquals(listOf(LocalDate.parse("2104-02-29")), RepeatRules.dates(r.copy(enabled = false), LocalDate.parse("2104-02-01"), LocalDate.parse("2104-02-29")))
    }
    @Test fun weeklyAndIntervalKeepAnchorAcrossYears() {
        val weekly = base.copy(startDate = "2026-09-21", repeat = RepeatKind.WEEKLY, weekdays = 1 or 4 or 16)
        assertEquals("2026-09-23T09:00", RepeatRules.next(weekly, at("2026-09-21T09:00"), zone).toString())
        val interval = base.copy(startDate = "2025-12-30", repeat = RepeatKind.INTERVAL, intervalDays = 3)
        assertEquals("2026-01-02T09:00", RepeatRules.next(interval, at("2025-12-31T09:00"), zone).toString())
        assertEquals("2025-12-30T09:00", RepeatRules.latest(interval, at("2026-01-02T08:59"), zone).toString())
    }
    @Test fun catchupIsLatestOnlyAndBoundaryIsExclusive() {
        val r = base.copy(repeat = RepeatKind.INTERVAL, intervalDays = 3)
        assertEquals("2026-02-03T09:00", RepeatRules.due(r, at("2026-02-04T08:59"), zone).toString())
        assertNull(RepeatRules.due(r, at("2026-02-04T09:00"), zone))
        val daily = base.copy(repeat = RepeatKind.DAILY)
        assertEquals("2026-02-05T09:00", RepeatRules.due(daily, at("2026-02-05T11:00"), zone).toString())
        assertNull(RepeatRules.due(daily.copy(deliveredFor = "2026-02-05T09:00"), at("2026-02-05T11:00"), zone))
        assertNull(RepeatRules.due(daily.copy(enabled = false), at("2026-02-05T11:00"), zone))
        assertNull(RepeatRules.due(daily.copy(effectiveFrom = at("2026-02-05T10:00").toEpochMilli()), at("2026-02-05T11:00"), zone))
    }
    @Test fun timezoneDstAndLongIntervalsUseLocalCalendar() {
        val ny = ZoneId.of("America/New_York")
        val r = base.copy(startDate = "2026-03-07", time = "02:30", repeat = RepeatKind.DAILY)
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), RepeatRules.next(r, Instant.parse("2026-03-08T05:00:00Z"), ny)!!.atZone(ny).toInstant())
        assertEquals("2026-03-08T02:30", RepeatRules.due(r, Instant.parse("2026-03-08T07:30:00Z"), ny).toString())
        val max = base.copy(repeat = RepeatKind.INTERVAL, intervalDays = 9999)
        assertEquals(LocalDate.parse(base.startDate).plusDays(9999).atTime(9, 0), RepeatRules.next(max, at("2026-01-31T09:00"), zone))
        assertNull(RepeatRules.next(base.copy(startDate = "9999-12-31", repeat = RepeatKind.DAILY), at("9999-12-31T10:00"), zone))
    }
    @Test fun backupV5RequiresAllFieldsAndReadsOldV4() {
        val r = base.copy(repeat = RepeatKind.DAILY, deliveredFor = "2026-02-01T09:00")
        val encoded = BackupCodec.encode(emptyList(), reminders = listOf(r))
        assertEquals(listOf(r), BackupCodec.decode(encoded).reminders)
        assertTrue(BackupCodec.decode("""{"formatVersion":4,"exportedAt":0,"entries":[],"memos":[]}""").reminders.isEmpty())
        listOf("reminders", "effectiveFrom", "revision", "deliveredFor").forEach { field ->
            assertThrows(Exception::class.java) { BackupCodec.decode(encoded.replace("\"$field\"", "\"missing\"")) }
        }
        assertThrows(Exception::class.java) { BackupCodec.encode(emptyList(), reminders = listOf(r, r)) }
        assertThrows(Exception::class.java) { base.copy(repeat = RepeatKind.WEEKLY).validate() }
        assertThrows(Exception::class.java) { base.copy(repeat = RepeatKind.INTERVAL, intervalDays = 0).validate() }
        assertThrows(Exception::class.java) { base.copy(deliveredFor = "2026-02-01T09:00").validate() }
    }
}
