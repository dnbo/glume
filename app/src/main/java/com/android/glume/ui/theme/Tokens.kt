package com.android.glume.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.android.glume.R

/*
 * Design tokens from the Immich TV design handoff (DesignSystem board + shared stylesheet).
 * All sizes are design pixels at 1920 x 1080. GlumeTheme rescales density so 1.dp == 1 design px
 * on any TV, which lets screens use the handoff numbers verbatim.
 */

@Immutable
data class GlumeColors(
    val bg: Color,
    val bg2: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val line: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val accent: Color,
    val accentStrong: Color,
    val accentSoft: Color,
    val ring: Color,
    val glow: Color,
    val danger: Color,
    val ok: Color,
    /** Fill of focused buttons, chips, rows and rail items (they invert to white). */
    val focusFill: Color,
    /** Text on [focusFill]. */
    val focusInk: Color,
    /** Secondary text on [focusFill]. */
    val focusInk2: Color,
    val highContrast: Boolean,
)

val DarkColors = GlumeColors(
    bg = Color(0xFF0A0C11),
    bg2 = Color(0xFF10131A),
    surface = Color(0xFF161A23),
    surface2 = Color(0xFF1E2330),
    surface3 = Color(0xFF272D3C),
    line = Color(0x17FFFFFF),
    text = Color(0xFFF3F5F9),
    text2 = Color(0xFFAEB5C4),
    text3 = Color(0xFF858DA0),
    accent = Color(0xFF7C8CFF),
    accentStrong = Color(0xFF4553D6),
    accentSoft = Color(0x297C8CFF),
    ring = Color(0xFFAEB8FF),
    glow = Color(0x736E7EFF),
    danger = Color(0xFFFF8A7A),
    ok = Color(0xFF7FE0B0),
    focusFill = Color.White,
    focusInk = Color(0xFF0A0C11),
    focusInk2 = Color(0xFF3A4050),
    highContrast = false,
)

val HighContrastColors = DarkColors.copy(
    bg = Color.Black,
    bg2 = Color.Black,
    surface = Color(0xFF0E0E10),
    surface2 = Color(0xFF18181C),
    surface3 = Color(0xFF222228),
    line = Color(0x59FFFFFF),
    text = Color.White,
    text2 = Color(0xFFE4E7EE),
    text3 = Color(0xFFC9CED8),
    ring = Color(0xFFFFE14D),
    glow = Color(0x59FFE14D),
    accent = Color(0xFFFFE14D),
    accentSoft = Color(0x2EFFE14D),
    accentStrong = Color(0xFF2F3BB8),
    focusFill = Color(0xFFFFE14D),
    focusInk = Color.Black,
    focusInk2 = Color(0xFF222228),
    highContrast = true,
)

private fun manrope(weight: Int) = Font(
    R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Manrope = FontFamily(
    manrope(300), manrope(400), manrope(500), manrope(600), manrope(650), manrope(700), manrope(800),
)

private val trimNone = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(size: Int, lineHeight: Float, weight: Int, tracking: Float = 0f) = TextStyle(
    fontFamily = Manrope,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.em,
    lineHeightStyle = trimNone,
)

/** Type scale: Display 64 · H1 52 · H2 40 · Title 32 · Body 28 (min) · Secondary 22 (min) · Label 22 caps. */
@Immutable
object GlumeType {
    val display = style(64, 1.08f, 700, -0.02f)
    val h1 = style(52, 1.1f, 700, -0.015f)
    val h2 = style(40, 1.15f, 700, -0.01f)
    val title = style(32, 1.2f, 650)
    val body = style(28, 1.35f, 500)
    val sec = style(22, 1.35f, 500)
    /** Uppercase the string yourself; Compose has no text-transform. */
    val label = style(22, 1.0f, 700, 0.06f)
    val badge = style(22, 1.0f, 700)
    val tabular = "tnum"
}

/** Motion tokens. Reduce motion swaps these for zero durations (see LocalMotion). */
@Immutable
data class GlumeMotion(
    val focusMs: Int = 150,
    val scrollMs: Int = 260,
    val screenMs: Int = 280,
    val panelMs: Int = 240,
    val crossfadeMs: Int = 1400,
    val sharpenMs: Int = 700,
    val kenBurns: Boolean = true,
) {
    companion object {
        val Ease = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
        val Reduced = GlumeMotion(0, 0, 0, 0, 0, 0, kenBurns = false)
        const val OVERLAY_HIDE_MS = 4000L
        const val TOAST_MS = 2600L
        const val LONG_PRESS_MS = 550L
    }
}

/** Spacing, radii and layout constants in design px. */
object Dims {
    const val SAFE_X = 96
    const val SAFE_Y = 54
    /** Left edge of content on screens with the collapsed rail. */
    const val CONTENT_LEFT = 216
    const val RAIL_COLLAPSED = 196
    const val RAIL_EXPANDED = 520
    const val R_BADGE = 8
    const val R_KEY = 12
    const val R_TILE = 14
    const val R_HERO = 20
    const val R_PANEL = 28
}

/** Focus scale steps: 1.03 large cards · 1.06 tiles · 1.10 keys and pins. */
object FocusScale {
    const val LARGE = 1.03f
    const val TILE = 1.06f
    const val KEY = 1.10f
    const val RAIL = 1.04f
    const val ROW = 1.02f
    const val PRESSED = 0.97f
}
