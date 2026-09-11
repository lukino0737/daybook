package dev.lukino.daybook

import dev.lukino.daybook.data.*
import dev.lukino.daybook.ui.relativeDayLabel
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReviewRangeTest {
    @Test fun inclusiveRangeAndExplicitTypeExcludeUndatedEntries() {
        val rows = listOf(Entry(kind = EntryKind.TASK, title = "无日期"),
            Entry(title = "起点", date = "2024-02-29"), Entry(title = "终点", date = "2024-03-01"),
            Entry(kind = EntryKind.NOTE, title = "记录", date = "2024-03-01"), Entry(title = "范围外", date = "2024-03-02"))
        val selection = ReviewSelection(kind = EntryKind.EVENT, allTime = false, start = "2024-02-29", end = "2024-03-01")
        assertEquals(listOf("终点", "起点"), ReviewRules.filter(rows, selection).map { it.title })
        assertEquals(5, ReviewRules.filter(rows, ReviewSelection()).size)
    }
    @Test fun unrestrictedIgnoresInactiveRangeButNotSearch() {
        assertTrue(ReviewSelection(start = "2020-01-01", end = "2020-01-02").isUnrestricted())
        assertFalse(ReviewSelection(query = "关键词").isUnrestricted())
        assertFalse(ReviewSelection(kind = EntryKind.NOTE).isUnrestricted())
        assertThrows(IllegalArgumentException::class.java) { ReviewSelection(allTime = false).validate() }
        assertThrows(IllegalArgumentException::class.java) { ReviewSelection(allTime = false, start = "2026-09-12", end = "2026-09-11").validate() }
    }
    @Test fun relativeDayLabelsHandleLeapDayAndYearBoundary() {
        assertNull(relativeDayLabel(LocalDate.parse("2024-02-29"), LocalDate.parse("2024-02-29")))
        assertEquals("2天后", relativeDayLabel(LocalDate.parse("2024-03-01"), LocalDate.parse("2024-02-28")))
        assertEquals("1天前", relativeDayLabel(LocalDate.parse("2025-12-31"), LocalDate.parse("2026-01-01")))
    }
}
