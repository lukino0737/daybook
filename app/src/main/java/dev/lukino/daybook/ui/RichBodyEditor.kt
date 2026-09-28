package dev.lukino.daybook.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.lukino.daybook.data.*
import dev.lukino.daybook.media.BodyImageStore
import kotlinx.coroutines.*
import java.util.UUID

@Composable fun RichBodyEditor(
    text: String, blocks: List<BodyBlock>, enabled: Boolean, tag: String, label: String,
    controller: ImageEditorController?, autoFocus: Boolean = false, onChange: (String, List<BodyBlock>) -> Unit,
) {
    val change by rememberUpdatedState(onChange)
    val context = androidx.compose.ui.platform.LocalContext.current
    val editor = remember(context) { InlineBodyEditText(context) }
    var selectionStart by rememberSaveable { mutableIntStateOf(0) }
    var selectionEnd by rememberSaveable { mutableIntStateOf(0) }
    var selectedStart by rememberSaveable { mutableIntStateOf(0) }
    var selectedEnd by rememberSaveable { mutableIntStateOf(0) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedImage by remember { mutableStateOf<Int?>(null) }
    var removeImage by remember { mutableStateOf<Int?>(null) }
    var viewing by rememberSaveable { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val importing by controller?.busy?.collectAsState() ?: remember { mutableStateOf(false) }
    val active = enabled && !importing
    val images = RichBody.images(blocks)
    val thumbnails by produceState<Map<String, android.graphics.Bitmap>>(emptyMap(), images, controller?.store) {
        value = withContext(Dispatchers.IO) {
            images.distinctBy { it.hash }.mapNotNull { image ->
                runCatching {
                    val file = controller?.store?.file(image) ?: return@runCatching null
                    val options = BitmapFactory.Options().apply {
                        var sample = 1
                        while (maxOf(image.width, image.height) / sample > 1024) sample *= 2
                        inSampleSize = sample
                    }
                    BitmapFactory.decodeFile(file.path, options)?.let { image.hash to it }
                }.getOrNull()
            }.toMap()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && controller != null) scope.launch {
            val owner = "import-${UUID.randomUUID()}"
            error = null
            try {
                val image = controller.import(uri, owner)
                withContext(Dispatchers.Main.immediate) { editor.insertImage(image, selectedStart, selectedEnd) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = if (e is IllegalArgumentException) e.message else "图片添加失败，请检查可用空间后重试" }
            finally { controller.finished(owner) }
        }
    }
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val fontSize = MaterialTheme.typography.bodyLarge.fontSize.value
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Surface(shape = MaterialTheme.shapes.small, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()) {
            androidx.compose.ui.viewinterop.AndroidView(factory = { context ->
                editor.apply {
                    this.tag = tag
                    hint = "写下正文…"
                    setBody(text, blocks)
                    setSelection(selectionStart.coerceIn(0, length()), selectionEnd.coerceIn(0, length()))
                    onBodyChange = { value, body -> change(value, body) }
                    onImageClick = { selectedImage = it }
                    onSelection = { start, end -> selectionStart = start; selectionEnd = end }
                    if (autoFocus) post {
                        requestFocus()
                        (context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                            .showSoftInput(this, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }, update = {
                it.isEnabled = active
                it.setTextColor(color); it.setHintTextColor(hintColor); it.textSize = fontSize
                it.setBody(text, blocks); it.setThumbnails(thumbnails)
            }, modifier = Modifier.fillMaxWidth().testTag(tag).semantics {
                editableText = androidx.compose.ui.text.AnnotatedString(editor.text.toString())
                textSelectionRange = TextRange(selectionStart, selectionEnd)
                customActions = images.indices.flatMap { ordinal ->
                    listOf(CustomAccessibilityAction("查看图片 ${ordinal + 1}") { viewing = ordinal; true }) +
                        if (active) listOf(CustomAccessibilityAction("移除图片 ${ordinal + 1}") { removeImage = ordinal; true }) else emptyList()
                }
                setText { value ->
                    if (!active) false else { editor.setText(value.text); true }
                }
                insertTextAtCursor { value ->
                    if (!active) false else {
                        editor.let { view ->
                            val start = minOf(view.selectionStart, view.selectionEnd).coerceAtLeast(0)
                            val end = maxOf(view.selectionStart, view.selectionEnd).coerceAtLeast(start)
                            view.text.replace(start, end, value.text)
                            view.setSelection((start + value.length).coerceAtMost(view.length()))
                        }; true
                    }
                }
                setSelection { start, end, _ ->
                    editor.let { it.setSelection(start.coerceIn(0, it.length()), end.coerceIn(0, it.length())) }; true
                }
                requestFocus { editor.requestFocus() }
            })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(enabled = active && controller?.store != null && images.size < 9, onClick = {
                selectedStart = editor.selectionStart.coerceAtLeast(0)
                selectedEnd = editor.selectionEnd.coerceAtLeast(0)
                picker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
            }, modifier = Modifier.testTag("insert-image")) { Text(if (importing) "处理图片…" else "插入图片") }
            Text("${images.size}/9", style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("image-error")) }
    }
    selectedImage?.let { ordinal ->
        if (ordinal in images.indices) AlertDialog(onDismissRequest = { selectedImage = null }, title = { Text("图片 ${ordinal + 1}") },
            confirmButton = { TextButton(onClick = { viewing = ordinal; selectedImage = null }) { Text("查看大图") } },
            dismissButton = { TextButton(enabled = active, onClick = { removeImage = ordinal; selectedImage = null }) { Text("移除图片") } })
    }
    removeImage?.let { ordinal ->
        AlertDialog(onDismissRequest = { removeImage = null }, title = { Text("移除这张图片？") },
            text = { Text("前后文字会保留，相册原图不受影响。") },
            confirmButton = { TextButton(enabled = active, modifier = Modifier.testTag("confirm-remove-image"), onClick = {
                editor.removeImage(ordinal); removeImage = null
            }) { Text("移除") } }, dismissButton = { TextButton(onClick = { removeImage = null }) { Text("取消") } })
    }
    viewing?.let { ordinal ->
        if (images.isNotEmpty()) ImageViewer(images, ordinal.coerceIn(images.indices), controller?.store) { viewing = null }
    }
}

/** Read-only body using the same image decoding and zoom viewer as the editor. */
@Composable fun RichBodyPreview(text: String, blocks: List<BodyBlock>, store: BodyImageStore?) {
    val body = RichBody.effective(text, blocks)
    val images = RichBody.images(blocks)
    var viewing by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        body.forEachIndexed { index, block ->
            if (block.image == null) {
                if (block.text.isNotBlank()) Text(block.text, style = MaterialTheme.typography.bodyLarge)
            } else {
                val ordinal = body.take(index).count { it.image != null }
                BodyImageView(block.image, store, Modifier.fillMaxWidth().height(240.dp)
                    .testTag("detail-image-$ordinal").clickable { viewing = ordinal }, 1080)
            }
        }
    }
    viewing?.let { index ->
        if (images.isNotEmpty()) ImageViewer(images, index.coerceIn(images.indices), store) { viewing = null }
    }
}

@Composable private fun BodyImageView(image: BodyImage, store: BodyImageStore?, modifier: Modifier, maxEdge: Int) {
    val bitmap by produceState<ImageBitmap?>(null, image.hash, store, maxEdge) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = requireNotNull(store).file(image)
                var sample = 1
                while (maxOf(image.width, image.height) / sample > maxEdge) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    if (bitmap == null) Box(modifier, contentAlignment = Alignment.Center) { Text("图片加载中或不可用") }
    else Image(bitmap!!, contentDescription = "正文图片", modifier = modifier, contentScale = ContentScale.Fit)
}

@Composable private fun ImageViewer(images: List<BodyImage>, initial: Int, store: BodyImageStore?, dismiss: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(initial) }
    var scale by remember(index) { mutableFloatStateOf(1f) }
    var offset by remember(index) { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else (offset + pan).let { Offset(it.x.coerceIn(-2000f, 2000f), it.y.coerceIn(-2000f, 2000f)) }
    }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("image-viewer")) {
            Column(Modifier.safeDrawingPadding().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("图片 ${index + 1}/${images.size}")
                    TextButton(onClick = dismiss, modifier = Modifier.testTag("close-image-viewer")) { Text("关闭") }
                }
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().transformable(transform), contentAlignment = Alignment.Center) {
                    BodyImageView(images[index], store, Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y), 2560)
                }
                Text("双指缩放和移动", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = index > 0, onClick = { index-- }) { Text("上一张") }
                    TextButton(onClick = { scale = 1f; offset = Offset.Zero }) { Text("还原大小") }
                    TextButton(enabled = index < images.lastIndex, onClick = { index++ }) { Text("下一张") }
                }
            }
        }
    }
}
