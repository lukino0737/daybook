package dev.lukino.daybook

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.lukino.daybook.calendar.*
import dev.lukino.daybook.data.Entry
import dev.lukino.daybook.ui.*
import java.time.LocalDate
import java.time.YearMonth
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HolidayCalendarTest {
    @get:Rule val compose = createComposeRule()
    @Test fun lunarConversionMatchesPublishedCalendarRegardlessOfDeviceZone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            mapOf("2025-01-28" to "除夕", "2025-01-29" to "春节", "2025-05-31" to "端午节",
                "2025-10-06" to "中秋节", "2026-02-16" to "除夕", "2026-02-17" to "春节",
                "2026-03-03" to "元宵节", "2026-06-19" to "端午节", "2026-08-19" to "七夕",
                "2026-09-25" to "中秋节").forEach { (date, name) ->
                assertTrue("$date should be $name", name in FestivalCalendar.forDate(LocalDate.parse(date)).festivals)
            }
            assertFalse("春节" in FestivalCalendar.forDate(LocalDate.parse("2026-02-15")).festivals)
            assertTrue(FestivalCalendar.forDate(LocalDate.parse("2028-02-29")).festivals.isEmpty())
            // 2025 has a leap sixth month; ordinary leap-month days must not acquire a festival.
            assertTrue(FestivalCalendar.forDate(LocalDate.parse("2025-07-25")).festivals.isEmpty())
        } finally { TimeZone.setDefault(original) }
    }
    @Test fun holidayWorkAndPersonalMarkersCoexistAndHaveAccessibleDescriptions() {
        val selected = LocalDate.parse("2026-09-25")
        compose.setContent { DaybookTheme { MonthCalendar(YearMonth.of(2026, 9), selected, selected,
            listOf(Entry(title = "测试日程", date = selected.toString())), {}, {}) } }
        compose.onNodeWithTag("holiday-OFF-$selected", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("holiday-WORK-2026-09-20", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("marker-$selected", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("day-$selected").assertContentDescriptionEquals("$selected，1 条记录，今天，中秋节 · 放假")
        compose.onNodeWithTag("day-2026-09-20").assertContentDescriptionEquals("2026-09-20，0 条记录，调休上班")
        compose.onNodeWithText("中秋节").assertIsDisplayed()
    }
}
