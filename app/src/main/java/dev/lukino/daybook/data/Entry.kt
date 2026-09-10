package dev.lukino.daybook.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@Serializable
enum class EntryKind(val label: String, val symbol: String) {
    EVENT("安排", "○"), TASK("任务", "□"), NOTE("记录", "◇")
}

/** Dates are ISO local calendar dates, never UTC-midnight timestamps. */
@Serializable
@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val kind: EntryKind = EntryKind.EVENT,
    val title: String,
    val note: String = "",
    val date: String? = null,
    val time: String? = null,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
) {
    fun validate() {
        require(UUID.fromString(id).toString() == id) { "记录 ID 无效" }
        require(title.isNotBlank() && title.length <= 300) { "标题需为 1–300 个字符" }
        require(note.length <= 20_000) { "备注不能超过 20000 个字符" }
        require(kind == EntryKind.TASK || date != null) { "安排和记录需要日期" }
        require(time == null || date != null) { "请先设置日期" }
        require(kind == EntryKind.TASK || !completed) { "只有任务可以标记完成" }
        date?.let { require(LocalDate.parse(it).toString() == it) { "日期格式无效" } }
        time?.let { require(it.matches(Regex("\\d{2}:\\d{2}"))); LocalTime.parse(it) }
        require(createdAt >= 0 && updatedAt >= createdAt) { "记录时间无效" }
    }
}

object EntryRules {
    fun isOverdue(entry: Entry, now: LocalDateTime): Boolean {
        if (entry.kind != EntryKind.TASK || entry.completed || entry.date == null) return false
        val date = LocalDate.parse(entry.date)
        return if (entry.time == null) date < now.toLocalDate()
        else !now.isBefore(date.atTime(LocalTime.parse(entry.time)))
    }

    fun upcoming(entries: List<Entry>, now: LocalDateTime): List<Entry> = entries.filter {
        it.kind == EntryKind.TASK && !it.completed && it.date != null &&
            LocalDate.parse(it.date) <= now.toLocalDate().plusDays(6)
    }.sortedWith(compareBy<Entry> { it.date }.thenBy { it.time ?: "23:59" }.thenBy { it.createdAt })

    fun forDay(entries: List<Entry>, date: LocalDate): List<Entry> = entries.filter { it.date == date.toString() }
        .sortedWith(compareBy<Entry> { it.completed }.thenBy { it.time ?: "24:00" }.thenBy { it.createdAt })
}
