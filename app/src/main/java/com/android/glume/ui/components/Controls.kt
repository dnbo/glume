package com.android.glume.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import com.android.glume.ui.theme.FocusScale
import com.android.glume.ui.theme.Glume
import com.android.glume.ui.theme.GlumeType

/** Text with the design defaults: one line, ellipsis. Pass [maxLines] for clamps. */
@Composable
fun T(
    text: String,
    style: TextStyle = GlumeType.body,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    tabular: Boolean = false,
    align: TextAlign? = null,
) {
    Text(
        text = text,
        style = if (tabular) style.copy(fontFeatureSettings = GlumeType.tabular) else style,
        color = if (color == Color.Unspecified) LocalContentColor.current else color,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        softWrap = maxLines > 1,
        textAlign = align,
    )
}

/** Uppercase kicker label ("ON THIS DAY", "RECOMMENDED"). */
@Composable
fun Label(text: String, color: Color = Glume.colors.accent, modifier: Modifier = Modifier) =
    T(text.uppercase(), GlumeType.label, modifier, color)

/** 72 px pill button. Primary = accent-strong fill. Focus inverts to white. */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    primary: Boolean = false,
    disabled: Boolean = false,
    onFocused: (() -> Unit)? = null,
) {
    val c = Glume.colors
    FocusSurface(
        onClick = onClick,
        modifier = modifier.height(72.dp),
        shape = Pill,
        containerColor = if (primary) c.accentStrong else Color.White.copy(alpha = 0.10f),
        focusedContainerColor = c.focusFill,
        contentColor = if (primary) Color.White else c.text,
        focusedContentColor = c.focusInk,
        onFocused = onFocused,
    ) {
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 36.dp).then(if (disabled) Modifier.alphaDim() else Modifier),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) GIcon(icon, 28.dp, strokeWidth = 2.4f, filled = icon == Ic.play)
            T(label, GlumeType.body.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold))
        }
    }
}

private val Pill = RoundedCornerShape(999.dp)

private fun Modifier.alphaDim() = alpha(0.4f)

/** Square icon button (mic: 96 x 96, radius 24). */
@Composable
fun IconTile(icon: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 96.dp, radius: Dp = 24.dp, iconSize: Dp = 36.dp) {
    val c = Glume.colors
    FocusSurface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = RoundedCornerShape(radius),
        containerColor = Color.White.copy(alpha = 0.10f),
        focusedContainerColor = c.focusFill,
        contentColor = c.text,
        focusedContentColor = c.focusInk,
    ) {
        GIcon(icon, iconSize, Modifier.align(Alignment.Center), strokeWidth = 2.2f)
    }
}

/** 56 px filter chip. [on] = selected (accent-soft). Focus inverts to white. */
@Composable
fun Chip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String? = null, on: Boolean = false, onFocused: (() -> Unit)? = null) {
    val c = Glume.colors
    FocusSurface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(999.dp),
        containerColor = if (on) c.accentSoft else Color.White.copy(alpha = 0.04f),
        focusedContainerColor = c.focusFill,
        contentColor = if (on) c.text else c.text2,
        focusedContentColor = c.focusInk,
        border = BorderStroke(2.dp, if (on) c.accent else c.line),
        onFocused = onFocused,
    ) {
        Row(Modifier.align(Alignment.Center).padding(horizontal = 26.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) GIcon(icon, 22.dp, strokeWidth = 2.2f)
            T(label, GlumeType.sec.copy(fontWeight = FontWeight(650)))
        }
    }
}

/** List row for settings, menus and suggestions: 84 px, radius 16, focus inverts to white at 1.02. */
@Composable
fun ListRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    minHeight: Dp = 84.dp,
    focusScale: Float = FocusScale.ROW,
    onLongClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    content: @Composable RowScope.(focused: Boolean) -> Unit,
) {
    val c = Glume.colors
    FocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = minHeight),
        shape = RoundedCornerShape(16.dp),
        focusScale = focusScale,
        containerColor = if (selected) Color.White.copy(alpha = 0.07f) else Color.Transparent,
        focusedContainerColor = c.focusFill,
        contentColor = c.text,
        focusedContentColor = c.focusInk,
        onLongClick = onLongClick,
        onFocused = onFocused,
    ) { focused ->
        Row(
            Modifier.align(Alignment.CenterStart).fillMaxWidth().padding(horizontal = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content(focused) }
    }
}

