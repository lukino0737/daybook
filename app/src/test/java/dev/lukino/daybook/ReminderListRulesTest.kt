package dev.lukino.daybook

import dev.lukino.daybook.data.*
import dev.lukino.daybook.reminder.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderListRulesTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2026-09-21T02:00:00Z")
    private val base = StandaloneReminder(title = "独立", startDate = "2026-09-21", time = "11:00", effectiveFrom = 0)
    @Test fun sameIdsAcrossSourcesRemainSeparateAndPendingIsChronological() {
        val memo = Memo(id = base.id, body = "便签", reminderAt = "2026-09-21T12:00")
        val entry = Entry(id = base.id, title = "日程", date = "2026-09-21", reminderAt = "2026-09-21T10:30")
        val rows = ReminderListRules.items(listOf(entry), listOf(memo), listOf(base), now, zone)
        assertEquals(listOf("日程", "独立提醒", "便签"), rows.map { it.source })
        assertEquals(3, rows.map { it.key }.distinct().size)
        assertTrue(rows.all { it.group == ReminderGroup.PENDING })
    }
    @Test fun finishedReasonsAndPausedOrderingAreExplicit() {
        val sent = base.copy(deliveredFor = "2026-09-21T11:00", updatedAt = 200, createdAt = 100)
        val missed = base.copy(id = java.util.UUID.randomUUID().toString(), startDate = "2026-09-19", updatedAt = 300, createdAt = 100)
        val task = Entry(kind = EntryKind.TASK, title = "已完成", completed = true, reminderAt = "2026-09-22T09:00")
        val paused = base.copy(id = java.util.UUID.randomUUID().toString(), enabled = false, showInCalendar = true)
        val rows = ReminderListRules.items(listOf(task), emptyList(), listOf(sent, missed, paused), now.plusSeconds(7200), zone)
        assertEquals(ReminderGroup.PAUSED, rows.first().group)
        assertTrue(rows.single { it.id == task.id }.status.contains("任务已完成"))
        assertTrue(rows.single { it.id == sent.id }.status.contains("已发出"))
        assertTrue(rows.single { it.id == missed.id }.status.contains("已错过"))
        assertEquals(listOf(task.id, missed.id, sent.id), rows.filter { it.group == ReminderGroup.FINISHED }.map { it.id })
    }
    @Test fun recurringItemAppearsOnceAndAdvancesAfterDelivery() {
        val r = base.copy(time = "09:00", repeat = RepeatKind.DAILY)
        val before = ReminderListRules.items(emptyList(), emptyList(), listOf(r), now, zone).single()
        assertTrue(before.status.contains("等待补发"))
        val after = ReminderListRules.items(emptyList(), emptyList(), listOf(r.copy(deliveredFor = "2026-09-21T09:00")), now, zone).single()
        assertEquals(ReminderGroup.PENDING, after.group)
        assertEquals(Instant.parse("2026-09-22T01:00:00Z"), after.next)
    }
    @Test fun attachedCatchupBoundaryMatchesScheduler() {
        val memo = Memo(body = "过期", reminderAt = "2026-09-20T10:00")
        val before = ReminderListRules.items(emptyList(), listOf(memo), emptyList(), now.minusSeconds(1), zone).single()
        assertEquals(ReminderGroup.PENDING, before.group)
        assertEquals(ReminderGroup.FINISHED, ReminderListRules.items(emptyList(), listOf(memo), emptyList(), now, zone).single().group)
    }
}
