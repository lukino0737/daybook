package dev.lukino.daybook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lukino.daybook.data.Entry
import dev.lukino.daybook.data.EntryKind
import java.time.LocalDate
import java.time.YearMonth

fun monthCells(month: YearMonth): List<LocalDate?> {
    val before = month.atDay(1).dayOfWeek.value - 1
    val days = List<LocalDate?>(before) { null } + (1..month.lengthOfMonth()).map(month::atDay)
    return days + List((7 - days.size % 7) % 7) { null }
}

@Composable fun MonthCalendar(month: YearMonth, selected: LocalDate, today: LocalDate, entries: List<Entry>, onMonth: (YearMonth) -> Unit, onSelect: (LocalDate) -> Unit) {
    val groups = remember(entries) { entries.sortedWith(compareBy<Entry> {
        if (it.kind == EntryKind.TASK && !it.completed) 0 else 1
    }.thenBy { it.time ?: "24:00" }.thenBy { it.createdAt }).groupBy { it.date } }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${month.year} 年", style = MaterialTheme.typography.labelLarge, color = Green)
                Text("${month.monthValue} 月", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = { onMonth(month.minusMonths(1)) }, modifier = Modifier.testTag("previous-month")) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上个月") }
            IconButton(onClick = { onMonth(month.plusMonths(1)) }, modifier = Modifier.testTag("next-month")) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下个月") }
        }
        Row(Modifier.padding(vertical = 12.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text(day, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        monthCells(month).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                week.forEach { date ->
                    val items = groups[date?.toString()].orEmpty()
                    Column(Modifier.weight(1f).fillMaxHeight().heightIn(min = 90.dp)
                        .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        .background(if (date != null && date == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .then(if (date == null) Modifier else Modifier.clickable { onSelect(date) }.testTag("day-$date")
                            .semantics { contentDescription = "$date，${items.size} 条记录${if (date == today) "，今天" else ""}" })
                        .padding(horizontal = 3.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (date != null) {
                            Box(Modifier.size(26.dp).clip(CircleShape).background(if (date == today) Green else Color.Transparent), contentAlignment = Alignment.Center) {
                                Text(date.dayOfMonth.toString(), fontSize = 13.sp, color = if (date == today) Color.White else Ink, fontWeight = if (date == selected) FontWeight.Bold else FontWeight.Normal)
                            }
                            items.take(2).forEach { entry ->
                                Text("${entry.kind.symbol}${entry.title}", fontSize = 10.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (entry.kind == EntryKind.TASK && !entry.completed) Clay else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (items.size > 2) Text("+${items.size - 2} 条", fontSize = 10.sp, color = Green)
                        }
                    }
                }
            }
        }
    }
}