/** Secondary text that darkens on a focused (white) row. */
@Composable
fun RowSub(text: String, focused: Boolean, modifier: Modifier = Modifier) =
    T(text, GlumeType.sec, modifier, if (focused) Glume.colors.focusInk2 else Glume.colors.text2)

/** 72 x 40 switch. */
@Composable
fun Toggle(on: Boolean, rowFocused: Boolean = false) {
    val c = Glume.colors
    val x by animateDpAsState(if (on) 32.dp else 0.dp, tween(Glume.motion.focusMs), label = "toggle")
    Box(
        Modifier.size(72.dp, 40.dp).clip(RoundedCornerShape(999.dp)).background(
            when {
                on -> c.accentStrong
                rowFocused -> Color(0xFFC9CEDA)
                else -> Color.White.copy(alpha = 0.18f)
            },
        ),
    ) {
        Box(Modifier.offset(x = 5.dp + x, y = 5.dp).size(30.dp).clip(CircleShape).background(Color.White))
    }
}

/** On-screen keyboard key: 68 px tall, radius 12, focus inverts and scales 1.1. */
@Composable
fun KeyButton(label: String, width: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Glume.colors
    FocusSurface(
        onClick = onClick,
        modifier = modifier.size(width, 68.dp),
        shape = RoundedCornerShape(12.dp),
        focusScale = FocusScale.KEY,
        containerColor = Color.White.copy(alpha = 0.07f),
        focusedContainerColor = c.focusFill,
        contentColor = c.text,
        focusedContentColor = c.focusInk,
    ) {
        T(label, GlumeType.body.copy(fontWeight = FontWeight(650)), Modifier.align(Alignment.Center))
    }
}

/** Dark translucent badge on photos: durations, LIVE/RAW, day labels. */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier, pill: Boolean = false, icon: String? = null, heavy: Boolean = false) {
    Row(
        modifier
            .clip(RoundedCornerShape(if (pill) 999.dp else 8.dp))
            .background(Color(0xC70A0C11))
            .padding(horizontal = if (pill) 12.dp else 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) GIcon(icon, 16.dp, filled = true, tint = Color.White)
        T(
            text,
            GlumeType.badge.copy(fontWeight = FontWeight(if (heavy) 800 else 700), letterSpacing = if (heavy) 0.06f.em() else 0f.em()),
            color = Color.White,
            tabular = true,
        )
    }
}

private fun Float.em() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Em)

/** Round avatar. Without a [url], shows [name]'s initial on [color] (Immich's avatar color). */
@Composable
fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier, name: String? = null, color: Color? = null) {
    if (url == null && name != null) {
        Box(modifier.size(size).clip(CircleShape).background(color ?: Glume.colors.accentStrong), contentAlignment = Alignment.Center) {
            T(name.take(1).uppercase(), GlumeType.body.copy(fontSize = (size.value * 0.45f).sp, fontWeight = FontWeight.Bold), color = Color.White)
        }
        return
    }
    UrlImage(url, modifier.size(size).clip(CircleShape))
}

/** Immich's named avatar colors. */
fun immichAvatarColor(name: String): Color = when (name) {
    "pink" -> Color(0xFFDE7FB3)
    "red" -> Color(0xFFD95C5C)
    "yellow" -> Color(0xFFC9A227)
    "blue" -> Color(0xFF4A90D9)
    "green" -> Color(0xFF3FA66B)
    "purple" -> Color(0xFF9B6BD9)
    "orange" -> Color(0xFFE08A3C)
    "gray" -> Color(0xFF7A8090)
    "amber" -> Color(0xFFD9A13C)
    else -> Color(0xFF4553D6)
}

/** Connect-form field: label over value, 108 px, radius 18. Focus = ring + 1.03. */
@Composable
fun Field(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier, valueTracking: Boolean = false) {
    val c = Glume.colors
    FocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(108.dp),
        shape = RoundedCornerShape(18.dp),
        focusScale = FocusScale.LARGE,
        containerColor = c.surface2,
        contentColor = c.text,
        border = BorderStroke(2.dp, c.line),
    ) {
        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T(label, GlumeType.sec, color = c.text2)
            T(value, if (valueTracking) GlumeType.body.copy(letterSpacing = 0.2f.em()) else GlumeType.body)
        }
    }
}

/** Accent progress bar (continue cards, storage). */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, track: Color = Color.White.copy(alpha = 0.18f), height: Dp = 6.dp) {
    Box(modifier.height(height).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).background(Glume.colors.accent))
    }
}

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
