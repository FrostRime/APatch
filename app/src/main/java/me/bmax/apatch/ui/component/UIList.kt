package me.bmax.apatch.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
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
import com.kyant.capsule.ContinuousRoundedRectangle
import me.bmax.apatch.util.ui.LocalInnerPadding
import me.bmax.apatch.util.ui.rememberUISensor
import kotlin.math.ln

data class ListItemData(
    val background: Color = Color.Unspecified,
    val title: @Composable (() -> Unit),
    val subtitle: String? = null,
    val description: String? = null,
    val label: @Composable (() -> Unit)? = null,
    val headerIcon: @Composable (() -> Unit)? = null,
    val trailingContent: @Composable (() -> Unit)? = null,
    val actions: @Composable (Backdrop) -> Unit,
    val checked: () -> Boolean,
    val onCheckChange: ((Boolean) -> Unit)? = null,
    val showCheckBox: () -> Boolean = { false }
)

val topShape = ContinuousRoundedRectangle(16.dp, 16.dp, 4.dp, 4.dp)
val bottomShape = ContinuousRoundedRectangle(4.dp, 4.dp, 16.dp, 16.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIList(
    items: () -> List<ListItemData>,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    backdrop: Backdrop,
    state: LazyListState,
    emptyContent: @Composable () -> Unit = {}
) {
    val items = items()
    PullToRefreshBox(
        modifier = modifier,
        onRefresh = onRefresh,
        isRefreshing = isRefreshing
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .clip(ContinuousRoundedRectangle(16.dp)),
            state = state,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = LocalInnerPadding.current
        ) {
            when {
                items.isEmpty() -> {
                    item {
                        emptyContent()
                    }
                }

                else -> {
                    itemsIndexed(items) { index, item ->
                        val shape = when (index) {
                            0 -> topShape
                            items.lastIndex -> bottomShape
                            else -> ContinuousRoundedRectangle(4.dp)
                        }
                        GenericItem(
                            data = item,
                            shape = shape,
                            backdrop = backdrop,
                            expandedContent = { backdrop ->
                                item.actions(backdrop)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenericItem(
    data: ListItemData,
    shape: Shape,
    backdrop: Backdrop,
    expandedContent: @Composable (Backdrop) -> Unit
) {
    var showActions by remember { mutableStateOf(false) }
    val backgroundColor = if (data.background != Color.Unspecified) {
        data.background
    } else {
        MaterialTheme.colorScheme.surface
    }

    fun ColorScheme.surfaceColorAtElevation(color: Color, elevation: Dp): Color {
        if (elevation == 0.dp) return color
        val alpha = ((4.5f * ln(elevation.value + 1)) + 2f) / 100f
        return surfaceTint.copy(alpha = alpha).compositeOver(color)
    }

    val tonalElevation = if (data.checked()) 4.dp else 1.dp
    val absoluteElevation = LocalAbsoluteTonalElevation.current + tonalElevation
    val color =
        MaterialTheme.colorScheme.surfaceColorAtElevation(backgroundColor, absoluteElevation)
    val itemSurfaceBackdrop = rememberLayerBackdrop()
    val uiSensor = rememberUISensor()

    val highlightAngle by animateFloatAsState(
        targetValue = uiSensor?.gravityAngle ?: 45f,
        animationSpec = tween(400)
    )
    Box {
        Surface(
            tonalElevation = if (data.checked()) 4.dp else 1.dp,
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
                        Highlight(
                            style = HighlightStyle.Default(
                                angle = highlightAngle,
                            ),
                            alpha = 0.05f
                        )
                    },
                    shadow = null,
                    onDrawSurface = {
                        drawRect(color, blendMode = BlendMode.Hue)
                        drawRect(color.copy(alpha = 0.87f))
                    }
                )
                .layerBackdrop(itemSurfaceBackdrop),
            shape = shape,
            color = Color.Transparent) {}
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    showActions = !showActions
                }
        ) {
            ListItem(
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                ),
                leadingContent = data.headerIcon,
                headlineContent = data.title,
                supportingContent = {
                    Column(
                        modifier = Modifier
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (data.subtitle != null) {
                            Text(
                                modifier = Modifier.padding(start = 4.dp),
                                text = data.subtitle,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (data.description != null) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp)
                                    .height(IntrinsicSize.Min)
                            ) {
                                VerticalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    thickness = 2.dp,
                                    modifier = Modifier.fillMaxHeight()
                                )

                                Spacer(Modifier.width(4.dp))

                                Text(
                                    text = data.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        data.label?.invoke()
                    }
                },
                trailingContent = {
                    if (data.showCheckBox()) {
                        Column(verticalArrangement = Arrangement.Center) {
                            Checkbox(
                                checked = data.checked(),
                                enabled = data.onCheckChange != null,
                                onCheckedChange = {
                                    data.onCheckChange?.invoke(!data.checked())
                                }
                            )
                        }
                    }
                }
            )
            AnimatedVisibility(showActions) {
                expandedContent(itemSurfaceBackdrop)
            }
        }
    }
}
