package io.music_assistant.client.ui.compose.home.players

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import io.music_assistant.client.utils.conditional
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Keeps the same three content slots alive when the window's fold changes axis or disappears. */
@Composable
internal fun PlayerPaneLayout(
    sideQueue: Boolean,
    queueAvailable: Boolean,
    header: @Composable (Boolean) -> Unit,
    player: @Composable (separateQueue: Boolean, scrollable: Boolean) -> Unit,
    queue: @Composable () -> Unit,
) {
    val density = LocalDensity.current.density
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val regions = LocalPlayerWindowGeometry.current.regions.map {
        Rect(
            (it.left * density).toFloat() - bounds.left,
            (it.top * density).toFloat() - bounds.top,
            (it.right * density).toFloat() - bounds.left,
            (it.bottom * density).toFloat() - bounds.top,
        ) to it.division
    }
    val localBounds = Rect(0f, 0f, bounds.width, bounds.height)
    val division = regions.firstOrNull { (frame, division) ->
        division && frame.overlaps(localBounds)
    }?.first
    val separated = division != null
    Layout(
        modifier = Modifier.padding(bottom = 4.dp).onGloballyPositioned { bounds = it.boundsInWindow() },
        content = {
            Box { header(separated || sideQueue) }
            BoxWithConstraints(Modifier.conditional(separated) { clipToBounds() }) {
                // Keep a single composition site so folding doesn't recreate player controls.
                val scroll = rememberScrollState()
                val contentHeight = maxHeight.coerceAtLeast(
                    WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND.dp,
                )
                Box(Modifier.conditional(separated) { verticalScroll(scroll) }) {
                    Box(Modifier.conditional(separated) { height(contentHeight) }) {
                        player(separated || sideQueue, separated)
                    }
                }
            }
            Box(Modifier.conditional(separated) { clipToBounds() }) {
                if (separated || sideQueue) queue()
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val whole = Rect(0f, 0f, width.toFloat(), height.toFloat())
        // Round outwards so neither pane borrows a fractional pixel from the reserved band.
        val fold = division?.intersect(whole)?.let {
            Rect(floor(it.left), floor(it.top), ceil(it.right), ceil(it.bottom))
        }
        val vertical = fold?.let { it.height > it.width } ?: false
        val primary = when {
            fold == null -> whole
            vertical -> Rect(0f, 0f, fold.left, height.toFloat())
            else -> Rect(0f, 0f, width.toFloat(), fold.top)
        }
        val secondary = when {
            fold == null -> whole
            vertical -> Rect(fold.right, 0f, width.toFloat(), height.toFloat())
            else -> Rect(0f, fold.bottom, width.toFloat(), height.toFloat())
        }
        // A camera can sit inside the safe area. Reserve the largest remaining
        // rectangle, including beside a full-height side strip on an iPad.
        val headerHeight = 56.dp.roundToPx()
        val headerBounds = regions.filterNot { it.second }.fold(primary) { available, (frame, _) ->
            val header = Rect(available.left, available.top, available.right, available.top + headerHeight)
            if (!frame.overlaps(header)) {
                available
            } else {
                val obstruction = frame.intersect(available)
                listOf(
                    Rect(available.left, obstruction.bottom, available.right, available.bottom),
                    Rect(available.left, available.top, obstruction.left, available.bottom),
                    Rect(obstruction.right, available.top, available.right, available.bottom),
                ).maxBy { it.width * it.height }
            }
        }
        val headerPlaceable = measurables[0].measure(
            Constraints(
                maxWidth = headerBounds.width.roundToInt().coerceAtLeast(0),
                maxHeight = headerBounds.height.roundToInt().coerceAtLeast(0),
            ),
        )
        val contentTop = headerBounds.top.roundToInt() + headerPlaceable.height + 8.dp.roundToPx()
        val playerWidth = when {
            separated || !sideQueue || !queueAvailable -> primary.width.roundToInt()
            else -> width * 2 / 3
        }.coerceAtLeast(0)
        val playerPlaceable = measurables[1].measure(
            Constraints.fixed(playerWidth, (primary.bottom.roundToInt() - contentTop).coerceAtLeast(0)),
        )
        val queueLeft = if (separated) secondary.left.roundToInt() else playerWidth
        val queueTop = if (separated) secondary.top.roundToInt() else contentTop
        val queuePlaceable = measurables[2].measure(
            Constraints.fixed(
                if (separated) secondary.width.roundToInt().coerceAtLeast(0) else (width - playerWidth),
                (secondary.bottom.roundToInt() - queueTop).coerceAtLeast(0),
            ),
        )
        layout(width, height) {
            headerPlaceable.place(headerBounds.left.roundToInt(), headerBounds.top.roundToInt())
            playerPlaceable.place(primary.left.roundToInt(), contentTop)
            queuePlaceable.place(queueLeft, queueTop)
        }
    }
}
