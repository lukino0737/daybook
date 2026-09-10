package dev.lukino.daybook.backup

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import dev.lukino.daybook.data.EntryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

class BackupService(context: Context, private val repository: EntryRepository) {
    private val resolver = context.contentResolver
    private val snapshot = AtomicFile(File(context.filesDir, "before-restore.json"))

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val content = BackupCodec.encode(repository.all())
        (resolver.openOutputStream(uri, "wt") ?: error("无法写入所选文件"))
            .bufferedWriter(Charsets.UTF_8).use { it.write(content) }
    }
    suspend fun preview(uri: Uri): BackupArchive = withContext(Dispatchers.IO) {
        (resolver.openInputStream(uri) ?: error("无法读取所选文件")).use { BackupCodec.decode(readLimited(it)) }
    }
    suspend fun previewSnapshot(): BackupArchive = withContext(Dispatchers.IO) {
        require(snapshot.baseFile.exists()) { "还没有替换前快照" }
        snapshot.openRead().use { BackupCodec.decode(readLimited(it)) }
    }
    suspend fun restore(archive: BackupArchive) = withContext(Dispatchers.IO) {
        repository.replaceAll(archive.entries) { previous ->
            val encoded = BackupCodec.encode(previous)
            val stream = snapshot.startWrite()
            try { stream.write(encoded.toByteArray(Charsets.UTF_8)); snapshot.finishWrite(stream) }
            catch (e: Exception) { snapshot.failWrite(stream); throw e }
        }
    }
    private fun readLimited(stream: InputStream): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            require(output.size() + count <= BackupCodec.MAX_BYTES) { "备份超过 20 MB" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray().toString(Charsets.UTF_8)
    }
}
