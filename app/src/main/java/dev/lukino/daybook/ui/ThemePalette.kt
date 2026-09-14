package dev.lukino.daybook.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlin.math.*

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ThemePalette(seed: Int, custom: Boolean, hasImage: Boolean, enabled: Boolean,
    onColor: (Int) -> Unit, onFollowImage: () -> Unit) {
    val hsv = remember(seed) { FloatArray(3).also { AndroidColor.colorToHSV(seed, it) } }
    // Preserve hue even when saturation or brightness is zero.
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var brightness by remember { mutableFloatStateOf(hsv[2]) }
    LaunchedEffect(seed) {
        if (hsv[1] > 0f && hsv[2] > 0f) hue = hsv[0]
        if (hsv[2] > 0f) saturation = hsv[1]
        brightness = hsv[2]
    }
    fun update(h: Float = hue, s: Float = saturation, v: Float = brightness) {
        hue = h; saturation = s; brightness = v
        onColor(AndroidColor.HSVToColor(floatArrayOf(h, s, v)))
    }
    val pickPoint by rememberUpdatedState<(Offset, Int) -> Unit>({ point, width ->
        val center = width / 2f
        val dx = point.x - center; val dy = point.y - center
        update(h = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f)
    })
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("主题色", style = MaterialTheme.typography.titleMedium)
        Text(if (custom) "自定义" else if (hasImage) "跟随背景取色" else "默认配色")
        Canvas(Modifier.size(180.dp).align(Alignment.CenterHorizontally).testTag("color-wheel")
            .semantics { contentDescription = "色相圆盘；也可使用下方色相滑块" }
            .pointerInput(enabled) { if (enabled) detectTapGestures { pickPoint(it, size.width) } }
            .pointerInput(enabled) { if (enabled) detectDragGestures(onDragStart = { pickPoint(it, size.width) }) { change, _ ->
                change.consume(); pickPoint(change.position, size.width)
            } }) {
            drawCircle(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
            drawCircle(Color(seed), radius = size.minDimension * .29f)
            drawCircle(Color.White, radius = size.minDimension * .29f, style = Stroke(3.dp.toPx()))
            val angle = hue * PI.toFloat() / 180f
            val position = center + Offset(cos(angle), sin(angle)) * (size.minDimension * .4f)
            drawCircle(Color.Black, 10.dp.toPx(), position)
            drawCircle(Color.White, 7.dp.toPx(), position)
        }
        Text("色相")
        Slider(hue, { update(h = it) }, enabled = enabled, valueRange = 0f..359.9f,
            modifier = Modifier.testTag("color-hue").semantics { contentDescription = "色相" })
        Text("饱和度")
        Slider(saturation, { update(s = it) }, enabled = enabled,
            modifier = Modifier.testTag("color-saturation").semantics { contentDescription = "饱和度" })
        Text("明度")
        Slider(brightness, { update(v = it) }, enabled = enabled,
            modifier = Modifier.testTag("color-brightness").semantics { contentDescription = "明度" })
        Text("常用颜色")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("松绿" to 0xFF466553, "海蓝" to 0xFF2878B5, "紫藤" to 0xFF8750B5,
                "玫红" to 0xFFB83F78, "橙色" to 0xFFE18335, "金黄" to 0xFFE0BD32, "灰色" to 0xFF777777).forEach { (name, color) ->
                Box(Modifier.size(48.dp).background(Color(color), CircleShape)
                    .clickable(enabled = enabled, role = Role.Button) { onColor(color.toInt()) }
                    .semantics { contentDescription = name; selected = seed == color.toInt() }
                    .testTag("preset-$name"))
            }
        }
        Text("#%06X".format(seed and 0xFFFFFF), modifier = Modifier.testTag("color-value"))
        if (hasImage) TextButton(onClick = onFollowImage, enabled = enabled && custom,
            modifier = Modifier.testTag("follow-image")) { Text("跟随背景取色") }
    }
}
