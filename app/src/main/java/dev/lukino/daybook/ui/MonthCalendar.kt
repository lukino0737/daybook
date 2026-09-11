package dev.lukino.daybook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lukino.daybook.calendar.FestivalCalendar
import dev.lukino.daybook.calendar.HolidayMark
import androidx.compose.ui.text.style.TextOverflow
import dev.lukino.daybook.data.Entry
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

fun monthCells(month: YearMonth): List<LocalDate?> {
    val before = month.atDay(1).dayOfWeek.value - 1
    val days = List<LocalDate?>(before) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    return days + List((7 - days.size % 7) % 7) { null }
}

fun relativeDayLabel(selected: LocalDate, today: LocalDate): String? {
    val days = ChronoUnit.DAYS.between(today, selected)
    return when { days > 0 -> "${days}天后"; days < 0 -> "${-days}天前"; else -> null }
}

@Composable fun MonthCalendar(month: YearMonth, selected: LocalDate, today: LocalDate, entries: List<Entry>, onMonth: (YearMonth) -> Unit, onSelect: (LocalDate) -> Unit) {
    val groups = remember(entries) { entries.groupBy { it.date } }
    val notes = remember(month) { monthCells(month).filterNotNull().associateWith(FestivalCalendar::forDate) }
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    Column(Modifier.testTag("month-calendar").pointerInput(month, threshold) {
        var distance = 0f
        detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragCancel = { distance = 0f },
            onDragEnd = {
                if (distance > threshold) onMonth(month.minusMonths(1))
                else if (distance < -threshold) onMonth(month.plusMonths(1))
            }) { change, delta -> change.consume(); distance += delta }
    }.semantics {
        customActions = listOf(CustomAccessibilityAction("上个月") { onMonth(month.minusMonths(1)); true },
            CustomAccessibilityAction("下个月") { onMonth(month.plusMonths(1)); true })
    }) {
        Text("${month.year} 年", style = MaterialTheme.typography.labelLarge, color = Green)
        Text("${month.monthValue} 月", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Row(Modifier.padding(vertical = 12.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(day, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        monthCells(month).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                week.forEach { date ->
                    val items = groups[date?.toString()].orEmpty()
                    val note = notes[date]
                    Column(Modifier.weight(1f).fillMaxHeight().heightIn(min = 66.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (date != null && date == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .then(if (date == null) Modifier else Modifier.clickable { onSelect(date) }.testTag("day-$date")
                            .semantics { contentDescription = "$date，${items.size} 条记录${if (date == today) "，今天" else ""}${note?.description?.takeIf { it.isNotEmpty() }?.let { "，$it" }.orEmpty()}" })
                        .padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (date != null) {
                            Box(Modifier.size(30.dp).clip(CircleShape).background(if (date == today) Green else Color.Transparent), contentAlignment = Alignment.Center) {
                                Text(date.dayOfMonth.toString(), fontSize = 16.sp, color = if (date == today) Color.White else Ink, fontWeight = if (date == selected) FontWeight.Bold else FontWeight.Normal)
                            }
                            Text(note?.festivals.orEmpty().joinToString("/"),
                                fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.height(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (items.isNotEmpty()) Box(Modifier.size(4.dp).background(Green, CircleShape).testTag("marker-$date"))
                                note?.holiday?.let { mark -> Box(Modifier.size(4.dp)
                                    .background(if (mark == HolidayMark.OFF) Color(0xFF287CC1) else Color(0xFFCC4545), CircleShape)
                                    .testTag("holiday-${mark.name}-$date")) }
                            }
                        }
                    }
                }
            }
        }
    }
}
