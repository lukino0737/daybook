package dev.lukino.daybook

import android.content.Context
import android.text.Spanned
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.lukino.daybook.data.*
import dev.lukino.daybook.ui.*
import org.junit.Test
import org.junit.Assert.*

class InlineBodyEditorTest {
    private val image = BodyImage("a".repeat(64), 20, 240, 160, "image/png")
    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun view() = InlineBodyEditText(ApplicationProvider.getApplicationContext<Context>())
    @Test fun existingBodiesRoundTripWithoutAddingNewlinesToStorage() = onMain {
        listOf("", "前文后文", "第一行\n第二行\n", "中文😀\n").forEach { text ->
            assertEquals(listOf(BodyBlock(text)), InlineBodyDocument.decode(InlineBodyDocument.encode(text, emptyList())))
        }
        val blocks = listOf(BodyBlock("前文\n"), BodyBlock(image = image), BodyBlock("\n后文"), BodyBlock(image = image), BodyBlock())
        val edit = view(); edit.setBody(RichBody.text(blocks), blocks)
        assertEquals(blocks, edit.body()); assertEquals(2, edit.text.getSpans(0, edit.length(), InlineImageSpan::class.java).size)
        RichBody.validate(RichBody.text(edit.body()), edit.body())
    }
    @Test fun insertsAtCursorAndContinuesTypingAfterPictureInOneDocument() = onMain {
        val edit = view(); edit.setBody("前文后文", emptyList()); edit.setSelection(2)
        edit.insertImage(image, 2, 2)
        edit.text.insert(edit.selectionEnd, "插图后文字")
        val blocks = edit.body()
        assertEquals(listOf(BodyBlock("前文"), BodyBlock(image = image), BodyBlock("插图后文字后文")), blocks)
        edit.removeImage(0)
        assertEquals(listOf(BodyBlock("前文插图后文字后文")), edit.body())
    }
    @Test fun rangeDeletionAcrossImageKeepsSurroundingWords() = onMain {
        val edit = view(); edit.setBody("前文后文", listOf(BodyBlock("前文"), BodyBlock(image = image), BodyBlock("后文")))
        edit.text.replace(1, edit.length() - 1, "替换")
        assertEquals(listOf(BodyBlock("前替换文")), edit.body())
        assertFalse(edit.text.contains('\uFFFC'))
    }
    @Test fun backspaceAtImageBoundaryDoesNotTrapCursorOrDropWords() = onMain {
        val edit = view(); edit.setBody("前文后文", listOf(BodyBlock("前文"), BodyBlock(image = image), BodyBlock("后文")))
        val span = edit.text.getSpans(0, edit.length(), InlineImageSpan::class.java).single()
        val afterImage = edit.text.getSpanEnd(span)
        edit.text.delete(afterImage, afterImage + 1)
        assertEquals(listOf(BodyBlock("前文后文")), edit.body())
    }
    @Test fun chineseComposingTextAndEmojiSurviveSelectionAndImageInsert() = onMain {
        val edit = view(); edit.setBody("", emptyList()); edit.requestFocus()
        val connection = edit.onCreateInputConnection(EditorInfo())!!
        connection.setComposingText("zhong", 1)
        connection.setComposingText("中文", 1)
        connection.commitText("中文😀", 1)
        connection.finishComposingText()
        assertEquals("中文😀", RichBody.text(edit.body()))
        edit.insertImage(image, edit.length(), edit.length())
        connection.commitText("继续输入", 1)
        assertEquals("中文😀继续输入", RichBody.text(edit.body()))
        assertEquals(1, RichBody.images(edit.body()).size)
    }
    @Test fun textLimitRejectsOverflowAndExternalRefreshPreservesSelection() = onMain {
        val edit = view(); edit.setBody("字".repeat(20000), emptyList()); edit.setSelection(20000)
        edit.text.insert(edit.length(), "超出")
        assertEquals(20000, RichBody.text(edit.body()).length)
        edit.setBody("字".repeat(20000), emptyList())
        assertEquals(20000, edit.selectionStart)
    }
    @Test fun rejectedOversizedReplacementKeepsSelectedPictureAndWords() = onMain {
        val original = listOf(BodyBlock("前文"), BodyBlock(image = image), BodyBlock("后文"))
        val edit = view(); edit.setBody(RichBody.text(original), original)
        edit.text.replace(0, edit.length(), "字".repeat(20001))
        assertEquals(original, edit.body())
    }
}
