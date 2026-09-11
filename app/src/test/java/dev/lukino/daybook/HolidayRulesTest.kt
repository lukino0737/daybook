package dev.lukino.daybook

import dev.lukino.daybook.calendar.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HolidayRulesTest {
    @Test fun officialSchedulesHaveExpectedTotalsAndBoundaries() {
        fun count(year: Int, mark: HolidayMark) = (0 until LocalDate.of(year, 1, 1).lengthOfYear())
            .count { HolidayRules.mark(LocalDate.of(year, 1, 1).plusDays(it.toLong())) == mark }
        assertEquals(28, count(2025, HolidayMark.OFF))
        assertEquals(5, count(2025, HolidayMark.WORK))
        assertEquals(33, count(2026, HolidayMark.OFF))
        assertEquals(6, count(2026, HolidayMark.WORK))
        listOf("2025-01-28", "2025-02-04", "2025-05-31", "2025-06-02", "2026-02-15", "2026-02-23").forEach {
            assertEquals(it, HolidayMark.OFF, HolidayRules.mark(LocalDate.parse(it)))
        }
        listOf("2025-01-27", "2025-02-05", "2025-06-03", "2025-12-31", "2026-02-24", "2027-01-01").forEach {
            assertNull(it, HolidayRules.mark(LocalDate.parse(it)))
        }
        assertEquals(HolidayMark.WORK, HolidayRules.mark(LocalDate.parse("2026-02-14")))
    }
    @Test fun festivalDayIsSeparateFromHolidayAndLeapMonthsDoNotRepeatFestivals() {
        assertEquals("清明节", HolidayRules.solarFestival(LocalDate.parse("2026-04-05")))
        assertNull(HolidayRules.solarFestival(LocalDate.parse("2026-04-04")))
        assertEquals("元旦", HolidayRules.solarFestival(LocalDate.parse("2027-01-01")))
        assertNull(HolidayRules.mark(LocalDate.parse("2027-01-01")))
        assertEquals("端午节", HolidayRules.lunarFestival(5, 5, false, false))
        assertNull(HolidayRules.lunarFestival(5, 5, true, false))
        assertEquals("除夕", HolidayRules.lunarFestival(12, 29, false, true))
        assertEquals("除夕", HolidayRules.lunarFestival(12, 30, false, true))
    }
}
