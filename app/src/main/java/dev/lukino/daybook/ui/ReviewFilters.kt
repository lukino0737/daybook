package dev.lukino.daybook.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.lukino.daybook.data.ReviewRules

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ReviewFilters(query: String, tag: String, allKinds: Boolean, tags: List<String>,
    onQuery: (String) -> Unit, onTag: (String) -> Unit, onAllKinds: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("把写下的日子，再翻一翻。", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = query, onValueChange = onQuery, singleLine = true,
            label = { Text("搜索标题和正文") }, modifier = Modifier.fillMaxWidth().testTag("review-query"),
            trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { onQuery("") }) { Text("清除") } })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !allKinds, onClick = { onAllKinds(false) }, label = { Text("生活记录") })
            FilterChip(selected = allKinds, onClick = { onAllKinds(true) }, label = { Text("全部类型") })
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = tag.isEmpty(), onClick = { onTag("") }, label = { Text("全部标签") }) }
            items(tags, key = { it }) { value -> FilterChip(selected = value == tag, onClick = { onTag(value) },
                label = { Text(value) }, modifier = Modifier.testTag("filter-tag-$value")) }
        }
        if (tag.isNotEmpty()) TextButton(onClick = { onTag("") }) { Text("正在查看：$tag · 清除筛选") }
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
