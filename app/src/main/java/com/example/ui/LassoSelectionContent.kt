package com.example.ui

import android.graphics.RectF
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.SelectionMode
import com.example.ui.CompactLanguageDropdown
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.PurpleNeon
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun LassoSelectionContent(
    onSelectionConfirmed: (android.graphics.Path?, RectF) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(SelectionMode.LASSO) }

    // Touch points tracking
    val freehandPoints = remember { mutableStateListOf<Offset>() }
    var rectStart by remember { mutableStateOf<Offset?>(null) }
    var rectCurrent by remember { mutableStateOf<Offset?>(null) }

    var isDrawing by remember { mutableStateOf(false) }

    // Calculate current compose Path
    val currentPath = remember(mode, freehandPoints.size, rectStart, rectCurrent) {
        val path = Path()
        when (mode) {
            SelectionMode.LASSO, SelectionMode.POLYGON -> {
                if (freehandPoints.isNotEmpty()) {
                    path.moveTo(freehandPoints[0].x, freehandPoints[0].y)
                    for (i in 1 until freehandPoints.size) {
                        val prev = freehandPoints[i - 1]
                        val curr = freehandPoints[i]
                        val midX = (prev.x + curr.x) / 2f
                        val midY = (prev.y + curr.y) / 2f
                        path.quadraticTo(prev.x, prev.y, midX, midY)
                    }
                    val last = freehandPoints.last()
                    path.lineTo(last.x, last.y)
                    if (!isDrawing && freehandPoints.size > 2) {
                        path.close()
                    }
                }
            }
            SelectionMode.RECTANGLE -> {
                val start = rectStart
                val curr = rectCurrent
                if (start != null && curr != null) {
                    val left = min(start.x, curr.x)
                    val top = min(start.y, curr.y)
                    val right = max(start.x, curr.x)
                    val bottom = max(start.y, curr.y)
                    path.addRect(Rect(left, top, right, bottom))
                }
            }
            SelectionMode.CIRCLE -> {
                val start = rectStart
                val curr = rectCurrent
                if (start != null && curr != null) {
                    val left = min(start.x, curr.x)
                    val top = min(start.y, curr.y)
                    val right = max(start.x, curr.x)
                    val bottom = max(start.y, curr.y)
                    path.addOval(Rect(left, top, right, bottom))
                }
            }
        }
        path
    }

    // Helper to calculate bounding box and submit
    fun completeSelection() {
        val bounds: RectF? = when (mode) {
            SelectionMode.LASSO, SelectionMode.POLYGON -> {
                if (freehandPoints.size > 5) {
                    var minX = Float.MAX_VALUE
                    var minY = Float.MAX_VALUE
                    var maxX = Float.MIN_VALUE
                    var maxY = Float.MIN_VALUE
                    for (p in freehandPoints) {
                        if (p.x < minX) minX = p.x
                        if (p.y < minY) minY = p.y
                        if (p.x > maxX) maxX = p.x
                        if (p.y > maxY) maxY = p.y
                    }
                    if ((maxX - minX) > 24f && (maxY - minY) > 24f) {
                        RectF(minX, minY, maxX, maxY)
                    } else null
                } else null
            }
            SelectionMode.RECTANGLE, SelectionMode.CIRCLE -> {
                val start = rectStart
                val curr = rectCurrent
                if (start != null && curr != null) {
                    val left = min(start.x, curr.x)
                    val top = min(start.y, curr.y)
                    val right = max(start.x, curr.x)
                    val bottom = max(start.y, curr.y)
                    if (abs(right - left) > 24f && abs(bottom - top) > 24f) {
                        RectF(left, top, right, bottom)
                    } else null
                } else null
            }
        }

        if (bounds != null) {
            val androidPath = if ((mode == SelectionMode.LASSO || mode == SelectionMode.POLYGON) && freehandPoints.size > 2) {
                currentPath.asAndroidPath()
            } else null
            onSelectionConfirmed(androidPath, bounds)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("lasso_selection_screen")
    ) {
        // Drawing Surface - 100% transparent so all underlying apps and screen content are fully visible
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(mode) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDrawing = true
                            when (mode) {
                                SelectionMode.LASSO, SelectionMode.POLYGON -> {
                                    freehandPoints.clear()
                                    freehandPoints.add(offset)
                                }
                                SelectionMode.RECTANGLE, SelectionMode.CIRCLE -> {
                                    rectStart = offset
                                    rectCurrent = offset
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            when (mode) {
                                SelectionMode.LASSO, SelectionMode.POLYGON -> {
                                    val last = freehandPoints.lastOrNull() ?: change.position
                                    val next = last + dragAmount
                                    freehandPoints.add(next)
                                }
                                SelectionMode.RECTANGLE, SelectionMode.CIRCLE -> {
                                    rectCurrent = change.position
                                }
                            }
                        },
                        onDragEnd = {
                            isDrawing = false
                            completeSelection()
                        },
                        onDragCancel = {
                            isDrawing = false
                            freehandPoints.clear()
                            rectStart = null
                            rectCurrent = null
                        }
                    )
                }
        ) {
            // No dark mask: background is 100% transparent and clear!
            if (!currentPath.isEmpty) {
                // Subtle transparent neon tint inside the drawn path so user clearly sees the framed region
                drawPath(
                    path = currentPath,
                    color = CyanGlow.copy(alpha = 0.12f)
                )

                // Glowing neon contour border around selected area (Solid continuous line)
                val neonBrush = Brush.linearGradient(
                    colors = listOf(CyanGlow, PurpleNeon, CyanGlow)
                )

                drawPath(
                    path = currentPath,
                    brush = neonBrush,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        pathEffect = null
                    )
                )

                // Subtle inner accent stroke
                drawPath(
                    path = currentPath,
                    color = Color.White.copy(alpha = 0.7f),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = null
                    )
                )
            }
        }

        // Top Control Toolbar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = Color(0xEE0F172A),
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .shadow(12.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // 1. Rectangle Mode (Default & First)
                    ModePill(
                        icon = Icons.Default.CropLandscape,
                        label = stringResource(R.string.mode_rect),
                        isSelected = mode == SelectionMode.RECTANGLE,
                        onClick = {
                            mode = SelectionMode.RECTANGLE
                            freehandPoints.clear()
                            rectStart = null
                            rectCurrent = null
                        }
                    )

                    // 2. Lasso Mode
                    ModePill(
                        icon = Icons.Default.Gesture,
                        label = stringResource(R.string.mode_lasso),
                        isSelected = mode == SelectionMode.LASSO,
                        onClick = {
                            mode = SelectionMode.LASSO
                            freehandPoints.clear()
                            rectStart = null
                            rectCurrent = null
                        }
                    )

                    // 3. Circle Mode
                    ModePill(
                        icon = Icons.Default.RadioButtonUnchecked,
                        label = stringResource(R.string.mode_circle),
                        isSelected = mode == SelectionMode.CIRCLE,
                        onClick = {
                            mode = SelectionMode.CIRCLE
                            freehandPoints.clear()
                            rectStart = null
                            rectCurrent = null
                        }
                    )

                    Spacer(modifier = Modifier.width(2.dp))

                    // Compact Flag Language Selector Dropdown
                    CompactLanguageDropdown(testTag = "overlay_language_button")

                    Spacer(modifier = Modifier.width(2.dp))

                    // Undo / Back button (Dismisses selection overlay, returns to screen)
                    IconButton(
                        onClick = {
                            freehandPoints.clear()
                            rectStart = null
                            rectCurrent = null
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .testTag("lasso_undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = stringResource(R.string.btn_cancel),
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // Cancel / Close 'X' button
                    IconButton(
                        onClick = {
                            freehandPoints.clear()
                            rectStart = null
                            rectCurrent = null
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .testTag("lasso_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.btn_cancel),
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModePill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgBrush = if (isSelected) {
        Brush.horizontalGradient(listOf(CyanGlow, PurpleNeon))
    } else {
        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgBrush)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) Color.Black else Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
