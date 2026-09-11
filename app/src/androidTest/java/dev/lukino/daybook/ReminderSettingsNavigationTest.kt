package dev.lukino.daybook

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import dev.lukino.daybook.reminder.openReminderSettings
import org.junit.Assert.*
import org.junit.Test

class ReminderSettingsNavigationTest {
    @Test fun missingAndProtectedVendorPagesFallBackToThisAppsDetails() {
        val visited = mutableListOf<Intent>()
        assertTrue(openReminderSettings("dev.lukino.daybook", listOf(Intent("missing"), Intent("protected"))) {
            visited += it
            when (it.action) {
                "missing" -> throw ActivityNotFoundException()
                "protected" -> throw SecurityException()
            }
        })
        assertEquals(3, visited.size)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, visited.last().action)
        assertEquals("package:dev.lukino.daybook", visited.last().dataString)
    }
    @Test fun supportedShortcutDoesNotAlsoOpenFallback() {
        val visited = mutableListOf<Intent>()
        assertTrue(openReminderSettings("dev.lukino.daybook", listOf(Intent("supported"))) { visited += it })
        assertEquals(listOf("supported"), visited.map { it.action })
    }
    @Test fun missingAppDetailsFallsBackToSystemSettingsAndTotalFailureIsReported() {
        val visited = mutableListOf<Intent>()
        assertTrue(openReminderSettings("dev.lukino.daybook", emptyList()) {
            visited += it
            if (it.action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) throw ActivityNotFoundException()
        })
        assertEquals(Settings.ACTION_SETTINGS, visited.last().action)
        assertFalse(openReminderSettings("dev.lukino.daybook", emptyList()) { throw ActivityNotFoundException() })
    }
}
