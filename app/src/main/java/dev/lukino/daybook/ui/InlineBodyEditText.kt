package dev.lukino.daybook.ui

import android.content.Context
import android.graphics.*
import android.text.*
import android.text.style.ReplacementSpan
import android.view.Gravity
import android.view.MotionEvent
import android.widget.EditText
import dev.lukino.daybook.data.*

/** Layout-only newlines are never persisted. Existing bodies round-trip byte-for-byte. */
internal class BodyLineBreak(val owner: InlineImageSpan)
internal class InlineImageSpan(val image: BodyImage) : ReplacementSpan() {
    var bitmap: Bitmap? = null
    var availableWidth = 1
    private var drawWidth = 1
    private var drawHeight = 1
    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val scale = minOf(availableWidth.toFloat() / image.width, availableWidth * 2f / image.height)
        drawWidth = (image.width * scale).toInt().coerceAtLeast(1)
        drawHeight = (image.height * scale).toInt().coerceAtLeast(1)
        fm?.apply { ascent = -drawHeight; descent = 0; top = ascent; bottom = 0 }
        return availableWidth.coerceAtLeast(1)
    }
    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val left = x + (availableWidth - drawWidth) / 2f
        val rect = RectF(left, (y - drawHeight).toFloat(), left + drawWidth, y.toFloat())
        val imageBitmap = bitmap
        if (imageBitmap != null) canvas.drawBitmap(imageBitmap, null, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        else {
            val placeholder = Paint(paint).apply { color = Color.LTGRAY }
            canvas.drawRoundRect(rect, 8f, 8f, placeholder)
            placeholder.color = Color.DKGRAY
            canvas.drawText("图片", x + 16, rect.centerY(), placeholder)
        }
    }
}

