package com.android.glume.ui.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.glume.domain.User
import com.android.glume.ui.components.Avatar
import com.android.glume.ui.components.immichAvatarColor
import com.android.glume.ui.components.FocusSurface
import com.android.glume.ui.components.GIcon
import com.android.glume.ui.components.Ic
import com.android.glume.ui.components.T
import com.android.glume.ui.nav.RailSection
import com.android.glume.ui.theme.Dims
import com.android.glume.ui.theme.FocusScale
import com.android.glume.ui.theme.Glume
import com.android.glume.ui.theme.GlumeType

/**
 * Left navigation rail. Collapsed (icons) while content has focus; expands with labels when it
 * takes focus. ◀ from column 0 enters it at the current section; ▶ returns to the content's last
 * focus; OK opens a section.
 */
@Composable
fun Rail(current: RailSection, user: User?, host: String, app: AppController) {
    val c = Glume.colors
    val open = app.railFocused
    val bgWidth by animateFloatAsState(if (open) Dims.RAIL_EXPANDED.toFloat() else Dims.RAIL_COLLAPSED.toFloat(), tween(200), label = "rail")
    val bgColor = c.bg
    Box(
        Modifier.fillMaxHeight().width(Dims.RAIL_EXPANDED.dp).drawBehind {
            val w = bgWidth.dp.toPx()
            val brush = if (open) {
                Brush.horizontalGradient(0f to bgColor.copy(alpha = 0.98f), 0.62f to bgColor.copy(alpha = 0.94f), 1f to bgColor.copy(alpha = 0f), endX = w)
            } else {
                Brush.horizontalGradient(0f to bgColor.copy(alpha = 0.9f), 0.7f to bgColor.copy(alpha = 0.55f), 1f to bgColor.copy(alpha = 0f), endX = w)
            }
            drawRect(brush, size = Size(w, size.height))
        },
    ) {
        Row(Modifier.offset(x = 92.dp, y = 54.dp).height(72.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(c.accentStrong), contentAlignment = Alignment.Center) {
                GIcon(Ic.aperture, 28.dp, tint = Color.White)
            }
            if (open) T("Immich", GlumeType.title)
        }
        Column(
            Modifier
                .offset(x = 76.dp, y = 196.dp)
                .width(if (open) 300.dp else 72.dp)
                .onFocusChanged { app.railFocused = it.hasFocus }
                // Entering the rail always lands on the current section.
                .focusProperties { onEnter = { app.railRequester.requestFocus() } }
                // ▶ returns to exactly where the content focus was.
                .onPreviewKeyEvent {
                    if (it.key == Key.DirectionRight) {
                        if (it.type == KeyEventType.KeyDown) app.focusContent()
                        true
                    } else false
                }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RailSection.entries.forEach { s ->
                val isCur = s == current
                Box {
                    Box(Modifier.offset(x = (-16).dp, y = 22.dp).size(6.dp, 28.dp).clip(RoundedCornerShape(3.dp)).background(if (isCur) c.accent else Color.Transparent))
                    FocusSurface(
                        onClick = { if (isCur) app.focusContent() else app.goTop(s.route) },
                        modifier = Modifier
                            .then(if (isCur) Modifier.focusRequester(app.railRequester) else Modifier)
                            .width(if (open) 300.dp else 72.dp).height(72.dp),
                        shape = RoundedCornerShape(18.dp),
                        focusScale = FocusScale.RAIL,
                        focusedContainerColor = c.focusFill,
                        contentColor = if (isCur) c.text else c.text2,
                        focusedContentColor = c.focusInk,
                    ) {
                        Row(Modifier.align(Alignment.CenterStart).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            GIcon(s.icon, 32.dp)
                            if (open) T(s.label, GlumeType.body.copy(fontWeight = FontWeight(650)))
                        }
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.BottomStart).padding(start = 88.dp, bottom = 54.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Avatar(user?.avatarUrl, 56.dp, name = user?.name, color = user?.let { immichAvatarColor(it.avatarColor) })
            if (open && user != null) {
                Column {
                    T(user.name, GlumeType.body.copy(fontWeight = FontWeight(650)))
                    T(host, GlumeType.sec, color = c.text2)
                }
            }
        }
    }
}
