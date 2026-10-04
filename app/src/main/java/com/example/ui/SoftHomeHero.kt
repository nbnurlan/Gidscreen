package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*

@Composable
fun SoftHomeHero() {
    Column(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.soft_title), color = SoftInk, fontSize = 30.sp,
            lineHeight = 36.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(stringResource(R.string.soft_subtitle), color = SoftMuted, fontSize = 28.sp,
            lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Canvas(Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 28.dp, vertical = 20.dp)) {
            val w = size.width
            val h = size.height
            drawOval(SoftPeach, Offset(w * .02f, h * .15f), Size(w * .68f, h * .76f))
            drawCircle(SoftSurfaceVariant, h * .37f, Offset(w * .8f, h * .53f))
            val left = w * .23f
            val top = h * .15f
            val rw = w * .54f
            val rh = h * .7f
            drawRoundRect(SoftSurface, Offset(left, top), Size(rw, rh), CornerRadius(12.dp.toPx()))
            drawRoundRect(SoftPrimary, Offset(left, top), Size(rw, rh), CornerRadius(10.dp.toPx()),
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 4.dp.toPx()))))
            for (i in 0..3) {
                drawRoundRect(SoftPrimary.copy(alpha = if (i == 0) .45f else .16f),
                    Offset(left + rw * .14f, top + rh * (.2f + i * .17f)),
                    Size(rw * listOf(.4f, .72f, .6f, .3f)[i], 6.dp.toPx()), CornerRadius(3.dp.toPx()))
            }
            for (x in listOf(left, left + rw)) for (y in listOf(top, top + rh)) {
                drawCircle(SoftPrimary, 6.dp.toPx(), Offset(x, y))
                drawCircle(Color.White, 3.dp.toPx(), Offset(x, y))
            }
        }
    }
}
