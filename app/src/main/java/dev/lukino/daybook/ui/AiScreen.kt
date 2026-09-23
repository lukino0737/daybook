package dev.lukino.daybook.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.lukino.daybook.ai.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun AiScreen(session: AiSession, onClose: () -> Unit) {
    val state by session.state.collectAsStateWithLifecycle()
    var settings by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(topBar = { TopAppBar(title = { Text("AI助手") }, navigationIcon = {
                TextButton(onClick = onClose) { Text("返回") }
            }, actions = {
                TextButton(onClick = { clear = true }, modifier = Modifier.testTag("ai-clear")) { Text("新对话") }
                TextButton(onClick = { settings = true }, enabled = !state.busy) { Text("AI设置") }
            }) }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AiProtocol.models.forEach { model -> FilterChip(selected = state.config.model == model, enabled = !state.busy,
                            onClick = { session.select(model, state.config.thinking) }, label = { Text(model) }) }
                        FilterChip(selected = state.config.thinking, enabled = !state.busy,
                            onClick = { session.select(state.config.model, !state.config.thinking) }, label = { Text("深度思考") })
                    }
                    Text("对话仅在本次应用会话保留；已保存的事项不受影响。", style = MaterialTheme.typography.labelSmall)
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("ai-messages"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.lines.isEmpty()) item { Text("可以从一句话开始。请先在AI设置中配置你的DeepSeek API Key。") }
                        itemsIndexed(state.lines) { _, line ->
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor =
                                if (line.role == "user") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (line.role == "user") "你" else "Daybook", style = MaterialTheme.typography.labelMedium)
                                    SelectionContainer { Text(line.text) }
                                }
                            }
                        }
                    }
                    state.status?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("ai-error")) }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    OutlinedTextField(value = state.input, onValueChange = session::input, enabled = !state.busy,
                        label = { Text("输入消息") }, maxLines = 5, modifier = Modifier.fillMaxWidth().testTag("ai-input"))
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                        if (state.busy) OutlinedButton(onClick = session::stop, modifier = Modifier.testTag("ai-stop")) { Text("停止") }
                        else Button(onClick = session::send, enabled = state.input.isNotBlank(), modifier = Modifier.testTag("ai-send")) { Text("发送") }
                    }
                }
            }
        }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("开始新对话？") },
        text = { Text("将清空本次聊天和未保存的AI内容，已保存的事项不会被删除。") },
        confirmButton = { TextButton(onClick = { session.clear(); clear = false }) { Text("清空并开始") } },
        dismissButton = { TextButton(onClick = { clear = false }) { Text("取消") } })
    if (settings) AiSettingsDialog(session) { settings = false }
}

@Composable private fun AiSettingsDialog(session: AiSession, onClose: () -> Unit) {
    val state by session.state.collectAsStateWithLifecycle()
    var key by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(state.config.configured) }
    var model by remember { mutableStateOf(state.config.model) }
    var thinking by remember { mutableStateOf(state.config.thinking) }
    AlertDialog(onDismissRequest = onClose, title = { Text("DeepSeek 设置") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("消息会发送至DeepSeek并使用你的API额度。密钥加密保存在本机，不进入Daybook备份。")
            OutlinedTextField(value = key, onValueChange = { key = it.take(512) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), label = { Text(if (state.config.configured) "新API Key（留空保留）" else "API Key") },
                modifier = Modifier.testTag("ai-key"))
            AiProtocol.models.forEach { value -> FilterChip(selected = model == value, onClick = { model = value }, label = { Text(value) }) }
            Row { Checkbox(checked = thinking, onCheckedChange = { thinking = it }); Text("默认深度思考") }
            Row { Checkbox(checked = consent, onCheckedChange = { consent = it }); Text("我同意将本次输入发送至DeepSeek") }
            if (state.config.configured) {
                TextButton(onClick = session::checkConnection, enabled = !state.busy) { Text("检查连接（获取模型列表）") }
                TextButton(onClick = { session.deleteKey(); key = ""; consent = false }, enabled = !state.busy) { Text("删除本机Key") }
            }
            state.status?.let { Text(it) }; state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = consent && !state.busy && (state.config.configured || key.isNotBlank()), onClick = {
        if (session.saveSettings(key.takeIf { it.isNotBlank() }, model, thinking)) { key = ""; onClose() }
    }) { Text("保存设置") } }, dismissButton = { TextButton(onClick = { key = ""; onClose() }) { Text("返回") } })
}
