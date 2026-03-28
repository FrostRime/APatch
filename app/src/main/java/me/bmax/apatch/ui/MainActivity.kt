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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
import com.kyant.backdrop.effects.colorControls
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
import me.bmax.apatch.ui.component.BackdropSurface
import me.bmax.apatch.ui.component.LiquidBottomTab
import me.bmax.apatch.ui.component.LiquidBottomTabs
import me.bmax.apatch.ui.component.LiquidButton
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
import me.bmax.apatch.util.ui.LocalWidgetBlur
import me.bmax.apatch.util.ui.LocalWidgetOpacity
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer
import kotlin.math.abs

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
                        LocalWidgetBlur provides remember {
                            mutableFloatStateOf(
                                prefs.getFloat(
                                    "widget_blur",
                                    1f
                                )
                            )
                        },
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
            }.toList()
        }
    }
    val visibleDestinationsSize = visibleDestinations.size

    val pagerState = rememberPagerState(pageCount = { visibleDestinationsSize })
    val coroutineScope = rememberCoroutineScope()
    var bottomBarMaximized by remember { mutableStateOf(true) }

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

    val fabBottomPadding by animateDpAsState(
        targetValue = if (bottomBarMaximized) 160.dp else 84.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
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
                                bottomBarMaximized = deltaY > 0
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
            BlurWallpaperBackground(backdrop = wallpaperBackdrop)

            WallpaperBackground()

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
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            contentAlignment = Alignment.BottomEnd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
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
            bottomBarMaximized = { bottomBarMaximized },
            selectedTab = { selectedTab },
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
                bottomBarMaximized = true
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun WallpaperBackground(modifier: Modifier = Modifier) {
    val wallpaper = LocalWallpaper.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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

@Composable
fun BlurWallpaperBackground(backdrop: LayerBackdrop, modifier: Modifier = Modifier) {
    val wallpaper = LocalWallpaper.current
    val blur = LocalWidgetBlur.current
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
                modifier = Modifier
                    .fillMaxSize()
                    .blur(32.dp * blur.value)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopAppBar(backdrop: LayerBackdrop, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val widgetOpacity = LocalWidgetOpacity.current
    TopAppBar(
        title = {},
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        modifier = modifier
            .graphicsLayer(alpha = widgetOpacity.value)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { ContinuousRoundedRectangle(0.dp) },
                effects = {},
                highlight = null,
                shadow = null,
                onDrawSurface = { drawRect(colorScheme.surface.copy(alpha = 0.45f)) }
            )
    )
}

@Composable
fun MainPager(
    pagerState: PagerState,
    visibleDestinations: List<BottomBarDestination>,
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
    val menuAnim by animateFloatAsState(
        targetValue = if (fabExpanded) 0f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )
    val fabAnim by animateFloatAsState(
        targetValue = if (currentFab != null) 0f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )
    val menuBackdrop = rememberLayerBackdrop()
    val fabBackdrop = rememberLayerBackdrop()
    AnimatedVisibility(
        visible = currentFab != null,
        enter = scaleIn(transformOrigin = TransformOrigin(1f, 1f)) + fadeIn(),
        exit = scaleOut(transformOrigin = TransformOrigin(1f, 1f)) + fadeOut(),
        modifier = modifier
    ) {
        lastNotNullFab.let { fab ->
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
            ) {
                if (fab.menuItems != null) {
                    AnimatedVisibility(
                        visible = fabExpanded,
                        enter = scaleIn(transformOrigin = TransformOrigin(1f, 1f)) + fadeIn(),
                        exit = scaleOut(transformOrigin = TransformOrigin(1f, 1f)) + fadeOut(),
                        modifier = Modifier.zIndex(1f)
                    ) {
                        Box {
                            LiquidButton(
                                backdrop = backdrop,
                                tint = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.layerBackdrop(menuBackdrop),
                                shape = ContinuousRoundedRectangle(16.dp)
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
                                                color = MaterialTheme.colorScheme.outline.copy(
                                                    alpha = 0.2f
                                                ),
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
                                            item.icon?.let {
                                                Icon(
                                                    it,
                                                    contentDescription = null
                                                )
                                            }
                                            item.title?.let { Text(it) }
                                        }
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .drawBackdrop(
                                        menuBackdrop,
                                        highlight = null,
                                        shadow = null,
                                        shape = { ContinuousRoundedRectangle(16.dp) },
                                        effects = {
                                            blur(menuAnim * 32.dp.toPx())
                                            lens(
                                                size.minDimension * 0.5f,
                                                menuAnim * size.minDimension,
                                                true
                                            )
                                            colorControls(brightness = menuAnim)
                                        })
                            )
                        }
                    }
                }

                Box {
                    LiquidButton(
                        backdrop = backdrop,
                        modifier = Modifier
                            .size(56.dp)
                            .layerBackdrop(fabBackdrop),
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
                        Crossfade(
                            targetState = fab.icon,
                            animationSpec = tween(500)
                        ) { targetIcon ->
                            Icon(
                                targetIcon,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .drawBackdrop(
                                fabBackdrop,
                                highlight = null,
                                shadow = null,
                                shape = { ContinuousCapsule },
                                effects = {
                                    blur(fabAnim * 32.dp.toPx())
                                    lens(
                                        size.minDimension * 0.5f,
                                        fabAnim * size.minDimension,
                                        true
                                    )
                                    colorControls(brightness = fabAnim)
                                })
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavigationSection(
    bottomBarMaximized: () -> Boolean,
    selectedTab: () -> Int,
    onTabSelected: (Int) -> Unit,
    visibleDestinations: List<BottomBarDestination>,
    pagerState: PagerState,
    backdrop: LayerBackdrop,
    onExpandBar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bottomPaddingAnim by animateFloatAsState(
        targetValue = if (bottomBarMaximized()) 1f else 0f
    )
    SharedTransitionLayout {
        AnimatedContent(targetState = bottomBarMaximized()) { bottomBarMaximized ->
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = modifier.padding(bottom = 44.dp * bottomPaddingAnim + 12.dp)
            ) {
                if (bottomBarMaximized) {
                    LiquidBottomTabs(
                        selectedTabIndex = selectedTab,
                        onTabSelected = onTabSelected,
                        backdrop = backdrop,
                        tabsCount = visibleDestinations.size,
                        modifier = Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(key = "bottomBar"),
                            animatedVisibilityScope = this@AnimatedContent,
                            clipInOverlayDuringTransition = OverlayClip(ContinuousCapsule)
                        )
                    ) {
                        repeat(visibleDestinations.size) { index ->
                            val destination = visibleDestinations.elementAtOrNull(index)
                            LiquidBottomTab(
                                modifier = Modifier.defaultMinSize(minWidth = 76.dp)
                            ) {
                                Icon(
                                    imageVector = destination?.iconSelected
                                        ?: return@LiquidBottomTab,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(id = destination.label),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .basicMarquee(),
                                    softWrap = false,
                                    overflow = TextOverflow.Visible
                                )
                            }
                        }
                    }
                } else {
                    val selectedColor = MaterialTheme.colorScheme.onSurface
                    val unselectedColor = MaterialTheme.colorScheme.outline
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.2f)
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState(key = "bottomBar"),
                                animatedVisibilityScope = this@AnimatedContent,
                                clipInOverlayDuringTransition = OverlayClip(ContinuousCapsule)
                            )
                    ) {
                        BackdropSurface(
                            backdrop = backdrop,
                            modifier = Modifier
                                .height(8.dp)
                                .padding(2.dp),
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
                                    modifier = Modifier.padding(horizontal = 1.dp)
                                ) {
                                    BackdropSurface(
                                        backdrop = backdrop,
                                        modifier = Modifier.fillMaxSize(),
                                        shape = ContinuousCapsule,
                                        onClick = onExpandBar,
                                        tint = iconColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}