package dev.lukino.daybook

import dev.lukino.daybook.data.*
import org.junit.Assert.*
import org.junit.Test

class RichBodyTest {
    private val image = BodyImage("a".repeat(64), 100, 20, 30, "image/png")
    @Test fun insertionAndRemovalPreserveTextExactly() {
        val original = "前文\n🙂后文\n"
        val blocks = RichBody.insert(original, emptyList(), 0, 3, image)
        assertEquals("前文\n", blocks.first().text)
        assertEquals("🙂后文\n", blocks.last().text)
        assertEquals(original, RichBody.text(blocks))
        assertEquals(listOf(BodyBlock(original)), RichBody.remove(blocks, 1))
        RichBody.validate(original, blocks)
    }
    @Test fun imageOnlyMemoAndLimit() {
        var blocks = listOf(BodyBlock())
        repeat(9) { blocks = RichBody.insert("", blocks, blocks.lastIndex, 0, image) }
        val memo = Memo(blocks = blocks)
        memo.validate()
        assertEquals("图片便签 · 9 张", memo.summary)
        assertThrows(IllegalArgumentException::class.java) { RichBody.insert("", blocks, 0, 0, image) }
        assertThrows(IllegalArgumentException::class.java) { Memo().validate() }
    }
    @Test fun malformedProjectionAndUnsafeFileNamesAreRejected() {
        val blocks = listOf(BodyBlock("a"), BodyBlock(image = image), BodyBlock("b"))
        assertThrows(IllegalArgumentException::class.java) { RichBody.validate("wrong", blocks) }
        assertThrows(IllegalArgumentException::class.java) { RichBody.validate("ab", blocks.dropLast(1)) }
        assertThrows(IllegalArgumentException::class.java) { image.copy(hash = "../file").validate() }
        assertThrows(IllegalArgumentException::class.java) { image.copy(width = 2561).validate() }
    }
}
