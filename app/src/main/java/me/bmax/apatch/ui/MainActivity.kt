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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.rememberNavController
import coil.Coil
import coil.ImageLoader
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
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
import kotlin.math.max
import kotlin.math.min

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
    val visibleDestinations = remember(state) {
        BottomBarDestination.entries.filter { destination ->
            !(destination.kPatchRequired && !kPatchReady) && !(destination.aPatchRequired && !aPatchReady)
        }.toSet()
    }

    val pagerState = rememberPagerState(
        pageCount = { visibleDestinations.size }
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
            .fillMaxSize()
            .padding(bottom = 56.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        FloatingBottomBar(
            visibleDestinations = visibleDestinations,
            currentPage = pagerState.currentPage,
            onPageSelected = { page ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(page)
                }
            },
            visible = isBottomBarVisible,
            backdrop = backdrop
        )
    }
}

@Suppress("AssignedValueIsNeverRead")
@Composable
private fun FloatingBottomBar(
    visibleDestinations: Set<BottomBarDestination>,
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    visible: Boolean,
    backdrop: LayerBackdrop
) {
    val itemSize = 66.dp
    val coroutineScope = rememberCoroutineScope()

    val barScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.2f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    var isPressed by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    val indicatorOffset = remember { Animatable(0.dp, Dp.VectorConverter) }

    val indicatorScale by animateFloatAsState(
        targetValue = if (isPressed) 1.5f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )

    val indicatorHighlight by animateFloatAsState(
        targetValue = if (isPressed) 0.25f else 0f,
        animationSpec = tween(durationMillis = 150)
    )

    val indicatorBlur by animateDpAsState(
        targetValue = if (isPressed) 0.dp else 8.dp,
        animationSpec = tween(durationMillis = 150)
    )

    val indicatorAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f, animationSpec = tween(durationMillis = 150)
    )

    val updatedOnPageSelected by rememberUpdatedState(onPageSelected)
    val updatedVisibleDestinations by rememberUpdatedState(visibleDestinations)

    LaunchedEffect(currentPage) {
        if (!isDragging) {
            indicatorOffset.animateTo(
                targetValue = currentPage * itemSize,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    fun animatePressIn() {
        isPressed = true
    }

    fun animateRelease(targetPage: Int) {
        isPressed = false
        isDragging = false
        updatedOnPageSelected(targetPage)
    }

    val background =
        MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)

    val sliderBackground =
        MaterialTheme.colorScheme.onSurface.copy(0.1f).compositeOver(background)

    Box(
        modifier = Modifier.graphicsLayer(
            scaleX = barScale,
            scaleY = barScale
        ), contentAlignment = Alignment.CenterStart
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = Modifier
                    .width(updatedVisibleDestinations.size * itemSize + 8.dp)
                    .height(itemSize / 1.25f * (1 - (indicatorScale - 1) / 4) + 8.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        highlight = { Highlight(alpha = 0.25f) },
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            colorControls(brightness = 0.2f)
                            blur(8f.dp.toPx())
                            lens(16.dp.toPx(), 32.dp.toPx())
                        },
                        onDrawSurface = { drawRect(background) }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .matchParentSize()
                        .pointerInput(coroutineScope) {
                            detectTapGestures(
                                onTap = { offset ->
                                    coroutineScope.launch {
                                        val page = (offset.x.toDp() / itemSize)
                                            .toInt()
                                            .coerceIn(0, updatedVisibleDestinations.size - 1)
                                        animateRelease(page)
                                    }
                                },
                            )
                        }
                        .pointerInput(coroutineScope) {
                            detectDragGestures(
                                onDragStart = { _ ->
                                    coroutineScope.launch {
                                        isDragging = true
                                        animatePressIn()
                                    }
                                },
                                onDrag = { _, dragAmount ->
                                    coroutineScope.launch {
                                        val newOffset =
                                            indicatorOffset.value + dragAmount.x.toDp()
                                        val clampedOffset = newOffset.coerceIn(
                                            0.dp,
                                            (updatedVisibleDestinations.size - 1) * itemSize
                                        )
                                        indicatorOffset.snapTo(clampedOffset)
                                    }
                                },
                                onDragEnd = {
                                    coroutineScope.launch {
                                        val targetPage =
                                            ((indicatorOffset.value + itemSize / 2) / itemSize)
                                                .toInt()
                                                .coerceIn(
                                                    0,
                                                    updatedVisibleDestinations.size - 1
                                                )

                                        animateRelease(targetPage)
                                        indicatorOffset.animateTo(
                                            targetPage * itemSize,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        )
                                    }
                                },
                                onDragCancel = {
                                    coroutineScope.launch {
                                        isDragging = false
                                        isPressed = false
                                    }
                                }
                            )
                        }
                )
            }

            Row(modifier = Modifier.alpha(indicatorAlpha)) {
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset.value)
                        .width(itemSize)
                        .height(itemSize / 1.25f)
                        .graphicsLayer(
                            scaleX = indicatorScale,
                            scaleY = indicatorScale
                        )
                        .drawBackdrop(
                            backdrop = backdrop,
                            highlight = { Highlight(alpha = indicatorHighlight) },
                            shape = { CircleShape },
                            effects = {
                                vibrancy()
                                colorControls(brightness = 0.2f)
                                blur(indicatorBlur.toPx())
                                lens(16.dp.toPx(), 32.dp.toPx())
                            },
                            onDrawSurface = { drawRect(sliderBackground) }
                        ),
                )
            }

            if (updatedVisibleDestinations.size > 10) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(updatedVisibleDestinations.size) { index ->
                        visibleDestinations.elementAtOrNull(index)?.let {
                            FloatingBottomBarItem(
                                destination = it,
                                sliderOffset = indicatorOffset.value,
                                visible = visible,
                                itemWidth = itemSize,
                                backdrop = backdrop,
                                index = index
                            )
                        }
                    }
                }
            } else {
                Row {
                    Spacer(Modifier.width(4.dp))
                    updatedVisibleDestinations.forEachIndexed { index, destination ->
                        FloatingBottomBarItem(
                            destination = destination,
                            sliderOffset = indicatorOffset.value,
                            visible = visible,
                            itemWidth = itemSize,
                            backdrop = backdrop,
                            index = index
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
    visible: Boolean,
    sliderOffset: Dp,
    itemWidth: Dp,
    backdrop: LayerBackdrop,
    index: Int
) {
    val sliderEnd = sliderOffset + itemWidth
    val itemStart = index * itemWidth
    val itemEnd = (index + 1) * itemWidth

    val overlapStart = max(sliderOffset.value, itemStart.value)
    val overlapEnd = min(sliderEnd.value, itemEnd.value)
    val overlapLength = max(0f, overlapEnd - overlapStart)
    val overlapRatio = overlapLength / itemWidth.value

    val highlightStrength by animateFloatAsState(
        targetValue = overlapRatio.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
        label = "highlightStrengthAnimation"
    )

    val selectedColor by rememberUpdatedState(MaterialTheme.colorScheme.primary)
    val unselectedColor by rememberUpdatedState(MaterialTheme.colorScheme.onSurface)
    val iconColor by remember {
        derivedStateOf {
            lerp(
                unselectedColor,
                selectedColor,
                FastOutSlowInEasing.transform(highlightStrength)
            )
        }
    }

    val backgroundColor by rememberUpdatedState(MaterialTheme.colorScheme.outline)

    val backgroundAlpha by animateFloatAsState(
        targetValue = if (visible) 0.0f else 1f,
    )

    val iconAlpha = 0.7f + 0.3f * highlightStrength

    Box(
        modifier = Modifier
            .size(itemWidth, itemWidth / 1.25f)
            .padding(backgroundAlpha * 4.dp)
            .padding(horizontal = backgroundAlpha * 6.dp)
            .clip(CircleShape)
            .alpha(iconAlpha),
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = destination.iconSelected,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .size(itemWidth / 3)
            )
            Text(
                text = stringResource(id = destination.label),
                style = MaterialTheme.typography.bodySmall,
                color = iconColor,
                modifier = Modifier
                    .padding(4.dp)
                    .basicMarquee(),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .alpha(backgroundAlpha)
                .drawBackdrop(
                    backdrop = backdrop,
                    highlight = null,
                    shape = { CircleShape },
                    effects = {
                        blur(8.dp.toPx())
                        lens(16.dp.toPx(), 32.dp.toPx())
                    },
                    onDrawSurface = {
                        drawRect(backgroundColor)
                    }
                ),
        )
    }
}