package dev.lukino.daybook.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.lukino.daybook.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun DaybookScreen(vm: DaybookViewModel) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val view by vm.view.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.failure.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { vm.refreshNow(); delay(60_000 - System.currentTimeMillis() % 60_000) }
        }
    }
    LaunchedEffect(vm) {
        vm.notices.collect { notice ->
            when (notice) {
                is UiNotice.Message -> snackbar.showSnackbar(notice.text)
                is UiNotice.Deleted -> if (snackbar.showSnackbar("已删除记录", "撤销", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.undoDelete(notice.entry)
            }
        }
    }
    val visible = when (view) {
        "undated" -> entries.filter { it.kind == EntryKind.TASK && it.date == null }.sortedBy { it.completed }
        "tasks" -> entries.filter { it.kind == EntryKind.TASK && !it.completed }.sortedWith(compareBy<Entry> { it.date ?: "9999-12-31" }.thenBy { it.time ?: "24:00" })
        else -> EntryRules.forDay(entries, LocalDate.parse(selected))
    }
    Scaffold(
        topBar = { TopAppBar(title = { Column { Text("Daybook", style = MaterialTheme.typography.headlineSmall); Text("把日子，记在一起。", style = MaterialTheme.typography.labelMedium) } },
            actions = { TextButton(onClick = vm::today) { Text("今天") } }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { vm.edit() }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("记一笔") }, modifier = Modifier.testTag("add")) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("calendar-list"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = view == "day", onClick = { vm.setView("day") }, label = { Text("日历") })
                FilterChip(selected = view == "undated", onClick = { vm.setView("undated") }, label = { Text("未安排 ${entries.count { it.kind == EntryKind.TASK && it.date == null && !it.completed }}") })
                FilterChip(selected = view == "tasks", onClick = { vm.setView("tasks") }, label = { Text("待完成") })
            } }
            item { Text(when (view) { "undated" -> "未安排"; "tasks" -> "待完成任务"; else -> selected }, style = MaterialTheme.typography.titleLarge) }
            if (visible.isEmpty()) item {
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.EditCalendar, null, tint = Green)
                    Text(if (view == "day") "这一天，留给你慢慢写。" else "这里暂时没有任务。", style = MaterialTheme.typography.titleMedium)
                    Text("安排、截止任务和生活片段，都可以记在这里。", style = MaterialTheme.typography.bodyMedium)
                } }
            }
            items(visible, key = { it.id }) { entry -> EntryCard(entry, now, busy, { vm.edit(entry) }, { vm.toggle(entry) }) }
        }
    }
    draft?.let { value -> EntryEditor(value, busy, error, vm::setDraft, vm::save, vm::dismissDraft,
        entries.firstOrNull { it.id == value.id }?.let { entry -> { vm.delete(entry) } }) }
    if (error != null && draft == null) AlertDialog(onDismissRequest = vm::clearError, title = { Text("操作未完成") },
        text = { Text(error!!) }, confirmButton = { TextButton(onClick = vm::clearError) { Text("知道了") } })
}

@Composable fun EntryCard(entry: Entry, now: LocalDateTime, busy: Boolean, onEdit: () -> Unit, onToggle: () -> Unit, showDate: Boolean = false) {
    val overdue = EntryRules.isOverdue(entry, now)
    Card(onClick = onEdit, enabled = !busy, colors = CardDefaults.cardColors(containerColor = if (overdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth().testTag("entry-${entry.id}")) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (entry.kind == EntryKind.TASK) Checkbox(checked = entry.completed, onCheckedChange = { onToggle() }, enabled = !busy)
            else Text(entry.kind.symbol, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.headlineSmall, color = Green)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (entry.completed) TextDecoration.LineThrough else null,
                    color = if (entry.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                Text(buildString {
                    append(entry.kind.label)
                    if (showDate) append(" · ${entry.date ?: "未设截止日期"}")
                    append(" · ${entry.time ?: if (entry.kind == EntryKind.TASK && entry.date == null) "未安排" else "时间未定"}")
                    if (overdue) append(" · 已逾期")
                    if (entry.completed) append(" · 已完成")
                }, style = MaterialTheme.typography.labelMedium, color = if (overdue) Clay else MaterialTheme.colorScheme.onSurfaceVariant)
                if (entry.note.isNotBlank()) Text(entry.note, maxLines = 3, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
