package dev.lukino.daybook.data

import kotlinx.serialization.Serializable

@Serializable
data class BodyImage(val hash: String, val bytes: Long, val width: Int, val height: Int, val mime: String) {
    fun validate() {
        require(hash.matches(Regex("[a-f0-9]{64}"))) { "图片标识无效" }
        require(bytes in 1..5L * 1024 * 1024 && width in 1..2560 && height in 1..2560) { "图片大小无效" }
        require(mime in setOf("image/jpeg", "image/png")) { "图片格式无效" }
    }
}

@Serializable
data class BodyBlock(val text: String = "", val image: BodyImage? = null)

/** Empty blocks are a lossless legacy text representation. The text column is a checked search projection. */
object RichBody {
    fun effective(text: String, blocks: List<BodyBlock>): List<BodyBlock> = blocks.ifEmpty { listOf(BodyBlock(text)) }
    fun text(blocks: List<BodyBlock>): String = blocks.joinToString("") { it.text }
    fun images(blocks: List<BodyBlock>): List<BodyImage> = blocks.mapNotNull { it.image }
    fun validate(text: String, blocks: List<BodyBlock>) {
        require(text.length <= 20_000) { "正文不能超过20000个字符" }
        if (blocks.isEmpty()) return
        require(blocks.size <= 19 && blocks.size % 2 == 1) { "正文结构无效" }
        blocks.forEachIndexed { index, block ->
            require(if (index % 2 == 0) block.image == null else block.image != null && block.text.isEmpty()) { "正文结构无效" }
            block.image?.validate()
        }
        require(text(blocks) == text) { "正文与文字摘要不一致" }
        require(images(blocks).size <= 9) { "每条内容最多9张图片" }
    }
    fun insert(text: String, blocks: List<BodyBlock>, index: Int, cursor: Int, image: BodyImage): List<BodyBlock> {
        val current = effective(text, blocks).toMutableList()
        require(images(current).size < 9) { "每条内容最多9张图片" }
        val block = current[index]
        require(block.image == null && cursor in 0..block.text.length)
        current.removeAt(index)
        current.addAll(index, listOf(BodyBlock(block.text.take(cursor)), BodyBlock(image = image), BodyBlock(block.text.drop(cursor))))
        return current.also { validate(text(it), it) }
    }
    fun remove(blocks: List<BodyBlock>, index: Int): List<BodyBlock> {
        require(index > 0 && index < blocks.lastIndex && blocks[index].image != null)
        return blocks.take(index - 1) + BodyBlock(blocks[index - 1].text + blocks[index + 1].text) + blocks.drop(index + 2)
    }
}
