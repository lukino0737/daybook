package dev.lukino.daybook.backup

import dev.lukino.daybook.data.Entry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class BackupArchive(val formatVersion: Int, val exportedAt: Long, val entries: List<Entry>)

object BackupCodec {
    const val MAX_BYTES = 20 * 1024 * 1024
    const val MAX_ENTRIES = 10_000
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private val fields = setOf("id", "kind", "title", "note", "date", "time", "completed", "createdAt", "updatedAt")

    fun encode(entries: List<Entry>, exportedAt: Long = System.currentTimeMillis()): String {
        validate(BackupArchive(2, exportedAt, entries))
        return json.encodeToString(BackupArchive(2, exportedAt, entries)).also {
            require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份超过 20 MB，请先减少长备注" }
        }
    }

    fun decode(text: String): BackupArchive {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份超过 20 MB" }
        val root = json.parseToJsonElement(text).jsonObject
        require(root["formatVersion"]?.jsonPrimitive?.intOrNull in 1..2) { "不支持此备份版本" }
        val entries = root["entries"]?.jsonArray ?: error("备份缺少记录列表")
        require(entries.size <= MAX_ENTRIES) { "备份最多支持 10000 条记录" }
        if (root["formatVersion"]?.jsonPrimitive?.intOrNull == 2) entries.forEach {
            require(it.jsonObject.keys.containsAll(setOf("reminderAt", "reminderDeliveredFor"))) { "备份提醒字段不完整" }
        }
        entries.forEach { require(it.jsonObject.keys.containsAll(fields)) { "备份记录字段不完整" } }
        return json.decodeFromString<BackupArchive>(text).also(::validate)
    }

    private fun validate(archive: BackupArchive) {
        require(archive.formatVersion in 1..2 && archive.exportedAt >= 0) { "备份信息无效" }
        require(archive.entries.size <= MAX_ENTRIES) { "备份最多支持 10000 条记录" }
        archive.entries.forEach(Entry::validate)
        require(archive.entries.map { it.id }.toSet().size == archive.entries.size) { "备份包含重复 ID" }
    }
}
