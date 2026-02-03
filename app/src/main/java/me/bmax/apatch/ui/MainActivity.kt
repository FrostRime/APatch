package me.bmax.apatch.ui

import android.R
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults.ExtraSmall
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.rememberNavController
import coil.Coil
import coil.ImageLoader
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.rememberNavHostEngine
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator
import kotlinx.coroutines.launch
import me.bmax.apatch.APApplication
import me.bmax.apatch.ui.screen.APModuleScreen
import me.bmax.apatch.ui.screen.BottomBarDestination
import me.bmax.apatch.ui.screen.HomeScreen
import me.bmax.apatch.ui.screen.KPModuleScreen
import me.bmax.apatch.ui.screen.SettingScreen
import me.bmax.apatch.ui.screen.SuperUserScreen
import me.bmax.apatch.ui.theme.APatchTheme
import me.bmax.apatch.ui.viewmodel.SuperUserViewModel
import me.bmax.apatch.util.ui.LocalSnackbarHost
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private var isLoading = true

    val localNavigator = compositionLocalOf<DestinationsNavigator> {
        error("No DestinationsNavigator provided! Make sure to wrap your composable with NavigationLocalProvider")
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen().setKeepOnScreenCondition { isLoading }

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)

        setContent {
            APatchTheme {
                val navController = rememberNavController()
                val navigator = navController.rememberDestinationsNavigator()
                CompositionLocalProvider(
                    localNavigator provides navigator
                ) {
                    val defaultTransitions = object : NavHostAnimatedDestinationStyle() {
                        override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
                            {
                                fadeIn(animationSpec = tween(340))
                            }

                        override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
                            {
                                fadeOut(animationSpec = tween(340))
                            }

                        override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
                            {
                                fadeIn(animationSpec = tween(340))
                            }

                        override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
                            {
                                fadeOut(animationSpec = tween(340))
                            }
                    }
                    LocalConfiguration.current
                    DestinationsNavHost(
                        navGraph = NavGraphs.root,
                        navController = navController,
                        engine = rememberNavHostEngine(navHostContentAlignment = Alignment.TopCenter),
                        defaultTransitions = defaultTransitions
                    )
                }
            }
        }

        // Initialize Coil
        val iconSize = resources.getDimensionPixelSize(R.dimen.app_icon_size)
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components {
                    add(AppIconKeyer())
                    add(AppIconFetcher.Factory(iconSize, false, this@MainActivity))
                }
                .build()
        )

        isLoading = false
    }
}


