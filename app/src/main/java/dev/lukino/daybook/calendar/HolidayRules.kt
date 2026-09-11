package dev.lukino.daybook.calendar

import java.time.LocalDate
import java.time.MonthDay

enum class HolidayMark(val label: String) { OFF("放假"), WORK("调休上班") }

data class CalendarNote(val festivals: List<String>, val holiday: HolidayMark?) {
    val description: String get() = (festivals + listOfNotNull(holiday?.label)).joinToString(" · ")
}

/** Bundled official mainland China schedules. Unlisted years have no inferred work/off days. */
object HolidayRules {
    private val offDays: Set<LocalDate> = buildSet {
        fun range(start: String, end: String = start) {
            var day = LocalDate.parse(start)
            val last = LocalDate.parse(end)
            while (day <= last) { add(day); day = day.plusDays(1) }
        }
        range("2025-01-01")
        range("2025-01-28", "2025-02-04")
        range("2025-04-04", "2025-04-06")
        range("2025-05-01", "2025-05-05")
        range("2025-05-31", "2025-06-02")
        range("2025-10-01", "2025-10-08")
        range("2026-01-01", "2026-01-03")
        range("2026-02-15", "2026-02-23")
        range("2026-04-04", "2026-04-06")
        range("2026-05-01", "2026-05-05")
        range("2026-06-19", "2026-06-21")
        range("2026-09-25", "2026-09-27")
        range("2026-10-01", "2026-10-07")
    }
    private val workDays = listOf("2025-01-26", "2025-02-08", "2025-04-27", "2025-09-28", "2025-10-11",
        "2026-01-04", "2026-02-14", "2026-02-28", "2026-05-09", "2026-09-20", "2026-10-10").map(LocalDate::parse).toSet()

    fun mark(date: LocalDate): HolidayMark? = when (date) {
        in offDays -> HolidayMark.OFF
        in workDays -> HolidayMark.WORK
        else -> null
    }

    fun solarFestival(date: LocalDate): String? {
        // Qingming is a solar term, not a fixed Gregorian or lunar date. Only verified years are bundled.
        if (date == LocalDate.of(2025, 4, 4) || date == LocalDate.of(2026, 4, 5)) return "清明节"
        return when (MonthDay.from(date)) {
            MonthDay.of(1, 1) -> "元旦"
            MonthDay.of(2, 14) -> "情人节"
            MonthDay.of(3, 8) -> "妇女节"
            MonthDay.of(5, 1) -> "劳动节"
            MonthDay.of(5, 4) -> "青年节"
            MonthDay.of(6, 1) -> "儿童节"
            MonthDay.of(8, 1) -> "建军节"
            MonthDay.of(9, 10) -> "教师节"
            MonthDay.of(10, 1) -> "国庆节"
            else -> null
        }
    }

    fun lunarFestival(month: Int, day: Int, leapMonth: Boolean, nextDayIsNewYear: Boolean): String? {
        if (nextDayIsNewYear) return "除夕"
        if (leapMonth) return null
        return when (month to day) {
            1 to 1 -> "春节"
            1 to 15 -> "元宵节"
            2 to 2 -> "龙抬头"
            5 to 5 -> "端午节"
            7 to 7 -> "七夕"
            7 to 15 -> "中元节"
            8 to 15 -> "中秋节"
            9 to 9 -> "重阳节"
            12 to 8 -> "腊八节"
            else -> null
        }
    }
}
