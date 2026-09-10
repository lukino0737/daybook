package dev.lukino.daybook

import dev.lukino.daybook.ui.monthCells
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class MonthCalendarTest {
    @Test fun calendarStartsMondayAndKeepsCompleteWeeks() {
        for (year in 2024..2030) for (month in 1..12) {
            val ym = YearMonth.of(year, month)
            val cells = monthCells(ym)
            assertEquals(0, cells.size % 7)
            assertEquals(ym.lengthOfMonth(), cells.filterNotNull().size)
            assertEquals(ym.atDay(1).dayOfWeek.value - 1, cells.indexOf(ym.atDay(1)))
        }
    }
    @Test fun leapFebruaryIncludesTwentyNinth() {
        assertTrue(monthCells(YearMonth.of(2028, 2)).contains(LocalDate.of(2028, 2, 29)))
    }
}
