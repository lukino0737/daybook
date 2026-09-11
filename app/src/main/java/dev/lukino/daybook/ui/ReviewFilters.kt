package dev.lukino.daybook.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.lukino.daybook.data.*
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ReviewFilters(selection: ReviewSelection, tags: List<String>, error: String?,
    onChange: (ReviewSelection) -> Unit, onApply: () -> Unit) {
    val context = LocalContext.current
    fun pick(initial: String?, update: (String) -> Unit) {
        val date = initial?.let(LocalDate::parse) ?: LocalDate.now()
        DatePickerDialog(context, { _, y, m, d -> update(LocalDate.of(y, m + 1, d).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("把写下的日子，再翻一翻。", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = selection.query, onValueChange = { onChange(selection.copy(query = it)) }, singleLine = true,
            label = { Text("搜索标题和正文") }, modifier = Modifier.fillMaxWidth().testTag("review-query"),
            trailingIcon = { if (selection.query.isNotEmpty()) TextButton(onClick = { onChange(selection.copy(query = "")) }) { Text("清除") } })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf(null) + EntryKind.entries).forEach { kind ->
                FilterChip(selected = selection.kind == kind, onClick = { onChange(selection.copy(kind = kind)) },
                    modifier = Modifier.testTag("filter-kind-${kind?.name ?: "ALL"}"), label = { Text(kind?.label ?: "全部类型") })
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = selection.tag.isEmpty(), onClick = { onChange(selection.copy(tag = "")) }, label = { Text("全部标签") }) }
            items(tags, key = { it }) { tag -> FilterChip(selected = selection.tag == tag, onClick = { onChange(selection.copy(tag = tag)) },
                modifier = Modifier.testTag("filter-tag-$tag"), label = { Text(tag) }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selection.allTime, onClick = { onChange(selection.copy(allTime = true)) }, label = { Text("全时间段") })
            FilterChip(selected = !selection.allTime, onClick = { onChange(selection.copy(allTime = false, end = LocalDate.now().toString())) }, label = { Text("自定义时间") })
        }
        if (!selection.allTime) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pick(selection.start) { onChange(selection.copy(start = it)) } }) { Text(selection.start ?: "开始日期") }
            OutlinedButton(onClick = { pick(selection.end) { onChange(selection.copy(end = it)) } }) { Text(selection.end) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onApply, modifier = Modifier.testTag("apply-review")) { Text("筛选") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun TagRow(tags: List<String>, enabled: Boolean, onTag: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { tag -> SuggestionChip(onClick = { onTag(tag) }, enabled = enabled, label = { Text(tag) }) }
    }
}

@Composable fun TagEditor(draft: Draft, busy: Boolean, knownTags: List<String>, onChange: (Draft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(value = draft.tagsText, onValueChange = { if (it.length <= 1000) onChange(draft.copy(tagsText = it)) },
            label = { Text("标签 · 用逗号分隔") }, supportingText = { Text("例如：恋爱，旅行。最多 10 个，每个不超过 30 字。") },
            modifier = Modifier.fillMaxWidth().testTag("tags"), enabled = !busy, maxLines = 4)
        val selected = ReviewRules.parseTags(draft.tagsText)
        val suggestions = knownTags.filterNot { it in selected }
        if (suggestions.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(suggestions, key = { it }) { tag -> SuggestionChip(enabled = !busy && selected.size < 10,
                onClick = { onChange(draft.copy(tagsText = (selected + tag).joinToString("，"))) }, label = { Text("+ $tag") }) }
        }
    }
}
