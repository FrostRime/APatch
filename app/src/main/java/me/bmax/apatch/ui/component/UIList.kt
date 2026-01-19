package me.bmax.apatch.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.material3.ShapeDefaults.ExtraLarge
import androidx.compose.material3.ShapeDefaults.ExtraSmall
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp

data class ListItemData(
    val background: Color = Color.Unspecified,
    val title: @Composable (() -> Unit),
    val subtitle: String? = null,
    val description: String? = null,
    val label: @Composable (() -> Unit)? = null,
    val headerIcon: @Composable (() -> Unit)? = null,
    val trailingContent: @Composable (() -> Unit)? = null,
    val actions: @Composable () -> Unit,
    val checked: Boolean,
    val onCheckChange: ((Boolean) -> Unit)? = null
)

val bottomShape = ExtraSmall.copy(
    bottomStart = ExtraLarge.bottomStart,
    bottomEnd = ExtraLarge.bottomEnd
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIList(
    items: List<ListItemData>,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    scrollBehavior: SearchBarScrollBehavior,
    state: LazyListState,
    emptyContent: @Composable () -> Unit = {}
) {
    PullToRefreshBox(
        modifier = modifier,
        onRefresh = onRefresh,
        isRefreshing = isRefreshing
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .clip(ExtraLarge)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            state = state,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = remember {
                PaddingValues(
                    bottom = 16.dp + 56.dp /*  Scaffold Fab Spacing + Fab container height */
                )
            }
        ) {
            when {
                items.isEmpty() -> {
                    item {
                        emptyContent()
                    }
                }

                else -> {
                    itemsIndexed(items) { index, item ->
                        val isLastest = index == items.lastIndex
                        GenericItem(
                            data = item,
                            shape = if (isLastest) bottomShape else ExtraSmall,
                            expandedContent = {
                                item.actions()
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
    expandedContent: @Composable () -> Unit
) {
    var showActions by remember { mutableStateOf(false) }
    var isChecked by remember { mutableStateOf(data.checked) }
    val backgroundColor = if (data.background != Color.Unspecified) {
        data.background
    } else {
        MaterialTheme.colorScheme.surface
    }
    Surface(
        shape = shape,
        tonalElevation = if (isChecked) 4.dp else 1.dp,
        color = backgroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    showActions = !showActions
                }
        ) {
            ListItem(
                colors = ListItemDefaults.colors(
                    containerColor = backgroundColor
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
                                text = data.subtitle,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (data.description != null) {
                            Text(
                                text = data.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        data.label?.invoke()
                    }
                },
                trailingContent = {
                    if (data.onCheckChange != null) {
                        Column(verticalArrangement = Arrangement.Center) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = {
                                    isChecked = it
                                    data.onCheckChange.invoke(isChecked)
                                }
                            )
                        }
                    }
                }
            )
            AnimatedVisibility(showActions) {
                expandedContent()
            }
        }
    }
}