internal object InlineBodyDocument {
    fun encode(text: String, blocks: List<BodyBlock>): SpannableStringBuilder = SpannableStringBuilder().apply {
        RichBody.effective(text, blocks).forEach { block ->
            if (block.image == null) append(block.text)
            else {
                val image = InlineImageSpan(block.image)
                appendBreak(image)
                val start = length
                append('\uFFFC')
                setSpan(image, start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                appendBreak(image)
            }
        }
    }
    private fun SpannableStringBuilder.appendBreak(owner: InlineImageSpan) {
        val start = length; append('\n')
        setSpan(BodyLineBreak(owner), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    fun decode(value: Spanned): List<BodyBlock> {
        val images = value.getSpans(0, value.length, InlineImageSpan::class.java)
            .filter { value.getSpanStart(it) >= 0 && value.getSpanEnd(it) > value.getSpanStart(it) }
            .sortedBy { value.getSpanStart(it) }
        val breaks = value.getSpans(0, value.length, BodyLineBreak::class.java)
            .flatMap { (value.getSpanStart(it) until value.getSpanEnd(it)).toList() }.toSet()
        fun segment(start: Int, end: Int) = (start until end).filterNot { it in breaks }.joinToString("") { value[it].toString() }
        val result = mutableListOf<BodyBlock>()
        var start = 0
        images.forEach { span ->
            result += BodyBlock(segment(start, value.getSpanStart(span)))
            result += BodyBlock(image = span.image)
            start = value.getSpanEnd(span)
        }
        result += BodyBlock(segment(start, value.length))
        return result
    }
}

/** One IME and one selection across the entire text/image document. */
internal class InlineBodyEditText(context: Context) : EditText(context) {
    var onBodyChange: (String, List<BodyBlock>) -> Unit = { _, _ -> }
    var onImageClick: (Int) -> Unit = {}
    var onSelection: (Int, Int) -> Unit = { _, _ -> }
    private var updating = false
    private var rejectedReplacement = false
    private var removing = emptySet<InlineImageSpan>()
    private var thumbnails: Map<String, Bitmap> = emptyMap()
    init {
        // SavedStateHandle owns the body. Android's parcelled TextView state drops custom image spans.
        isSaveEnabled = false
        gravity = Gravity.TOP or Gravity.START
        background = null
        setPadding(dp(14), dp(14), dp(14), dp(14))
        minHeight = dp(160)
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setHorizontallyScrolling(false)
        isVerticalScrollBarEnabled = false
        // Keep the parent Compose scroller responsible for the document's height.
        filters = arrayOf(InputFilter { source, start, end, dest, dstart, dend ->
            if (updating) null else {
                val candidate = SpannableStringBuilder(dest).replace(dstart, dend, source, start, end)
                val blocks = InlineBodyDocument.decode(candidate)
                rejectedReplacement = RichBody.text(blocks).length > 20_000 || RichBody.images(blocks).size > 9
                if (rejectedReplacement) dest.subSequence(dstart, dend) else null
            }
        })
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (updating || rejectedReplacement || count == 0 || s !is Spanned) return
                // Backspace at the picture boundary removes the picture, never an undeletable separator.
                removing = s.getSpans(start, start + count, BodyLineBreak::class.java).filter {
                    s.getSpanStart(it) < start + count && s.getSpanEnd(it) > start
                }.map { it.owner }.toSet()
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (updating) return
                // A rejected replacement re-inserts the original selection, including its spans.
                // It is not a user request to delete the selected image boundaries.
                rejectedReplacement = false
                updating = true
                try {
                    removing.forEach { span ->
                        val start = s.getSpanStart(span); val end = s.getSpanEnd(span)
                        if (start >= 0 && end > start) s.delete(start, end)
                    }
                    removing = emptySet()
                    normalize(s)
                    refreshImages()
                } finally { updating = false }
                publish()
            }
        })
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    fun body() = InlineBodyDocument.decode(text)
    private fun publish() {
        val blocks = body()
        onBodyChange(RichBody.text(blocks), blocks.takeIf { it.size > 1 }.orEmpty())
    }
    fun setBody(value: String, blocks: List<BodyBlock>) {
        if (body() == RichBody.effective(value, blocks)) return
        val start = selectionStart.coerceAtLeast(0); val end = selectionEnd.coerceAtLeast(0)
        updating = true
        try {
            setText(InlineBodyDocument.encode(value, blocks))
            setSelection(start.coerceAtMost(length()), end.coerceAtMost(length()))
            refreshImages()
        } finally { updating = false }
    }
    fun insertImage(image: BodyImage, start: Int, end: Int) {
        if (body().count { it.image != null } >= 9) return
        val encoded = InlineBodyDocument.encode("", listOf(BodyBlock(), BodyBlock(image = image), BodyBlock()))
        updating = true
        try {
            val from = minOf(start, end).coerceIn(0, length())
            val to = maxOf(start, end).coerceIn(from, length())
            text.replace(from, to, encoded)
            setSelection((from + encoded.length).coerceAtMost(length()))
            normalize(text); refreshImages()
        } finally { updating = false }
        publish(); requestFocus()
    }
    fun removeImage(ordinal: Int) {
        val spans = text.getSpans(0, length(), InlineImageSpan::class.java).sortedBy { text.getSpanStart(it) }
        val span = spans.getOrNull(ordinal) ?: return
        text.delete(text.getSpanStart(span), text.getSpanEnd(span))
    }
    private fun normalize(value: Editable) {
        value.getSpans(0, value.length, BodyLineBreak::class.java).sortedByDescending { value.getSpanStart(it) }.forEach {
            if (value.getSpanStart(it.owner) < 0 || value.getSpanEnd(it.owner) <= value.getSpanStart(it.owner)) {
                val start = value.getSpanStart(it); val end = value.getSpanEnd(it)
                value.removeSpan(it)
                if (start >= 0 && end > start) value.delete(start, end)
            }
        }
        value.getSpans(0, value.length, InlineImageSpan::class.java).forEach { span ->
            var start = value.getSpanStart(span)
            if (start > 0 && value[start - 1] != '\n') {
                value.insert(start, "\n")
                value.setSpan(BodyLineBreak(span), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val end = value.getSpanEnd(span)
            if (end == value.length || value[end] != '\n') {
                value.insert(end, "\n")
                value.setSpan(BodyLineBreak(span), end, end + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
    fun setThumbnails(value: Map<String, Bitmap>) { thumbnails = value; refreshImages() }
    private fun refreshImages() {
        text.getSpans(0, length(), InlineImageSpan::class.java).forEach {
            it.availableWidth = (width - paddingLeft - paddingRight).coerceAtLeast(dp(200))
            it.bitmap = thumbnails[it.image.hash]
        }
        // TextView caches span metrics; reassigning the same span invalidates that layout safely.
        text.getSpans(0, length(), InlineImageSpan::class.java).forEach {
            text.setSpan(it, text.getSpanStart(it), text.getSpanEnd(it), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        requestLayout(); invalidate()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw) refreshImages()
    }
    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        // TextView can call this from its constructor, before Kotlin fields are initialized.
        if (selStart >= 0 && selEnd >= 0) selectionListener(selStart, selEnd)
    }
    private fun selectionListener(start: Int, end: Int) { @Suppress("SENSELESS_COMPARISON") if (onSelection != null) onSelection(start, end) }
    private var downX = 0f
    private var downY = 0f
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) { downX = event.x; downY = event.y }
        if (event.action == MotionEvent.ACTION_UP && kotlin.math.abs(event.x - downX) < dp(8) && kotlin.math.abs(event.y - downY) < dp(8)) {
            val offset = getOffsetForPosition(event.x, event.y)
            val spans = text.getSpans(0, length(), InlineImageSpan::class.java).sortedBy { text.getSpanStart(it) }
            val ordinal = spans.indexOfFirst { offset in text.getSpanStart(it)..text.getSpanEnd(it) &&
                layout?.getLineForOffset(text.getSpanStart(it)) == layout?.getLineForVertical((event.y - totalPaddingTop + scrollY).toInt()) }
            if (ordinal >= 0) { performClick(); onImageClick(ordinal); return true }
        }
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
