package dev.lukino.daybook.appearance

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.AtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** Local appearance is deliberately separate from the user's record backup. */
data class Appearance(val file: File, val bitmap: Bitmap, val seed: Int)
data class AppearanceState(
    val current: Appearance? = null, val preview: Appearance? = null,
    val busy: Boolean = true, val error: String? = null,
)

class AppearanceStore(private val context: Context, private val directory: File = File(context.filesDir, "appearance")) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(AppearanceState())
    val state = mutable.asStateFlow()
    private val config = AtomicFile(File(directory, "current.json"))
    val initialized = operation {
        directory.mkdirs()
        if (config.baseFile.exists()) {
            val json = JSONObject(config.openRead().bufferedReader().use { it.readText() })
            if (json.has("image")) {
                val name = json.getString("image")
                require(name.matches(Regex("[a-f0-9-]+\\.jpg")))
                val file = File(directory, name)
                val bitmap = BitmapFactory.decodeFile(file.path) ?: error("背景图片不可用，请重新选择。")
                mutable.value = mutable.value.copy(current = Appearance(file, bitmap, json.getInt("seed")))
            }
        }
        cleanup()
    }

    private fun operation(block: suspend () -> Unit): Job = scope.launch {
        mutex.withLock {
            mutable.value = mutable.value.copy(busy = true, error = null)
            try { withContext(Dispatchers.IO) { block() } }
            catch (e: Exception) { mutable.value = mutable.value.copy(error = e.message ?: "操作未完成，请重试。") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }

    fun select(uri: Uri): Job = operation {
        val input = File.createTempFile("import-", ".tmp", directory)
        val output = File(directory, "${UUID.randomUUID()}.jpg")
        try {
            context.contentResolver.openInputStream(uri)?.use { source ->
                input.outputStream().use { destination ->
                    val buffer = ByteArray(16 * 1024)
                    var count = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        count += read
                        require(count <= 32L * 1024 * 1024) { "图片超过 32 MB，请选择较小的图片。" }
                        destination.write(buffer, 0, read)
                    }
                }
            } ?: error("无法读取图片，请重新选择。")
            val bitmap = decodeImage(input)
            output.outputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)) { "图片保存失败，请重试。" }
                stream.fd.sync()
            }
            val preview = Appearance(output, bitmap, ImageColors.seed(bitmap))
            mutable.value = mutable.value.copy(preview = preview)
            cleanup()
        } catch (e: Exception) {
            output.delete()
            throw e
        } finally { input.delete() }
    }

    fun apply(): Job = operation {
        val value = mutable.value.preview ?: return@operation
        writeConfig(JSONObject().put("image", value.file.name).put("seed", value.seed))
        mutable.value = mutable.value.copy(current = value, preview = null)
        cleanup()
    }

    fun reset(): Job = operation {
        writeConfig(JSONObject())
        mutable.value = mutable.value.copy(current = null, preview = null)
        cleanup()
    }

    fun cancel(): Job = operation {
        mutable.value = mutable.value.copy(preview = null)
        cleanup()
    }

    private fun writeConfig(json: JSONObject) {
        val stream = config.startWrite()
        try {
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            config.finishWrite(stream)
        } catch (e: Exception) { config.failWrite(stream); throw e }
    }

    private fun cleanup() {
        val keep = setOfNotNull(mutable.value.current?.file?.name, mutable.value.preview?.file?.name)
        directory.listFiles()?.filter { (it.extension == "jpg" || it.extension == "tmp") && it.name !in keep }?.forEach { it.delete() }
    }
}

internal fun decodeImage(file: File): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "这张图片无法解码，请尝试 JPG、PNG 或其他系统支持的图片。" }
    // Bound both source dimensions and decoded memory before allocating a bitmap.
    require(bounds.outWidth.toLong() * bounds.outHeight <= 150_000_000L) { "图片尺寸过大，请选择较小的图片。" }
    val decoded = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val scale = minOf(1f, 1920f / max(info.size.width, info.size.height))
            decoder.setTargetSize(max(1, (info.size.width * scale).roundToInt()), max(1, (info.size.height * scale).roundToInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
        }
    } else decodeLegacyImage(file, bounds)
    // Flatten transparency onto the default paper; persistent JPEG and palette see the same pixels.
    val result = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
    Canvas(result).apply { drawColor(0xFFF8F6EF.toInt()); drawBitmap(decoded, 0f, 0f, null) }
    decoded.recycle()
    return result
}

internal fun decodeLegacyImage(file: File, bounds: BitmapFactory.Options = BitmapFactory.Options().apply {
    inJustDecodeBounds = true; BitmapFactory.decodeFile(file.path, this)
}): Bitmap {
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > 1920) sample *= 2
    val source = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("这张图片无法解码，请重新选择。")
    val orientation = runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
    val matrix = Matrix().apply {
        when (orientation) {
            2 -> setScale(-1f, 1f)
            3 -> setRotate(180f)
            4 -> setScale(1f, -1f)
            5 -> { setRotate(90f); postScale(-1f, 1f) }
            6 -> setRotate(90f)
            7 -> { setRotate(-90f); postScale(-1f, 1f) }
            8 -> setRotate(-90f)
        }
    }
    val result = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    if (result !== source) source.recycle()
    return result
}
