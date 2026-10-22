package com.android.glume.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.EventListener
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.android.glume.ui.components.T
import com.android.glume.ui.theme.GlumeType
import java.util.concurrent.atomic.AtomicInteger

/** Counts where Coil served images from, for the debug overlay. */
object ImageStats : EventListener() {
    val memory = AtomicInteger()
    val disk = AtomicInteger()
    val network = AtomicInteger()

    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        when (result.dataSource) {
            DataSource.MEMORY_CACHE, DataSource.MEMORY -> memory.incrementAndGet()
            DataSource.DISK -> disk.incrementAndGet()
            DataSource.NETWORK -> network.incrementAndGet()
        }
    }
}

/**
 * Hidden overlay (▲ ▲ ▼ ▼ ◀ ▶ ◀ ▶): frame rate, janky frames (> 25 ms) in the last second, and
 * image cache hit rates, to judge feel on the TV.
 */
@Composable
fun BoxScope.DebugOverlay() {
    var fps by mutableFloatStateOf(0f).let { remember { it } }
    var jank by remember { mutableIntStateOf(0) }
    var worst by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        var frames = 0
        var janky = 0
        var maxMs = 0f
        var windowStart = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val ms = (now - last) / 1_000_000f
                    frames++
                    if (ms > 25f) janky++
                    if (ms > maxMs) maxMs = ms
                }
                if (windowStart == 0L) windowStart = now
                if (now - windowStart >= 1_000_000_000L) {
                    fps = frames * 1e9f / (now - windowStart)
                    jank = janky; worst = maxMs
                    frames = 0; janky = 0; maxMs = 0f; windowStart = now
                }
                last = now
            }
        }
    }
    val m = ImageStats.memory.get()
    val d = ImageStats.disk.get()
    val n = ImageStats.network.get()
    val total = (m + d + n).coerceAtLeast(1)
    Column(
        Modifier.align(Alignment.TopEnd).padding(24.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xD9000000)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val style = GlumeType.badge
        T("FPS %.0f · jank %d · worst %.0f ms".format(fps, jank, worst), style, color = if (jank > 3) Color(0xFFFF8A7A) else Color(0xFF7FE0B0), tabular = true)
        T("Images: mem %d%% · disk %d%% · net %d%%".format(m * 100 / total, d * 100 / total, n * 100 / total), style, color = Color.White, tabular = true)
        T("Requests: %d".format(m + d + n), style, color = Color(0xFFAEB5C4), tabular = true)
    }
}
