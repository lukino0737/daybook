package dev.lukino.daybook

import dev.lukino.daybook.data.*
import dev.lukino.daybook.backup.*
import dev.lukino.daybook.reminder.ReminderTarget
import org.junit.Assert.*
import org.junit.Test

class MemoBackupTest {
    @Test fun undatedMemoRoundTripsWithReminderAndRemainsSeparate() {
        val memo = Memo(body = "突然想到的点子\n明天继续", reminderAt = "2026-09-20T09:00")
        memo.validate()
        val archive = BackupCodec.decode(BackupCodec.encode(emptyList(), memos = listOf(memo)))
        assertEquals(6, archive.formatVersion)
        assertTrue(archive.entries.isEmpty())
        assertEquals(listOf(memo), archive.memos)
        assertEquals("突然想到的点子", memo.summary)
        assertTrue(ReminderTarget.from(memo).pending)
        assertFalse(ReminderTarget.from(memo.copy(reminderDeliveredFor = memo.reminderAt)).pending)
    }
    @Test fun oldBackupHasNoMemosAndNewArchiveRequiresCompleteMemoFields() {
        val legacy = """{"formatVersion":3,"exportedAt":0,"entries":[]}"""
        assertTrue(BackupCodec.decode(legacy).memos.isEmpty())
        val current = BackupCodec.encode(emptyList(), memos = listOf(Memo(body = "备忘")))
        assertThrows(Exception::class.java) { BackupCodec.decode(current.replace("\"memos\"", "\"missing\"")) }
        assertThrows(Exception::class.java) { BackupCodec.decode(current.replace("\"reminderDeliveredFor\"", "\"missing\"")) }
    }
    @Test fun blankInvalidDatesAndDuplicateMemoIdsAreRejected() {
        assertThrows(Exception::class.java) { Memo(body = " ").validate() }
        assertThrows(Exception::class.java) { Memo(body = "备忘", date = "2026-02-30").validate() }
        val memo = Memo(body = "备忘")
        assertThrows(Exception::class.java) { BackupCodec.encode(emptyList(), memos = listOf(memo, memo)) }
    }
    @Test fun sameIdAcrossTablesHasDistinctNotificationIdentity() {
        val memo = Memo(body = "便签", reminderAt = "2026-09-20T09:00")
        val entry = Entry(id = memo.id, title = "日程", date = "2026-09-20", reminderAt = memo.reminderAt)
        assertNotEquals(ReminderTarget.from(memo).key, ReminderTarget.from(entry).key)
        assertNotEquals(ReminderTarget.from(memo).uri, ReminderTarget.from(entry).uri)
    }
}
