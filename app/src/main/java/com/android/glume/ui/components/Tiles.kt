package com.android.glume.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.glume.domain.Album
import com.android.glume.domain.Asset
import com.android.glume.domain.AssetKind
import com.android.glume.domain.ContinueItem
import com.android.glume.domain.ContinueKind
import com.android.glume.domain.Memory
import com.android.glume.domain.Person
import com.android.glume.domain.Place
import com.android.glume.ui.theme.FocusScale
import com.android.glume.ui.theme.Glume
import com.android.glume.ui.theme.GlumeType
import com.android.glume.util.Format
import kotlinx.coroutines.delay

private val gradBottom = Brush.verticalGradient(0f to Color(0x000A0C11), 0.45f to Color(0x000A0C11), 1f to Color(0xD9080A0E))

/** Photo/video tile with the design's badges (duration, LIVE/RAW, favorite, day). */
@Composable
fun PhotoTile(
    asset: Asset,
    favorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    dayLabel: String? = null,
    inset: Dp = 12.dp,
    dim: Boolean = false,
    caption: String? = null,
) {
    FocusSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        onFocused = onFocused,
        modifier = modifier,
        containerColor = Glume.colors.surface,
    ) {
        AssetImage(asset.thumbUrl, asset.thumbhash, Modifier.fillMaxSize(), alpha = if (dim) 0.5f else 1f)
        if (dayLabel != null) Badge(dayLabel, Modifier.align(Alignment.TopStart).padding(inset - 2.dp))
        if (caption != null) Badge(caption, Modifier.align(Alignment.BottomStart).padding(inset - 2.dp))
        when (asset.kind) {
            AssetKind.VIDEO -> Badge(Format.duration(asset.durationSec), Modifier.align(Alignment.BottomEnd).padding(inset), pill = true, icon = Ic.play)
            AssetKind.LIVE -> Badge("LIVE", Modifier.align(Alignment.TopEnd).padding(inset), heavy = true)
            AssetKind.RAW -> Badge("RAW", Modifier.align(Alignment.TopEnd).padding(inset), heavy = true)
            else -> Unit
        }
        if (favorite && caption == null) {
            GIcon(Ic.heart, 26.dp, Modifier.align(Alignment.BottomStart).padding(inset), filled = true, tint = Color.White)
        }
    }
}

/** Skeleton stand-in for a tile while its bucket loads. */
@Composable
fun TileSkeleton(modifier: Modifier, radius: Dp = 14.dp) = SkeletonBox(modifier.clip(RoundedCornerShape(radius)))

/** Album card: cover tile + title + meta below. */
@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tileHeight: Dp = 240.dp,
    avatarSize: Dp = 44.dp,
    onLongClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    tileModifier: Modifier = Modifier,
    offline: Boolean = false,
) {
    val c = Glume.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FocusSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            onFocused = onFocused,
            modifier = tileModifier.fillMaxWidth().height(tileHeight),
            containerColor = c.surface,
        ) {
            val cover = album.cover
            if (cover != null) {
                AssetImage(cover.thumbUrl, cover.thumbhash, Modifier.fillMaxSize(), alpha = if (offline) 0.5f else 1f)
            } else {
                Column(Modifier.fillMaxSize().background(c.surface2), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                    GIcon(Ic.album, 56.dp, strokeWidth = 1.6f, tint = c.text3)
                    T("No photos yet", GlumeType.badge.copy(fontWeight = FontWeight(600)), color = c.text3)
                }
            }
            if (album.shared) {
                Row(
                    Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(999.dp)).background(Color(0xBF0A0C11)).padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    GIcon(Ic.people, 20.dp, strokeWidth = 2.2f, tint = Color.White)
                    T("Shared", GlumeType.badge, color = Color.White)
                }
            }
            Avatar(album.ownerAvatarUrl, avatarSize, Modifier.align(Alignment.BottomEnd).padding(12.dp).border(3.dp, Color(0xD90A0C11), CircleShape))
            if (offline) {
                Column(Modifier.fillMaxSize().background(Color(0xB810131A)), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                    GIcon(Ic.cloudOff, 40.dp, tint = c.text2)
                    T("Not available offline", GlumeType.badge.copy(fontWeight = FontWeight(600)), color = c.text2)
                }
            }
        }
        T(album.title, GlumeType.body.copy(fontWeight = FontWeight.Bold))
        T(
            (if (album.count > 0) "${album.count} photos" else "Empty") + " · " + album.dateLabel,
            GlumeType.sec, color = c.text2, modifier = Modifier.padding(top = 0.dp).graphicsLayer { translationY = -6f },
        )
    }
}

