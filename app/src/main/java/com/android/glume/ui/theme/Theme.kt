package com.android.glume.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle

val LocalGlumeColors = staticCompositionLocalOf { DarkColors }
val LocalMotion = staticCompositionLocalOf { GlumeMotion() }

object Glume {
    val colors: GlumeColors @Composable get() = LocalGlumeColors.current
    val motion: GlumeMotion @Composable get() = LocalMotion.current
}

/** Design width the handoff was drawn at. */
const val DESIGN_WIDTH = 1920f

/**
 * Root theme. Rescales density so 1.dp equals one design pixel (1920-wide canvas), applies the
 * accessibility switches from Settings: high contrast, larger text (x1.15) and reduce motion.
 */
@Composable
fun GlumeTheme(
    highContrast: Boolean = false,
    largeText: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val widthPx = LocalWindowInfo.current.containerSize.width
    val density = remember(widthPx, largeText) {
        Density(
            density = if (widthPx > 0) widthPx / DESIGN_WIDTH else 1f,
            fontScale = if (largeText) 1.15f else 1f,
        )
    }
    val colors = if (highContrast) HighContrastColors else DarkColors
    CompositionLocalProvider(
        LocalDensity provides density,
        LocalGlumeColors provides colors,
        LocalMotion provides if (reduceMotion) GlumeMotion.Reduced else GlumeMotion(),
        LocalContentColor provides colors.text,
        LocalTextStyle provides GlumeType.body,
        content = content,
    )
}
