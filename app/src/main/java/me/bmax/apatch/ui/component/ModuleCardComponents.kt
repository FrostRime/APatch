package me.bmax.apatch.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.filled.ArrowAutofitDown
import com.composables.icons.tabler.filled.Settings
import com.composables.icons.tabler.filled.Trash
import me.bmax.apatch.R

@Composable
fun ModuleUpdateButton(
    onClick: () -> Unit
) = FilledTonalButton(
    onClick = onClick, enabled = true, contentPadding = PaddingValues(12.dp)
) {
    Icon(
        modifier = Modifier.size(20.dp),
        imageVector = Tabler.Filled.ArrowAutofitDown,
        contentDescription = stringResource(id = R.string.apm_update)
    )
}

@Composable
fun ModuleSettingsButton(
    onClick: () -> Unit
) = FilledTonalButton(
    onClick = onClick, enabled = true, contentPadding = PaddingValues(12.dp)
) {
    Icon(
        modifier = Modifier.size(20.dp),
        imageVector = Tabler.Filled.Settings,
        contentDescription = stringResource(id = R.string.settings)
    )
}

@Composable
fun ModuleRemoveButton(
    enabled: Boolean, onClick: () -> Unit
) = FilledTonalButton(
    onClick = onClick, enabled = enabled, contentPadding = PaddingValues(12.dp)
) {
    Icon(
        modifier = Modifier.size(20.dp),
        imageVector = Tabler.Filled.Trash,
        contentDescription = stringResource(id = R.string.apm_remove)
    )
}

@Composable
fun KPModuleRemoveButton(
    enabled: Boolean, onClick: () -> Unit
) = FilledTonalButton(
    onClick = onClick, enabled = enabled, contentPadding = PaddingValues(12.dp)
) {
    Icon(
        modifier = Modifier.size(20.dp),
        imageVector = Tabler.Filled.Trash,
        contentDescription = stringResource(id = R.string.kpm_unload)
    )
}

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