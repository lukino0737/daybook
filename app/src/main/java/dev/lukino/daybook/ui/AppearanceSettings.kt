package dev.lukino.daybook.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lukino.daybook.DaybookApplication
import dev.lukino.daybook.appearance.Appearance
import dev.lukino.daybook.appearance.AppearanceStore
import kotlinx.coroutines.launch

val LocalAppearance = staticCompositionLocalOf<Appearance?> { null }

@Composable fun AppearanceBackground(appearance: Appearance?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        appearance?.let {
            Image(remember(it.bitmap) { it.bitmap.asImageBitmap() }, null,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            // Keep ordinary foreground text legible even over an entirely black photograph.
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = .78f)))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun AppearanceSettingsScreen(
    store: AppearanceStore = (LocalContext.current.applicationContext as DaybookApplication).appearance,
    onClose: () -> Unit,
) {
    val state by store.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val close = { if (!state.busy) { store.cancel(); onClose() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(store::select) }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(modifier = Modifier.fillMaxSize().testTag("appearance-settings"),
            topBar = { TopAppBar(title = { Text("外观设置") }, navigationIcon = {
                IconButton(onClick = close, enabled = !state.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
            }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("选一张喜欢的图片，让背景与按钮换上它的颜色。")
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !state.busy, modifier = Modifier.testTag("choose-background")) {
                    Text(if (state.current == null && state.preview == null) "选择背景图片" else "更换背景图片")
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (state.preview == null) "当前外观" else "预览 · 尚未应用", style = MaterialTheme.typography.titleSmall)
                val preview = state.preview ?: state.current
                DaybookTheme(preview?.seed) {
                    OutlinedCard(Modifier.fillMaxWidth().testTag("appearance-preview")) {
                        Box(Modifier.fillMaxWidth().heightIn(min = 260.dp)) {
                            AppearanceBackground(preview, Modifier.matchParentSize())
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("2026年9月", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primary) { Text("13", Modifier.padding(12.dp)) }
                                    Text("把日子，记在一起。", color = MaterialTheme.colorScheme.onBackground)
                                }
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp)) { Text("想到什么，就记下来。"); Text("便签 · 无需日期", style = MaterialTheme.typography.labelMedium) }
                                }
                                Button(onClick = {}, modifier = Modifier.clearAndSetSemantics { contentDescription = "主题色示例" }) { Text("主题色预览") }
                            }
                        }
                    }
                }
                Text("四个主页使用同一背景；编辑和设置页面保留清晰底色。图片与配色仅保存在本机，不包含在记录备份中。", style = MaterialTheme.typography.bodySmall)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("appearance-error")) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { scope.launch { store.apply().join(); if (store.state.value.error == null) onClose() } },
                        enabled = state.preview != null && !state.busy, modifier = Modifier.testTag("apply-appearance")) { Text("应用") }
                    TextButton(onClick = { store.reset() }, enabled = !state.busy && (state.current != null || state.preview != null), modifier = Modifier.testTag("reset-appearance")) { Text("恢复默认") }
                }
            }
        }
    }
}
