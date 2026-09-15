package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.PurpleNeon

enum class DockSide {
    LEFT, RIGHT
}

@Composable
fun FloatingBubbleContent(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isTucked: Boolean = false,
    dockSide: DockSide = DockSide.LEFT,
    onTuckClick: () -> Unit = {},
    onUntuckClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    if (isTucked) {
        // -------------------------------------------------------------
        // Tucked State: Compact, sleek Edge Tab attached to screen border
        // -------------------------------------------------------------
        val edgeShape = if (dockSide == DockSide.LEFT) {
            RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 20.dp, bottomEnd = 20.dp)
        } else {
            RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 0.dp, bottomEnd = 0.dp)
        }

        Surface(
            color = Color(0xEE0F172A),
            shape = edgeShape,
            border = BorderStroke(
                width = 1.5.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(CyanGlow, PurpleNeon, CyanGlow)
                )
            ),
            modifier = modifier
                .shadow(elevation = 10.dp, shape = edgeShape)
                .clip(edgeShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .testTag("floating_bubble_tucked_tab")
        ) {
            Column(
                modifier = Modifier
                    .width(32.dp)
                    .height(68.dp)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Mini glowing sparkle
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = stringResource(R.string.bubble_sparkle_desc),
                    tint = CyanGlow,
                    modifier = Modifier
                        .size(15.dp)
                        .scale(pulseScale)
                )

                // Drag / Pull-out indicator
                Icon(
                    imageVector = if (dockSide == DockSide.LEFT) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                    contentDescription = stringResource(R.string.bubble_untuck_desc),
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp)
                )

                // Mini cyan glow pill
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CyanGlow.copy(alpha = glowAlpha))
                )
            }
        }
    } else {
        // -------------------------------------------------------------
        // Floating State: Movable anywhere on screen with Tuck Button
        // -------------------------------------------------------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.testTag("floating_bubble_container")
        ) {
            // Main floating button with pulsing aura
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(64.dp)
            ) {
                // Outer pulsing glow aura
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    CyanGlow.copy(alpha = glowAlpha * 0.45f),
                                    PurpleNeon.copy(alpha = glowAlpha * 0.2f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Main floating button body
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(54.dp)
                        .shadow(elevation = 12.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF0F172A),
                                    Color(0xFF1E1B4B),
                                    Color(0xFF0F172A)
                                )
                            )
                        )
                        .border(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                listOf(CyanGlow, PurpleNeon, CyanGlow)
                            ),
                            shape = CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick
                        )
                        .testTag("floating_bubble_button")
                ) {
                    // Icon layered: Crop/Lasso + AutoAwesome sparkle
                    Icon(
                        imageVector = Icons.Default.CropFree,
                        contentDescription = stringResource(R.string.bubble_content_desc),
                        tint = CyanGlow,
                        modifier = Modifier.size(26.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = stringResource(R.string.bubble_sparkle_desc),
                        tint = Color.White,
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.TopEnd)
                            .padding(end = 6.dp, top = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Tuck-to-edge action pill button
            Surface(
                color = Color(0xDD0F172A),
                shape = CircleShape,
                border = BorderStroke(1.dp, CyanGlow.copy(alpha = 0.5f)),
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTuckClick
                    )
                    .testTag("floating_bubble_tuck_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (dockSide == DockSide.LEFT) {
                            Icons.AutoMirrored.Filled.ArrowBack
                        } else {
                            Icons.AutoMirrored.Filled.ArrowForward
                        },
                        contentDescription = stringResource(R.string.bubble_tuck_desc),
                        tint = CyanGlow,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}
