package dev.lukino.daybook.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import dev.lukino.daybook.data.EntryKind
import dev.lukino.daybook.data.RichBody
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable fun EntryEditor(
    draft: Draft, busy: Boolean, error: String?,
    onChange: (Draft) -> Unit, onSave: () -> Unit, onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    knownTags: List<String> = emptyList(),
    onNewReminder: (() -> Unit)? = null,
    imageController: ImageEditorController? = null,
) {
    val importing by imageController?.busy?.collectAsState() ?: remember { mutableStateOf(false) }
    val locked = busy || importing
    val context = LocalContext.current
    var reminderSettings by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let {
                WindowCompat.getInsetsController(it, view).apply { isAppearanceLightStatusBars = true; isAppearanceLightNavigationBars = true }
            }
        }
        Surface(Modifier.fillMaxSize().testTag("editor-root")) {
            Column(Modifier.safeDrawingPadding().imePadding().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss, enabled = !locked) { Text("取消") }
                    Text(if (draft.id == null) "记一笔" else "编辑记录", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onSave, enabled = !locked && draft.title.isNotBlank(), modifier = Modifier.testTag("save")) { Text(if (busy) "保存中" else "保存") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EntryKind.entries.forEach { kind ->
                            FilterChip(selected = draft.kind == kind, enabled = !locked, onClick = {
                                val newTask = draft.id == null && draft.kind != kind && kind == EntryKind.TASK
                                onChange(draft.copy(kind = kind,
                                    date = if (newTask) null else draft.date ?: if (kind != EntryKind.TASK) LocalDate.now().toString() else null,
                                    time = if (newTask) null else draft.time,
                                    completed = if (kind == EntryKind.TASK) draft.completed else false))
                            }, label = { Text("${kind.symbol} ${kind.label}") })
                        }
                        if (draft.id == null && onNewReminder != null) FilterChip(selected = false, enabled = !locked && RichBody.images(draft.blocks).isEmpty(), onClick = onNewReminder, label = { Text("提醒") }, modifier = Modifier.testTag("new-reminder-kind"))
                    }
                    OutlinedTextField(value = draft.title, onValueChange = { if (it.length <= 300) onChange(draft.copy(title = it)) },
                        label = { Text("写点什么") }, modifier = Modifier.fillMaxWidth().testTag("title"), enabled = !locked, maxLines = 4)
                    Text(if (draft.kind == EntryKind.TASK) "截止日期" else "发生日期", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !locked, onClick = {
                            val d = draft.date?.let(LocalDate::parse) ?: LocalDate.now()
                            DatePickerDialog(context, { _, y, m, day -> onChange(draft.copy(date = LocalDate.of(y, m + 1, day).toString())) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                        }) { Text(draft.date ?: "不设截止日期") }
                        if (draft.kind == EntryKind.TASK && draft.date != null) IconButton(enabled = !locked, modifier = Modifier.testTag("clear-date"), onClick = { onChange(draft.copy(date = null, time = null)) }) { Icon(Icons.Outlined.Close, "清除截止日期") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = !locked && draft.date != null, onClick = {
                            val t = draft.time?.let(LocalTime::parse) ?: LocalTime.of(9, 0)
                            TimePickerDialog(context, { _, h, m -> onChange(draft.copy(time = LocalTime.of(h, m).format(DateTimeFormatter.ofPattern("HH:mm")))) }, t.hour, t.minute, true).show()
                        }) { Text(draft.time ?: "具体时间未定") }
                        if (draft.time != null) TextButton(enabled = !locked, onClick = { onChange(draft.copy(time = null)) }) { Text("清除时间") }
                    }
                    if (draft.kind == EntryKind.TASK) Text("只设日期时，当天结束后才算逾期。这里记录的是截止时间。", style = MaterialTheme.typography.bodySmall)
                    TagEditor(draft, busy, knownTags, onChange)
                    if (draft.kind != EntryKind.NOTE) ReminderEditor(draft, busy, onChange) { reminderSettings = true }
                    RichBodyEditor(draft.note, draft.blocks, !locked, "note",
                        if (draft.kind == EntryKind.NOTE) "今天发生了什么" else "备注 · 链接、地点、细节", imageController) { text, blocks ->
                        onChange(draft.copy(note = text, blocks = blocks))
                    }
                    if (draft.id == null && RichBody.images(draft.blocks).isNotEmpty()) Text("独立提醒不支持图片；移除图片后可切换。", style = MaterialTheme.typography.bodySmall)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (onDelete != null) TextButton(onClick = { confirmDelete = true }, enabled = !locked) { Text("删除记录", color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        if (reminderSettings) ReminderSettingsScreen { reminderSettings = false }
        if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("删除这条记录？") },
            text = { Text("删除后可通过底部提示短时撤销。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete?.invoke() }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
    }
}
