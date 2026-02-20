package me.bmax.apatch.ui.component

import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.kyant.backdrop.backdrops.emptyBackdrop

@Composable
fun SwitchItem(
    icon: ImageVector? = null,
    title: String,
    summary: String? = null,
    checked: () -> Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = LocalContentColor.current
            )
        },
        leadingContent = icon?.let {
            { Icon(icon, title) }
        },
        trailingContent = {
            LiquidToggle(
                selected = checked,
                onSelect = onCheckedChange,
                backdrop = emptyBackdrop() // todo
            )
        },
        supportingContent = {
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    )
}

@Suppress("unused")
@Composable
fun RadioItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(title)
        },
        leadingContent = {
            RadioButton(selected = selected, onClick = onClick)
        },
    )
}
