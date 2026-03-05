package me.bmax.apatch.ui

//noinspection SuspiciousImport
import android.R
import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.rememberNavController
import coil.Coil
import coil.ImageLoader
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Error404
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.rememberNavHostEngine
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.launch
import me.bmax.apatch.APApplication
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.component.LiquidBottomTab
import me.bmax.apatch.ui.component.LiquidBottomTabs
import me.bmax.apatch.ui.component.LiquidButton
import me.bmax.apatch.ui.component.LiquidSurface
import me.bmax.apatch.ui.screen.APModuleScreen
import me.bmax.apatch.ui.screen.BottomBarDestination
import me.bmax.apatch.ui.screen.HomeScreen
import me.bmax.apatch.ui.screen.KPModuleScreen
import me.bmax.apatch.ui.screen.SettingScreen
import me.bmax.apatch.ui.screen.SuperUserScreen
import me.bmax.apatch.ui.theme.APatchTheme
import me.bmax.apatch.ui.viewmodel.SuperUserViewModel
import me.bmax.apatch.util.ui.LocalInnerPadding
import me.bmax.apatch.util.ui.LocalNavigator
import me.bmax.apatch.util.ui.LocalSnackbarHost
import me.bmax.apatch.util.ui.LocalWallpaper
import me.bmax.apatch.util.ui.LocalWallpaperBackdrop
import me.bmax.apatch.util.ui.LocalWidgetOpacity
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

typealias FabProvider = (Fab?) -> Unit

data class Fab(
    val icon: ImageVector,
    val menuItems: List<MenuItem>? = null,
    val onClick: (() -> Unit)? = null
)

data class MenuItem(
    val icon: ImageVector? = null,
    val title: String? = null,
    val onClick: () -> Unit
)

typealias ScreenEntry = @Composable (FabProvider) -> Unit

class MainActivity : AppCompatActivity() {

