package dev.lukino.daybook.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.lukino.daybook.reminder.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ReminderListScreen(rows: List<ReminderListItem>, message: String?, onBack: () -> Unit,
    onNew: () -> Unit, onOpen: (ReminderListItem) -> Unit) {
    var pausedExpanded by rememberSaveable { mutableStateOf(false) }
    var finishedExpanded by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val dialogView = LocalView.current
        SideEffect { (dialogView.parent as? DialogWindowProvider)?.window?.let {
            WindowCompat.getInsetsController(it, dialogView).isAppearanceLightStatusBars = true
        } }
        Scaffold(Modifier.fillMaxSize().testTag("reminder-list-screen"), topBar = {
            TopAppBar(title = { Text("提醒列表") }, navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("reminder-list-back")) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
            }, actions = { TextButton(onClick = { settings = true }) { Text("提醒设置") } })
        }, floatingActionButton = {
            FloatingActionButton(onClick = onNew, modifier = Modifier.testTag("reminder-list-add")) { Icon(Icons.Outlined.Add, "新建提醒") }
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("reminder-list"), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                message?.let { item { Text(it, modifier = Modifier.testTag("reminder-list-message")) } }
                ReminderGroup.entries.forEach { group ->
                    val section = rows.filter { it.group == group }
                    val expanded = when (group) { ReminderGroup.PENDING -> true; ReminderGroup.PAUSED -> pausedExpanded; ReminderGroup.FINISHED -> finishedExpanded }
                    item {
                        if (group == ReminderGroup.PENDING) Text("${group.label} · ${section.size}", style = MaterialTheme.typography.titleLarge)
                        else TextButton(onClick = { if (group == ReminderGroup.PAUSED) pausedExpanded = !pausedExpanded else finishedExpanded = !finishedExpanded }, modifier = Modifier.testTag("reminder-group-${group.name}")) {
                            Text("${if (expanded) "收起" else "展开"} ${group.label} · ${section.size}", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    if (expanded) {
                        if (section.isEmpty()) item { Text(if (group == ReminderGroup.PENDING) "暂无待提醒内容，可点右下角新建提醒。" else "暂无${group.label}的提醒") }
                        items(section, key = { it.key }) { row ->
                            Card(onClick = { onOpen(row) }, modifier = Modifier.fillMaxWidth().testTag("reminder-row-${row.key}")) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(row.title, style = MaterialTheme.typography.titleMedium)
                                    Text("${row.source} · ${row.rule}", style = MaterialTheme.typography.bodySmall)
                                    Text(row.status, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
        BackgroundReminderGuide { settings = true }
        if (settings) ReminderSettingsScreen { settings = false }
    }
}
