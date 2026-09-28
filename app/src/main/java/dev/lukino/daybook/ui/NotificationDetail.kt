package dev.lukino.daybook.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.CancellationException
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
    val host = LocalView.current
    val configuration = LocalConfiguration.current
    val backdrop by produceState<android.graphics.Bitmap?>(null, host, configuration.screenWidthDp, configuration.screenHeightDp) {
        value = try { blurredPreviewBackdrop(host) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
    }
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.let {
            WindowCompat.getInsetsController(it, view).apply { isAppearanceLightStatusBars = true; isAppearanceLightNavigationBars = true }
        } }
        Box(Modifier.fillMaxSize().testTag("notification-detail"), contentAlignment = Alignment.Center) {
            backdrop?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().testTag("detail-blurred-background"), contentScale = ContentScale.FillBounds) }
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.22f)))
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 28.dp), contentAlignment = Alignment.Center) {
                Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight * 0.68f).testTag("detail-card"),
                    shape = RoundedCornerShape(24.dp), tonalElevation = 6.dp, shadowElevation = 12.dp) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack, modifier = Modifier.testTag("detail-back")) {
                                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "退出预览")
                            }
                            Text(detail?.entry?.kind?.label ?: if (detail?.memo != null) "便签" else "提醒",
                                modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            TextButton(enabled = detail?.exists == true, onClick = onEdit, modifier = Modifier.testTag("detail-edit")) { Text("编辑") }
                        }
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp)
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
        }
    }
}

@Composable private fun DetailReminderTime(at: String, delivered: String?) {
    Text("提醒 · ${at.replace('T', ' ')}${if (at == delivered) " · 已发出" else ""}", style = MaterialTheme.typography.bodyMedium)
}
