package dev.lukino.daybook

import dev.lukino.daybook.data.EntryKind
import dev.lukino.daybook.ui.defaultCalendarKind
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarDraftKindTest {
    @Test fun pastDatesAreRecordsIncludingMonthYearAndLeapBoundaries() {
        listOf("2026-09-15", "2026-01-01", "2028-03-01", "2026-10-01").forEach {
            val today = LocalDate.parse(it)
            assertEquals(EntryKind.NOTE, defaultCalendarKind(today.minusDays(1), today))
            assertEquals(EntryKind.EVENT, defaultCalendarKind(today, today))
            assertEquals(EntryKind.EVENT, defaultCalendarKind(today.plusDays(1), today))
        }
    }
    @Test fun sameSelectedDateBecomesPastAcrossMidnight() {
        val selected = LocalDate.of(2026, 12, 31)
        assertEquals(EntryKind.EVENT, defaultCalendarKind(selected, selected))
        assertEquals(EntryKind.NOTE, defaultCalendarKind(selected, selected.plusDays(1)))
    }
}
