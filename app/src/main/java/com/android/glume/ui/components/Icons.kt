package com.android.glume.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor

/** 24 x 24 stroke icon paths from the design handoff. */
object Ic {
    const val home = "M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z"
    const val grid = "M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z"
    const val album = "M3 7h18v13H3zM6 4h12M3 16l5-5 4 4 3-3 6 6"
    const val people = "M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM2 21v-1a6 6 0 0 1 6-6h2a6 6 0 0 1 6 6v1M16 3.5a4 4 0 0 1 0 7.5M22 21v-1a6 6 0 0 0-4-5.6"
    const val pin = "M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21zM12 12a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z"
    const val heart = "M12 20.5s-7.6-4.6-9.4-9.3C1.3 7.6 3.6 4 7.1 4c2 0 3.6 1.1 4.9 3 1.3-1.9 2.9-3 4.9-3 3.5 0 5.8 3.6 4.5 7.2-1.8 4.7-9.4 9.3-9.4 9.3z"
    const val search = "M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM21 21l-5-5"
    const val gear = "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 0 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 0 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 0 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 0 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"
    const val play = "M7 4.5v15l12.5-7.5z"
    const val pause = "M6.5 4h4v16h-4zM13.5 4h4v16h-4z"
    const val shuffle = "M16 3h5v5M4 20 21 3M21 16v5h-5M15 15l6 6M4 4l5 5"
    const val mic = "M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3zM5 11a7 7 0 0 0 14 0M12 18v3"
    const val cloudOff = "M2 2l20 20M5.8 7.6A5 5 0 0 0 7 17h10M18.6 17A4 4 0 0 0 17 9.2h-.5A7 7 0 0 0 9.4 4.5"
    const val info = "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 16v-5M12 8h.01"
    const val check = "M5 12.5l4.5 4.5L19 7.5"
    const val plus = "M12 5v14M5 12h14"
    const val chevR = "M9 18l6-6-6-6"
    const val camera = "M3 8h4l2-3h6l2 3h4v12H3zM12 17a4 4 0 1 0 0-8 4 4 0 0 0 0 8z"
    const val calendar = "M3 5h18v16H3zM3 10h18M8 3v4M16 3v4"
    const val film = "M3 5h18v14H3zM7 5v14M17 5v14M3 9h4M3 15h4M17 9h4M17 15h4"
    const val back10 = "M3 12a9 9 0 1 0 3-6.7M3 4v5h5"
    const val fwd10 = "M21 12a9 9 0 1 1-3-6.7M21 4v5h-5"
    const val server = "M3 4h18v7H3zM3 13h18v7H3zM7 7.5h.01M7 16.5h.01"
    const val monitor = "M3 4h18v12H3zM8 20h8M12 16v4"
    const val image = "M3 5h18v14H3zM3 15l5-5 4 4 3-3 6 6M15.5 9.5h.01"
    const val db = "M12 8c4.4 0 8-1.3 8-3s-3.6-3-8-3-8 1.3-8 3 3.6 3 8 3zM4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3"
    const val user = "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0"
    const val eye = "M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z"
    const val eyeOff = "M3 3l18 18M10.6 5.1A10.5 10.5 0 0 1 12 5c6.4 0 10 7 10 7a17 17 0 0 1-3 3.8M6.6 6.6C3.9 8.3 2 12 2 12s3.6 7 10 7c1.9 0 3.6-.6 5-1.5M9.9 9.9a3 3 0 0 0 4.2 4.2"
    const val globe = "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM3 12h18M12 3c2.5 2.6 3.8 5.6 3.8 9s-1.3 6.4-3.8 9c-2.5-2.6-3.8-5.6-3.8-9S9.5 5.6 12 3z"
    const val map = "M9 4 3 6v14l6-2 6 2 6-2V4l-6 2zM9 4v14M15 6v14"
    const val sparkle = "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8zM19 16l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7z"
    const val refresh = "M20 11a8 8 0 1 0-2.3 5.7M20 4v7h-7"
    const val aperture = "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM14.3 3.3 8.5 13.5M20.6 9.5H9M17.7 19 12 9M9.7 20.7l5.8-10.2M3.4 14.5H15M6.3 5 12 15"
    const val chevL = "M15 18l-6-6 6-6"

    /** Name → path, for icons named in data (search filters). */
    fun byName(name: String): String = when (name) {
        "people" -> people
        "pin" -> pin
        "calendar" -> calendar
        "film" -> film
        "camera" -> camera
        else -> info
    }
}

private val vectorCache = HashMap<String, ImageVector>()

private fun vectorFor(path: String, strokeWidth: Float, filled: Boolean): ImageVector {
    val key = "$filled|$strokeWidth|$path"
    return vectorCache.getOrPut(key) {
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = addPathNodes(path),
                fill = if (filled) SolidColor(Color.White) else null,
                stroke = if (filled) null else SolidColor(Color.White),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
    }
}

/** Draws one of the [Ic] paths, tinted with the current content color. */
@Composable
fun GIcon(
    path: String,
    size: Dp,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 2f,
    filled: Boolean = false,
    tint: Color = LocalContentColor.current,
) {
    Image(
        painter = rememberVectorPainter(vectorFor(path, strokeWidth, filled)),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}
