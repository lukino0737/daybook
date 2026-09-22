package dev.lukino.daybook.backup

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import dev.lukino.daybook.data.*
import dev.lukino.daybook.media.BodyImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Stage and validate every byte before confirmation. Files are immutable; the DB transaction is the visibility switch. */
class BackupService(context: Context, private val repository: EntryRepository) {
    private val resolver = context.contentResolver
    private val images = repository.images ?: BodyImageStore(context)
    private val snapshot = AtomicFile(File(context.filesDir, "before-restore.zip"))
    private val oldSnapshot = AtomicFile(File(context.filesDir, "before-restore.json"))
    private val staging = File(context.filesDir, "backup-staging").apply { mkdirs() }

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("export-", ".zip", staging)
        try {
            repository.readSnapshot { entries, memos, reminders ->
                temp.outputStream().use { output -> writeZip(output, entries, memos, reminders); output.fd.sync() }
            }
            (resolver.openOutputStream(uri, "wt") ?: error("无法写入所选文件")).use { output -> temp.inputStream().use { it.copyTo(output) } }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { throw IllegalStateException("备份未完成，所选文件不可作为有效备份。${e.message.orEmpty()}", e) }
        finally { temp.delete() }
    }
    suspend fun preview(uri: Uri): BackupArchive = withContext(Dispatchers.IO) {
        (resolver.openInputStream(uri) ?: error("无法读取所选文件")).use { previewStream(it) }
    }
    suspend fun previewSnapshot(): BackupArchive = withContext(Dispatchers.IO) {
        // openRead also recovers AtomicFile's interrupted-write backup before checking existence.
        val current = if (snapshot.baseFile.exists() || File(snapshot.baseFile.path + ".bak").exists()) snapshot else oldSnapshot
        require(current.baseFile.exists() || File(current.baseFile.path + ".bak").exists()) { "还没有替换前快照" }
        current.openRead().use { previewStream(it) }
    }
    fun discard(archive: BackupArchive?) {
        archive?.imageDirectory?.let { path ->
            val dir = File(path)
            if (dir.parentFile?.canonicalFile == staging.canonicalFile && dir.name.startsWith("preview-")) dir.deleteRecursively()
        }
    }
    fun discardAbandonedPreviews() {
        staging.listFiles()?.filter { (it.name.startsWith("preview-") || it.name.startsWith("export-")) && System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.deleteRecursively() }
    }
    private fun previewStream(input: InputStream): BackupArchive {
        val dir = File(staging, "preview-${UUID.randomUUID()}").apply { check(mkdirs()) { "无法准备备份，请检查可用空间" } }
        try {
            val source = input.buffered()
            source.mark(4)
            val magic = ByteArray(4); val n = source.read(magic); source.reset()
            if (n < 4 || !magic.contentEquals(byteArrayOf(80, 75, 3, 4))) {
                val archive = BackupCodec.decode(readLimited(source))
                require(archive.images.isEmpty()) { "含图片备份必须使用完整ZIP文件" }
                dir.deleteRecursively()
                return archive
            }
            val zipFile = File(dir, "source.zip")
            zipFile.outputStream().use { BodyImageStore.copyLimited(source, it, MAX_ARCHIVE_BYTES) }
            val archive = ZipFile(zipFile).use { zip ->
                val entries = zip.entries().toList()
                require(entries.size <= 90_001 && entries.map { it.name }.distinct().size == entries.size) { "备份文件重复或过多" }
                require(entries.all { !it.isDirectory && (it.name == "manifest.json" || it.name.matches(Regex("images/[a-f0-9]{64}"))) }) { "备份包含非法路径或多余文件" }
                val manifest = zip.getEntry("manifest.json") ?: error("备份缺少清单")
                val parsed = zip.getInputStream(manifest).use { BackupCodec.decode(readLimited(it)) }
                require(parsed.formatVersion == 6) { "图片备份清单版本无效" }
                require(entries.map { it.name }.toSet() == setOf("manifest.json") + parsed.images.map { "images/${it.hash}" }) { "备份图片缺失或包含多余文件" }
                parsed.images.forEach { image ->
                    val entry = zip.getEntry("images/${image.hash}")
                    require(entry.size == image.bytes) { "图片大小不一致" }
                    val file = File(dir, image.hash)
                    zip.getInputStream(entry).use { inputImage -> file.outputStream().use { BodyImageStore.copyLimited(inputImage, it, image.bytes) } }
                    images.verify(image, file, decodePixels = true)
                }
                parsed.copy(imageDirectory = dir.path)
            }
            zipFile.delete()
            return archive
        } catch (e: Exception) {
            dir.deleteRecursively()
            if (e is java.util.zip.ZipException) throw IllegalArgumentException("备份压缩文件损坏或路径无效", e)
            throw e
        }
    }
    suspend fun restore(archive: BackupArchive) = withContext(Dispatchers.IO) {
        BackupCodec.validate(archive)
        archive.images.forEach { image -> images.verify(image, sourceFor(archive, image), decodePixels = true) }
        val owner = "restore-${UUID.randomUUID()}"
        try {
            repository.replaceData(archive.entries, archive.memos, archive.reminders) { previous, previousMemos, previousReminders ->
                val stream = snapshot.startWrite()
                try {
                    writeZip(stream, previous, previousMemos, previousReminders)
                    snapshot.finishWrite(stream)
                } catch (e: Exception) { snapshot.failWrite(stream); throw e }
                images.pin(owner, archive.images)
                archive.images.forEach { images.install(sourceFor(archive, it), it) }
            }
        } finally { images.release(owner) }
        runCatching { discard(archive) }
        // The self-contained snapshot owns its own images; it never relies on the live image directory.
        runCatching { repository.collectImages() }
    }
    private fun sourceFor(archive: BackupArchive, image: BodyImage): File {
        val path = archive.imageDirectory ?: return images.file(image)
        val dir = File(path)
        require(dir.parentFile?.canonicalFile == staging.canonicalFile && dir.name.startsWith("preview-")) { "备份预览已失效，请重新选择" }
        return File(dir, image.hash)
    }
    private fun writeZip(output: OutputStream, entries: List<Entry>, memos: List<Memo>, reminders: List<StandaloneReminder>) {
        val manifest = BackupCodec.encode(entries, memos = memos, reminders = reminders)
        val archive = BackupCodec.decode(manifest)
        archive.images.forEach(images::verify)
        val zip = ZipOutputStream(object : java.io.FilterOutputStream(output) { override fun close() { flush() } })
        zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toByteArray(Charsets.UTF_8)); zip.closeEntry()
        archive.images.forEach { image ->
            zip.putNextEntry(ZipEntry("images/${image.hash}"))
            images.file(image).inputStream().use { it.copyTo(zip) }; zip.closeEntry()
        }
        // Do not close the underlying AtomicFile stream before finishWrite/fsync.
        zip.finish(); zip.close()
    }
    private fun readLimited(stream: InputStream): String {
        val output = java.io.ByteArrayOutputStream()
        BodyImageStore.copyLimited(stream, output, BackupCodec.MAX_BYTES.toLong())
        return output.toByteArray().toString(Charsets.UTF_8)
    }
    companion object { const val MAX_ARCHIVE_BYTES = 1024L * 1024 * 1024 + 32L * 1024 * 1024 }
}
