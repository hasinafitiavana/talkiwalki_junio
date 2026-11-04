package com.talkiwalki.junior.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun TalkieWalkieMark(size: Dp = 88.dp, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    Canvas(modifier = modifier.size(size).semantics { contentDescription = "Logo TalkiWalki Junior" }) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(color, Offset(w * .22f, h * .25f), Size(w * .56f, h * .62f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * .1f))
        drawLine(color, Offset(w * .62f, h * .27f), Offset(w * .8f, h * .08f), strokeWidth = w * .07f)
        drawCircle(surfaceColor, w * .07f, Offset(w * .5f, h * .48f))
        drawLine(surfaceColor, Offset(w * .34f, h * .68f), Offset(w * .66f, h * .68f), strokeWidth = w * .05f)
        drawCircle(color, w * .43f, Offset(w * .5f, h * .49f), style = Stroke(w * .025f))
    }
}
