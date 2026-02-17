package me.bmax.apatch.ui.component

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.Plus
import com.kyant.capsule.ContinuousRoundedRectangle
import me.bmax.apatch.util.ui.LocalInnerPadding

data class ListItemData(
    val background: Color = Color.Unspecified,
    val title: @Composable (() -> Unit),
    val subtitle: String? = null,
    val description: String? = null,
    val label: @Composable (() -> Unit)? = null,
    val headerIcon: @Composable (() -> Unit)? = null,
    val trailingContent: @Composable (() -> Unit)? = null,
    val actions: @Composable () -> Unit,
    val checked: () -> Boolean,
    val onCheckChange: ((Boolean) -> Unit)? = null,
    val showCheckBox: () -> Boolean = { false }
)

val bottomShape = ContinuousRoundedRectangle(4.dp, 4.dp, 28.dp, 28.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UIList(
    items: () -> List<ListItemData>,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    scrollBehavior: SearchBarScrollBehavior,
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
                .clip(ContinuousRoundedRectangle(28.dp))
                .nestedScroll(scrollBehavior.nestedScrollConnection),
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
                        val isLastest = index == items.lastIndex
                        GenericItem(
                            data = item,
                            shape = if (isLastest) bottomShape else ContinuousRoundedRectangle(4.dp),
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
    val backgroundColor = if (data.background != Color.Unspecified) {
        data.background
    } else {
        MaterialTheme.colorScheme.surface
    }
    Surface(
        shape = shape,
        tonalElevation = if (data.checked()) 4.dp else 1.dp,
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
                expandedContent()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, heightDp = 600)
@Composable
fun PreviewUIList() {
    rememberSearchBarState()
    val scrollBehavior = remember {
        object : SearchBarScrollBehavior {
            override var scrollOffset: Float
                get() = 0f
                set(_) {}
            override var scrollOffsetLimit: Float
                get() = 0f
                set(_) {}
            override var contentOffset: Float
                get() = 0f
                set(_) {}
            override val nestedScrollConnection: NestedScrollConnection =
                object : NestedScrollConnection {}

            @SuppressLint("ModifierFactoryUnreferencedReceiver")
            override fun Modifier.searchBarScrollBehavior(): Modifier {
                TODO("Not yet implemented")
            }
        }
    }
    val items = listOf(
        ListItemData(
            title = { Text("1111111") },
            subtitle = "22222222",
            description = "33333333",
            label = { Text("444", color = MaterialTheme.colorScheme.primary) },
            headerIcon = { Icon(Tabler.Outline.Plus, null) }, // 示例图标
            actions = {
            },
            checked = { true },
            showCheckBox = { true },
            onCheckChange = {}
        ),
        ListItemData(
            title = { Text("55555555") },
            subtitle = "666666666",
            description = "777777777",
            headerIcon = { Icon(Tabler.Outline.InfoCircle, null) },
            actions = {
                Text("888888888888888", modifier = Modifier.padding(16.dp))
            },
            checked = { false },
            showCheckBox = { true },
            onCheckChange = {}
        ),
        ListItemData(
            title = { Text("999999999") },
            actions = {},
            checked = { false }
        ),
        ListItemData(
            title = { Text("1010101010") },
            subtitle = "11111111111111",
            background = MaterialTheme.colorScheme.secondaryContainer,
            actions = {},
            checked = { false }
        )
    )

    // 构建 UIList
    UIList(
        items = { items },
        onRefresh = {}, // 预览中无实际刷新
        isRefreshing = false,
        scrollBehavior = scrollBehavior,
        state = rememberLazyListState(),
        emptyContent = { Text("暂无数据") }
    )
}