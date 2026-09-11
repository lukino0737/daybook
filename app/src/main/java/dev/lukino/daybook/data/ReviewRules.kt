package dev.lukino.daybook.data

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class ReviewSelection(
    val kind: EntryKind? = null,
    val query: String = "",
    val tag: String = "",
    val allTime: Boolean = true,
    val start: String? = null,
    val end: String = LocalDate.now().toString(),
) {
    fun isUnrestricted() = kind == null && query.isBlank() && tag.isEmpty() && allTime
    fun validate() {
        if (!allTime) {
            require(start != null) { "请选择开始日期" }
            val from = LocalDate.parse(start)
            val to = LocalDate.parse(end)
            require(from <= to) { "开始日期不能晚于结束日期" }
        }
    }
}

object ReviewRules {
    fun parseTags(text: String): List<String> = text.split(',', '，', '\n', '\r').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    fun filter(entries: List<Entry>, selection: ReviewSelection): List<Entry> {
        selection.validate()
        val search = selection.query.trim()
        return entries.filter { entry ->
            (selection.kind == null || entry.kind == selection.kind) && (selection.tag.isEmpty() || selection.tag in entry.tags) &&
                (search.isEmpty() || entry.title.contains(search, ignoreCase = true) || entry.note.contains(search, ignoreCase = true)) &&
                (selection.allTime || (entry.date != null && entry.date >= selection.start!! && entry.date <= selection.end))
        }.sortedWith(compareByDescending<Entry> { it.date ?: "" }.thenByDescending { it.time ?: "" }
            .thenByDescending { it.createdAt }.thenBy { it.id })
    }
}
