package dev.lukino.daybook

import dev.lukino.daybook.data.*
import dev.lukino.daybook.ui.Draft
import org.junit.Assert.*
import org.junit.Test

class ReviewRulesTest {
    private val love = Entry(kind = EntryKind.NOTE, title = "散步", note = "看到河边的落日", date = "2026-09-10", tags = listOf("恋爱", "生活"))
    private val study = Entry(kind = EntryKind.NOTE, title = "读书", note = "Kotlin 笔记", date = "2026-09-09", tags = listOf("学习"))
    private val task = Entry(kind = EntryKind.TASK, title = "读书", tags = listOf("学习"))
    @Test fun normalizesInputWithoutSplittingSpacesInsideLabels() {
        assertEquals(listOf("恋爱", "生活", "AI coding"), ReviewRules.parseTags(" 恋爱，生活,恋爱\n AI coding \n"))
        assertEquals(listOf("恋爱"), Draft(kind = EntryKind.NOTE, title = "散步", date = "2026-09-10", tagsText = "恋爱,恋爱").entry().tags)
    }
    @Test fun searchAndTagAreAnIntersectionAndIncludeBody() {
        assertEquals(listOf(love), ReviewRules.filter(listOf(study, task, love), ReviewSelection(query = "落日", tag = "恋爱", kind = EntryKind.NOTE)))
        assertTrue(ReviewRules.filter(listOf(study, love), ReviewSelection(query = "落日", tag = "学习", kind = EntryKind.NOTE)).isEmpty())
        assertEquals(listOf(study), ReviewRules.filter(listOf(study, love), ReviewSelection(query = "kOTLIN", tag = "", kind = EntryKind.NOTE)))
    }
    @Test fun notesDefaultAndAllKindsCanIncludeUndatedTasks() {
        assertEquals(listOf(love, study), ReviewRules.filter(listOf(task, study, love), ReviewSelection(query = "", tag = "", kind = EntryKind.NOTE)))
        assertEquals(listOf(study, task), ReviewRules.filter(listOf(task, study, love), ReviewSelection(query = "", tag = "学习", kind = null)))
    }
    @Test fun invalidTagsAreRejectedBeforePersistenceOrImport() {
        listOf(listOf(""), listOf(" A"), listOf("a", "a"), listOf("a,b"), listOf("长".repeat(31)), (1..11).map { "$it" })
            .forEach { tags -> assertThrows(IllegalArgumentException::class.java) { love.copy(tags = tags).validate() } }
    }
    @Test fun sameDateOrderingIsStableAndNewestFirst() {
        val early = love.copy(id = "00000000-0000-0000-0000-000000000010", time = "08:00")
        val late = love.copy(id = "00000000-0000-0000-0000-000000000011", time = "21:00")
        assertEquals(listOf(late, early, love), ReviewRules.filter(listOf(early, love, late), ReviewSelection(query = "", tag = "", kind = EntryKind.NOTE)))
    }
}
