package dev.lukino.daybook.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import java.time.YearMonth
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.MoreVert

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun DaybookScreen(vm: DaybookViewModel) {
    val entries by vm.entries.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val view by vm.view.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val tag by vm.tag.collectAsStateWithLifecycle()
    val reviewAll by vm.reviewAll.collectAsStateWithLifecycle()
    val knownTags = remember(entries) { entries.flatMap { it.tags }.distinct().sorted() }
    val draft by vm.draft.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.failure.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val pending by vm.pendingRestore.collectAsStateWithLifecycle()
    val restoringSnapshot by vm.restoringSnapshot.collectAsStateWithLifecycle()
    val historyVersion by vm.historyVersion.collectAsStateWithLifecycle()
    var reminderSettings by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::export) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::previewImport) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(historyVersion) { snackbar.currentSnackbarData?.dismiss() }
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
                is UiNotice.Deleted -> {
                    if (notice.historyVersion == vm.historyVersion.value &&
                        snackbar.showSnackbar("已删除记录", "撤销", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed)
                        vm.undoDelete(notice.entry, notice.historyVersion)
                }
            }
        }
    }
    val visible = when (view) {
        "review" -> ReviewRules.filter(entries, query, tag, reviewAll)
        "undated" -> entries.filter { it.kind == EntryKind.TASK && it.date == null }.sortedBy { it.completed }
        "tasks" -> entries.filter { it.kind == EntryKind.TASK && !it.completed }.sortedWith(compareBy<Entry> { it.date ?: "9999-12-31" }.thenBy { it.time ?: "24:00" })
        else -> EntryRules.forDay(entries, LocalDate.parse(selected))
    }
    Scaffold(
        topBar = { TopAppBar(title = { Column { Text("Daybook", style = MaterialTheme.typography.headlineSmall); Text("把日子，记在一起。", style = MaterialTheme.typography.labelMedium) } },
            actions = {
                TextButton(onClick = vm::today) { Text("今天") }
                Box {
                    IconButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.testTag("backup-menu")) { Icon(Icons.Outlined.MoreVert, "备份与恢复") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("提醒设置") }, onClick = { menu = false; reminderSettings = true })
                        DropdownMenuItem(text = { Text("导出备份") }, onClick = { menu = false; exportFile.launch("daybook-backup-${now.toLocalDate()}.json") })
                        DropdownMenuItem(text = { Text("从备份恢复") }, onClick = { menu = false; importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
                        DropdownMenuItem(text = { Text("恢复替换前快照") }, onClick = { menu = false; vm.previewSnapshot() })
                    }
                }
            }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { vm.edit() }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("记一笔") }, modifier = Modifier.testTag("add")) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("calendar-list"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (busy && draft == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = view == "day", onClick = { vm.setView("day") }, label = { Text("日历") })
                FilterChip(selected = view == "undated", onClick = { vm.setView("undated") }, label = { Text("未安排 ${entries.count { it.kind == EntryKind.TASK && it.date == null && !it.completed }}") })
                FilterChip(selected = view == "review", onClick = { vm.setView("review") }, label = { Text("回顾") })
                FilterChip(selected = view == "tasks", onClick = { vm.setView("tasks") }, label = { Text("待完成") })
            } }
            if (view == "review") item {
                ReviewFilters(query, tag, reviewAll, knownTags, vm::setQuery, vm::setTag, vm::setReviewAll)
            }
            if (entries.any { it.reminderAt != null && !it.completed }) item { ReminderStatus { reminderSettings = true } }
            if (view == "day") {
                item { MonthCalendar(YearMonth.parse(month), LocalDate.parse(selected), now.toLocalDate(), entries,
                    { vm.setMonth(it.toString()) }, vm::select) }
                val upcoming = EntryRules.upcoming(entries, now)
                if (upcoming.isNotEmpty()) {
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("近期截止", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Clay)
                        TextButton(onClick = { vm.setView("tasks") }) { Text("全部任务") }
                    } }
                    items(upcoming.take(3), key = { "upcoming-${it.id}" }) { entry -> EntryCard(entry, now, busy, { vm.edit(entry) }, { vm.toggle(entry) }, showDate = true, onTag = vm::openTag) }
                }
            }
            item { Text(when (view) { "undated" -> "未安排"; "tasks" -> "待完成任务"; "review" -> "回顾 · ${visible.size} 条"; else -> selected }, style = MaterialTheme.typography.titleLarge) }
            if (visible.isEmpty()) item {
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.EditCalendar, null, tint = Green)
                    Text(if (view == "day") "这一天，留给你慢慢写。" else if (view == "review") "没有找到匹配的记录。" else "这里暂时没有任务。", style = MaterialTheme.typography.titleMedium)
                    Text("安排、截止任务和生活片段，都可以记在这里。", style = MaterialTheme.typography.bodyMedium)
                } }
            }
            items(visible, key = { it.id }) { entry -> EntryCard(entry, now, busy, { vm.edit(entry) }, { vm.toggle(entry) }, showDate = view != "day", onTag = vm::openTag) }
        }
    }
    if (reminderSettings) AlertDialog(onDismissRequest = { reminderSettings = false },
        title = { Text("提醒设置") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { ReminderPermissions() } },
        confirmButton = { TextButton(onClick = { reminderSettings = false }) { Text("完成") } })
    draft?.let { value -> EntryEditor(value, busy, error, vm::setDraft, vm::save, vm::dismissDraft,
        entries.firstOrNull { it.id == value.id }?.let { entry -> { vm.delete(entry) } }, knownTags) }
    pending?.let { archive ->
        AlertDialog(onDismissRequest = vm::dismissRestore, modifier = Modifier.testTag("restore-dialog"),
            title = { Text(if (restoringSnapshot) "恢复替换前快照？" else "从备份恢复？") },
            text = { Text("备份包含 ${archive.entries.size} 条记录，将完整替换当前 ${entries.size} 条记录。" +
                (if (archive.entries.isEmpty()) "\n\n这是空备份，恢复后当前列表将被清空。" else "") +
                "\n\n替换前会保存本地快照。备份恢复不会合并记录。") },
            confirmButton = { TextButton(onClick = vm::confirmRestore, enabled = !busy) { Text(if (busy) "恢复中" else "确认替换") } },
            dismissButton = { TextButton(onClick = vm::dismissRestore, enabled = !busy) { Text("取消") } })
    }
    if (error != null && draft == null) AlertDialog(onDismissRequest = vm::clearError, title = { Text("操作未完成") },
        text = { Text(error!!) }, confirmButton = { TextButton(onClick = vm::clearError) { Text("知道了") } })
}

@Composable fun EntryCard(entry: Entry, now: LocalDateTime, busy: Boolean, onEdit: () -> Unit, onToggle: () -> Unit, showDate: Boolean = false, onTag: (String) -> Unit = {}) {
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
                if (entry.tags.isNotEmpty()) TagRow(entry.tags, enabled = !busy, onTag = onTag)
                entry.reminderAt?.let { Text("提醒 · ${it.replace('T', ' ')}" + if (entry.reminderDeliveredFor == it) " · 已发出" else "",
                    style = MaterialTheme.typography.bodySmall) }
                if (entry.note.isNotBlank()) Text(entry.note, maxLines = 3, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
