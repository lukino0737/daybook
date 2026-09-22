package dev.lukino.daybook.backup

import dev.lukino.daybook.data.Entry
import dev.lukino.daybook.data.Memo
import dev.lukino.daybook.data.StandaloneReminder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class BackupArchive(val formatVersion: Int, val exportedAt: Long, val entries: List<Entry>, val memos: List<Memo> = emptyList(), val reminders: List<StandaloneReminder> = emptyList())

object BackupCodec {
    const val MAX_BYTES = 20 * 1024 * 1024
    const val MAX_ENTRIES = 10_000
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private val fields = setOf("id", "kind", "title", "note", "date", "time", "completed", "createdAt", "updatedAt")

    fun encode(entries: List<Entry>, exportedAt: Long = System.currentTimeMillis(), memos: List<Memo> = emptyList(), reminders: List<StandaloneReminder> = emptyList()): String {
        require((entries.flatMap { it.blocks } + memos.flatMap { it.blocks }).isEmpty()) { "图文内容需要完整图片备份" }
        validate(BackupArchive(5, exportedAt, entries, memos, reminders))
        return json.encodeToString(BackupArchive(5, exportedAt, entries, memos, reminders)).also {
            require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份超过 20 MB，请先减少长备注" }
        }
    }

    fun decode(text: String): BackupArchive {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份超过 20 MB" }
        val root = json.parseToJsonElement(text).jsonObject
        require(root["formatVersion"]?.jsonPrimitive?.intOrNull in 1..5) { "不支持此备份版本" }
        val entries = root["entries"]?.jsonArray ?: error("备份缺少记录列表")
        require(entries.size <= MAX_ENTRIES) { "备份最多支持 10000 条记录" }
        if (root["formatVersion"]?.jsonPrimitive?.intOrNull in 2..5) entries.forEach {
            require(it.jsonObject.keys.containsAll(setOf("reminderAt", "reminderDeliveredFor"))) { "备份提醒字段不完整" }
        }
        if (root["formatVersion"]?.jsonPrimitive?.intOrNull in 3..5) entries.forEach {
            require("tags" in it.jsonObject) { "备份标签字段不完整" }
        }
        entries.forEach { require(it.jsonObject.keys.containsAll(fields)) { "备份记录字段不完整" } }
        if (root["formatVersion"]?.jsonPrimitive?.intOrNull in 4..5) {
            val memos = root["memos"]?.jsonArray ?: error("备份缺少便签列表")
            memos.forEach { require(it.jsonObject.keys.containsAll(setOf("id", "body", "date", "reminderAt", "reminderDeliveredFor", "createdAt", "updatedAt"))) { "便签字段不完整" } }
        }
        if (root["formatVersion"]?.jsonPrimitive?.intOrNull == 5) {
            val reminders = root["reminders"]?.jsonArray ?: error("备份缺少独立提醒列表")
            val required = setOf("id", "title", "note", "startDate", "time", "repeat", "weekdays", "intervalDays", "enabled", "showInCalendar", "effectiveFrom", "revision", "deliveredFor", "createdAt", "updatedAt")
            reminders.forEach { require(it.jsonObject.keys.containsAll(required)) { "独立提醒字段不完整" } }
        }
        return json.decodeFromString<BackupArchive>(text).also(::validate)
    }

    fun validate(archive: BackupArchive) {
        require(archive.formatVersion in 1..5 && archive.exportedAt >= 0) { "备份信息无效" }
        require(archive.entries.size + archive.memos.size + archive.reminders.size <= MAX_ENTRIES) { "备份最多支持 10000 条记录" }
        require(archive.formatVersion >= 4 || archive.memos.isEmpty()) { "旧版备份不支持便签" }
        require(archive.formatVersion == 5 || archive.reminders.isEmpty()) { "旧版备份不支持独立提醒" }
        archive.reminders.forEach(StandaloneReminder::validate)
        require(archive.reminders.map { it.id }.toSet().size == archive.reminders.size) { "备份包含重复提醒 ID" }
        archive.memos.forEach(Memo::validate)
        require(archive.memos.map { it.id }.toSet().size == archive.memos.size) { "备份包含重复便签 ID" }
        archive.entries.forEach(Entry::validate)
        require(archive.entries.map { it.id }.toSet().size == archive.entries.size) { "备份包含重复 ID" }
    }
}
