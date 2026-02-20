package me.bmax.apatch.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarDefaults.InputField
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ArrowLeft
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.launch
import me.bmax.apatch.R
import me.bmax.apatch.util.ui.InteractiveHighlight
import me.bmax.apatch.util.ui.rememberUISensor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchAppBar(
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    wallpaperBackdrop: Backdrop,
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

    val animationScope = rememberCoroutineScope()

    remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope
        )
    }

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

    LaunchedEffect(Unit) {
        scope.launch {
            searchBarState.animateToCollapsed()
            keyboardController?.hide()
            focusManager.clearFocus()

        }
    }

    DisposableEffect(Unit) {
        onDispose { keyboardController?.hide() }
    }

    val colorScheme by rememberUpdatedState(MaterialTheme.colorScheme)

    val uiSensor = rememberUISensor()

    val highlightAngle by animateFloatAsState(
        targetValue = uiSensor?.gravityAngle ?: 45f,
        animationSpec = tween(400)
    )
    Box(Modifier.fillMaxWidth()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            title = {})

        Column(modifier = Modifier.matchParentSize()) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                InputField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .clip(SearchBarDefaults.inputFieldShape)
                        .height(53.dp) // box padding + icon padding + icon size
                        .drawBackdrop(
                            backdrop = wallpaperBackdrop,
                            shape = { ContinuousCapsule },
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
                                    alpha = 0.25f
                                )
                            },
                            shadow = {
                                Shadow(
                                    alpha = 0f
                                )
                            },
                            onDrawSurface = {
                                drawRect(
                                    colorScheme.surfaceContainerHigh,
                                    blendMode = BlendMode.Hue
                                )
                                drawRect(colorScheme.surfaceContainerHigh.copy(alpha = 0.87f))
                            }
                        ),
                    searchBarState = searchBarState,
                    textFieldState = textFieldState,
                    onSearch = { text ->
                        scope.launch { searchBarState.animateToCollapsed() }
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onSearchTextChange(text)
                    },
                    leadingIcon = {}
                )
                AnimatedContent(
                    modifier = Modifier.matchParentSize(),
                    targetState = isExpanded,
                    transitionSpec = {
                        (fadeIn() + slideInHorizontally { -it / 2 })
                            .togetherWith(fadeOut() + slideOutHorizontally { -it / 2 })
                    },
                    label = "searchBarTextAnimation"
                ) { expanded ->
                    Row(
                        Modifier.matchParentSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (expanded) {
                            Icon(
                                imageVector = Tabler.Outline.ArrowLeft,
                                contentDescription =
                                    stringResource(R.string.back),
                                tint = colorScheme.onSurface,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
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
                        if (searchText.isEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                            }
                        }
                    }
                }
            }
        }
    }
}