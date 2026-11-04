package com.talkiwalki.junior.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun QrCode(value: String, modifier: Modifier = Modifier) {
    val matrix = remember(value) { QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 512, 512) }
    Canvas(modifier.size(220.dp).background(Color.White)) {
        drawQrModules(matrix.width, matrix.height) { x, y -> matrix.get(x, y) }
    }
}

private fun DrawScope.drawQrModules(width: Int, height: Int, isSet: (Int, Int) -> Boolean) {
    val moduleWidth = size.width / width
    val moduleHeight = size.height / height
    for (y in 0 until height) for (x in 0 until width) {
        if (isSet(x, y)) drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(x * moduleWidth, y * moduleHeight), size = androidx.compose.ui.geometry.Size(moduleWidth + 0.5f, moduleHeight + 0.5f))
    }
}
