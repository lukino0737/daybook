package dev.lukino.daybook

import dev.lukino.daybook.data.*
import java.time.LocalDateTime
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class EntryRulesTest {
    private fun task(date: String?, time: String? = null) = Entry(kind = EntryKind.TASK, title = "提交材料", date = date, time = time)

    @Test fun dateOnlyDeadlineLastsThroughWholeDay() {
        val e = task("2026-09-10")
        assertFalse(EntryRules.isOverdue(e, LocalDateTime.parse("2026-09-10T23:59:59")))
        assertTrue(EntryRules.isOverdue(e, LocalDateTime.parse("2026-09-11T00:00:00")))
    }
    @Test fun timedDeadlineUsesExactMinute() {
        val e = task("2026-09-10", "13:00")
        assertFalse(EntryRules.isOverdue(e, LocalDateTime.parse("2026-09-10T12:59:59")))
        assertTrue(EntryRules.isOverdue(e, LocalDateTime.parse("2026-09-10T13:00")))
    }
    @Test fun onlyUnfinishedDatedTasksCanBeOverdue() {
        val now = LocalDateTime.parse("2026-10-01T12:00")
        listOf(
            task(null), task("2026-09-10").copy(completed = true),
            Entry(title = "游戏更新", date = "2026-09-10"),
            Entry(kind = EntryKind.NOTE, title = "奶茶", date = "2026-09-10"),
        ).forEach { assertFalse(EntryRules.isOverdue(it, now)) }
    }
    @Test fun sevenDayWindowIncludesOverdueButNotDayEight() {
        val items = listOf(task("2025-12-31"), task("2026-01-01"), task("2026-01-07"), task("2026-01-08"), task(null))
        assertEquals(items.take(3), EntryRules.upcoming(items, LocalDateTime.parse("2026-01-01T00:00")))
    }
    @Test fun leapDayAndYearBoundary() {
        assertFalse(EntryRules.isOverdue(task("2028-02-29"), LocalDateTime.parse("2028-02-29T23:59")))
        assertTrue(EntryRules.isOverdue(task("2028-02-29"), LocalDateTime.parse("2028-03-01T00:00")))
        assertTrue(EntryRules.isOverdue(task("2026-12-31"), LocalDateTime.parse("2027-01-01T00:00")))
    }
    @Test fun completionKeepsEntryOnItsOriginalDay() {
        val e = task("2026-09-10").copy(completed = true)
        assertEquals(listOf(e), EntryRules.forDay(listOf(e), LocalDate.parse("2026-09-10")))
        assertTrue(EntryRules.upcoming(listOf(e), LocalDateTime.parse("2026-09-10T12:00")).isEmpty())
    }
    @Test fun rejectsInvalidCombinations() {
        listOf(Entry(title = ""), Entry(title = "课程"), task(null, "13:00"),
            Entry(title = "约会", date = "2026-09-10", completed = true), task("2026-02-30"),
            task("2026-09-10", "25:00")).forEach {
            assertThrows(Exception::class.java) { it.validate() }
        }
    }
}
