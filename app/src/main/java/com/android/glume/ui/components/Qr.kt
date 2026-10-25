package com.android.glume.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** A QR code for [text], drawn as crisp modules (no bitmap scaling). Dark modules on a light background. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier, color: Color = Color(0xFF0A0C11)) {
    val matrix = remember(text) { encode(text) }
    Canvas(modifier) {
        val n = matrix.width
        val cell = minOf(size.width, size.height) / n
        val ox = (size.width - cell * n) / 2
        val oy = (size.height - cell * n) / 2
        for (y in 0 until n) for (x in 0 until n) {
            // Slight overlap avoids hairline gaps between modules.
            if (matrix[x, y]) drawRect(color, Offset(ox + x * cell, oy + y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

private fun encode(text: String): BitMatrix =
    QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
