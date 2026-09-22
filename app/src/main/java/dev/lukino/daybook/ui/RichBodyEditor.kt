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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
    val current = RichBody.effective(text, blocks)
    val latest by rememberUpdatedState(current)
    val change by rememberUpdatedState(onChange)
    var focusIndex by rememberSaveable { mutableIntStateOf(0) }
    var cursor by rememberSaveable { mutableIntStateOf(0) }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedCursor by rememberSaveable { mutableIntStateOf(0) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var removeIndex by remember { mutableStateOf<Int?>(null) }
    var viewing by rememberSaveable { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val importing by controller?.busy?.collectAsState() ?: remember { mutableStateOf(false) }
    val active = enabled && !importing
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && controller != null) scope.launch {
            val owner = "import-${UUID.randomUUID()}"
            error = null
            try {
                val image = controller.import(uri, owner)
                val value = latest
                val index = selectedIndex.coerceIn(0, value.lastIndex)
                val inserted = RichBody.insert(RichBody.text(value), value, index, selectedCursor.coerceIn(0, value[index].text.length), image)
                change(RichBody.text(inserted), inserted)
                focusIndex = index + 2; cursor = 0
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = if (e is IllegalArgumentException) e.message else "图片添加失败，请检查可用空间后重试" }
            finally { controller.finished(owner) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        current.forEachIndexed { index, block ->
            if (block.image == null) {
                val requester = remember { FocusRequester() }
                LaunchedEffect(Unit) { if (autoFocus && index == 0) { requester.requestFocus(); keyboard?.show() } }
                var value by rememberSaveable(index, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(block.text)) }
                if (value.text != block.text) value = value.copy(text = block.text,
                    selection = TextRange(value.selection.start.coerceAtMost(block.text.length), value.selection.end.coerceAtMost(block.text.length)))
                OutlinedTextField(value = value, enabled = active, onValueChange = { updated ->
                    if (text.length - block.text.length + updated.text.length <= 20_000) {
                        value = updated; focusIndex = index; cursor = updated.selection.end
                        val changed = current.toMutableList().apply { set(index, BodyBlock(updated.text)) }
                        onChange(RichBody.text(changed), if (changed.size == 1) emptyList() else changed)
                    }
                }, placeholder = { Text(if (index == 0) "写下正文…" else "继续输入文字…") },
                    minLines = if (current.size == 1) 4 else 2,
                    modifier = Modifier.fillMaxWidth().focusRequester(requester).testTag(if (index == 0) tag else "$tag-$index")
                        .onFocusChanged { if (it.isFocused) { focusIndex = index; cursor = value.selection.end } })
            } else {
                val ordinal = index / 2 + 1
                Card(Modifier.fillMaxWidth()) {
                    BodyImageView(block.image, controller?.store, Modifier.fillMaxWidth().height(220.dp)
                        .testTag("body-image-$index").clickable(enabled = !importing) { viewing = ordinal - 1 }, 720)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("图片 $ordinal · 点击查看", style = MaterialTheme.typography.bodySmall)
                        TextButton(enabled = active, onClick = { removeIndex = index }, modifier = Modifier.testTag("remove-image-$index")) { Text("移除") }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(enabled = active && controller?.store != null && RichBody.images(blocks).size < 9, onClick = {
                selectedIndex = focusIndex.coerceIn(0, current.lastIndex)
                if (current[selectedIndex].image != null) selectedIndex = current.lastIndex
                selectedCursor = cursor.coerceIn(0, current[selectedIndex].text.length)
                picker.launch(arrayOf("image/jpeg", "image/png", "image/webp"))
            }, modifier = Modifier.testTag("insert-image")) { Text(if (importing) "处理图片…" else "插入图片") }
            Text("${RichBody.images(blocks).size}/9", style = MaterialTheme.typography.bodySmall)
        }
        Text("在文字光标处插图；每张原图最多20 MiB，保存压缩副本。", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("image-error")) }
    }
    removeIndex?.let { index ->
        AlertDialog(onDismissRequest = { removeIndex = null }, title = { Text("移除这张图片？") },
            text = { Text("前后文字会保留，相册原图不受影响。") },
            confirmButton = { TextButton(enabled = active, modifier = Modifier.testTag("confirm-remove-image"), onClick = {
                val removed = RichBody.remove(current, index)
                onChange(RichBody.text(removed), if (removed.size == 1) emptyList() else removed)
                focusIndex = (index - 1).coerceAtLeast(0); cursor = removed[focusIndex].text.length; removeIndex = null
            }) { Text("移除") } },
            dismissButton = { TextButton(onClick = { removeIndex = null }) { Text("取消") } })
    }
    viewing?.let { index ->
        val images = RichBody.images(blocks)
        if (images.isNotEmpty()) ImageViewer(images, index.coerceIn(images.indices), controller?.store) { viewing = null }
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
