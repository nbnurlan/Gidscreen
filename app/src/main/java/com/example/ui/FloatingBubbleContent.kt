package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R

enum class DockSide { LEFT, RIGHT }

/** Concept 9: ivory focus corners and a diamond on soft violet. */
@Composable
fun FloatingBubbleContent(
    modifier: Modifier = Modifier,
    docked: Boolean = false,
    dockRight: Boolean = true
) {
    val description = stringResource(R.string.bubble_content_desc)
    Canvas(modifier.size(if (docked) 20.dp else 64.dp, if (docked) 56.dp else 64.dp)
        .testTag("floating_bubble_button")
        .semantics { contentDescription = description }) {
        val radius = (if (docked) 23.dp else 27.dp).toPx()
        val center = Offset(
            if (!docked) size.width / 2 else if (dockRight) size.width + 8.dp.toPx() else -8.dp.toPx(),
            size.height / 2
        )
        drawCircle(Brush.linearGradient(listOf(Color(0xFF9565E8), Color(0xFF7950D4))), radius, center)
        if (!docked) {
            val s = 1.dp.toPx()
            val corners = Path().apply {
                moveTo(center.x-13*s, center.y-5*s); lineTo(center.x-13*s, center.y-13*s); lineTo(center.x-5*s, center.y-13*s)
                moveTo(center.x+5*s, center.y-13*s); lineTo(center.x+13*s, center.y-13*s); lineTo(center.x+13*s, center.y-5*s)
                moveTo(center.x+13*s, center.y+5*s); lineTo(center.x+13*s, center.y+13*s); lineTo(center.x+5*s, center.y+13*s)
                moveTo(center.x-5*s, center.y+13*s); lineTo(center.x-13*s, center.y+13*s); lineTo(center.x-13*s, center.y+5*s)
            }
            drawPath(corners, Color(0xFFFFFAF6), style = Stroke(4*s, cap = StrokeCap.Round))
            val diamond = Path().apply {
                moveTo(center.x, center.y-6*s); lineTo(center.x+6*s, center.y)
                lineTo(center.x, center.y+6*s); lineTo(center.x-6*s, center.y); close()
            }
            drawPath(diamond, Color(0xFFFFFAF6))
        }
    }
}
