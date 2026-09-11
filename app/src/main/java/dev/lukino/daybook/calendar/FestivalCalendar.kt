package dev.lukino.daybook.calendar

import android.icu.util.Calendar
import android.icu.util.ChineseCalendar
import android.icu.util.TimeZone
import java.time.LocalDate
import java.time.ZoneId

object FestivalCalendar {
    /** Interpret the supplied calendar date in China, independent of the phone's current zone. */
    fun forDate(date: LocalDate): CalendarNote {
        val lunar = ChineseCalendar(TimeZone.getTimeZone("Asia/Shanghai"))
        lunar.timeInMillis = date.atTime(12, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
        val month = lunar.get(Calendar.MONTH) + 1
        val day = lunar.get(Calendar.DAY_OF_MONTH)
        val leap = lunar.get(ChineseCalendar.IS_LEAP_MONTH) != 0
        lunar.add(Calendar.DAY_OF_MONTH, 1)
        val nextNewYear = lunar.get(Calendar.MONTH) == 0 && lunar.get(Calendar.DAY_OF_MONTH) == 1 && lunar.get(ChineseCalendar.IS_LEAP_MONTH) == 0
        return CalendarNote(listOfNotNull(HolidayRules.solarFestival(date),
            HolidayRules.lunarFestival(month, day, leap, nextNewYear)), HolidayRules.mark(date))
    }
}