/** Round person avatar with name (and optional count) below. */
@Composable
fun PersonAvatar(
    person: Person,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = size + 12.dp,
    showCount: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    avatarModifier: Modifier = Modifier,
) {
    val c = Glume.colors
    Column(modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FocusSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            onFocused = onFocused,
            shape = CircleShape,
            modifier = avatarModifier.size(size),
            containerColor = c.surface2,
        ) {
            UrlImage(person.avatarUrl, Modifier.fillMaxSize())
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            T(person.name ?: "Add a name", GlumeType.body.copy(fontWeight = FontWeight(650)), color = if (person.name == null) c.text3 else c.text, modifier = Modifier.widthIn(max = width + 32.dp))
            if (showCount) T("${person.count} photos", GlumeType.sec, color = c.text2, tabular = true)
        }
    }
}

/** Continue-row card (resume slideshow / last album / back where you were). */
@Composable
fun ContinueCard(item: ContinueItem, onClick: () -> Unit, modifier: Modifier = Modifier, onFocused: (() -> Unit)? = null) {
    FocusSurface(onClick = onClick, onFocused = onFocused, modifier = modifier.size(440.dp, 248.dp), shape = RoundedCornerShape(18.dp), containerColor = Glume.colors.surface) {
        AssetImage(item.cover.thumbUrl, item.cover.thumbhash, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x330A0C11), 0.3f to Color(0x330A0C11), 1f to Color(0xE6080A0E))))
        Box(Modifier.padding(start = 24.dp, top = 22.dp).size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
            val icon = when (item.kind) {
                ContinueKind.RESUME_SLIDESHOW -> Ic.play
                ContinueKind.ALBUM -> Ic.album
                ContinueKind.TIMELINE -> Ic.grid
            }
            GIcon(icon, 26.dp, strokeWidth = 2.4f, tint = Color(0xFF0A0C11))
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, end = 24.dp, bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T(item.title, GlumeType.body.copy(fontWeight = FontWeight.Bold), color = Color.White)
            T(item.subtitle, GlumeType.sec, color = Color(0xFFC3C9D6))
        }
        ProgressBar(item.progress, Modifier.align(Alignment.BottomStart).fillMaxWidth())
    }
}

/** Places grid tile: photo with name + meta over a gradient. */
@Composable
fun PlaceTile(place: Place, onClick: () -> Unit, modifier: Modifier = Modifier, onFocused: (() -> Unit)? = null) {
    FocusSurface(onClick = onClick, onFocused = onFocused, modifier = modifier, containerColor = Glume.colors.surface) {
        AssetImage(place.cover.thumbUrl, place.cover.thumbhash, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.4f to Color.Transparent, 1f to Color(0xE0080A0E))))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 22.dp, end = 22.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T(place.name, GlumeType.title, color = Color.White)
            T("${place.country} · ${place.count} photos", GlumeType.sec, color = Color(0xFFC3C9D6))
        }
    }
}

/** Translucent pill used on hero cards ("ON THIS DAY", "2023"). */
@Composable
fun HeroPill(text: String, bg: Color, color: Color = Color.White, modifier: Modifier = Modifier) {
    T(
        text.uppercase(),
        GlumeType.label,
        modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 16.dp, vertical = 10.dp),
        color = color,
        tabular = true,
    )
}

/**
 * "On this day" hero: crossfades through the memory's photos every 5.5 s with a cheap Ken Burns
 * (graphicsLayer scale/translate only), plus the text block and page dots.
 */
