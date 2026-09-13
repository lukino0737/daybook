package dev.lukino.daybook

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.lukino.daybook.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import java.time.LocalDate
import java.time.YearMonth

class CalendarPagerTest {
    @get:Rule val compose = createComposeRule()
    @Test fun shortDragReturnsAndMonthChangesKeepAllDatesClickable() {
        var month by mutableStateOf(YearMonth.of(2026, 2))
        var selected by mutableStateOf(LocalDate.of(2026, 2, 1))
        compose.setContent { DaybookTheme { MonthCalendar(month, selected, LocalDate.of(2026, 2, 1), emptyList(), { month = it }, { selected = it }) } }
        compose.onNodeWithTag("month-calendar").performTouchInput { swipe(center, center - Offset(35f, 0f), 1000) }
        compose.runOnIdle { assertEquals(YearMonth.of(2026, 2), month) }
        compose.onNodeWithTag("month-calendar").performTouchInput { swipeLeft() }
        compose.onNodeWithText("2026年3月").assertIsDisplayed()
        compose.onNodeWithTag("day-2026-03-31").performClick()
        compose.runOnIdle { assertEquals(LocalDate.of(2026, 3, 31), selected); month = YearMonth.of(2026, 2) }
        compose.onNodeWithText("2026年2月").assertIsDisplayed()
        compose.onNodeWithTag("day-2026-02-28").assertIsDisplayed()
    }
}
