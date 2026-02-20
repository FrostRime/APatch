package me.bmax.apatch.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import me.bmax.apatch.util.ui.InteractiveHighlight
import me.bmax.apatch.util.ui.rememberUISensor
import kotlin.math.ln

@Composable
fun LiquidSurface(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    shadowAlpha: Float = 0f,
    tonalElevation: Dp = 0.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable RowScope.(Backdrop) -> Unit
) {
    val animationScope = rememberCoroutineScope()

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope
        )
    }

    val uiSensor = rememberUISensor()

    val highlightAngle by animateFloatAsState(
        targetValue = uiSensor?.gravityAngle ?: 45f,
        animationSpec = tween(400)
    )

    fun ColorScheme.surfaceColorAtElevation(color: Color, elevation: Dp): Color {
        if (elevation == 0.dp) return color
        val alpha = ((4.5f * ln(elevation.value + 1)) + 2f) / 100f
        return surfaceTint.copy(alpha = alpha).compositeOver(color)
    }

    val absoluteElevation = LocalAbsoluteTonalElevation.current + tonalElevation

    val contentColor = contentColorFor(
        when {
            tint.isSpecified -> tint
            surfaceColor.isSpecified -> surfaceColor
            else -> LocalContentColor.current
        }
    )

    val tint = if (tint.isSpecified) MaterialTheme.colorScheme.surfaceColorAtElevation(
        tint,
        absoluteElevation
    ) else Color.Unspecified
    val surfaceColor =
        if (surfaceColor.isSpecified) MaterialTheme.colorScheme.surfaceColorAtElevation(
            surfaceColor,
            absoluteElevation
        ) else Color.Unspecified

    val surfaceBackdrop = rememberLayerBackdrop()

    Box {
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(12f.dp.toPx(), 24f.dp.toPx())
                    },
                    highlight = {
                        val progress = interactiveHighlight.pressProgress
                        Highlight(
                            style = HighlightStyle.Default(
                                angle = highlightAngle,
                            ),
                            alpha = progress * 0.45f + 0.25f
                        )
                    },
                    shadow = {
                        Shadow(
                            alpha = shadowAlpha
                        )
                    },
                    onDrawSurface = {
                        if (tint.isSpecified) {
                            drawRect(tint, blendMode = BlendMode.Hue)
                            drawRect(tint.copy(alpha = 0.87f))
                        }
                        if (surfaceColor.isSpecified) {
                            drawRect(surfaceColor)
                        }
                    }
                )
                .clickable(
                    interactionSource = null,
                    indication = if (isInteractive) null else LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick
                )
                .then(
                    if (isInteractive) {
                        Modifier
                            .then(interactiveHighlight.modifier)
                            .then(interactiveHighlight.gestureModifier)
                    } else {
                        Modifier
                    }
                )
                .layerBackdrop(surfaceBackdrop)
        )
        Row(
            modifier,
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = verticalAlignment
        ) {
            CompositionLocalProvider(
                LocalContentColor provides contentColor,
                LocalAbsoluteTonalElevation provides absoluteElevation,
            ) {
                content(surfaceBackdrop)
            }
        }
    }
}