package dev.lukino.daybook

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.lukino.daybook.ui.*

class MainActivity : ComponentActivity() {
    private val model: DaybookViewModel by viewModels {
        viewModelFactory { initializer {
            val app = application as DaybookApplication
            DaybookViewModel(app.repository, createSavedStateHandle(), app.backup)
        } }
    }
    private fun openNotification(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "daybook") uri.lastPathSegment?.let { id ->
            when (uri.host) { "entry" -> model.openEntry(id); "memo" -> model.openMemo(id) }
        }
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        openNotification(intent)
    }
    override fun onResume() {
        super.onResume()
        (application as DaybookApplication).reminders.refresh()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openNotification(intent)
        enableEdgeToEdge()
        setContent {
            val appearance by (application as DaybookApplication).appearance.state.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppearance provides appearance.current) {
            DaybookTheme(appearance.current?.seed) {
                DaybookScreen(model)
                StartupReminderSetup()
            }
            }
        }
    }
}
