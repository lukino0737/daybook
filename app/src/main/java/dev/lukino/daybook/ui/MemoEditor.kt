package dev.lukino.daybook.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lukino.daybook.data.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun MemoEditor(value: Memo, controller: MemoController) {
    val saving by controller.busy.collectAsStateWithLifecycle()
    val importing by controller.images.busy.collectAsStateWithLifecycle()
    val busy = saving || importing
    val status by controller.status.collectAsStateWithLifecycle()
    val error by controller.error.collectAsStateWithLifecycle()
    val blankExit by controller.confirmBlankExit.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) controller.flush() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Dialog(onDismissRequest = { controller.close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {

        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.let { WindowCompat.getInsetsController(it, view).apply { isAppearanceLightStatusBars = true; isAppearanceLightNavigationBars = true } } }
        Scaffold(Modifier.fillMaxSize().imePadding().testTag("memo-editor"), topBar = {
            TopAppBar(title = { Text("便签") }, navigationIcon = {
                IconButton(enabled = !busy, onClick = { controller.close() }, modifier = Modifier.testTag("memo-back")) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回并保存") }
            }, actions = { TextButton(enabled = !busy, onClick = { controller.close() }, modifier = Modifier.testTag("memo-done")) { Text("完成") } })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RichBodyEditor(value.body, value.blocks, !busy, "memo-body", "正文", controller.images, autoFocus = true) { text, blocks ->
                    controller.change(value.copy(body = text, blocks = blocks))
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { controller.flush() }) { Text("重试保存") } }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = {
                        val date = value.date?.let(LocalDate::parse) ?: LocalDate.now()
                        DatePickerDialog(context, { _, y, m, d -> controller.change(value.copy(date = LocalDate.of(y, m + 1, d).toString())) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }) { Text(value.date ?: "添加日期") }
                    if (value.date != null) TextButton(enabled = !busy, onClick = { controller.change(value.copy(date = null)) }) { Text("清除日期") }
                }
                ReminderEditor(Draft(kind = EntryKind.TASK, reminderAt = value.reminderAt), busy,
                    { controller.change(value.copy(reminderAt = it.reminderAt)) }, { settings = true })
                TextButton(enabled = !busy, onClick = { confirmDelete = true }, modifier = Modifier.testTag("memo-delete"),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除便签") }
            }
        }
        if (blankExit) AlertDialog(onDismissRequest = controller::cancelBlankExit,
            title = { Text("便签内容已清空") },
            text = { Text("可以保留上次保存的内容并退出，或删除这条便签。") },
            confirmButton = { TextButton(enabled = !busy, onClick = controller::keepSavedAndClose) { Text("保留内容并退出") } },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { controller.cancelBlankExit(); confirmDelete = true }, modifier = Modifier.testTag("blank-delete")) { Text("删除便签") }
            })
        if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("删除这条便签？") },
            text = { Text(error ?: "确认后将删除正文、图片、日期及这条便签的提醒。") },
            confirmButton = { TextButton(enabled = !busy, onClick = { controller.close(true) }) { Text("确认删除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
    }
    if (settings) ReminderSettingsScreen { settings = false }
}
