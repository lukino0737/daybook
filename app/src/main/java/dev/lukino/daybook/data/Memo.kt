package dev.lukino.daybook.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

/** A separate notebook item, never an EntryKind or a calendar/task row. */
@Serializable
@Entity(tableName = "memos")
data class Memo(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val body: String = "",
    val date: String? = null,
    val reminderAt: String? = null,
    val reminderDeliveredFor: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
) {
    val summary: String get() = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(300).orEmpty()
    fun validate() {
        require(body.isNotBlank() && body.length <= 20_000) { "便签正文需为 1–20000 个字符" }
        // Reuse the existing strict ID, local date, minute precision and delivery-state validation.
        Entry(id = id, kind = EntryKind.TASK, title = summary, note = body, date = date,
            reminderAt = reminderAt, reminderDeliveredFor = reminderDeliveredFor,
            createdAt = createdAt, updatedAt = updatedAt).validate()
    }
}
