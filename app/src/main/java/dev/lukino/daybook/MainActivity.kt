package dev.lukino.daybook

import android.os.Bundle
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DaybookTheme { DaybookScreen(model) }
        }
    }
}
