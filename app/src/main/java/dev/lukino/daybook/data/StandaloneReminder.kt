package dev.lukino.daybook.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@Serializable
enum class RepeatKind(val label: String) { ONCE("单次"), DAILY("每天"), WEEKLY("每周"), MONTHLY("每月"), YEARLY("每年"), INTERVAL("每隔 N 天") }

@Serializable
@Entity(tableName = "reminders")
data class StandaloneReminder(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val note: String = "",
    val startDate: String,
    val time: String,
    val repeat: RepeatKind = RepeatKind.ONCE,
    // ISO weekdays, Monday = bit 0. Only WEEKLY uses this mask.
    val weekdays: Int = 0,
    val intervalDays: Int = 1,
    val enabled: Boolean = true,
    val showInCalendar: Boolean = false,
    val effectiveFrom: Long = System.currentTimeMillis(),
    val revision: Long = 1,
    val deliveredFor: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
) {
    fun sameSchedule(other: StandaloneReminder) = startDate == other.startDate && time == other.time &&
        repeat == other.repeat && weekdays == other.weekdays && intervalDays == other.intervalDays

    fun validate() {
        require(UUID.fromString(id).toString() == id) { "提醒 ID 无效" }
        require(title.isNotBlank() && title.length <= 300 && note.length <= 20_000) { "标题需为 1–300 字，备注最多 20000 字" }
        val date = LocalDate.parse(startDate)
        require(date.toString() == startDate && date.year in 1..9999) { "提醒日期无效" }
        require(time.matches(Regex("\\d{2}:\\d{2}")) && LocalTime.parse(time).second == 0) { "提醒时间需精确到分钟" }
        require(if (repeat == RepeatKind.WEEKLY) weekdays in 1..127 else weekdays == 0) { "请至少选择一个星期几" }
        require(if (repeat == RepeatKind.INTERVAL) intervalDays in 1..9999 else intervalDays == 1) { "间隔需为 1–9999 天" }
        require(effectiveFrom >= 0 && revision > 0 && createdAt >= 0 && updatedAt >= createdAt) { "提醒状态无效" }
        deliveredFor?.let {
            val value = LocalDateTime.parse(it)
            require(value.toString() == it && value.second == 0 && value.nano == 0 && value.toLocalTime() == LocalTime.parse(time)) { "提醒发送状态无效" }
            require(dev.lukino.daybook.reminder.RepeatRules.occursOn(this, value.toLocalDate())) { "提醒发送日期无效" }
        }
    }
}
