package dev.lukino.daybook.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ReminderEditor(draft: Draft, busy: Boolean, onChange: (Draft) -> Unit, onSettings: () -> Unit) {
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
            TextButton(onClick = onSettings, modifier = Modifier.testTag("reminder-settings-entry")) { Text("提醒设置") }
        }
    }
}
