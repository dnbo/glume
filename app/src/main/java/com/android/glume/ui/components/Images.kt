package com.android.glume.ui.components

import android.graphics.Bitmap
import android.util.Base64
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.android.glume.ui.theme.Glume
import com.android.glume.util.ThumbHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decoded ThumbHash placeholders, shared app-wide. Each is ~32 px, so 600 of them is < 3 MB. */
object ThumbHashCache {
    private val cache = LruCache<String, ImageBitmap>(600)

    fun peek(hash: String): ImageBitmap? = cache.get(hash)

    fun decode(hash: String): ImageBitmap? {
        cache.get(hash)?.let { return it }
        val img = runCatching { ThumbHash.toRgba(Base64.decode(hash, Base64.DEFAULT)) }.getOrNull() ?: return null
        val px = IntArray(img.width * img.height) { i ->
            val j = i * 4
            ((img.rgba[j + 3].toInt() and 0xFF) shl 24) or ((img.rgba[j].toInt() and 0xFF) shl 16) or
                ((img.rgba[j + 1].toInt() and 0xFF) shl 8) or (img.rgba[j + 2].toInt() and 0xFF)
        }
        val bmp = Bitmap.createBitmap(px, img.width, img.height, Bitmap.Config.ARGB_8888).asImageBitmap()
        cache.put(hash, bmp)
        return bmp
    }
}

/** The placeholder bitmap for [hash], decoded off the main thread on first use. */
@Composable
fun rememberThumbHash(hash: String?): ImageBitmap? {
    if (hash == null) return null
    var bmp by remember(hash) { mutableStateOf(ThumbHashCache.peek(hash)) }
    if (bmp == null) {
        LaunchedEffect(hash) { bmp = withContext(Dispatchers.Default) { ThumbHashCache.decode(hash) } }
    }
    return bmp
}

/** Draws [bmp] stretched over the whole node; bilinear upscaling of a 32 px image reads as a blur. */
fun Modifier.drawPlaceholder(bmp: ImageBitmap?): Modifier =
    if (bmp == null) this else drawBehind {
        drawImage(bmp, IntOffset.Zero, IntSize(bmp.width, bmp.height), IntOffset.Zero, IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Low)
    }

/**
 * An asset image: ThumbHash immediately, then the real image (decoded at the node's size by Coil)
 * crossfades in. Use thumbnail URLs in grids/rows and preview URLs in the viewer.
 */
@Composable
fun AssetImage(
    url: String,
    thumbhash: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = 1f,
) {
    val placeholder = rememberThumbHash(thumbhash)
    val ctx = LocalPlatformContext.current
    val fade = Glume.motion.sharpenMs.coerceAtMost(300)
    val request = remember(url, fade) {
        ImageRequest.Builder(ctx).data(url).crossfade(fade).build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = contentScale,
        alpha = alpha,
        modifier = modifier.drawPlaceholder(placeholder),
    )
}

/** Plain remote image (avatars, map) with no placeholder hash. */
@Composable
fun UrlImage(url: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, alpha: Float = 1f) {
    if (url == null) {
        Box(modifier.background(Glume.colors.surface2))
        return
    }
    val ctx = LocalPlatformContext.current
    val request = remember(url) { ImageRequest.Builder(ctx).data(url).crossfade(200).build() }
    AsyncImage(model = request, contentDescription = null, contentScale = contentScale, alpha = alpha, modifier = modifier.background(Glume.colors.surface2))
}

/**
 * The ambient backdrop behind screens: the focused item's ThumbHash, stretched, faded to 32 % and
 * covered by the design's bottom-heavy gradient. A 32 px bitmap instead of a 90 px CSS blur keeps it cheap.
 */
@Composable
fun AmbientBackdrop(thumbhash: String?, modifier: Modifier = Modifier) {
    val bg = Glume.colors.bg
    val gradient = remember(bg) {
        Brush.verticalGradient(0f to bg.copy(alpha = 0.25f), 0.62f to bg.copy(alpha = 0.82f), 1f to bg)
    }
    Crossfade(targetState = thumbhash, animationSpec = tween(600), modifier = modifier.fillMaxSize(), label = "ambient") { hash ->
        val bmp = rememberThumbHash(hash)
        if (bmp != null) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width * 1.2f
                val h = size.height * 1.2f
                drawImage(
                    bmp, IntOffset.Zero, IntSize(bmp.width, bmp.height),
                    IntOffset((-size.width * 0.1f).toInt(), (-size.height * 0.1f).toInt()), IntSize(w.toInt(), h.toInt()),
                    alpha = 0.32f, filterQuality = FilterQuality.Low,
                )
                drawRect(gradient)
            }
        }
    }
}

/** One shared shimmer clock for all skeleton tiles, so loading N tiles costs one animation. */
val LocalShimmer = staticCompositionLocalOf<State<Float>> { mutableStateOf(0f) }

@Composable
fun ShimmerProvider(content: @Composable () -> Unit) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val p = t.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "p")
    CompositionLocalProvider(LocalShimmer provides p, content = content)
}

private val skelA = Color(0xFF171B24)
private val skelB = Color(0xFF232838)

/** Skeleton fill; reads the shared shimmer in the draw phase only (no recomposition). */
fun Modifier.skeleton(progress: State<Float>): Modifier = drawBehind {
    val w = size.width
    val x = -w + progress.value * w * 3
    drawRect(Brush.linearGradient(listOf(skelA, skelB, skelA), start = Offset(x - w * 0.4f, 0f), end = Offset(x + w * 0.4f, size.height)))
}

@Composable
fun SkeletonBox(modifier: Modifier) {
    val p = LocalShimmer.current
    Box(modifier.background(skelA).skeleton(p))
}

/** Converts a design-px pivot into a [PivotSpec] for the current density. */
@Composable
fun rememberPivot(pivot: Dp, band: Dp = Dp(0f)): PivotSpec {
    val d = LocalDensity.current
    return remember(d, pivot, band) { with(d) { PivotSpec(pivot.toPx(), band.toPx()) } }
}