@Destination<RootGraph>(start = true)
@Composable
fun MainScreen(navigator: DestinationsNavigator) {
    val state by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
    val kPatchReady = state != APApplication.State.UNKNOWN_STATE
    val aPatchReady = state == APApplication.State.ANDROIDPATCH_INSTALLED
    val visibleDestinations = remember(state) {
        BottomBarDestination.entries.filter { destination ->
            !(destination.kPatchRequired && !kPatchReady) && !(destination.aPatchRequired && !aPatchReady)
        }.toSet()
    }
    val snackBarHostState = remember { SnackbarHostState() }

    val pagerState = rememberPagerState(
        pageCount = { visibleDestinations.size }
    )
    val coroutineScope = rememberCoroutineScope()
    var isBottomBarVisible by remember { mutableStateOf(true) }
    var lastPage by remember { mutableIntStateOf(pagerState.currentPage) }
    var lastTouchY by remember { mutableStateOf(0f) }
    var lastTouchTime by remember { mutableStateOf(0L) }
    val minDragThreshold = 85f

    fun showBottomBar() {
        isBottomBarVisible = true
    }

    LaunchedEffect(Unit) {
        showBottomBar()
        if (SuperUserViewModel.apps.isEmpty()) {
            SuperUserViewModel().fetchAppList()
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != lastPage) {
            showBottomBar()
        }
    }

    LaunchedEffect(pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) {
            showBottomBar()
        }
    }

    val screenContents = remember {
        mapOf<String, @Composable () -> Unit>(
            BottomBarDestination.Home.name to {
                HomeScreen(navigator)
            },
            BottomBarDestination.KModule.name to {
                KPModuleScreen(navigator, isBottomBarVisible)
            },
            BottomBarDestination.SuperUser.name to { SuperUserScreen(isBottomBarVisible) },
            BottomBarDestination.AModule.name to {
                APModuleScreen(navigator, isBottomBarVisible)
            },
            BottomBarDestination.Settings.name to { SettingScreen() }
        )
    }

    LaunchedEffect(visibleDestinations) {
        if (pagerState.currentPage >= visibleDestinations.size) {
            pagerState.animateScrollToPage(0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()

                        val pressed = event.changes.any { it.pressed }
                        if (pressed) {
                            val currentY = event.changes[0].position.y
                            val deltaY = currentY - lastTouchY

                            if (minDragThreshold < abs(deltaY) && System.currentTimeMillis() - lastTouchTime < 100) {
                                isBottomBarVisible = deltaY > 0
                                @Suppress("AssignedValueIsNeverRead")
                                lastTouchY = currentY
                            }

                            lastTouchTime = System.currentTimeMillis()
                            lastTouchY = currentY
                        }
                    }
                }
            },
    ) {
        CompositionLocalProvider(
            LocalSnackbarHost provides snackBarHostState,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { page ->
                    visibleDestinations.elementAtOrNull(page)?.name ?: "unknown"
                },
                pageSpacing = 0.dp
            ) { page ->
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val destination = visibleDestinations.elementAtOrNull(page)
                    val screenContent = screenContents[destination?.name ?: ""]
                    if (screenContent != null) {
                        screenContent()
                    } else {
                        HomeScreen(navigator)
                    }
                }
            }
        }

        AnimatedVisibility(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 56.dp),
            visible = isBottomBarVisible,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight }
            ),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight }
            )
        ) {
            Box(
                contentAlignment = Alignment.BottomCenter
            ) {
                FloatingBottomBar(
                    visibleDestinations = visibleDestinations,
                    currentPage = pagerState.currentPage,
                    onPageSelected = { page ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(page)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FloatingBottomBar(
    visibleDestinations: Set<BottomBarDestination>,
    currentPage: Int,
    onPageSelected: (Int) -> Unit
) {
    val itemSize = 66.dp

    val indicatorOffset by animateDpAsState(
        targetValue = currentPage * itemSize,
        label = "indicatorOffset"
    )

    AnimatedContent(
        targetState = visibleDestinations,
    ) { visibleDestinations ->
        Surface(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    shape = CircleShape
                )
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    clip = true
                ),
            shape = CircleShape,
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.75f)
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .alpha(0.7f)
                        .matchParentSize()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth()
                            .alpha(0.15f)
                            .matchParentSize()
                            .clip(CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(x = indicatorOffset)
                                .width(itemSize)
                                .height(itemSize)
                                .clip(ExtraSmall)
                                .background(
                                    MaterialTheme.colorScheme.outline
                                )
                        )
                    }
                    Row(
                        modifier = Modifier
                            .padding(9.dp)
                            .fillMaxWidth()
                            .matchParentSize()
                            .clip(CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(x = indicatorOffset)
                                .width(itemSize - 2.dp)
                                .height(itemSize - 2.dp)
                                .clip(ExtraSmall)
                                .background(
                                    MaterialTheme.colorScheme.tertiaryContainer
                                )
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .padding(8.dp)
                ) {
                    visibleDestinations.forEachIndexed { index, destination ->
                        FloatingBottomBarItem(
                            destination = destination,
                            sliderOffset = indicatorOffset,
                            itemWidth = itemSize,
                            selected = currentPage == index,
                            index = index,
                            onClick = { onPageSelected(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingBottomBarItem(
    destination: BottomBarDestination,
    selected: Boolean,
    sliderOffset: Dp,
    itemWidth: Dp,
    index: Int,
    onClick: () -> Unit
) {
    val density = LocalDensity.current

    val iconStartPx = index * with(density) { itemWidth.toPx() }
    val iconEndPx = (index + 1) * with(density) { itemWidth.toPx() }
    val sliderPx = with(density) { sliderOffset.toPx() }
    val sliderEndPx = sliderPx + with(density) { itemWidth.toPx() }

    val isCovered = sliderPx <= iconStartPx && sliderEndPx >= iconEndPx

    val iconColor by animateColorAsState(
        targetValue = if (isCovered) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "iconColorAnimation"
    )

    val scale by animateFloatAsState(
        targetValue = if (isCovered) 1.1f else 1f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "iconScaleAnimation"
    )

    Box(
        modifier = Modifier
            .size(66.dp)
            .clickable(
                enabled = !selected,
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = isCovered,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "iconCrossfade"
        ) { covered ->
            Icon(
                imageVector = if (covered) destination.iconSelected else destination.iconNotSelected,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .size(22.dp)
                    .scale(scale)
            )
        }
    }
}
