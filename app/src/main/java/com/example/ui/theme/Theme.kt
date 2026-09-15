package com.example.ui.theme

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val DarkColorScheme =
  darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme =
  lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
  )

private class SafeIndicationNode(
    private val interactionSource: InteractionSource,
    private val indicationColor: Color
) : Modifier.Node(), DrawModifierNode {
    private val alpha = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        alpha.animateTo(0.12f, tween(80))
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        alpha.animateTo(0f, tween(180))
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val currentAlpha = alpha.value
        if (currentAlpha > 0f) {
            val color = if (indicationColor != Color.Unspecified) indicationColor else Color.White
            drawRect(color = color.copy(alpha = currentAlpha))
        }
    }
}

private class SafeTouchIndication(private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): Modifier.Node {
        return SafeIndicationNode(interactionSource, color)
    }

    override fun hashCode(): Int = color.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SafeTouchIndication) return false
        return color == other.color
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  val indicationColor = if (darkTheme) Color.White else Color.Black
  val safeIndication = remember(indicationColor) { SafeTouchIndication(indicationColor) }

  CompositionLocalProvider(
    LocalRippleConfiguration provides null,
    LocalIndication provides safeIndication
  ) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
