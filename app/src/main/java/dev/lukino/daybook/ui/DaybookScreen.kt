package dev.lukino.daybook.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.lukino.daybook.data.*
import dev.lukino.daybook.reminder.*
import java.time.ZoneId
import dev.lukino.daybook.calendar.FestivalCalendar
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
    val reminderListVisible by vm.reminderListVisible.collectAsStateWithLifecycle()
    val reminderListMessage by vm.reminderListMessage.collectAsStateWithLifecycle()
    val standaloneDraft by vm.standaloneEditor.draft.collectAsStateWithLifecycle()
    val pendingNotification by vm.pendingNotification.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val completing by vm.completing.collectAsStateWithLifecycle()
    var completedExpanded by rememberSaveable { mutableStateOf(false) }
    var openedSwipe by remember { mutableStateOf<String?>(null) }
    var deleteEntry by remember { mutableStateOf<Entry?>(null) }
    var deleteMemo by remember { mutableStateOf<Memo?>(null) }
    val memos by vm.memoEditor.items.collectAsStateWithLifecycle()
    val memoDraft by vm.memoEditor.draft.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    val view by vm.view.collectAsStateWithLifecycle()
    val selection by vm.reviewSelection.collectAsStateWithLifecycle()
    val applied by vm.appliedReview.collectAsStateWithLifecycle()
    val confirmAll by vm.confirmAllReview.collectAsStateWithLifecycle()
    val reviewError by vm.reviewError.collectAsStateWithLifecycle()
    val knownTags = remember(entries) { entries.flatMap { it.tags }.distinct().sorted() }
    val draft by vm.draft.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.failure.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val pending by vm.pendingRestore.collectAsStateWithLifecycle()
    val restoringSnapshot by vm.restoringSnapshot.collectAsStateWithLifecycle()
    val historyVersion by vm.historyVersion.collectAsStateWithLifecycle()
    var appearanceSettings by rememberSaveable { mutableStateOf(false) }
    var reminderSettings by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(vm::export) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::previewImport) }
    LaunchedEffect(draft, memoDraft, standaloneDraft, pendingNotification) {
        if (draft == null && memoDraft == null && standaloneDraft == null) vm.consumeNotification()
    }
    val dayReminders = remember(reminders, selected) { reminders.filter { it.showInCalendar && RepeatRules.occursOn(it, LocalDate.parse(selected)) }.sortedWith(compareBy<StandaloneReminder> { it.time }.thenBy { it.createdAt }.thenBy { it.id }) }
    val reminderRows = remember(entries, memos, reminders, now) { ReminderListRules.items(entries, memos, reminders, now.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()) }
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val listDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(listDragged) { if (listDragged) openedSwipe = null }
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
                    var undoRequested = false
                    try {
                        if (notice.historyVersion == vm.historyVersion.value &&
                            snackbar.showSnackbar("已删除记录", "撤销", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                            undoRequested = true
                            vm.undoDelete(notice.entry, notice.historyVersion)
                        }
                    } finally { if (!undoRequested) vm.releaseUndo(notice.entry.id) }
                }
            }
        }
    }
    LaunchedEffect(view, selected, historyVersion) { openedSwipe = null; deleteEntry = null; deleteMemo = null }
    val completedTasks = entries.filter { it.kind == EntryKind.TASK && it.completed && it.id !in completing }
        .sortedWith(compareByDescending<Entry> { it.updatedAt }.thenBy { it.id })
    val selectedNote = remember(selected) { FestivalCalendar.forDate(LocalDate.parse(selected)) }
    val visible = when (view) {
        "memos" -> emptyList()
        "review" -> applied?.let { ReviewRules.filter(entries, it) }.orEmpty()
        "tasks" -> entries.filter { it.kind == EntryKind.TASK && (!it.completed || it.id in completing) }.sortedWith(compareBy<Entry> { it.date ?: "9999-12-31" }.thenBy { it.time ?: "24:00" })
        else -> EntryRules.forDay(entries, LocalDate.parse(selected))
    }
    Box(Modifier.fillMaxSize()) {
    AppearanceBackground(LocalAppearance.current)
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = { TopAppBar(title = { Column { Text("Daybook", style = MaterialTheme.typography.headlineSmall); Text("把日子，记在一起。", style = MaterialTheme.typography.labelMedium) } },
            actions = {
                if (view == "day" && (selected != now.toLocalDate().toString() || month != YearMonth.from(now).toString())) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TextButton(onClick = vm::today, modifier = Modifier.testTag("today")) { Text("今天") }
                        relativeDayLabel(LocalDate.parse(selected), now.toLocalDate())?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.testTag("backup-menu")) { Icon(Icons.Outlined.MoreVert, "设置与备份") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("外观设置") }, onClick = { menu = false; appearanceSettings = true })
                        DropdownMenuItem(text = { Text("提醒列表") }, onClick = { menu = false; vm.showReminderList() }, modifier = Modifier.testTag("reminder-list-menu"))
                        DropdownMenuItem(text = { Text("提醒设置") }, onClick = { menu = false; reminderSettings = true })
                        DropdownMenuItem(text = { Text("导出备份") }, onClick = { menu = false; exportFile.launch("daybook-backup-${now.toLocalDate()}.zip") })
                        DropdownMenuItem(text = { Text("从备份恢复") }, onClick = { menu = false; importFile.launch(arrayOf("application/zip", "application/json", "text/plain", "application/octet-stream")) })
                        DropdownMenuItem(text = { Text("恢复替换前快照") }, onClick = { menu = false; vm.previewSnapshot() })
                    }
                }
            }) },
        bottomBar = { NavigationBar(Modifier.testTag("bottom-navigation"), containerColor = MaterialTheme.colorScheme.surface) {
            listOf(Triple("day", "日历", Icons.Outlined.CalendarMonth),
                Triple("tasks", "任务", Icons.Outlined.Checklist), Triple("memos", "便签", Icons.Outlined.StickyNote2), Triple("review", "回顾", Icons.Outlined.History)).forEach { (key, label, icon) ->
                NavigationBarItem(selected = view == key, onClick = { vm.setView(key) }, modifier = Modifier.testTag("nav-$key"),
                    icon = { Icon(icon, null) }, label = { Text(if (key == "tasks") "$label ${entries.count { it.kind == EntryKind.TASK && !it.completed }}" else label) })
            }
        } },
        floatingActionButton = { FloatingActionButton(onClick = { if (view == "memos") vm.memoEditor.open() else vm.edit() }, modifier = Modifier.testTag("add")) { Icon(Icons.Outlined.Add, "记一笔") } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("calendar-list"), state = listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (busy && draft == null && completing.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (view == "review") item {
                ReviewFilters(selection, knownTags, reviewError, vm::setReview, { vm.applyReview() }, vm::resetReview)
            }
            if (view == "day") {
                item { MonthCalendar(YearMonth.parse(month), LocalDate.parse(selected), now.toLocalDate(), entries,
                    { vm.setMonth(it.toString()) }, vm::select, reminders) }
                val upcoming = EntryRules.upcoming(entries, now)
                if (upcoming.isNotEmpty()) {
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("近期截止", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Clay)
                        TextButton(onClick = { vm.setView("tasks") }) { Text("全部任务") }
                    } }
                    items(upcoming.take(3), key = { "upcoming-${it.id}" }) { entry -> EntryCard(entry, now, busy, { openedSwipe = null; vm.edit(entry) }, { vm.toggle(entry) }, showDate = true, onTag = vm::openTag) }
                }
            }
            if (view != "memos" && (view != "review" || applied != null)) item {
                Text(when (view) { "tasks" -> "任务"; "review" -> "回顾 · ${visible.size} 条"; else -> selected }, style = MaterialTheme.typography.titleLarge)
                if (view == "day" && selectedNote.description.isNotEmpty()) Text(selectedNote.description, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (view != "memos" && visible.isEmpty() && (view != "day" || dayReminders.isEmpty()) && (view != "tasks" || completedTasks.isEmpty()) && (view != "review" || applied != null)) item {
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.EditCalendar, null, tint = MaterialTheme.colorScheme.primary)
                    Text(if (view == "day") "这一天，留给你慢慢写。" else if (view == "review") "没有找到匹配的记录。" else "这里暂时没有任务。", style = MaterialTheme.typography.titleMedium)
                    Text(when (view) {
                        "tasks" -> "任务按截止日期分组，完成后可在下方查看或恢复。"
                        "review" -> "试试清除搜索或标签，或切换到全部类型。"
                        else -> "日程、截止任务和生活片段，都可以记在这里。"
                    }, style = MaterialTheme.typography.bodyMedium)
                } }
            }
            if (view == "memos") {
                item { Text("便签", style = MaterialTheme.typography.titleLarge) }
                if (memos.isEmpty()) item { Text("想到什么，就记下来。", style = MaterialTheme.typography.bodyLarge) }
                items(memos, key = { "memo-${it.id}" }) { memo ->
                    SwipeDeleteRow("memo-${memo.id}", openedSwipe, !busy, { openedSwipe = it }, { deleteMemo = memo }, Modifier.animateItem()) {
                        Card(onClick = { openedSwipe = null; vm.memoEditor.open(memo) }, modifier = Modifier.fillMaxWidth().testTag("memo-${memo.id}")) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(memo.summary, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                                if (memo.body.contains('\n')) Text(memo.body.substringAfter('\n'), maxLines = 3, style = MaterialTheme.typography.bodyMedium)
                                memo.date?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                                memo.reminderAt?.let { Text("提醒 · ${it.replace('T', ' ')}" + if (it == memo.reminderDeliveredFor) " · 已发出" else "", style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                }
            } else if (view == "tasks") {
                listOf(true, false).forEach { dated ->
                    val group = visible.filter { (it.date != null) == dated }
                    if (group.isNotEmpty()) {
                        item { Text(if (dated) "有截止日期" else "无截止日期", style = MaterialTheme.typography.titleSmall) }
                        items(group, key = { it.id }) { entry ->
                            SwipeDeleteRow(entry.id, openedSwipe, !busy, { openedSwipe = it }, { deleteEntry = entry }, Modifier.animateItem()) {
                                EntryCard(entry, now, busy, { openedSwipe = null; vm.edit(entry) }, { vm.toggle(entry) }, showDate = true, onTag = vm::openTag)
                            }
                        }
                    }
                }
                item(key = "completed-heading") {
                    TextButton(onClick = { completedExpanded = !completedExpanded }, modifier = Modifier.testTag("completed-heading")) {
                        Text("${if (completedExpanded) "▾" else "▸"} 已完成 · ${completedTasks.size}")
                    }
                }
                if (completedExpanded) {
                    if (completedTasks.isEmpty()) item { Text("还没有已完成任务", style = MaterialTheme.typography.bodyMedium) }
                    items(completedTasks, key = { it.id }) { entry ->
                        SwipeDeleteRow(entry.id, openedSwipe, !busy, { openedSwipe = it }, { deleteEntry = entry }, Modifier.animateItem()) {
                            EntryCard(entry, now, busy, { openedSwipe = null; vm.edit(entry) }, { vm.toggle(entry) }, showDate = true, onTag = vm::openTag)
                        }
                    }
                }
            } else items(visible, key = { it.id }) { entry ->
                if (view == "day") SwipeDeleteRow(entry.id, openedSwipe, !busy, { openedSwipe = it }, { deleteEntry = entry }, Modifier.animateItem()) {
                    EntryCard(entry, now, busy, { openedSwipe = null; vm.edit(entry) }, { vm.toggle(entry) }, onTag = vm::openTag)
                } else EntryCard(entry, now, busy, { openedSwipe = null; vm.edit(entry) }, { vm.toggle(entry) }, showDate = true, onTag = vm::openTag)
            }
            if (view == "day" && dayReminders.isNotEmpty()) {
                item { Text("提醒", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("day-reminders-heading")) }
                items(dayReminders, key = { "day-reminder-${it.id}" }) { reminder ->
                    Card(onClick = { vm.openReminder(reminder.id) }, modifier = Modifier.fillMaxWidth().testTag("day-reminder-${reminder.id}")) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(reminder.title, style = MaterialTheme.typography.titleMedium)
                            Text("${reminder.time} · ${RepeatRules.summary(reminder)}${if (!reminder.enabled) " · 已暂停" else ""}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    }
    if (deleteEntry != null || deleteMemo != null) AlertDialog(
        onDismissRequest = { deleteEntry = null; deleteMemo = null },
        title = { Text(if (deleteMemo != null) "删除这条便签？" else "删除这条${deleteEntry?.kind?.label.orEmpty()}？") },
        text = { Text(deleteMemo?.summary ?: deleteEntry?.title.orEmpty(), maxLines = 3) },
        confirmButton = { TextButton(enabled = !busy, modifier = Modifier.testTag("confirm-row-delete"), onClick = {
            deleteEntry?.let(vm::delete); deleteMemo?.let(vm::deleteMemo)
            deleteEntry = null; deleteMemo = null; openedSwipe = null
        }) { Text("确认删除") } },
        dismissButton = { TextButton(onClick = { deleteEntry = null; deleteMemo = null }) { Text("取消") } })
    if (appearanceSettings) AppearanceSettingsScreen { appearanceSettings = false }
    if (reminderListVisible) ReminderListScreen(reminderRows, reminderListMessage, vm::hideReminderList, vm::newReminder, vm::openReminderRow)
    memoDraft?.let { MemoEditor(it, vm.memoEditor) }
    if (confirmAll) AlertDialog(onDismissRequest = vm::dismissReviewConfirmation,
        modifier = Modifier.testTag("review-confirm-all"), title = { Text("确定要回顾所有内容吗？") },
        confirmButton = { TextButton(onClick = { vm.applyReview(true) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = vm::dismissReviewConfirmation) { Text("取消") } })
    if (reminderSettings) ReminderSettingsScreen { reminderSettings = false }
    draft?.let { value -> EntryEditor(value, busy, error, vm::setDraft, vm::save, vm::dismissDraft,
        entries.firstOrNull { it.id == value.id }?.let { entry -> { vm.delete(entry) } }, knownTags, vm::reminderFromEntry, vm.imageEditor) }
    standaloneDraft?.let { StandaloneReminderEditor(it, vm.standaloneEditor, vm::entryFromReminder) }
    pending?.let { archive ->
        AlertDialog(onDismissRequest = vm::dismissRestore, modifier = Modifier.testTag("restore-dialog"),
            title = { Text(if (restoringSnapshot) "恢复替换前快照？" else "从备份恢复？") },
            text = { Text("备份包含 ${archive.entries.size} 条记录、${archive.memos.size} 条便签、${archive.reminders.size} 条独立提醒，将完整替换当前 ${entries.size} 条记录、${memos.size} 条便签、${reminders.size} 条独立提醒。" +
                (if (archive.formatVersion < 4) "\n\n这是不含便签的旧版备份，恢复会清空当前便签。" else "") +
                (if (archive.formatVersion < 6) "\n\n这是不含图片的旧版备份，恢复会移除当前图片；替换前快照仍保留原图片。" else "") +
                (if (archive.formatVersion < 5) "\n\n这是不含独立提醒的旧版备份，恢复会清空当前独立提醒。" else "") +
                (if (archive.entries.isEmpty() && archive.memos.isEmpty() && archive.reminders.isEmpty()) "\n\n这是空备份，恢复后当前列表将被清空。" else "") +
                "\n\n包含 ${archive.imageCount} 张图片，图片体积 ${"%.1f".format(archive.imageBytes / 1048576.0)} MiB。替换前会保存含图片的本地快照。备份恢复不会合并记录。") },
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
            else Text(entry.kind.symbol, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val titleColor by animateColorAsState(if (entry.completed) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface, tween(250), label = "completed-color")
                val strike by animateFloatAsState(if (entry.completed) 1f else 0f, tween(250), label = "completed-strike")
                var titleLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
                Text(entry.title, style = MaterialTheme.typography.titleMedium, color = titleColor,
                    onTextLayout = { titleLayout = it }, modifier = Modifier.drawWithContent {
                        drawContent()
                        titleLayout?.let { layout ->
                            for (line in 0 until layout.lineCount) {
                                val left = layout.getLineLeft(line)
                                val right = layout.getLineRight(line)
                                val y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2
                                if (strike > 0) drawLine(titleColor, Offset(left, y), Offset(left + (right - left) * strike, y), 1.dp.toPx())
                            }
                        }
                    })
                Text(buildString {
                    append(entry.kind.label)
                    if (showDate) append(" · ${entry.date ?: "未设截止日期"}")
                    append(" · ${entry.time ?: if (entry.kind == EntryKind.TASK && entry.date == null) "未安排" else "具体时间未定"}")
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
