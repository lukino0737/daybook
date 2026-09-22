package dev.lukino.daybook.ui

import android.net.Uri
import dev.lukino.daybook.data.EntryRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class ImageEditorController(private val repository: EntryRepository, private val scope: CoroutineScope) {
    val store get() = repository.images
    val busy = MutableStateFlow(false)
    suspend fun import(uri: Uri, owner: String) = try {
        busy.value = true
        repository.importImage(uri, owner)
    } finally { busy.value = false }
    fun finished(owner: String) {
        store?.release(owner)
        scope.launch { runCatching { repository.collectImages() } }
    }
}
