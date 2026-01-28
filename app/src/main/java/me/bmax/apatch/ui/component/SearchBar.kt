package me.bmax.apatch.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowLeft
import kotlinx.coroutines.launch
import me.bmax.apatch.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@ExperimentalMaterial3Api
@Composable
fun pinnedScrollBehavior(
    canScroll: () -> Boolean = { true },
    snapAnimationSpec: AnimationSpec<Float> = MotionScheme.expressive().defaultEffectsSpec(),
    flingAnimationSpec: DecayAnimationSpec<Float> = rememberSplineBasedDecay(),
    reverseLayout: Boolean = false,
): SearchBarScrollBehavior =
    rememberSaveable(
        snapAnimationSpec,
        flingAnimationSpec,
        canScroll,
        reverseLayout,
        saver =
            PinnedScrollBehavior.Saver(
                canScroll = canScroll,
            ),
    ) {
        PinnedScrollBehavior(
            canScroll = canScroll,
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
private class PinnedScrollBehavior(
    val canScroll: () -> Boolean,
) : SearchBarScrollBehavior {
    // Offset remains 0 so the bar never moves vertically
    override var scrollOffset by mutableFloatStateOf(0f)
    override var scrollOffsetLimit by mutableFloatStateOf(0f)

    // Track contentOffset to allow for tonal elevation/color changes on scroll
    override var contentOffset by mutableFloatStateOf(0f)

    override fun Modifier.searchBarScrollBehavior(): Modifier {
        // We remove the .layout { ... } and .draggable blocks
        // that were responsible for hiding/moving the bar.
        return this.onSizeChanged { size ->
            scrollOffsetLimit = -size.height.toFloat()
        }
    }

    override val nestedScrollConnection: NestedScrollConnection =
        object : NestedScrollConnection {
            @Suppress("SameReturnValue")
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!canScroll()) return Offset.Zero

                // We don't modify scrollOffset here because we want it pinned.
                // We only return Offset.Zero to show we aren't consuming any scroll.
                return Offset.Zero
            }

            @Suppress("SameReturnValue")
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!canScroll()) return Offset.Zero

                // Update contentOffset so the UI knows how far the user has scrolled
                // This is used for "overlapped" state (changing colors/elevation)
                contentOffset += consumed.y
                return Offset.Zero
            }
        }

    companion object {
        fun Saver(
            canScroll: () -> Boolean
        ): Saver<PinnedScrollBehavior, *> =
            listSaver(
                save = {
                    listOf(
                        it.scrollOffset,
                        it.scrollOffsetLimit,
                        it.contentOffset,
                    )
                },
                restore = {
                    PinnedScrollBehavior(
                        canScroll = canScroll,
                    )
                },
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchAppBar(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    dropdownContent: @Composable (() -> Unit)? = null,
    scrollBehavior: SearchBarScrollBehavior? = null,
    searchBarPlaceHolderText: String
) {
    val textFieldState = rememberTextFieldState(initialText = searchText)
    val searchBarState = rememberSearchBarState(
        animationSpecForExpand = tween(0),
        animationSpecForCollapse = tween(0)
    )

    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    val isExpanded by remember {
        derivedStateOf { searchBarState.currentValue == SearchBarValue.Expanded }
    }

    BackHandler(isExpanded) {
        scope.launch { searchBarState.animateToCollapsed() }
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    LaunchedEffect(textFieldState.text) {
        onSearchTextChange(textFieldState.text.toString())
    }

    DisposableEffect(Unit) {
        onDispose { keyboardController?.hide() }
    }

    val colorScheme = MaterialTheme.colorScheme
    val cardColor = colorScheme.background

    AppBarWithSearch(
        state = searchBarState,
        inputField = {
            SearchBarDefaults.InputField(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .padding(bottom = 5.dp)
                    .clip(SearchBarDefaults.inputFieldShape)
                    .height(53.dp), // box padding + icon padding + icon size
                searchBarState = searchBarState,
                textFieldState = textFieldState,
                onSearch = { text ->
                    scope.launch { searchBarState.animateToCollapsed() }
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onSearchTextChange(text)
                },
                colors = SearchBarDefaults.inputFieldColors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                placeholder = {
                    AnimatedContent(
                        targetState = isExpanded,
                        transitionSpec = {
                            (fadeIn() + slideInHorizontally { -it })
                                .togetherWith(fadeOut() + slideOutHorizontally())
                        },
                        label = "searchBarTextAnimation"
                    ) { expanded ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = if (expanded) {
                                Arrangement.Start
                            } else {
                                Arrangement.Center
                            }
                        ) {
                            Text(
                                text = searchBarPlaceHolderText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(40.dp))
                        }
                    }
                },
                leadingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clipToBounds()
                    ) {
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn() + slideInHorizontally { -it / 2 },
                            exit = fadeOut() + slideOutHorizontally { -it / 2 }
                        ) {
                            Icon(
                                imageVector = Tabler.Outline.ArrowLeft,
                                contentDescription =
                                    stringResource(R.string.back),
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        scope.launch {
                                            searchBarState.animateToCollapsed()
                                            if (textFieldState.text.isNotEmpty()) {
                                                textFieldState.edit {
                                                    replace(0, length, "")
                                                }
                                            } else {
                                                keyboardController?.hide()
                                                focusManager.clearFocus()
                                            }
                                        }
                                    }
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            )
        },
        actions = {
            dropdownContent?.invoke()
        },
        scrollBehavior = scrollBehavior,
        colors = SearchBarDefaults.appBarWithSearchColors(
            searchBarColors = SearchBarDefaults.colors(
                containerColor = cardColor.copy(alpha = 0f)
            ),
            scrolledSearchBarContainerColor = cardColor.copy(alpha = 0f),
            appBarContainerColor = cardColor.copy(alpha = 0f),
            scrolledAppBarContainerColor = cardColor.copy(alpha = 0f)
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun SearchAppBarPreview() {
    SearchAppBar(
        searchText = "",
        onSearchTextChange = {},
        searchBarPlaceHolderText = ""
    )
}