@Composable
fun MemoryHero(
    memory: Memory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    onFrame: ((Asset) -> Unit)? = null,
) {
    val motion = Glume.motion
    var frame by remember(memory.id) { mutableIntStateOf(0) }
    val frames = memory.assets
    LaunchedEffect(memory.id) {
        while (frames.size > 1) {
            delay(5500)
            frame = (frame + 1) % frames.size
        }
    }
    LaunchedEffect(frame) { frames.getOrNull(frame)?.let { onFrame?.invoke(it) } }
    FocusSurface(
        onClick = onClick, onLongClick = onLongClick, onFocused = onFocused,
        modifier = modifier.size(1040.dp, 460.dp), shape = RoundedCornerShape(20.dp), focusScale = FocusScale.LARGE,
        containerColor = Glume.colors.surface,
    ) {
        frames.forEachIndexed { j, a ->
            // Only the current and previous frames are composed.
            val prev = (frame - 1 + frames.size) % frames.size
            if (j == frame || j == prev) KenBurnsImage(a, visible = j == frame, variant = j % 3, durationMs = 9000, crossfadeMs = motion.crossfadeMs, enabled = motion.kenBurns)
        }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xD9080A0E), 0.48f to Color(0x66080A0E), 0.75f to Color(0x00080A0E))))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 52.dp, end = 52.dp, bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroPill("On this day", Color(0x3D7C8CFF), Color(0xFFD4D9FF))
                HeroPill(memory.year.toString(), Color.White.copy(alpha = 0.16f))
            }
            T(memory.agoLabel, GlumeType.display, color = Color.White)
            T("${memory.place} · ${memory.count} photos", GlumeType.body, color = Color(0xFFD5DAE4))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                GIcon(Ic.play, 24.dp, filled = true, tint = Color(0xFFC3C9D6))
                T("Press OK to play this memory", GlumeType.sec, color = Color(0xFFC3C9D6))
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 52.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            frames.indices.forEach { j ->
                val w by animateFloatAsState(if (j == frame) 28f else 10f, tween(300), label = "dot")
                Box(Modifier.size(w.dp, 10.dp).clip(RoundedCornerShape(5.dp)).background(if (j == frame) Color.White else Color.White.copy(alpha = 0.4f)))
            }
        }
    }
}

/** Side memory card next to the hero (260 x 460). */
@Composable
fun MemoryCard(memory: Memory, onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, onFocused: (() -> Unit)? = null) {
    val cover = memory.assets.first()
    FocusSurface(onClick = onClick, onLongClick = onLongClick, onFocused = onFocused, modifier = modifier.size(260.dp, 460.dp), shape = RoundedCornerShape(20.dp), containerColor = Glume.colors.surface) {
        AssetImage(cover.thumbUrl, cover.thumbhash, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(gradBottom))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, end = 24.dp, bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroPill(memory.year.toString(), Color.White.copy(alpha = 0.16f))
            T(memory.agoLabel, GlumeType.title, color = Color.White)
            T(memory.place, GlumeType.sec, color = Color(0xFFC3C9D6))
        }
    }
}

/**
 * A full-bleed image that fades in/out with [visible] and drifts with a Ken Burns move. GPU-only:
 * alpha + scale + translation on a graphics layer.
 */
@Composable
fun KenBurnsImage(
    asset: Asset,
    visible: Boolean,
    variant: Int,
    durationMs: Int,
    crossfadeMs: Int,
    enabled: Boolean,
    useThumb: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(crossfadeMs), label = "xf")
    val progress = remember(asset.id) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(asset.id, enabled) {
        if (enabled) progress.animateTo(1f, tween(durationMs, easing = LinearEasing)) else progress.snapTo(0f)
    }
    // From/to (scale, tx%, ty%) for the three moves in the design's CSS (kbA, kbB, kbC).
    val moves = KB_MOVES[variant % 3]
    Box(
        modifier.fillMaxSize().graphicsLayer {
            val p = progress.value
            val s = moves[0] + (moves[3] - moves[0]) * p
            scaleX = s; scaleY = s
            translationX = size.width * (moves[1] + (moves[4] - moves[1]) * p) / 100f
            translationY = size.height * (moves[2] + (moves[5] - moves[2]) * p) / 100f
            this.alpha = alpha
        },
    ) {
        AssetImage(if (useThumb) asset.thumbUrl else asset.previewUrl, asset.thumbhash, Modifier.fillMaxSize())
    }
}

private val KB_MOVES = listOf(
    floatArrayOf(1.02f, 0f, 0f, 1.16f, -2.5f, -1.5f),
    floatArrayOf(1.18f, 2f, 1f, 1.04f, -1f, 0f),
    floatArrayOf(1.05f, -2f, 1.5f, 1.17f, 2f, -1f),
)
