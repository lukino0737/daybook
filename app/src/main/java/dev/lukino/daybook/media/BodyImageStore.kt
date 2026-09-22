package dev.lukino.daybook.media

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import dev.lukino.daybook.data.BodyImage
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt

/** Immutable, content-addressed images. Database references become visible only after durable file writes. */
class BodyImageStore(private val context: Context) {
    val directory = File(context.filesDir, "body-images").apply { mkdirs() }
    private val leases = mutableMapOf<String, Set<String>>()
    private var ready = false
    @Synchronized fun pin(owner: String, images: List<BodyImage>) { leases[owner] = images.map { it.hash }.toSet() }
    @Synchronized fun release(owner: String) { leases.remove(owner) }
    @Synchronized fun ready() { ready = true }
    fun file(image: BodyImage): File { image.validate(); return File(directory, image.hash) }
    fun verify(image: BodyImage) {
        val f = file(image)
        require(f.isFile && f.length() == image.bytes && digest(f) == image.hash) { "图片缺失或损坏，请检查备份或重新选择" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        require(bounds.outWidth == image.width && bounds.outHeight == image.height && bounds.outMimeType == image.mime) { "图片信息不一致" }
    }
    @Synchronized fun collect(referenced: List<BodyImage>) {
        if (!ready) return
        val keep = referenced.map { it.hash }.toSet() + leases.values.flatten()
        directory.listFiles()?.filter { it.name.matches(Regex("[a-f0-9]{64}")) && it.name !in keep }?.forEach { it.delete() }
        directory.listFiles()?.filter { it.name.startsWith("import-") && System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L }?.forEach { it.delete() }
    }
    fun import(uri: Uri, owner: String): BodyImage {
        val source = File.createTempFile("import-", ".tmp", directory)
        val encoded = File.createTempFile("import-", ".tmp", directory)
        try {
            (context.contentResolver.openInputStream(uri) ?: error("无法读取所选图片")).use { input ->
                source.outputStream().use { copyLimited(input, it, 20L * 1024 * 1024) }
            }
            val bitmap = decode(source)
            try {
                val transparent = bitmap.hasAlpha()
                encoded.outputStream().use {
                    check(bitmap.compress(if (transparent) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 85, it)) { "图片处理失败" }
                    it.fd.sync()
                }
                require(encoded.length() <= 5L * 1024 * 1024) { "处理后的图片超过5 MiB，请选择较小的图片" }
                val image = BodyImage(digest(encoded), encoded.length(), bitmap.width, bitmap.height, if (transparent) "image/png" else "image/jpeg")
                install(encoded, image)
                synchronized(this) { leases[owner] = leases[owner].orEmpty() + image.hash }
                return image
            } finally { bitmap.recycle() }
        } finally { source.delete(); encoded.delete() }
    }
    fun install(source: File, image: BodyImage) {
        image.validate()
        require(source.length() == image.bytes && digest(source) == image.hash) { "图片校验失败" }
        val destination = file(image)
        if (destination.exists()) { verify(image); return }
        val temp = File.createTempFile("import-", ".tmp", directory)
        try {
            source.inputStream().use { input -> temp.outputStream().use { output -> input.copyTo(output); output.fd.sync() } }
            check(temp.renameTo(destination)) { "无法保存图片，请检查可用空间" }
            verify(image)
        } finally { temp.delete() }
    }
    companion object {
        fun digest(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer = ByteArray(32768); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        fun copyLimited(input: InputStream, output: java.io.OutputStream, limit: Long): Long {
            val buffer = ByteArray(32768); var size = 0L
            while (true) { val n = input.read(buffer); if (n < 0) break; size += n; require(size <= limit) { "文件超过允许大小" }; output.write(buffer, 0, n) }
            return size
        }
        private fun decode(file: File): Bitmap {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            require(bounds.outMimeType in setOf("image/jpeg", "image/png", "image/webp")) { "仅支持静态 JPEG、PNG、WebP 图片" }
            require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000L) { "图片尺寸过大或文件已损坏" }
            // Inspect container chunks, rather than trusting a name or MIME supplied by the picker.
            java.io.RandomAccessFile(file, "r").use { input ->
                if (bounds.outMimeType == "image/png") {
                    input.seek(8)
                    while (input.filePointer + 12 <= input.length()) {
                        val length = input.readInt().toLong() and 0xffffffffL
                        val type = ByteArray(4).also(input::readFully).toString(Charsets.US_ASCII)
                        require(type != "acTL") { "暂不支持动态图，请选择静态图片" }
                        require(input.filePointer + length + 4 <= input.length()) { "图片已损坏" }
                        input.seek(input.filePointer + length + 4)
                    }
                } else if (bounds.outMimeType == "image/webp") {
                    input.seek(12)
                    while (input.filePointer + 8 <= input.length()) {
                        val type = ByteArray(4).also(input::readFully).toString(Charsets.US_ASCII)
                        val length = Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
                        require(type !in setOf("ANIM", "ANMF")) { "暂不支持动态图，请选择静态图片" }
                        require(input.filePointer + length + (length % 2) <= input.length()) { "图片已损坏" }
                        input.seek(input.filePointer + length + (length % 2))
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 28) return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                require(!info.isAnimated) { "暂不支持动态图" }
                val scale = minOf(1.0, 2560.0 / max(info.size.width, info.size.height))
                decoder.setTargetSize(max(1, (info.size.width * scale).roundToInt()), max(1, (info.size.height * scale).roundToInt()))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                decoder.setOnPartialImageListener { false }
            }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > 5120) sample *= 2
            var bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("图片无法解码")
            val orientation = runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
            val matrix = Matrix().apply { when (orientation) {
                2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
                7 -> { setRotate(-90f); postScale(-1f, 1f) }; 8 -> setRotate(-90f)
            } }
            val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (oriented !== bitmap) { bitmap.recycle(); bitmap = oriented }
            val scale = minOf(1.0, 2560.0 / max(bitmap.width, bitmap.height))
            val result = Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * scale).roundToInt()), max(1, (bitmap.height * scale).roundToInt()), true)
            if (result !== bitmap) bitmap.recycle()
            return result
        }
    }
}
