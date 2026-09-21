package dev.lukino.daybook.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lukino.daybook.data.*
import dev.lukino.daybook.reminder.RepeatRules
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun StandaloneReminderEditor(value: StandaloneReminder, controller: StandaloneReminderController, onEntryKind: (EntryKind) -> Unit) {
    val context = LocalContext.current
    val busy by controller.busy.collectAsStateWithLifecycle()
    val error by controller.error.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }

    var tick by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(value.id) { while (true) { tick = Instant.now(); kotlinx.coroutines.delay(1000) } }
    val preview = runCatching {
        value.copy(deliveredFor = null).validate()
        val previewValue = controller.schedulingPreview(value, tick)
        val next = RepeatRules.next(previewValue, tick, ZoneId.systemDefault())
        RepeatRules.summary(value) + "\n" + if (!value.enabled) "已暂停；恢复并保存后安排未来提醒" else next?.let { "下一次 · ${it.toString().replace('T', ' ')}" } ?: if (previewValue.deliveredFor != null) "已发出 · ${previewValue.deliveredFor!!.replace('T', ' ')}" else "没有未来提醒，请调整日期或时间"
    }.getOrElse { it.localizedMessage ?: "请检查重复规则" }
    Dialog(onDismissRequest = controller::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val dialogView = LocalView.current
        SideEffect { (dialogView.parent as? DialogWindowProvider)?.window?.let {
            WindowCompat.getInsetsController(it, dialogView).isAppearanceLightStatusBars = true
        } }
        Scaffold(Modifier.fillMaxSize().imePadding().testTag("standalone-editor"), topBar = {
            Column { TopAppBar(title = { Text(if (controller.isNew) "记一笔 · 提醒" else "编辑提醒") }, navigationIcon = {
                TextButton(enabled = !busy, onClick = controller::dismiss, modifier = Modifier.testTag("standalone-cancel")) { Text("取消") }
            }, actions = { TextButton(enabled = !busy && value.title.isNotBlank(), onClick = controller::save, modifier = Modifier.testTag("standalone-save")) { Text(if (busy) "保存中" else "保存") } })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).testTag("standalone-error")) }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (controller.isNew) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EntryKind.entries.forEach { kind -> FilterChip(selected = false, enabled = !busy, onClick = { onEntryKind(kind) }, label = { Text(kind.label) }, modifier = Modifier.testTag("standalone-kind-${kind.name}")) }
                    FilterChip(selected = true, onClick = {}, label = { Text("提醒") })
                }
                OutlinedTextField(value.title, { if (it.length <= 300) controller.change(value.copy(title = it)) }, enabled = !busy,
                    label = { Text("提醒什么") }, modifier = Modifier.fillMaxWidth().testTag("standalone-title"))
                Text(if (value.repeat == RepeatKind.ONCE) "提醒日期" else "起始日期")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = {
                        val d = LocalDate.parse(value.startDate)
                        DatePickerDialog(context, { _, y, m, day -> controller.change(value.copy(startDate = LocalDate.of(y, m + 1, day).toString())) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                    }, modifier = Modifier.testTag("standalone-date")) { Text(value.startDate) }
                    OutlinedButton(enabled = !busy, onClick = {
                        val t = LocalTime.parse(value.time)
                        TimePickerDialog(context, { _, h, m -> controller.change(value.copy(time = LocalTime.of(h, m).format(DateTimeFormatter.ofPattern("HH:mm")))) }, t.hour, t.minute, true).show()
                    }, modifier = Modifier.testTag("standalone-time")) { Text(value.time) }
                }
                Text("重复规则")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RepeatKind.entries.forEach { kind -> FilterChip(selected = value.repeat == kind, enabled = !busy,
                        onClick = { controller.setRepeat(kind) }, label = { Text(kind.label) }, modifier = Modifier.testTag("repeat-${kind.name}")) }
                }
                if (value.repeat == RepeatKind.WEEKLY) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { day -> FilterChip(selected = value.weekdays and (1 shl (day - 1)) != 0, enabled = !busy,
                        onClick = { controller.change(value.copy(weekdays = value.weekdays xor (1 shl (day - 1)))) },
                        label = { Text("周" + listOf("一", "二", "三", "四", "五", "六", "日")[day - 1]) }, modifier = Modifier.testTag("weekday-$day")) }
                }
                if (value.repeat == RepeatKind.INTERVAL) OutlinedTextField(value.intervalDays.takeIf { it > 0 }?.toString().orEmpty(), {
                    if (it.length <= 4 && it.all(Char::isDigit)) { controller.change(value.copy(intervalDays = it.toIntOrNull() ?: 0)) }
                }, label = { Text("每隔多少天 · 1–9999") }, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("interval-days"))
                Text(preview, modifier = Modifier.testTag("standalone-preview"), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("启用提醒", modifier = Modifier.weight(1f)); Switch(value.enabled, { controller.change(value.copy(enabled = it)) }, enabled = !busy, modifier = Modifier.testTag("standalone-enabled"))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("显示在日历", modifier = Modifier.weight(1f)); Switch(value.showInCalendar, controller::setCalendar, enabled = !busy, modifier = Modifier.testTag("standalone-calendar"))
                }
                Text("暂停只停止通知；日历显示由上方开关控制。", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value.note, { if (it.length <= 20_000) controller.change(value.copy(note = it)) }, enabled = !busy,
                    label = { Text("备注") }, minLines = 3, modifier = Modifier.fillMaxWidth().testTag("standalone-note"))
                TextButton(onClick = { settings = true }) { Text("提醒设置") }
                if (!controller.isNew) TextButton(enabled = !busy, onClick = { confirmDelete = true }, modifier = Modifier.testTag("standalone-delete")) { Text("删除提醒", color = MaterialTheme.colorScheme.error) }
            }
        }
        if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("删除整条提醒？") },
            text = { Text("将删除这条提醒及其全部重复提醒，日历中的对应显示也会移除。") },
            confirmButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false; controller.delete() }, modifier = Modifier.testTag("standalone-confirm-delete")) { Text("确认删除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }, modifier = Modifier.testTag("standalone-cancel-delete")) { Text("取消") } })
        BackgroundReminderGuide { settings = true }
        if (settings) ReminderSettingsScreen { settings = false }
    }
}
