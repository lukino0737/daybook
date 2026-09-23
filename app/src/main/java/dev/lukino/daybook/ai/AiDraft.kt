package dev.lukino.daybook.ai

import dev.lukino.daybook.data.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

@Serializable data class AiDraft(
    val id: String, val kind: String = "TASK", val title: String = "", val body: String = "",
    val date: String? = null, val time: String? = null, val reminderAt: String? = null, val tags: List<String> = emptyList(),
) {
    fun shapeCheck() {
        require(id.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) { "草稿标识无效" }
        require(kind in setOf("TASK", "EVENT", "NOTE", "MEMO")) { "不支持的草稿类型" }
        require(title.length <= 300 && body.length <= 20_000 && tags.size <= 10) { "草稿内容过长" }
        date?.let { require(LocalDate.parse(it).toString() == it) { "日期格式无效" } }
        time?.let { require(it.matches(Regex("\\d{2}:\\d{2}"))); LocalTime.parse(it) }
        reminderAt?.let { val value = LocalDateTime.parse(it); require(value.toString() == it && value.second == 0 && value.nano == 0) }
    }
    fun entry(id: String, now: Long): Entry {
        shapeCheck(); require(kind != "MEMO")
        return Entry(id = id, kind = EntryKind.valueOf(kind), title = title.trim(), note = body,
            date = date, time = time, reminderAt = reminderAt, tags = tags,
            createdAt = now, updatedAt = now).also { it.validate() }
    }
    fun memo(id: String, now: Long): Memo {
        shapeCheck(); require(kind == "MEMO")
        require(time == null && tags.isEmpty()) { "便签不支持时间或标签，请移到正文" }
        return Memo(id = id, body = body, date = date, reminderAt = reminderAt, createdAt = now, updatedAt = now).also { it.validate() }
    }
    fun validationError(): String? = try {
        if (kind == "MEMO") memo(UUID.randomUUID().toString(), 0) else entry(UUID.randomUUID().toString(), 0)
        null
    } catch (e: Exception) { e.message ?: "请补全草稿" }
}
@Serializable private data class DraftEnvelope(val items: List<AiDraft>)
object AiDrafts {
    private val strict = Json { ignoreUnknownKeys = false }
    fun parse(raw: String): List<AiDraft> {
        if (raw.length > 45_000) throw AiFailure("草稿内容过多，请分批生成。")
        return try {
            val values = strict.decodeFromString<DraftEnvelope>(raw).items
            require(values.size in 1..20 && values.map { it.id }.distinct().size == values.size)
            values.forEach { it.shapeCheck() }; values
        } catch (_: Exception) { throw AiFailure("草稿格式或日期不正确，请调整描述后重试。") }
    }
    fun encode(values: List<AiDraft>): String = strict.encodeToString(DraftEnvelope(values))
    val tool: JsonObject = Json.parseToJsonElement("""{
      "type":"function","function":{"name":"propose_items",
      "description":"生成或更新待用户确认的草稿，不写入数据库。更新时返回完整草稿列表并保留未改动项及id。缺少必需日期先向用户询问。",
      "parameters":{"type":"object","additionalProperties":false,"required":["items"],"properties":{
        "items":{"type":"array","maxItems":20,"items":{"type":"object","additionalProperties":false,
          "required":["id","kind","title","body"],"properties":{
            "id":{"type":"string"},"kind":{"type":"string","enum":["TASK","EVENT","NOTE","MEMO"]},
            "title":{"type":"string"},"body":{"type":"string"},"date":{"type":["string","null"],"description":"YYYY-MM-DD；任务没说截止日期就必须为null"},
            "time":{"type":["string","null"],"description":"HH:mm；没有明确时间就为null"},
            "reminderAt":{"type":["string","null"],"description":"YYYY-MM-DDTHH:mm，只设置明确要求的提醒；NOTE必须为null"},
            "tags":{"type":"array","items":{"type":"string"}}
          }}}
      }}}}
    """).jsonObject
}
