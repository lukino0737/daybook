package dev.lukino.daybook.reminder

import android.app.*
import android.content.*
import android.net.Uri
import android.media.AudioAttributes
import android.provider.Settings
import android.os.Build
import dev.lukino.daybook.MainActivity
import dev.lukino.daybook.R
import dev.lukino.daybook.data.Entry
import dev.lukino.daybook.data.EntryRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

/** One OS alarm for the next reminder, with the database as the authoritative queue. */
class ReminderCoordinator(private val context: Context, private val repository: EntryRepository) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    val failure = MutableStateFlow<String?>(null)

    init {
        // Existing channels belong to the user. Keep their sound/vibration/importance unchanged.
        if (notifications.getNotificationChannel(CHANNEL) == null) notifications.createNotificationChannel(defaultChannel())
    }
    fun notificationsEnabled(): Boolean = notifications.areNotificationsEnabled() &&
        notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    fun exactEnabled(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    fun start() { scope.launch { combine(repository.entries, repository.memos) { _, _ -> Unit }.collect { reconcile() } } }
    fun refresh() { scope.launch { reconcile() } }

    suspend fun reconcile() = lock.withLock {
        try {
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            val ready = notificationsEnabled() && exactEnabled()
            repository.reconcileReminders(deliver = { entry ->
                if (ready && entry.pending && entry.instant(zone) <= now && entry.instant(zone) > now.minusSeconds(24 * 60 * 60)) { notify(entry, now, zone); true } else false
            }, schedule = { entries ->
                // Remove already-visible notifications for changed/deleted/completed items.
                notifications.activeNotifications.filter { it.tag != null }.forEach { active ->
                    val entry = entries.firstOrNull { it.key == active.tag }
                    if (entry == null || !entry.enabled || entry.at == null ||
                        active.notification.extras.getString("daybook.reminder") != entry.at ||
                        active.notification.extras.getLong("daybook.updated") != entry.updatedAt)
                        notifications.cancel(active.tag, 0)
                }
                val pending = alarmIntent()
                alarms.cancel(pending)
                if (ready) entries.filter { it.pending }.map { it.instant(zone) }.filter { it > now }.minOrNull()?.let {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.toEpochMilli(), pending)
                }
            })
            failure.value = null
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failure.value = "提醒调度未完成，请检查权限后重试。记录已保留。" }
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_DUE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun notify(entry: ReminderTarget, now: Instant, zone: ZoneId) {
        val open = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW).setData(Uri.parse(entry.uri))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val late = now.isAfter(entry.instant(zone).plusSeconds(60))
        val body = entry.description + if (late) " · 补发提醒" else ""
        val extras = android.os.Bundle().apply {
            putString("daybook.reminder", entry.at)
            putLong("daybook.updated", entry.updatedAt)
        }
        val notification = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(entry.title).setContentText(body).setContentIntent(pending)
            .setAutoCancel(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE).addExtras(extras).build()
        notifications.notify(entry.key, 0, notification)
    }

    companion object {
        internal fun defaultChannel() = NotificationChannel(CHANNEL, "事项提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 150, 250)
            setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        }
        const val CHANNEL = "daybook.reminders"
        const val ACTION_DUE = "dev.lukino.daybook.REMINDER_DUE"
    }
}
