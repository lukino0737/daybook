package dev.lukino.daybook

import dev.lukino.daybook.backup.*
import dev.lukino.daybook.data.*
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private val sample = listOf(
        Entry(title = "发布会", date = "2026-09-10", time = "01:00"),
        Entry(title = "游戏更新", date = "2026-09-10"),
        Entry(kind = EntryKind.TASK, title = "材料提交", date = "2026-09-10"),
        Entry(title = "网络课程", date = "2026-09-17", note = "https://example.com/class"),
        Entry(title = "周末见面", date = "2026-09-12"),
        Entry(kind = EntryKind.NOTE, title = "奶茶 🧋", date = "2026-09-09", note = "半糖\n少冰"),
        Entry(kind = EntryKind.TASK, title = "读论文"),
    )
    @Test fun roundTripPreservesEveryFieldAndId() {
        assertEquals(sample, BackupCodec.decode(BackupCodec.encode(sample, 1000)).entries)
    }
    @Test fun readsLegacyV1AndPreservesReminderFields() {
        val original = sample.first()
        val legacy = """{"formatVersion":1,"exportedAt":0,"entries":[{"id":"${original.id}","kind":"EVENT","title":"发布会","note":"","date":"2026-09-10","time":"01:00","completed":false,"createdAt":0,"updatedAt":0}]}"""
        assertEquals(original.copy(createdAt = 0, updatedAt = 0), BackupCodec.decode(legacy).entries.single())
        val reminded = original.copy(tags = listOf("生活", "学习"), reminderAt = "2026-09-10T00:30", reminderDeliveredFor = "2026-09-10T00:30")
        assertEquals(reminded, BackupCodec.decode(BackupCodec.encode(listOf(reminded))).entries.single())
        val broken = BackupCodec.encode(listOf(reminded)).replace("\"reminderAt\":", "\"missingReminder\":")
        assertThrows(Exception::class.java) { BackupCodec.decode(broken) }
    }
    @Test fun readsV2WithEmptyTagsAndRejectsV3MissingTags() {
        val current = BackupCodec.encode(listOf(sample.first()))
        val v2 = current.replace("\"formatVersion\": 4", "\"formatVersion\": 2").replace("    \"tags\": [],\n", "")
        assertTrue(BackupCodec.decode(v2).entries.single().tags.isEmpty())
        assertThrows(Exception::class.java) { BackupCodec.decode(current.replace("\"tags\":", "\"missingTags\":")) }
    }
    @Test fun emptyBackupIsValidAndCanBePreviewedAsZero() {
        assertTrue(BackupCodec.decode(BackupCodec.encode(emptyList())).entries.isEmpty())
    }
    @Test fun rejectsMalformedUnknownAndTruncatedBackups() {
        val valid = BackupCodec.encode(sample)
        listOf("not JSON", valid.take(valid.length / 2), valid.replace("\"formatVersion\": 4", "\"formatVersion\": 9"),
            valid.replace("\"EVENT\"", "\"UNKNOWN\""), valid.replace("2026-09-10", "2026-02-30"),
            valid.replace("\"id\":", "\"missingId\":"), valid.replace("\"01:00\"", "\"25:00\""))
            .forEach { assertThrows(Exception::class.java) { BackupCodec.decode(it) } }
    }
    @Test fun duplicateIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.encode(listOf(sample.first(), sample.first())) }
    }
    @Test fun missingFieldsCannotGenerateNewIdsSilently() {
        assertThrows(Exception::class.java) { BackupCodec.decode("""{"formatVersion":1,"exportedAt":0,"entries":[{"title":"test","date":"2026-09-10"}]}""") }
    }
    @Test fun oversizedInputIsRejectedBeforeParsing() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(" ".repeat(BackupCodec.MAX_BYTES + 1)) }
    }
}
