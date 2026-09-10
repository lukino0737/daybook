package dev.lukino.daybook.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.lukino.daybook.DaybookApplication
import kotlinx.coroutines.*

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { (context.applicationContext as DaybookApplication).reminders.reconcile() }
            finally { pending.finish() }
        }
    }
}
