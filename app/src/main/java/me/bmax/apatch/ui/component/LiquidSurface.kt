package me.bmax.apatch.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import me.bmax.apatch.util.ui.InteractiveHighlight
import me.bmax.apatch.util.ui.rememberUISensor

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
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable RowScope.() -> Unit
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

    Row(
        modifier
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
            ),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        content = content
    )
}