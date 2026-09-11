package dev.lukino.daybook.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lukino.daybook.DaybookApplication
import dev.lukino.daybook.reminder.ReminderCoordinator

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ReminderSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as DaybookApplication
    val manager = context.getSystemService(NotificationManager::class.java)
    var revision by remember { mutableIntStateOf(0) }
    val state = remember(revision) { app.reminders.notificationsEnabled() to app.reminders.exactEnabled() }
    val channel = remember(revision) { manager.getNotificationChannel(ReminderCoordinator.CHANNEL) }
    val needsRuntimePermission = remember(revision) { Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED }
    val failure by app.reminders.failure.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { revision++; app.reminders.refresh() } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { revision++; app.reminders.refresh() }
    var error by remember { mutableStateOf<String?>(null) }
    fun open(intent: Intent) {
        try { context.startActivity(intent); error = null }
        catch (_: android.content.ActivityNotFoundException) { error = "请在手机系统设置中找到 Daybook。" }
        catch (_: SecurityException) { error = "请在手机系统设置中找到 Daybook。" }
    }
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.let {
            WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = true
        } }
        Scaffold(Modifier.fillMaxSize().testTag("reminder-settings-screen"), topBar = {
            TopAppBar(title = { Text("提醒设置") }, navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("reminder-settings-back")) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
            })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("请开启以下两项", style = MaterialTheme.typography.titleMedium)
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("通知 · ${if (state.first) "已开启" else "未开启"}", style = MaterialTheme.typography.titleMedium)
                    Text("声音${if (channel?.sound != null) "开启" else "关闭"} · 振动${if (channel?.shouldVibrate() == true) "开启" else "关闭"}", style = MaterialTheme.typography.bodySmall)
                    if (needsRuntimePermission) OutlinedButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("允许通知") }
                    TextButton(onClick = { open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, ReminderCoordinator.CHANNEL)) }, modifier = Modifier.testTag("notification-system-settings")) { Text("声音、振动与悬浮设置") }
                } }
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("准时提醒 · ${if (state.second) "已开启" else "未开启"}", style = MaterialTheme.typography.titleMedium)
                    if (Build.VERSION.SDK_INT >= 31) TextButton(onClick = {
                        open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                    }, modifier = Modifier.testTag("exact-system-settings")) { Text("设置准时提醒") }
                } }
                Text("静音时的振动与悬浮通知由手机系统控制。", style = MaterialTheme.typography.bodySmall)
                (error ?: failure)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
