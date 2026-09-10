package dev.lukino.daybook.data

object ReviewRules {
    fun parseTags(text: String): List<String> = text.split(',', '，', '\n', '\r').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    fun filter(entries: List<Entry>, query: String, tag: String, allKinds: Boolean): List<Entry> {
        val search = query.trim()
        return entries.filter { entry ->
            (allKinds || entry.kind == EntryKind.NOTE) && (tag.isEmpty() || tag in entry.tags) &&
                (search.isEmpty() || entry.title.contains(search, ignoreCase = true) || entry.note.contains(search, ignoreCase = true))
        }.sortedWith(compareByDescending<Entry> { it.date ?: "" }.thenByDescending { it.time ?: "" }
            .thenByDescending { it.createdAt }.thenBy { it.id })
    }
}
