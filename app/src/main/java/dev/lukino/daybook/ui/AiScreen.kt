package dev.lukino.daybook.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import dev.lukino.daybook.data.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun AiScreen(session: AiSession, memos: List<Memo> = emptyList(), onSource: (AiSource) -> Unit = {}, onClose: () -> Unit) {
    val state by session.state.collectAsStateWithLifecycle()
    var settings by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    var pickMemo by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AiDraft?>(null) }
    var confirmSave by remember { mutableStateOf<Boolean?>(null) }
    var confirmReads by remember { mutableStateOf(false) }
    val messages = rememberLazyListState()
    LaunchedEffect(state.lines.size, state.drafts.size) {
        if (messages.layoutInfo.totalItemsCount > 0) messages.animateScrollToItem(messages.layoutInfo.totalItemsCount - 1)
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(topBar = { TopAppBar(title = { Text("AI助手") }, navigationIcon = {
                TextButton(onClick = onClose) { Text("返回") }
            }, actions = {
                TextButton(onClick = { clear = true }, enabled = !state.saving, modifier = Modifier.testTag("ai-clear")) { Text("新对话") }
                TextButton(onClick = { settings = true }, enabled = !state.busy) { Text("AI设置") }
            }) }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AiProtocol.models.forEach { model -> FilterChip(selected = state.config.model == model, enabled = !state.busy,
                            onClick = { session.select(model, state.config.thinking) }, label = { Text(model) }) }
                        FilterChip(selected = state.config.thinking, enabled = !state.busy,
                            onClick = { session.select(state.config.model, !state.config.thinking) }, label = { Text("深度思考") })
                        FilterChip(selected = state.readsEnabled, enabled = !state.busy,
                            onClick = { confirmReads = true }, label = { Text(if (state.readsEnabled) "按需读取" else "仅聊天") })
                    }
                    Text("对话仅在本次应用会话保留；已保存的事项不受影响。", style = MaterialTheme.typography.labelSmall)
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("ai-messages"), state = messages, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("帮我记" to "请帮我记录：", "拆解任务" to "请把下面的目标拆成可执行的普通任务，不设截止日期：", "整理文字" to "请将下面的文字整理成便签，保留原意，并提取其中明确的待办：",
                                    "查找内容" to "请查找我的记录：", "阶段回顾" to "请回顾我本周的日程和任务，说明日期依据与未完成事项，并生成便签草稿。").forEach { (title, text) ->
                                    SuggestionChip(enabled = !state.busy, onClick = { session.input(text) }, label = { Text(title) })
                                }
                                SuggestionChip(enabled = !state.busy, onClick = { pickMemo = true }, label = { Text("选择便签") })
                            }
                        }
                        if (state.lines.isEmpty()) item { Text(if (state.config.configured) "可以聊天、记录想法，或把一个目标拆成任务。生成内容会先展示草稿。" else "请先在AI设置中配置你的DeepSeek API Key。") }
                        state.sourceMemo?.let { original -> item {
                            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                                Text("已选择便签 · 发送时将读取以下文字", style = MaterialTheme.typography.titleSmall)
                                SelectionContainer { Text(original.body, maxLines = 8) }
                                if (RichBody.images(original.blocks).isNotEmpty()) Text("原便签含图片，整理结果可另存为文本，原图文保留。")
                                TextButton(onClick = { session.source(null) }, enabled = !state.busy) { Text("取消选择并清除草稿") }
                            } }
                        } }
                        itemsIndexed(state.lines) { _, line ->
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor =
                                if (line.role == "user") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(if (line.role == "user") "你" else "Daybook", style = MaterialTheme.typography.labelMedium)
                                    SelectionContainer { Text(line.text) }
                                    line.scope?.let { Text("本次读取：$it", style = MaterialTheme.typography.labelSmall) }
                                    if (line.scope != null && line.sources.isEmpty()) Text("所查范围没有匹配记录。", style = MaterialTheme.typography.labelSmall)
                                    line.sources.forEach { source ->
                                        TextButton(onClick = { onSource(source) }, enabled = !state.busy, modifier = Modifier.testTag("ai-source-${source.key}")) {
                                            Text("来源 · ${source.title}", maxLines = 2)
                                        }
                                    }
                                }
                            }
                        }
                        if (state.drafts.isNotEmpty()) item { Text("待确认草稿 · ${state.drafts.size} 条", style = MaterialTheme.typography.titleMedium) }
                        items(state.drafts, key = { "draft-${it.id}" }) { draft ->
                            OutlinedCard(Modifier.fillMaxWidth().testTag("ai-draft-${draft.id}")) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row {
                                        Checkbox(checked = draft.id in state.selected, enabled = !state.busy, onCheckedChange = { session.selectDraft(draft.id, it) })
                                        Text("${when (draft.kind) { "TASK" -> "任务"; "EVENT" -> "日程"; "NOTE" -> "记录"; else -> "便签" }} · ${draft.title.ifBlank { "整理后的文字" }}", Modifier.weight(1f))
                                    }
                                    if (draft.body.isNotEmpty()) SelectionContainer { Text(draft.body) }
                                    Text("${draft.date ?: if (draft.kind == "TASK") "不设截止日期" else "未设置日期"}${draft.time?.let { " $it" }.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                                    draft.reminderAt?.let { Text("提醒 · ${it.replace('T', ' ')}") }
                                    if (draft.tags.isNotEmpty()) Text("标签 · ${draft.tags.joinToString("，")}")
                                    draft.validationError()?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                    Row {
                                        TextButton(enabled = !state.busy, onClick = { editing = draft }) { Text("编辑草稿") }
                                        TextButton(enabled = !state.busy, onClick = { session.removeDraft(draft.id) }) { Text("移除") }
                                    }
                                }
                            }
                        }
                        if (state.drafts.isNotEmpty()) item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { confirmSave = false }, enabled = !state.busy && state.selected.isNotEmpty(), modifier = Modifier.testTag("ai-save-preview")) { Text("保存选中的 ${state.selected.size} 条") }
                                if (state.sourceMemo != null && RichBody.images(state.sourceMemo!!.blocks).isEmpty()) OutlinedButton(
                                    enabled = !state.busy && state.selected.size == 1 && state.drafts.any { it.id in state.selected && it.kind == "MEMO" },
                                    onClick = { confirmSave = true }) { Text("将选中便签替换原文") }
                                Text("也可以继续输入要求调整草稿，例如：第二条改到周五。", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    state.status?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("ai-error")) }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    OutlinedTextField(value = state.input, onValueChange = session::input, enabled = !state.busy,
                        label = { Text("输入消息") }, maxLines = 5, modifier = Modifier.fillMaxWidth().testTag("ai-input"))
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                        if (state.busy) OutlinedButton(onClick = session::stop, enabled = !state.saving, modifier = Modifier.testTag("ai-stop")) { Text(if (state.saving) "正在保存" else "停止") }
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
    if (confirmReads) AlertDialog(onDismissRequest = { confirmReads = false }, title = { Text(if (state.readsEnabled) "改为仅聊天？" else "允许按需读取？") },
        text = { Text("切换会清除当前对话、来源和未保存草稿。开启后，仅在你询问个人事项时查询相关记录；读取的文字会发送到DeepSeek，并展示来源。") },
        confirmButton = { TextButton(onClick = { session.reads(!state.readsEnabled); confirmReads = false }) { Text("确认切换") } },
        dismissButton = { TextButton(onClick = { confirmReads = false }) { Text("取消") } })
    if (pickMemo) AlertDialog(onDismissRequest = { pickMemo = false }, title = { Text("选择要整理的便签") }, text = {
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            item { Text("选择后会展示原文；发送消息时才交给DeepSeek。") }
            if (memos.isEmpty()) item { Text("暂无便签，可以直接粘贴文字。") }
            items(memos) { memo -> TextButton(onClick = { session.source(memo); pickMemo = false }) { Text(memo.summary, maxLines = 2) } }
        }
    }, confirmButton = { TextButton(onClick = { pickMemo = false }) { Text("返回") } })
    editing?.let { draft -> AiDraftEditor(draft, { session.editDraft(it); editing = null }, { editing = null }) }
    confirmSave?.let { replace -> AlertDialog(onDismissRequest = { confirmSave = null }, title = { Text(if (replace) "确认替换原便签正文？" else "确认保存草稿？") },
        text = { Text(if (replace) "仅替换正文，原便签日期和提醒保持不变。若原文已经变化，本次替换会被拒绝。" else "将创建 ${state.selected.size} 条新内容。请确认预览中的日期、时间和提醒。") },
        confirmButton = { TextButton(onClick = { confirmSave = null; session.saveDrafts(replace) }, modifier = Modifier.testTag("ai-confirm-save")) { Text("确认保存") } },
        dismissButton = { TextButton(onClick = { confirmSave = null }) { Text("返回检查") } }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun AiDraftEditor(initial: AiDraft, onSave: (AiDraft) -> Unit, onClose: () -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    var tags by remember { mutableStateOf(initial.tags.joinToString("，")) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onClose, title = { Text("编辑AI草稿") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow { listOf("TASK" to "任务", "EVENT" to "日程", "NOTE" to "记录", "MEMO" to "便签").forEach { (kind, name) ->
                FilterChip(selected = draft.kind == kind, onClick = { draft = draft.copy(kind = kind) }, label = { Text(name) })
            } }
            if (draft.kind != "MEMO") OutlinedTextField(draft.title, { draft = draft.copy(title = it.take(300)) }, label = { Text("标题") })
            OutlinedTextField(draft.body, { draft = draft.copy(body = it.take(20_000)) }, label = { Text("正文") }, maxLines = 8)
            OutlinedTextField(draft.date.orEmpty(), { draft = draft.copy(date = it.ifBlank { null }) }, label = { Text("日期 YYYY-MM-DD（可留空）") }, singleLine = true)
            if (draft.kind != "MEMO") {
                OutlinedTextField(draft.time.orEmpty(), { draft = draft.copy(time = it.ifBlank { null }) }, label = { Text("时间 HH:mm（可留空）") }, singleLine = true)
                OutlinedTextField(tags, { tags = it }, label = { Text("标签，用逗号分隔") })
            }
            if (draft.kind != "NOTE") OutlinedTextField(draft.reminderAt.orEmpty(), { draft = draft.copy(reminderAt = it.ifBlank { null }) }, label = { Text("提醒 YYYY-MM-DDTHH:mm（可留空）") })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        try {
            val value = draft.copy(tags = if (draft.kind == "MEMO") emptyList() else ReviewRules.parseTags(tags),
                time = draft.time.takeUnless { draft.kind == "MEMO" }, reminderAt = draft.reminderAt.takeUnless { draft.kind == "NOTE" })
            val invalid = value.validationError()
            if (invalid != null) error = invalid else onSave(value)
        } catch (_: Exception) { error = "请检查草稿字段" }
    }) { Text("应用修改") } }, dismissButton = { TextButton(onClick = onClose) { Text("取消") } })
}

@Composable private fun AiSettingsDialog(session: AiSession, onClose: () -> Unit) {
    val state by session.state.collectAsStateWithLifecycle()
    var key by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(state.config.configured) }
    var model by remember { mutableStateOf(state.config.model) }
    var thinking by remember { mutableStateOf(state.config.thinking) }
    AlertDialog(onDismissRequest = onClose, title = { Text("DeepSeek 设置") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("消息和你询问个人事项时按需读取的相关记录会发送至DeepSeek，并使用你的API额度。读取范围会显示，可切换为仅聊天。密钥加密保存在本机，不进入Daybook备份。")
            OutlinedTextField(value = key, onValueChange = { key = it.take(512) }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), label = { Text(if (state.config.configured) "新API Key（留空保留）" else "API Key") },
                modifier = Modifier.testTag("ai-key"))
            AiProtocol.models.forEach { value -> FilterChip(selected = model == value, onClick = { model = value }, label = { Text(value) }) }
            Row { Checkbox(checked = thinking, onCheckedChange = { thinking = it }); Text("默认深度思考") }
            Row { Checkbox(checked = consent, onCheckedChange = { consent = it }); Text("我同意发送消息及按需读取的相关记录") }
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
