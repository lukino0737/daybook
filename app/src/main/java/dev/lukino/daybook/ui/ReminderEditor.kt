package dev.lukino.daybook.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lukino.daybook.DaybookApplication
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Composable private fun reminderPermissionState(): Pair<Boolean, Boolean> {
    val app = LocalContext.current.applicationContext as DaybookApplication
    var state by remember { mutableStateOf(app.reminders.notificationsEnabled() to app.reminders.exactEnabled()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            state = app.reminders.notificationsEnabled() to app.reminders.exactEnabled()
            app.reminders.refresh()
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return state
}

@Composable fun ReminderStatus(onClick: () -> Unit) {
    val (notifications, exact) = reminderPermissionState()
    val app = LocalContext.current.applicationContext as DaybookApplication
    val failure by app.reminders.failure.collectAsStateWithLifecycle()
    TextButton(onClick = onClick) {
        Text(failure ?: when {
            !notifications -> "提醒尚未启用：开启通知"
            !exact -> "提醒尚未启用：允许准时提醒"
            else -> "提醒权限已开启 · 查看设置"
        })
    }
}

@Composable fun ReminderPermissions() {
    val context = LocalContext.current
    val app = context.applicationContext as DaybookApplication
    val (notifications, exact) = reminderPermissionState()
    var granted by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it; app.reminders.refresh()
    }
    var settingError by remember { mutableStateOf<String?>(null) }
    fun open(intent: Intent) {
        try { context.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) {
            settingError = "无法打开此设置，请在手机系统设置中找到 Daybook。"
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("需同时开启通知和准时提醒，才能发送提醒。未授权不会影响记录保存。", style = MaterialTheme.typography.bodySmall)
        Text("通知：${if (notifications || granted) "已允许" else "未开启"}")
        if (!notifications && !granted && Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("允许通知") }
        TextButton(onClick = { open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, dev.lukino.daybook.reminder.ReminderCoordinator.CHANNEL)) }) { Text("打开通知设置") }
        Text("准时提醒：${if (exact) "已允许" else "未开启"}")
        if (!exact && Build.VERSION.SDK_INT >= 31) OutlinedButton(onClick = {
            open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        }) { Text("允许准时提醒") }
        Text("系统强行停止应用后，需重新打开。省电设置可能影响送达；过去 24 小时内遗漏的提醒会补发，超过 24 小时不补发。", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { app.reminders.refresh() }) { Text("重新检查提醒") }
        settingError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ReminderEditor(draft: Draft, busy: Boolean, onChange: (Draft) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("提醒", style = MaterialTheme.typography.titleMedium)
            Switch(checked = draft.reminderAt != null, enabled = !busy, modifier = Modifier.testTag("reminder-toggle"), onCheckedChange = { enabled ->
                val candidate = draft.date?.let(LocalDate::parse)?.atTime(draft.time?.let(LocalTime::parse) ?: LocalTime.of(9, 0))
                val initial = candidate?.takeIf { it > LocalDateTime.now() } ?: LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)
                onChange(draft.copy(reminderAt = if (enabled) initial.toString() else null, reminderDeliveredFor = null))
            })
        }
        draft.reminderAt?.let { raw ->
            val time = LocalDateTime.parse(raw)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = {
                    DatePickerDialog(context, { _, y, m, d -> onChange(draft.copy(reminderAt = LocalDate.of(y, m + 1, d).atTime(time.toLocalTime()).toString(), reminderDeliveredFor = null)) }, time.year, time.monthValue - 1, time.dayOfMonth).show()
                }) { Text(time.toLocalDate().toString()) }
                OutlinedButton(enabled = !busy, onClick = {
                    TimePickerDialog(context, { _, h, m -> onChange(draft.copy(reminderAt = time.toLocalDate().atTime(h, m).toString(), reminderDeliveredFor = null)) }, time.hour, time.minute, true).show()
                }) { Text(time.toLocalTime().toString()) }
            }
            Text("提醒时间独立设置，修改事项日期不会自动移动提醒。", style = MaterialTheme.typography.bodySmall)
            if (time <= LocalDateTime.now()) Text("提醒时间已过，请确认是否需要调整。", color = MaterialTheme.colorScheme.error)
            ReminderPermissions()
        }
    }
}
