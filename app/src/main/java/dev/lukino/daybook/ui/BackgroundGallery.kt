package dev.lukino.daybook.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

internal enum class PhotoAccess { FULL, PARTIAL, DENIED }
// The supplied SDK is the runtime SDK; injectable for permission transition tests.
@SuppressLint("InlinedApi")
internal fun photoPermissions(sdk: Int): Array<String> = when {
    sdk >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    sdk >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}
@SuppressLint("InlinedApi")
internal fun photoAccess(sdk: Int, granted: (String) -> Boolean): PhotoAccess = when {
    granted(if (sdk >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE) -> PhotoAccess.FULL
    sdk >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> PhotoAccess.PARTIAL
    else -> PhotoAccess.DENIED
}
private fun Context.photoAccess() = photoAccess(Build.VERSION.SDK_INT) { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
private data class GalleryPhoto(val uri: Uri, val albumId: String, val album: String, val name: String)
private suspend fun loadPhotos(context: Context): List<GalleryPhoto> = withContext(Dispatchers.IO) {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val result = mutableListOf<GalleryPhoto>()
    context.contentResolver.query(collection, arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media.DISPLAY_NAME), null, null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC")?.use { cursor ->
        while (cursor.moveToNext()) result += GalleryPhoto(ContentUris.withAppendedId(collection, cursor.getLong(0)),
            cursor.getString(1) ?: "", cursor.getString(2) ?: "其他图片", cursor.getString(3) ?: "图片")
    }
    result
}
private suspend fun thumbnail(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    try {
        if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(uri, Size(240, 240), null)
        else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 480) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun BackgroundGallery(onSelect: (Uri) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var access by remember { mutableStateOf(context.photoAccess()) }
    var revision by remember { mutableIntStateOf(0) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var photos by remember { mutableStateOf(emptyList<GalleryPhoto>()) }
    var album by rememberSaveable { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = context.photoAccess(); revision++
    }
    val fallback = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(onSelect) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(onSelect) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            access = context.photoAccess(); revision++
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        if (!requested && access != PhotoAccess.FULL) {
            requested = true; permission.launch(photoPermissions(Build.VERSION.SDK_INT))
        }
    }
    LaunchedEffect(access, revision) {
        loading = true; photos = emptyList(); error = null
        try { if (access != PhotoAccess.DENIED) photos = loadPhotos(context) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "无法读取相册，请检查图片访问权限，或使用系统选图。" }
        finally { loading = false }
        if (album != null && photos.none { it.albumId == album }) album = null
    }
    fun launchSafely(action: () -> Unit) {
        try { action() } catch (_: Exception) { error = "此设备暂不支持该入口，请尝试其他选图方式。" }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(Modifier.fillMaxSize().testTag("background-gallery"), topBar = {
            TopAppBar(title = { Text("选择背景图片") }, navigationIcon = {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回相册前一页") }
            })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                Text(when (access) { PhotoAccess.FULL -> "全部图片"; PhotoAccess.PARTIAL -> "仅显示已授权图片"; PhotoAccess.DENIED -> "尚未获得相册访问权限" },
                    modifier = Modifier.testTag("photo-access"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (access != PhotoAccess.FULL) {
                        TextButton(onClick = { permission.launch(photoPermissions(Build.VERSION.SDK_INT)) }) { Text(if (access == PhotoAccess.PARTIAL) "管理可访问图片" else "允许访问相册") }
                        TextButton(onClick = { launchSafely { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) } }) { Text("权限设置") }
                    }
                    TextButton(onClick = { launchSafely { fallback.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }) { Text("系统选图") }
                    TextButton(onClick = { launchSafely { files.launch(arrayOf("image/*")) } }) { Text("从文件选择") }
                }
                if (access != PhotoAccess.DENIED) Box {
                    TextButton(onClick = { menu = true }) { Text(photos.firstOrNull { it.albumId == album }?.album ?: "所有相册") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("所有相册") }, onClick = { album = null; menu = false })
                        photos.distinctBy { it.albumId }.forEach { item ->
                            DropdownMenuItem(text = { Text(item.album) }, onClick = { album = item.albumId; menu = false })
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                else if (photos.isEmpty() && access != PhotoAccess.DENIED) Text("没有可显示的图片，可尝试系统选图或从文件选择。")
                LazyVerticalGrid(columns = GridCells.Adaptive(96.dp), modifier = Modifier.weight(1f).testTag("photo-grid"),
                    horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(photos.filter { album == null || it.albumId == album }, key = { it.uri.toString() }) { photo ->
                        val bitmap by produceState<Bitmap?>(null, photo.uri, revision) { value = thumbnail(context, photo.uri) }
                        Box(Modifier.aspectRatio(1f).clickable { onSelect(photo.uri) }) {
                            bitmap?.let { Image(it.asImageBitmap(), photo.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                ?: Text(photo.name, maxLines = 3)
                        }
                    }
                }
            }
        }
    }
}
