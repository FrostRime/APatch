package me.bmax.apatch.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.capsule.ContinuousRoundedRectangle

data class DialogData(
    val content: @Composable () -> Unit
)

@Composable
fun DialogOverlay(dialog: MutableState<DialogData?>) {
    val dialogBackdrop = rememberLayerBackdrop()
    val dialogAnim by animateFloatAsState(
        targetValue = if (dialog.value != null) 0f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )
    dialog.value?.let {
        ScreenShield {
            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, _ ->
                                change.consume()
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures {
                            dialog.value = null
                        }
                    }
                    .fillMaxSize()
                    .alpha(1 - dialogAnim),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .wrapContentHeight(), contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .layerBackdrop(dialogBackdrop)
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .blur(64.dp * dialogAnim),
                            shape = ContinuousRoundedRectangle(28.dp),
                            tonalElevation = AlertDialogDefaults.TonalElevation,
                            color = AlertDialogDefaults.containerColor,
                        ) {
                            dialog.value?.content?.invoke()
                        }
                    }

                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .drawBackdrop(
                                dialogBackdrop,
                                highlight = null,
                                shadow = null,
                                shape = { ContinuousRoundedRectangle(28.dp) },
                                effects = {
                                    vibrancy()
                                    lens(
                                        size.minDimension * 0.25f,
                                        dialogAnim * size.minDimension
                                    )
                                }
                            )
                    )
                }
            }
        }
    }
}
