package dev.lukino.daybook.reminder

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings

internal fun openReminderSettings(packageName: String, preferred: List<Intent>, startActivity: (Intent) -> Unit): Boolean {
    val candidates = preferred + listOf(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        Intent(Settings.ACTION_SETTINGS))
    for (intent in candidates) {
        try {
            startActivity(intent)
            return true
        } catch (_: ActivityNotFoundException) {
            // OEM settings activities can disappear between system versions.
        } catch (_: SecurityException) {
            // Some system settings activities exist but aren't exported to ordinary apps.
        }
    }
    return false
}
