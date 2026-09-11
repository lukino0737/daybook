package dev.lukino.daybook.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import dev.lukino.daybook.DaybookApplication

/** Installation-local UI state, independent of calendar data and JSON backups. */
internal class ReminderSetupPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("reminder_setup", Context.MODE_PRIVATE)
    var notificationRequested: Boolean
        get() = preferences.getBoolean("notification_requested", false)
        set(value) { preferences.edit().putBoolean("notification_requested", value).apply() }
    var guideCompleted: Boolean
        get() = preferences.getBoolean("guide_completed", false)
        set(value) { preferences.edit().putBoolean("guide_completed", value).apply() }
}

@Composable fun StartupReminderSetup() {
    val context = LocalContext.current
    val preferences = remember { ReminderSetupPreferences(context) }
    var requesting by rememberSaveable { mutableStateOf(false) }
    var ready by rememberSaveable { mutableStateOf(preferences.notificationRequested || preferences.guideCompleted) }
    var completed by remember { mutableStateOf(preferences.guideCompleted) }
    var settings by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requesting = false
        ready = true
        (context.applicationContext as DaybookApplication).reminders.refresh()
    }
    LaunchedEffect(Unit) {
        if (!ready && !requesting) {
            // Record the attempt before launching: a refusal/dismissal must not prompt on every launch.
            preferences.notificationRequested = true
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requesting = true
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else ready = true
        }
    }
    fun finish(openSettings: Boolean) {
        preferences.guideCompleted = true
        completed = true
        settings = openSettings
    }
    if (ready && !completed) AlertDialog(
        modifier = Modifier.testTag("reminder-setup-guide"),
        onDismissRequest = { finish(false) },
        title = { Text("让提醒按时到达") },
        text = { Text("请在提醒设置中开启通知与准时提醒，并按手机设置开启自启动和悬浮通知。稍后也可从右上角菜单进入。") },
        confirmButton = { TextButton(onClick = { finish(true) }, Modifier.testTag("reminder-setup-open")) { Text("去设置") } },
        dismissButton = { TextButton(onClick = { finish(false) }, Modifier.testTag("reminder-setup-skip")) { Text("稍后设置") } }
    )
    if (settings) ReminderSettingsScreen { settings = false }
}
