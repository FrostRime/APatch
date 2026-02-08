package me.bmax.apatch.ui

//noinspection SuspiciousImport
import android.R
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.rememberNavController
import coil.Coil
import coil.ImageLoader
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousCapsule
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
import me.bmax.apatch.ui.component.LiquidBottomTab
import me.bmax.apatch.ui.component.LiquidBottomTabs
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
                val snackBarHostState = remember { SnackbarHostState() }
                CompositionLocalProvider(
                    localNavigator provides navigator,
                    LocalSnackbarHost provides snackBarHostState,
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
    val visibleDestinations by remember(kPatchReady, aPatchReady) {
        derivedStateOf {
            BottomBarDestination.entries.filter { destination ->
                !(destination.kPatchRequired && !kPatchReady) &&
                        !(destination.aPatchRequired && !aPatchReady)
            }.toSet()
        }
    }

    val visibleDestinationsSize = remember(visibleDestinations.size) {
        visibleDestinations.size
    }

    val pagerState = rememberPagerState(
        pageCount = { visibleDestinationsSize }
    )
    val coroutineScope = rememberCoroutineScope()
    var isBottomBarVisible by remember { mutableStateOf(true) }
    var lastTouchY by remember { mutableFloatStateOf(0f) }
    var lastTouchTime by remember { mutableLongStateOf(0L) }
    val minDragThreshold = 85f

    val backdrop = rememberLayerBackdrop()

    LaunchedEffect(Unit) {
        if (SuperUserViewModel.apps.isEmpty()) {
            SuperUserViewModel().fetchAppList()
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
        if (pagerState.currentPage >= visibleDestinationsSize) {
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
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop),
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

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .padding(bottom = 56.dp)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box {
            val barStateProgress by animateFloatAsState(
                targetValue = if (isBottomBarVisible) 1f else 0f,
                animationSpec = tween(250)
            )
            LiquidBottomTabs(
                selectedTabIndex = { pagerState.currentPage },
                onTabSelected = { index ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                },
                backdrop = backdrop,
                tabsCount = visibleDestinationsSize,
                modifier = Modifier.graphicsLayer(
                    scaleY = barStateProgress * 0.85f + 0.15f,
                    scaleX = barStateProgress * 0.85f + 0.15f,
                    alpha = barStateProgress
                )
            ) {
                repeat(visibleDestinationsSize) { index ->
                    LiquidBottomTab({
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    }) {
                        val destination = visibleDestinations.elementAtOrNull(index)
                        if (destination != null) {
                            Icon(
                                modifier = Modifier
                                    .size(28.dp),
                                imageVector = destination.iconSelected,
                                tint = MaterialTheme.colorScheme.onSurface,
                                contentDescription = null,
                            )
                            Text(
                                text = stringResource(id = destination.label),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .basicMarquee(),
                            )
                        }
                    }
                }
            }
            if (barStateProgress < 0.25f) {
                val selectedColor by rememberUpdatedState(MaterialTheme.colorScheme.onSurface)
                val unselectedColor by rememberUpdatedState(MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(4.dp)
                        .graphicsLayer(
                            scaleY = barStateProgress * 0.85f + 0.15f,
                            scaleX = barStateProgress * 0.85f + 0.15f,
                            alpha = 1 - barStateProgress
                        )
                        .clip(ContinuousCapsule)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable {
                            isBottomBarVisible = true
                        }
                ) {
                    visibleDestinations.forEachIndexed { index, _ ->
                        val iconColor by animateColorAsState(
                            targetValue = if (pagerState.currentPage == index) selectedColor else unselectedColor,
                            animationSpec = tween(250)
                        )
                        LiquidBottomTab(
                            {},
                            modifier = Modifier
                                .aspectRatio(1f, true)
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(iconColor)
                            )
                        }
                    }
                }
            }
        }
    }
}