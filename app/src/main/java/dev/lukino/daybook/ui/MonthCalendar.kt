package dev.lukino.daybook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    val pager = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = monthPage(month), pageCount = { 9999 * 12 })
    val onMonthChanged by rememberUpdatedState(onMonth)
    val scope = rememberCoroutineScope()
    LaunchedEffect(month) {
        val target = monthPage(month)
        if (pager.settledPage != target) pager.requestScrollToPage(target)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page -> onMonthChanged(pageMonth(page)) }
    }
    androidx.compose.foundation.pager.HorizontalPager(state = pager,
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth().animateContentSize().testTag("month-calendar").semantics {
            customActions = listOf(
                CustomAccessibilityAction("上个月") { scope.launch { pager.animateScrollToPage((pager.settledPage - 1).coerceAtLeast(0)) }; true },
                CustomAccessibilityAction("下个月") { scope.launch { pager.animateScrollToPage((pager.settledPage + 1).coerceAtMost(pager.pageCount - 1)) }; true })
        }) { page ->
        MonthGrid(pageMonth(page), selected, today, entries, onSelect)
    }
}

private fun monthPage(month: YearMonth) = (month.year - 1) * 12 + month.monthValue - 1
private fun pageMonth(page: Int): YearMonth = YearMonth.of(page / 12 + 1, page % 12 + 1)

@Composable private fun MonthGrid(month: YearMonth, selected: LocalDate, today: LocalDate, entries: List<Entry>, onSelect: (LocalDate) -> Unit) {
    val groups = remember(entries) { entries.groupBy { it.date } }
    val notes = remember(month) { monthCells(month).filterNotNull().associateWith(FestivalCalendar::forDate) }
    Column(Modifier.fillMaxWidth()) {
        Text("${month.year}年${month.monthValue}月", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("month-title"))
        Row(Modifier.padding(vertical = 8.dp)) {
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
