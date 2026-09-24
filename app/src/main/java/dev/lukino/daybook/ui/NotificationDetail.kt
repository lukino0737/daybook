package dev.lukino.daybook.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import dev.lukino.daybook.data.*
import dev.lukino.daybook.media.BodyImageStore
import dev.lukino.daybook.reminder.RepeatRules
import java.time.LocalDateTime
import java.time.ZoneId

/** Only the source reference is saved; displayed content always comes from the repository. */
data class NotificationDetail(
    val target: String,
    val entry: Entry? = null,
    val memo: Memo? = null,
    val reminder: StandaloneReminder? = null,
    val error: String? = null,
) {
    val exists get() = entry != null || memo != null || reminder != null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun NotificationDetailScreen(
    detail: NotificationDetail?, now: LocalDateTime, images: BodyImageStore?,
    onBack: () -> Unit, onEdit: () -> Unit,
) {
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.let {
            WindowCompat.getInsetsController(it, view).apply { isAppearanceLightStatusBars = true; isAppearanceLightNavigationBars = true }
        } }
        Scaffold(Modifier.fillMaxSize().testTag("notification-detail"), topBar = {
            TopAppBar(title = { Text(detail?.entry?.kind?.label ?: if (detail?.memo != null) "便签" else "提醒") },
                navigationIcon = { IconButton(onClick = onBack, modifier = Modifier.testTag("detail-back")) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
                } }, actions = { TextButton(enabled = detail?.exists == true, onClick = onEdit, modifier = Modifier.testTag("detail-edit")) { Text("编辑") } })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)
                .testTag("detail-content"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    detail == null -> CircularProgressIndicator()
                    detail.error != null -> Text(detail.error)
                    !detail.exists -> Text("这条内容已删除或已不在当前数据中", Modifier.testTag("detail-missing"))
                    else -> {
                        detail.entry?.let { entry ->
                            Text(entry.title, style = MaterialTheme.typography.headlineSmall)
                            Text(if (entry.kind == EntryKind.TASK) {
                                listOf(if (entry.completed) "已完成" else if (EntryRules.isOverdue(entry, now)) "已逾期" else "未完成",
                                    entry.date?.let { "截止 · $it · ${entry.time ?: "具体时间未定"}" } ?: "不设截止日期").joinToString("\n")
                            } else "${entry.date.orEmpty()} · ${entry.time ?: "具体时间未定"}", style = MaterialTheme.typography.bodyMedium)
                            entry.reminderAt?.let { DetailReminderTime(it, entry.reminderDeliveredFor) }
                            if (entry.tags.isNotEmpty()) Text(entry.tags.joinToString(" · ") { "#$it" }, color = MaterialTheme.colorScheme.primary)
                            RichBodyPreview(entry.note, entry.blocks, images)
                        }
                        detail.memo?.let { memo ->
                            memo.date?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            memo.reminderAt?.let { DetailReminderTime(it, memo.reminderDeliveredFor) }
                            RichBodyPreview(memo.body, memo.blocks, images)
                        }
                        detail.reminder?.let { reminder ->
                            Text(reminder.title, style = MaterialTheme.typography.headlineSmall)
                            Text("起始 · ${reminder.startDate} ${reminder.time}")
                            Text(RepeatRules.summary(reminder))
                            Text(if (reminder.enabled) "提醒已启用" else "提醒已暂停")
                            RepeatRules.next(reminder, now.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())?.let {
                                Text("下一次 · ${it.toString().replace('T', ' ')}")
                            }
                            reminder.deliveredFor?.let { Text("最近发出 · ${it.replace('T', ' ')}") }
                            if (reminder.note.isNotBlank()) Text(reminder.note, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun DetailReminderTime(at: String, delivered: String?) {
    Text("提醒 · ${at.replace('T', ' ')}${if (at == delivered) " · 已发出" else ""}", style = MaterialTheme.typography.bodyMedium)
}
