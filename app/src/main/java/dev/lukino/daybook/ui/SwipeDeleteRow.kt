package dev.lukino.daybook.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Reveal a button, never delete as a side effect of a gesture. */
@Composable internal fun SwipeDeleteRow(
    id: String, opened: String?, enabled: Boolean,
    onOpen: (String?) -> Unit, onDelete: () -> Unit,
    modifier: Modifier = Modifier, content: @Composable () -> Unit,
) {
    val width = with(LocalDensity.current) { 88.dp.toPx() }
    var drag by remember(id) { mutableStateOf<Float?>(null) }
    val expanded = opened == id
    val offset by animateFloatAsState(drag ?: if (expanded) -width else 0f,
        animationSpec = tween(if (drag == null) 180 else 0), label = "delete-reveal")
    val currentOpen by rememberUpdatedState(onOpen)
    val currentExpanded by rememberUpdatedState(expanded)
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).testTag("swipe-$id")
        .semantics { if (enabled) customActions = listOf(CustomAccessibilityAction("删除") { onDelete(); true }) }
        .pointerInput(id, enabled, width) {
            if (!enabled) return@pointerInput
            detectHorizontalDragGestures(
                onDragStart = {
                    drag = if (currentExpanded) -width else 0f
                    currentOpen(null)
                },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    drag = ((drag ?: 0f) + amount).coerceIn(-width, 0f)
                },
                onDragEnd = { currentOpen(if ((drag ?: 0f) < -width / 2) id else null); drag = null },
                onDragCancel = { drag = null },
            )
        }) {
        if (offset < -1f) Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
            FilledTonalButton(enabled = enabled, onClick = onDelete,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer),
                contentPadding = PaddingValues(8.dp), modifier = Modifier.width(80.dp).testTag("delete-$id")) { Text("删除") }
        }
        Box(Modifier.offset { IntOffset(offset.roundToInt(), 0) }) {
            content()
            if (expanded) Box(Modifier.matchParentSize().clickable { onOpen(null) })
        }
    }
}
