package me.bmax.apatch.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.filled.ArrowAutofitDown
import com.composables.icons.tabler.filled.Settings
import com.composables.icons.tabler.filled.Trash
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import me.bmax.apatch.R

@Composable
fun ModuleUpdateButton(
    backdrop: Backdrop,
    onClick: () -> Unit
) {
    val colorScheme by rememberUpdatedState(MaterialTheme.colorScheme)
    FilledTonalButton(
        modifier = Modifier.drawBackdrop(
            backdrop = backdrop,
            effects = {
                vibrancy()
                blur(2f.dp.toPx())
                lens(12f.dp.toPx(), 24f.dp.toPx())
            },
            highlight = {
                Highlight(
                    alpha = 0f
                )
            },
            shape = { ContinuousCapsule },
            shadow = {
                Shadow(
                    alpha = 0f
                )
            },
            onDrawSurface = {
                drawRect(colorScheme.secondaryContainer, blendMode = BlendMode.Hue)
                drawRect(colorScheme.secondaryContainer.copy(alpha = 0.75f))
            }
        ), onClick = onClick, enabled = true, contentPadding = PaddingValues(12.dp)
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = Tabler.Filled.ArrowAutofitDown,
            contentDescription = stringResource(id = R.string.apm_update)
        )
    }
}

@Composable
fun ModuleSettingsButton(
    backdrop: Backdrop,
    onClick: () -> Unit
) {
    val colorScheme by rememberUpdatedState(MaterialTheme.colorScheme)
    FilledTonalButton(
        modifier = Modifier.drawBackdrop(
            backdrop = backdrop,
            effects = {
                vibrancy()
                blur(2f.dp.toPx())
                lens(12f.dp.toPx(), 24f.dp.toPx())
            },
            highlight = {
                Highlight(
                    alpha = 0f
                )
            },
            shape = { ContinuousCapsule },
            shadow = {
                Shadow(
                    alpha = 0f
                )
            },
            onDrawSurface = {
                drawRect(colorScheme.secondaryContainer, blendMode = BlendMode.Hue)
                drawRect(colorScheme.secondaryContainer.copy(alpha = 0.75f))
            }
        ),
        onClick = onClick, contentPadding = PaddingValues(12.dp), shape = ContinuousCapsule
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = Tabler.Filled.Settings,
            contentDescription = stringResource(id = R.string.settings)
        )
    }
}

@Composable
fun ModuleRemoveButton(
    backdrop: Backdrop,
    enabled: Boolean, onClick: () -> Unit
) {
    val colorScheme by rememberUpdatedState(MaterialTheme.colorScheme)
    FilledTonalButton(
        modifier = Modifier.drawBackdrop(
            backdrop = backdrop,
            effects = {
                vibrancy()
                blur(2f.dp.toPx())
                lens(12f.dp.toPx(), 24f.dp.toPx())
            },
            highlight = {
                Highlight(
                    alpha = 0f
                )
            },
            shape = { ContinuousCapsule },
            shadow = {
                Shadow(
                    alpha = 0f
                )
            },
            onDrawSurface = {
                drawRect(colorScheme.secondaryContainer, blendMode = BlendMode.Hue)
                drawRect(colorScheme.secondaryContainer.copy(alpha = 0.75f))
            }
        ), onClick = onClick, enabled = enabled, contentPadding = PaddingValues(12.dp)
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = Tabler.Filled.Trash,
            contentDescription = stringResource(id = R.string.apm_remove)
        )
    }
}

@Composable
fun KPModuleRemoveButton(
    backdrop: Backdrop,
    enabled: Boolean, onClick: () -> Unit
) {
    val colorScheme by rememberUpdatedState(MaterialTheme.colorScheme)
    FilledTonalButton(
        modifier = Modifier.drawBackdrop(
            backdrop = backdrop,
            effects = {
                vibrancy()
                blur(2f.dp.toPx())
                lens(12f.dp.toPx(), 24f.dp.toPx())
            },
            highlight = {
                Highlight(
                    alpha = 0f
                )
            },
            shape = { ContinuousCapsule },
            shadow = {
                Shadow(
                    alpha = 0f
                )
            },
            onDrawSurface = {
                drawRect(colorScheme.secondaryContainer, blendMode = BlendMode.Hue)
                drawRect(colorScheme.secondaryContainer.copy(alpha = 0.75f))
            }
        ), onClick = onClick, enabled = enabled, contentPadding = PaddingValues(12.dp)
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = Tabler.Filled.Trash,
            contentDescription = stringResource(id = R.string.kpm_unload)
        )
    }
}

@Suppress("unused")
@Composable
fun ModuleStateIndicator(
    icon: ImageVector, color: Color = MaterialTheme.colorScheme.outline
) {
    Image(
        modifier = Modifier.requiredSize(150.dp),
        imageVector = icon,
        contentDescription = null,
        alpha = 0.1f,
        colorFilter = ColorFilter.tint(color)
    )
}