    private var isLoading = true

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen().setKeepOnScreenCondition { isLoading }

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)
        val prefs = APApplication.sharedPreferences

        val wallpaperDir: ExtendedFile =
            FileSystemManager.getLocal().getFile(apApp.filesDir.parent, "wallpaper")
        if (!wallpaperDir.exists()) {
            wallpaperDir.mkdirs()
        }
        val wallpaperFile = wallpaperDir.getChildFile("wallpaper.jpg")

        val initialWallpaperImageBitmap = try {
            BitmapFactory.decodeStream(wallpaperFile.inputStream()).asImageBitmap()
        } catch (_: Exception) {
            null
        }

        setContent {
            CompositionLocalProvider(
                LocalWallpaper provides remember {
                    mutableStateOf(initialWallpaperImageBitmap)
                }
            ) {
                val wallpaper = LocalWallpaper.current
                APatchTheme(wallpaper.value) {
                    val navController = rememberNavController()
                    val navigator = navController.rememberDestinationsNavigator()
                    val snackBarHostState = remember { SnackbarHostState() }
                    CompositionLocalProvider(
                        LocalNavigator provides navigator,
                        LocalSnackbarHost provides snackBarHostState,
                        LocalWidgetOpacity provides remember {
                            mutableFloatStateOf(
                                prefs.getFloat(
                                    "widget_opacity",
                                    1f
                                )
                            )
                        }) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>(start = true)
@Composable
fun MainScreen() {
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
    val visibleDestinationsSize = visibleDestinations.size

    val pagerState = rememberPagerState(pageCount = { visibleDestinationsSize })
    val coroutineScope = rememberCoroutineScope()
    var isBottomBarVisible by remember { mutableStateOf(true) }

    val backdrop = rememberLayerBackdrop()
    var fabExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (SuperUserViewModel.apps.isEmpty()) SuperUserViewModel().fetchAppList()
        fabExpanded = false
    }
    LaunchedEffect(visibleDestinations) {
        if (pagerState.currentPage >= visibleDestinationsSize) pagerState.animateScrollToPage(0)
        fabExpanded = false
    }
    LaunchedEffect(pagerState.isScrollInProgress) { fabExpanded = false }

    val barStateProgress by animateFloatAsState(
        targetValue = if (isBottomBarVisible) 1f else 0f,
        animationSpec = tween(250)
    )
    val fabBottomPadding by animateDpAsState(
        targetValue = if (isBottomBarVisible) 160.dp else 84.dp
    )
    val innerPadding by remember(fabBottomPadding) {
        derivedStateOf { PaddingValues(bottom = fabBottomPadding + 40.dp) }
    }

    val fabStates = remember { mutableStateMapOf<BottomBarDestination, Fab?>() }
    val screenRegistry: Map<BottomBarDestination, ScreenEntry> = mapOf(
        BottomBarDestination.Home to { setFab -> HomeScreen(setFab) },
        BottomBarDestination.KModule to { setFab -> KPModuleScreen(setFab) },
        BottomBarDestination.SuperUser to { setFab -> SuperUserScreen(setFab) },
        BottomBarDestination.AModule to { setFab -> APModuleScreen(setFab) },
        BottomBarDestination.Settings to { setFab -> SettingScreen(setFab) }
    )

    val currentFab = remember(fabStates, pagerState.currentPage) {
        derivedStateOf {
            visibleDestinations.elementAtOrNull(pagerState.currentPage)?.let { fabStates[it] }
        }
    }.value
    var lastNotNullFab by remember { mutableStateOf(Fab(icon = Tabler.Outline.Error404)) }
    LaunchedEffect(currentFab) { currentFab?.let { lastNotNullFab = it } }

    var selectedTab by remember { mutableIntStateOf(pagerState.currentPage) }
    var isUserDragging by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.interactionSource) {
        pagerState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) isUserDragging = true
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (isUserDragging) selectedTab = pagerState.currentPage
    }
    LaunchedEffect(pagerState) {
        launch {
            snapshotFlow { pagerState.isScrollInProgress }.collect { isScrolling ->
                if (!isScrolling) isUserDragging = false
            }
        }
        launch {
            snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }.collect { (page, isScrolling) ->
                if (!isScrolling && page != selectedTab) selectedTab = page
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .layerBackdrop(backdrop)
            .pointerInput(Unit) {
                var lastY = 0f
                var lastTime = 0L
                val threshold = 85f
                val timeWindow = 100L
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.any { it.pressed }
                        if (pressed) {
                            val currentY = event.changes[0].position.y
                            val deltaY = currentY - lastY
                            val now = System.currentTimeMillis()
                            if (abs(deltaY) > threshold && now - lastTime < timeWindow) {
                                @Suppress("AssignedValueIsNeverRead")
                                isBottomBarVisible = deltaY > 0
                            }
                            lastY = currentY
                            lastTime = now
                        }
                    }
                }
            }
    ) {
        CompositionLocalProvider(
            LocalWallpaperBackdrop provides rememberLayerBackdrop(),
            LocalInnerPadding provides innerPadding
        ) {
            val wallpaperBackdrop = LocalWallpaperBackdrop.current

            WallpaperBackground(backdrop = wallpaperBackdrop)

            MainTopAppBar(backdrop = wallpaperBackdrop)

            MainPager(
                pagerState = pagerState,
                visibleDestinations = visibleDestinations,
                screenRegistry = screenRegistry,
                onFabChange = { dest, fab -> fabStates[dest] = fab }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            contentAlignment = Alignment.BottomEnd,
            modifier = Modifier.fillMaxWidth()
        ) {
            FloatingActionButton(
                currentFab = currentFab,
                lastNotNullFab = lastNotNullFab,
                fabExpanded = fabExpanded,
                onFabExpandedChange = { fabExpanded = it },
                backdrop = backdrop,
                modifier = Modifier.padding(bottom = fabBottomPadding)
            )
        }

        BottomNavigationSection(
            barStateProgress = barStateProgress,
            selectedTab = {selectedTab},
            onTabSelected = { index ->
                coroutineScope.launch {
                    selectedTab = index
                    pagerState.animateScrollToPage(index)
                }
            },
            visibleDestinations = visibleDestinations,
            pagerState = pagerState,
            backdrop = backdrop,
            onExpandBar = {
                @Suppress("AssignedValueIsNeverRead")
                isBottomBarVisible = true
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun WallpaperBackground(backdrop: LayerBackdrop, modifier: Modifier = Modifier) {
    val wallpaper = LocalWallpaper.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .layerBackdrop(backdrop)
    ) {
        wallpaper.value?.let { wallpaperBitmap ->
            Image(
                bitmap = wallpaperBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopAppBar(backdrop: LayerBackdrop, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    TopAppBar(
        title = {},
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        modifier = modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { ContinuousRoundedRectangle(0.dp) },
            effects = {
                blur(8.dp.toPx())
                lens(12f.dp.toPx(), 24f.dp.toPx())
            },
            highlight = null,
            shadow = null,
            onDrawSurface = { drawRect(colorScheme.surface.copy(alpha = 0.45f)) }
        )
    )
}

@Composable
fun MainPager(
    pagerState: PagerState,
    visibleDestinations: Set<BottomBarDestination>,
    screenRegistry: Map<BottomBarDestination, ScreenEntry>,
    onFabChange: (BottomBarDestination, Fab?) -> Unit,
    modifier: Modifier = Modifier
) {
    val widgetOpacity = LocalWidgetOpacity.current
    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxSize()
            .alpha(widgetOpacity.value),
        beyondViewportPageCount = 3,
        key = { page -> visibleDestinations.elementAtOrNull(page)?.name ?: "unknown" },
        pageSpacing = 0.dp
    ) { page ->
        Box(Modifier.fillMaxSize()) {
            val destination = visibleDestinations.elementAtOrNull(page) ?: BottomBarDestination.Home
            screenRegistry[destination]?.invoke { fab -> onFabChange(destination, fab) }
        }
    }
}

@Composable
fun FloatingActionButton(
    currentFab: Fab?,
    lastNotNullFab: Fab,
    fabExpanded: Boolean,
    onFabExpandedChange: (Boolean) -> Unit,
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = currentFab != null,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        lastNotNullFab.let { fab ->
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
            ) {
                if (fab.menuItems != null) {
                    val alpha by animateFloatAsState(
                        targetValue = if (fabExpanded) 2f else 0f,
                        animationSpec = tween(500)
                    )
                    if (fabExpanded) {
                        LiquidButton(
                            backdrop = backdrop,
                            tint = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .zIndex(1f)
                                .alpha(min(alpha, 1f)),
                            shape = ContinuousRoundedRectangle(16.dp),
                            onClick = {},
                            shadowAlpha = max(0f, alpha - 1)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .width(IntrinsicSize.Max),
                                horizontalAlignment = Alignment.End
                            ) {
                                fab.menuItems.forEachIndexed { index, item ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                            thickness = 1.dp
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                            .clickable(
                                                interactionSource = null,
                                                indication = null,
                                                role = Role.Button,
                                                onClick = {
                                                    item.onClick()
                                                    onFabExpandedChange(false)
                                                }
                                            ),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        item.icon?.let { Icon(it, contentDescription = null) }
                                        item.title?.let { Text(it) }
                                    }
                                }
                            }
                        }
                    }
                }

                LiquidButton(
                    backdrop = backdrop,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary,
                    shape = ContinuousCapsule,
                    onClick = {
                        if (fab.menuItems == null) {
                            fab.onClick?.invoke()
                        } else {
                            onFabExpandedChange(!fabExpanded)
                        }
                    }
                ) {
                    Crossfade(targetState = fab.icon, animationSpec = tween(500)) { targetIcon ->
                        Icon(targetIcon, contentDescription = null, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavigationSection(
    barStateProgress: Float,
    selectedTab: () -> Int,
    onTabSelected: (Int) -> Unit,
    visibleDestinations: Set<BottomBarDestination>,
    pagerState: PagerState,
    backdrop: LayerBackdrop,
    onExpandBar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = modifier.padding(bottom = 48.dp * barStateProgress + 8.dp)
    ) {
        if (barStateProgress >= 0.25f) {
            LiquidBottomTabs(
                selectedTabIndex = selectedTab,
                onTabSelected = onTabSelected,
                backdrop = backdrop,
                tabsCount = visibleDestinations.size,
                modifier = Modifier
                    .scale(barStateProgress * 0.85f + 0.15f)
                    .graphicsLayer(alpha = barStateProgress)
            ) {
                repeat(visibleDestinations.size) { index ->
                    val destination = visibleDestinations.elementAtOrNull(index)
                    LiquidBottomTab {
                        Icon(
                            imageVector = destination?.iconSelected ?: return@LiquidBottomTab,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(id = destination.label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .basicMarquee()
                        )
                    }
                }
            }
        }

        if (barStateProgress < 0.25f) {
            val selectedColor = MaterialTheme.colorScheme.onSurface
            val unselectedColor = MaterialTheme.colorScheme.outline
            Box(
                Modifier
                    .fillMaxWidth()
                    .scale(barStateProgress * 0.85f + 0.15f)
                    .graphicsLayer(alpha = 1 - barStateProgress)
            ) {
                LiquidSurface(
                    backdrop = backdrop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    shape = ContinuousCapsule,
                    tint = MaterialTheme.colorScheme.surface,
                    onClick = onExpandBar
                ) {
                    visibleDestinations.forEachIndexed { index, _ ->
                        val iconColor by animateColorAsState(
                            targetValue = if (pagerState.currentPage == index) selectedColor else unselectedColor,
                            animationSpec = tween(250)
                        )
                        LiquidBottomTab(
                            modifier = Modifier
                                .size(28.dp)
                                .padding(4.dp)
                        ) {
                            LiquidSurface(
                                backdrop = backdrop,
                                modifier = Modifier.fillMaxSize(),
                                shape = ContinuousCapsule,
                                onClick = onExpandBar,
                                tint = iconColor
                            ) {}
                        }
                    }
                }
            }
        }
    }
}