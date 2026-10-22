package com.android.glume.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.LocalContentColor
import com.android.glume.ui.theme.FocusScale
import com.android.glume.ui.theme.Glume
import com.android.glume.ui.theme.GlumeColors
import com.android.glume.ui.theme.GlumeMotion

/**
 * The app-wide focusable surface. Focus = scale (graphicsLayer, no relayout) + 4 px ring + a cheap
 * stroked glow; no blurred shadows, so it stays smooth on the Streamer. Optionally inverts its colors
 * when focused (buttons, chips, rows, rail items).
 *
 * Long-press OK is handled by combinedClickable's key support and opens context menus.
 */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    focusScale: Float = FocusScale.TILE,
    containerColor: Color = Color.Transparent,
    focusedContainerColor: Color = containerColor,
    contentColor: Color = Glume.colors.text,
    focusedContentColor: Color = contentColor,
    onLongClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    ring: Boolean = true,
    clip: Boolean = true,
    border: BorderStroke? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    var focused by remember { mutableStateOf(false) }
    val pressed by interaction.collectIsPressedAsState()
    val motion = Glume.motion
    val colors = Glume.colors
    val target = when {
        focused && pressed -> FocusScale.PRESSED
        focused -> focusScale
        else -> 1f
    }
    val scale by animateFloatAsState(target, tween(motion.focusMs, easing = GlumeMotion.Ease), label = "focusScale")
    Box(
        modifier
            .zIndex(if (focused) 3f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (ring && focused) Modifier.focusRing(shape, colors) else Modifier)
            .then(if (border != null && !focused) Modifier.border(border, shape) else Modifier)
            .background(if (focused) focusedContainerColor else containerColor, shape)
            .then(if (clip) Modifier.clip(shape) else Modifier)
            .onFocusChanged {
                if (it.isFocused != focused) {
                    focused = it.isFocused
                    if (it.isFocused) onFocused?.invoke()
                }
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides if (focused) focusedContentColor else contentColor) {
            content(focused)
        }
    }
}

/** Ring + soft glow drawn outside the shape. High contrast: thicker yellow ring with a black gap. */
fun Modifier.focusRing(shape: Shape, colors: GlumeColors): Modifier = drawWithCache {
    fun outline(grow: Float) = shape.createOutline(Size(size.width + grow * 2, size.height + grow * 2), layoutDirection, this)
    if (colors.highContrast) {
        val black = outline(7.5f.dp.toPx())
        val ring = outline(3f.dp.toPx())
        onDrawBehind {
            translate(-7.5f.dp.toPx(), -7.5f.dp.toPx()) { drawOutline(black, Color.Black, style = Stroke(3f.dp.toPx())) }
            translate(-3f.dp.toPx(), -3f.dp.toPx()) { drawOutline(ring, colors.ring, style = Stroke(6f.dp.toPx())) }
        }
    } else {
        val g1 = 10f.dp.toPx()
        val g2 = 18f.dp.toPx()
        val r = 2f.dp.toPx()
        val glow1 = outline(g1)
        val glow2 = outline(g2)
        val ring = outline(r)
        onDrawBehind {
            translate(-g2, -g2) { drawOutline(glow2, colors.glow.copy(alpha = colors.glow.alpha * 0.25f), style = Stroke(12f.dp.toPx())) }
            translate(-g1, -g1) { drawOutline(glow1, colors.glow.copy(alpha = colors.glow.alpha * 0.55f), style = Stroke(12f.dp.toPx())) }
            translate(-r, -r) { drawOutline(ring, colors.ring, style = Stroke(4f.dp.toPx())) }
        }
    }
}

/**
 * Remembers which item had focus on a screen (lives in the screen's ViewModel, so it survives
 * navigating to the viewer and back) and can put focus back on it.
 */
@Stable
class FocusMemory(initialKey: Any? = null) {
    var lastKey: Any? = initialKey
    private val requesters = HashMap<Any, FocusRequester>()

    internal fun register(key: Any, fr: FocusRequester) { requesters[key] = fr }
    internal fun unregister(key: Any, fr: FocusRequester) { if (requesters[key] === fr) requesters.remove(key) }

    fun requester(key: Any): FocusRequester? = requesters[key]

    /** Focuses the remembered item, else [fallbackKey]. Returns false if neither is composed. */
    fun restore(fallbackKey: Any? = null): Boolean {
        val fr = lastKey?.let(requesters::get) ?: fallbackKey?.let(requesters::get) ?: return false
        return runCatching { fr.requestFocus() }.isSuccess
    }

    fun focus(key: Any): Boolean {
        val fr = requesters[key] ?: return false
        return runCatching { fr.requestFocus() }.isSuccess
    }
}

/** Registers this element with [memory] under [key]. Apply before the element's focus target. */
@Composable
fun Modifier.remembered(memory: FocusMemory, key: Any): Modifier {
    val fr = remember(key) { FocusRequester() }
    DisposableEffect(memory, key) {
        memory.register(key, fr)
        onDispose { memory.unregister(key, fr) }
    }
    return this
        .focusRequester(fr)
        .onFocusChanged { if (it.isFocused) memory.lastKey = key }
}

/**
 * Once [ready] is true, waits a frame for layout and restores focus to the remembered item (or the
 * fallback). Re-runs when the screen re-enters composition, e.g. after Back from the viewer.
 */
@Composable
fun RestoreFocus(memory: FocusMemory, ready: Boolean, fallbackKey: Any?) {
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        repeat(4) {
            withFrameNanos { }
            if (memory.restore(fallbackKey)) return@LaunchedEffect
        }
    }
}

/**
 * Scrolls so the focused item's leading edge sits [pivot] px from the viewport start, the TV
 * "docked focus" pattern from the design (rows keep focus in column 2, grids dock a row from the top).
 * Items whose required scroll is between 0 and [band] are left alone, so headers don't twitch.
 */
@OptIn(ExperimentalFoundationApi::class)
class PivotSpec(private val pivot: Float, private val band: Float = 0f) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val d = offset - pivot
        if (band > 0f && d > 0f && d < band && offset + size <= containerSize) return 0f
        return d
    }
}

/** For containers that scroll themselves (Home): ignore bring-into-view requests. */
@OptIn(ExperimentalFoundationApi::class)
object NoScrollSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
}

/** Media play/pause key on a focused element (⏯ starts a slideshow from the focused item). */
fun Modifier.onPlayPause(action: () -> Unit): Modifier = onKeyEvent {
    if (it.type == KeyEventType.KeyDown && (it.key == Key.MediaPlayPause || it.key == Key.MediaPlay)) {
        action(); true
    } else false
}
