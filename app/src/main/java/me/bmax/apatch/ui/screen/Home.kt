package me.bmax.apatch.ui.screen

//import androidx.compose.material3.OutlinedTextField
import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.system.Os
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.edit
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.lifecycleScope
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.filled.AlertTriangle
import com.composables.icons.tabler.filled.Eye
import com.composables.icons.tabler.outline.ArrowAutofitDown
import com.composables.icons.tabler.outline.Blur
import com.composables.icons.tabler.outline.Check
import com.composables.icons.tabler.outline.Checks
import com.composables.icons.tabler.outline.CircleDashedX
import com.composables.icons.tabler.outline.DeviceUnknown
import com.composables.icons.tabler.outline.Edit
import com.composables.icons.tabler.outline.EyeOff
import com.composables.icons.tabler.outline.Forbid2
import com.composables.icons.tabler.outline.HelpCircle
import com.composables.icons.tabler.outline.PhotoCog
import com.composables.icons.tabler.outline.PhotoX
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Reload
import com.composables.icons.tabler.outline.Wand
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.materialkolor.ktx.themeColor
import com.ramcosta.composedestinations.generated.destinations.AboutScreenDestination
import com.ramcosta.composedestinations.generated.destinations.InstallModeSelectScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import me.bmax.apatch.R
import me.bmax.apatch.TAG
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.FabProvider
import me.bmax.apatch.ui.component.LiquidButton
import me.bmax.apatch.ui.component.LiquidSlider
import me.bmax.apatch.ui.component.LiquidSurface
import me.bmax.apatch.ui.component.ProvideMenuShape
import me.bmax.apatch.ui.component.WarningCard
import me.bmax.apatch.ui.component.rememberConfirmDialog
import me.bmax.apatch.util.LatestVersionInfo
import me.bmax.apatch.util.Version
import me.bmax.apatch.util.Version.getManagerVersion
import me.bmax.apatch.util.checkNewVersion
import me.bmax.apatch.util.getSELinuxStatus
import me.bmax.apatch.util.inputStream
import me.bmax.apatch.util.reboot
import me.bmax.apatch.util.rootShellForResult
import me.bmax.apatch.util.ui.LocalInnerPadding
import me.bmax.apatch.util.ui.LocalWallpaper
import me.bmax.apatch.util.ui.LocalWallpaperBackdrop
import me.bmax.apatch.util.ui.LocalWidgetOpacity

private val managerVersion = getManagerVersion()

@Composable
fun HomeScreen(
    navigator: DestinationsNavigator,
    setFab: FabProvider
) {
    val kpState by APApplication.kpStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
    val apState by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)

    LaunchedEffect(Unit) {
        setFab(null)
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopBar(
                onInstallClick =
                    dropUnlessResumed {
                        navigator.navigate(InstallModeSelectScreenDestination)
                    },
                kpState
            )
        }) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp)
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clip(ContinuousRoundedRectangle(16.dp)),
            contentPadding = LocalInnerPadding.current
        ) {
            item {
                WarningCard()
            }
            item {
                KStatusCard(kpState, apState, navigator)
            }
            if (kpState != APApplication.State.UNKNOWN_STATE &&
                apState != APApplication.State.ANDROIDPATCH_INSTALLED
            ) {
                item {
                    AStatusCard(apState)
                }
            }
            val checkUpdate = APApplication.sharedPreferences.getBoolean("check_update", true)
            if (checkUpdate) {
                item {
                    UpdateCard()
                }
            }
            item {
                InfoCard()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthFailedTipDialog(showDialog: MutableState<Boolean>) {
    BasicAlertDialog(
        onDismissRequest = { showDialog.value = false },
        properties =
            DialogProperties(
                decorFitsSystemWindows = true,
                usePlatformDefaultWidth = false,
                securePolicy = SecureFlagPolicy.SecureOff
            )
    ) {
        Surface(
            modifier = Modifier
                .width(320.dp)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            color = AlertDialogDefaults.containerColor,
        ) {
            Column(modifier = Modifier.padding(all = 24.dp)) {
                // Title
                Box(
                    Modifier
                        .padding(bottom = 16.dp)
                        .align(Alignment.Start)
                ) {
                    Text(
                        text = stringResource(id = R.string.home_dialog_auth_fail_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                // Content
                Box(
                    Modifier
                        .weight(weight = 1f, fill = false)
                        .padding(bottom = 24.dp)
                        .align(Alignment.Start)
                ) {
                    Text(
                        text = stringResource(id = R.string.home_dialog_auth_fail_content),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showDialog.value = false }) {
                        Text(text = stringResource(id = android.R.string.ok))
                    }
                }
            }
        }
    }
}

val checkSuperKeyValidation: (superKey: String) -> Boolean = { superKey ->
    superKey.length in 8..63 && superKey.any { it.isDigit() } && superKey.any { it.isLetter() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthSuperKey(showDialog: MutableState<Boolean>, showFailedDialog: MutableState<Boolean>) {
    var key by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var enable by remember { mutableStateOf(false) }

    BasicAlertDialog(
        onDismissRequest = { showDialog.value = false },
        properties =
            DialogProperties(
                decorFitsSystemWindows = true,
                securePolicy = SecureFlagPolicy.SecureOff
            )
    ) {
        Surface(
            modifier = Modifier
                .width(310.dp)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            color = AlertDialogDefaults.containerColor,
        ) {
            Column(modifier = Modifier.padding(all = 24.dp)) {
                // Title
                Box(
                    Modifier
                        .padding(bottom = 16.dp)
                        .align(Alignment.Start)
                ) {
                    Text(
                        text = stringResource(id = R.string.home_auth_key_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                // Content
                Box(
                    Modifier
                        .weight(weight = 1f, fill = false)
                        .align(Alignment.Start)
                ) {
                    Text(
                        text = stringResource(id = R.string.home_auth_key_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Content2
                Box(
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    OutlinedTextField(
                        value = key,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        onValueChange = {
                            key = it
                            enable = checkSuperKeyValidation(key)
                        },
                        shape = MaterialTheme.shapes.large,
                        label = { Text(stringResource(id = R.string.super_key)) },
                        visualTransformation =
                            if (keyVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true
                    )
                    IconButton(
                        modifier = Modifier
                            .size(40.dp)
                            .padding(top = 15.dp, end = 5.dp),
                        onClick = { keyVisible = !keyVisible }
                    ) {
                        Icon(
                            imageVector =
                                if (keyVisible) Tabler.Filled.Eye
                                else Tabler.Outline.EyeOff,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                // Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showDialog.value = false }) {
                        Text(stringResource(id = android.R.string.cancel))
                    }

                    Button(
                        onClick = {
                            showDialog.value = false

                            val preVerifyKey = Natives.nativeReady(key)
                            if (preVerifyKey) {
                                APApplication.superKey = key
                            } else {
                                showFailedDialog.value = true
                            }
                        },
                        enabled = enable
                    ) { Text(stringResource(id = android.R.string.ok)) }
                }
            }
        }
    }
}

@Composable
fun RebootDropdownItem(@StringRes id: Int, reason: String = "") {
    DropdownMenuItem(text = { Text(stringResource(id)) }, onClick = { reboot(reason) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetSUPathDialog(showDialog: MutableState<Boolean>) {
    val context = LocalContext.current
    var suPath by remember { mutableStateOf(Natives.suPath()) }
    BasicAlertDialog(
        onDismissRequest = { showDialog.value = false }, properties = DialogProperties(
            decorFitsSystemWindows = true
        )
    ) {
        Surface(
            modifier = Modifier
                .width(310.dp)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            color = AlertDialogDefaults.containerColor,
        ) {
            Column(modifier = Modifier.padding(all = 24.dp)) {
                Box(
                    Modifier
                        .padding(bottom = 16.dp)
                        .align(Alignment.Start)
                ) {
                    Text(
                        text = stringResource(id = R.string.setting_reset_su_path),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = suPath,
                    onValueChange = {
                        suPath = it
                    },
                    shape = MaterialTheme.shapes.large,
                    label = { Text(stringResource(id = R.string.setting_reset_su_new_path)) },
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showDialog.value = false }) {

                        Text(stringResource(id = android.R.string.cancel))
                    }

                    Button(enabled = suPathChecked(suPath), onClick = {
                        showDialog.value = false
                        val success = Natives.resetSuPath(suPath)
                        Toast.makeText(
                            context,
                            if (success) R.string.success else R.string.failure,
                            Toast.LENGTH_SHORT
                        ).show()
                        rootShellForResult("echo $suPath > ${APApplication.SU_PATH_FILE}")
                    }) {
                        Text(stringResource(id = android.R.string.ok))
                    }
                }
            }
        }
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onInstallClick: () -> Unit,
    kpState: APApplication.State
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val colorScheme by rememberUpdatedState(MaterialTheme.colorScheme)
    val prefs = APApplication.sharedPreferences
    val widgetOpacity = LocalWidgetOpacity.current
    val wallpaper = LocalWallpaper.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val ucropLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.let { data ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    val output = UCrop.getOutput(data)
                    if (output == null) {
                        wallpaper.value = null
                    } else {
                        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            val bitmap =
                                BitmapFactory.decodeStream(output.inputStream()).asImageBitmap()
                            val seedColor = bitmap.themeColor(fallback = Color.Blue)
                            withContext(Dispatchers.Main) {
                                wallpaper.value = bitmap
                                prefs.edit { putInt("theme_seed_color", seedColor.toArgb()) }
                            }
                        }
                    }
                    Log.d(TAG, "Crop success: ${data.data}")
                } else if (result.resultCode == UCrop.RESULT_ERROR) {
                    val error = UCrop.getError(data)
                    Log.e(TAG, "Crop error: $error")
                }
            }
        }

    val wallpaperPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                val wallpaperDir: ExtendedFile =
                    FileSystemManager.getLocal().getFile(apApp.filesDir.parent, "wallpaper")
                if (!wallpaperDir.exists()) {
                    wallpaperDir.mkdirs()
                }
                val screenWidth = configuration.screenWidthDp
                val screenHeight = configuration.screenHeightDp
                val destFile = wallpaperDir.getChildFile("wallpaper.jpg")

                val options = UCrop.Options()
                options.setToolbarColor(colorScheme.surface.toArgb())
                options.setToolbarWidgetColor(colorScheme.onSurface.toArgb())
                options.setActiveControlsWidgetColor(colorScheme.primary.toArgb())
                options.setRootViewBackgroundColor(colorScheme.surface.toArgb())
                options.setLogoColor(colorScheme.onSurface.toArgb())

                UCrop.of(uri, Uri.fromFile(destFile)).withOptions(options)
                    .withAspectRatio(screenWidth.toFloat(), screenHeight.toFloat())
                    .withMaxResultSize(screenWidth, screenHeight).start(context, ucropLauncher)
            }
        }

    var showWallpaperEdit by remember { mutableStateOf(false) }
    var showWidgetOpacityEdit by remember { mutableStateOf(false) }
    var showDropdownReboot by remember { mutableStateOf(false) }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.app_name))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.tertiary
                ) {
                    Text(
                        text = "BETTER",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp
                        ),
                        modifier = Modifier.padding(
                            horizontal = 4.dp,
                            vertical = 0.5.dp
                        ),
                        color = MaterialTheme.colorScheme.onTertiary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = { showWallpaperEdit = !showWallpaperEdit }) {
                Icon(
                    imageVector = Tabler.Outline.Edit,
                    contentDescription = null
                )
            }
            AnimatedVisibility(visible = showWallpaperEdit) {
                Row {
                    IconButton(
                        onClick = {
                            val request =
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            wallpaperPicker.launch(request)
                        }
                    ) {
                        Icon(
                            imageVector = Tabler.Outline.PhotoCog,
                            contentDescription = null
                        )
                    }

                    AnimatedVisibility(visible = wallpaper.value != null) {
                        Row {
                            IconButton(
                                onClick = {
                                    val wallpaperDir: ExtendedFile =
                                        FileSystemManager.getLocal()
                                            .getFile(apApp.filesDir.parent, "wallpaper")
                                    if (wallpaperDir.exists()) {
                                        wallpaperDir.deleteRecursively()
                                    }
                                    wallpaper.value = null
                                }
                            ) {
                                Icon(
                                    imageVector = Tabler.Outline.PhotoX,
                                    contentDescription = null
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            showWidgetOpacityEdit = !showWidgetOpacityEdit
                        }
                    ) {
                        Icon(
                            imageVector = Tabler.Outline.Blur,
                            contentDescription = null
                        )
                    }

                    ProvideMenuShape(MaterialTheme.shapes.medium) {
                        DropdownMenu(
                            expanded = showWidgetOpacityEdit,
                            onDismissRequest = { showWidgetOpacityEdit = false }
                        ) {
                            LiquidSlider(
                                modifier = Modifier
                                    .padding(horizontal = 32.dp)
                                    .width(240.dp)
                                    .height(38.dp),
                                backdrop = emptyBackdrop(),
                                value = {
                                    widgetOpacity.value
                                },
                                onValueChange = {
                                    widgetOpacity.value = it
                                    prefs.edit {
                                        putFloat("widget_opacity", it)
                                    }
                                },
                                valueRange = 0.1f..1f,
                                visibilityThreshold = 0.01f
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(visible = !showWallpaperEdit) {
                Row {
                    IconButton(onClick = onInstallClick) {
                        Icon(
                            imageVector = Tabler.Outline.Wand,
                            contentDescription =
                                stringResource(id = R.string.mode_select_page_title)
                        )
                    }

                    if (kpState != APApplication.State.UNKNOWN_STATE) {
                        IconButton(onClick = { showDropdownReboot = true }) {
                            Icon(
                                imageVector = Tabler.Outline.Reload,
                                contentDescription = stringResource(id = R.string.reboot)
                            )

                            ProvideMenuShape(MaterialTheme.shapes.medium) {
                                DropdownMenu(
                                    expanded = showDropdownReboot,
                                    onDismissRequest = { showDropdownReboot = false }
                                ) {
                                    RebootDropdownItem(id = R.string.reboot)
                                    RebootDropdownItem(
                                        id = R.string.reboot_recovery,
                                        reason = "recovery"
                                    )
                                    RebootDropdownItem(
                                        id = R.string.reboot_bootloader,
                                        reason = "bootloader"
                                    )
                                    RebootDropdownItem(
                                        id = R.string.reboot_download,
                                        reason = "download"
                                    )
                                    RebootDropdownItem(id = R.string.reboot_edl, reason = "edl")
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun KStatusCard(
    kpState: APApplication.State,
    apState: APApplication.State,
    navigator: DestinationsNavigator
) {
    val showAuthFailedTipDialog = remember { mutableStateOf(false) }
    val colorScheme by rememberUpdatedState(MaterialTheme.colorScheme)
    if (showAuthFailedTipDialog.value) {
        AuthFailedTipDialog(showDialog = showAuthFailedTipDialog)
    }

    val showAuthKeyDialog = remember { mutableStateOf(false) }
    if (showAuthKeyDialog.value) {
        AuthSuperKey(showDialog = showAuthKeyDialog, showFailedDialog = showAuthFailedTipDialog)
    }

    val showResetSuPathDialog = remember { mutableStateOf(false) }
    if (showResetSuPathDialog.value) {
        ResetSUPathDialog(showResetSuPathDialog)
    }

    val wallpaperBackdrop = LocalWallpaperBackdrop.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(intrinsicSize = IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LiquidSurface(
                backdrop = wallpaperBackdrop,
                isInteractive = false,
                onClick = {
                    if (kpState != APApplication.State.KERNELPATCH_INSTALLED) {
                        navigator.navigate(InstallModeSelectScreenDestination)
                    }
                },
                modifier = Modifier
                    .aspectRatio(1f, true)
                    .widthIn(min = 0.dp)
                    .fillMaxHeight(),
                tint = colorScheme.tertiaryContainer,
                shape = ContinuousRoundedRectangle(16.dp)
            ) {
                Box(
                    Modifier.fillMaxSize()
                ) {
                    Box(
                        contentAlignment = Alignment.BottomEnd,
                        modifier = Modifier
                            .padding(8.dp)
                            .matchParentSize()
                    ) {
                        when (kpState) {
                            APApplication.State.KERNELPATCH_INSTALLED -> {
                                Icon(
                                    Tabler.Outline.Checks,
                                    modifier = Modifier
                                        .size(56.dp),
                                    tint = colorScheme.outline,
                                    contentDescription = stringResource(R.string.home_working)
                                )
                            }

                            APApplication.State.KERNELPATCH_NEED_UPDATE,
                            APApplication.State.KERNELPATCH_NEED_REBOOT -> {
                                Icon(
                                    Tabler.Outline.Check,
                                    modifier = Modifier
                                        .size(56.dp),
                                    tint = colorScheme.outline,
                                    contentDescription =
                                        stringResource(R.string.home_need_update)
                                )
                            }

                            else -> {
                                Icon(
                                    Tabler.Outline.DeviceUnknown,
                                    modifier = Modifier
                                        .size(56.dp),
                                    tint = colorScheme.outline,
                                    contentDescription = "Unknown"
                                )
                            }
                        }
                    }
                    Column(
                        Modifier
                            .padding(16.dp)
                            .matchParentSize()
                    ) {
                        if (kpState != APApplication.State.UNKNOWN_STATE) {
                            Text(
                                text = "${Version.installedKPVString()} " +
                                        if (apState !=
                                            APApplication.State
                                                .ANDROIDPATCH_NOT_INSTALLED
                                        )
                                            "[FULL]"
                                        else "[HALF]",
                                modifier = Modifier
                                    .fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text =
                                when (kpState) {
                                    APApplication.State.KERNELPATCH_INSTALLED -> {
                                        stringResource(R.string.home_working)
                                    }

                                    APApplication.State.KERNELPATCH_NEED_UPDATE,
                                    APApplication.State.KERNELPATCH_NEED_REBOOT -> {
                                        stringResource(R.string.home_need_update)
                                    }

                                    else -> {
                                        stringResource(R.string.home_install_unknown)
                                    }
                                },
                            modifier = Modifier
                                .fillMaxWidth(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (kpState == APApplication.State.UNKNOWN_STATE || kpState == APApplication.State.KERNELPATCH_NEED_UPDATE ||
                            kpState == APApplication.State.KERNELPATCH_NEED_REBOOT
                        ) {
                            Text(
                                text = if ((kpState == APApplication.State.KERNELPATCH_NEED_UPDATE ||
                                            kpState == APApplication.State.KERNELPATCH_NEED_REBOOT)
                                ) stringResource(
                                    R.string.kpatch_version_update,
                                    Version.installedKPVString(),
                                    Version.buildKPVString()
                                ) else stringResource(
                                    R.string.home_install_unknown_summary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .basicMarquee(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Column(
                Modifier
                    .height(intrinsicSize = IntrinsicSize.Min),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val suPatchUnknown = kpState == APApplication.State.UNKNOWN_STATE
                LiquidSurface(
                    backdrop = wallpaperBackdrop,
                    tint = colorScheme.tertiaryContainer,
                    shape = ContinuousRoundedRectangle(16.dp),
                    isInteractive = false,
                    onClick = {
                        if (!suPatchUnknown) {
                            showResetSuPathDialog.value = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(intrinsicSize = IntrinsicSize.Min)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = if (suPatchUnknown) "Unknown" else Natives.suPath(),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.basicMarquee(),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(),
                            text = stringResource(R.string.home_su_path),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.End
                        )
                    }
                }

                val managerUnknown =
                    apState == APApplication.State.UNKNOWN_STATE || apState == APApplication.State.ANDROIDPATCH_NOT_INSTALLED
                LiquidSurface(
                    backdrop = wallpaperBackdrop,
                    tint = colorScheme.tertiaryContainer,
                    shape = ContinuousRoundedRectangle(16.dp),
                    isInteractive = false,
                    onClick = {
                        if (managerUnknown) {
                            showAuthKeyDialog.value = true
                        } else {
                            navigator.navigate(AboutScreenDestination)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(intrinsicSize = IntrinsicSize.Min)
                ) {
                    Column(
                        Modifier
                            .padding(16.dp)
                    ) {
                        Text(
                            text = if (managerUnknown) stringResource(R.string.home_install_unknown_summary) else managerVersion.second.toString() + " (" + managerVersion.first + ")",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.basicMarquee(),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(),
                            text = stringResource(R.string.home_apatch_version),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AStatusCard(apState: APApplication.State) {
    val wallpaperBackdrop = LocalWallpaperBackdrop.current
    LiquidSurface(
        backdrop = wallpaperBackdrop,
        tint = MaterialTheme.colorScheme.secondaryContainer,
        shape = ContinuousRoundedRectangle(16.dp),
        isInteractive = false,
        onClick = {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row {
                Text(
                    text = stringResource(R.string.android_patch),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (apState) {
                    APApplication.State.ANDROIDPATCH_NOT_INSTALLED -> {
                        Icon(Tabler.Outline.Forbid2, stringResource(R.string.home_not_installed))
                    }

                    APApplication.State.ANDROIDPATCH_INSTALLING -> {
                        Icon(
                            Tabler.Outline.ArrowAutofitDown,
                            stringResource(R.string.home_installing)
                        )
                    }

                    APApplication.State.ANDROIDPATCH_NEED_UPDATE -> {
                        Icon(
                            Tabler.Outline.ArrowAutofitDown,
                            stringResource(R.string.home_need_update)
                        )
                    }

                    else -> {
                        Icon(
                            Tabler.Outline.HelpCircle,
                            stringResource(R.string.home_install_unknown)
                        )
                    }
                }
                Column(
                    Modifier
                        .weight(2f)
                        .padding(start = 16.dp)
                ) {
                    when (apState) {
                        APApplication.State.ANDROIDPATCH_NOT_INSTALLED -> {
                            Text(
                                text = stringResource(R.string.home_not_installed),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        APApplication.State.ANDROIDPATCH_INSTALLING -> {
                            Text(
                                text = stringResource(R.string.home_installing),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        APApplication.State.ANDROIDPATCH_NEED_UPDATE -> {
                            Text(
                                text = stringResource(R.string.home_need_update),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text =
                                    stringResource(
                                        R.string.apatch_version_update,
                                        Version.installedApdVString,
                                        managerVersion.second
                                    ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        else -> {
                            Text(
                                text = stringResource(R.string.home_install_unknown),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
                if (apState != APApplication.State.UNKNOWN_STATE &&
                    (apState == APApplication.State.ANDROIDPATCH_NOT_INSTALLED ||
                            apState == APApplication.State.ANDROIDPATCH_NEED_UPDATE)
                ) {
                    Column(modifier = Modifier.align(Alignment.CenterVertically)) {
                        LiquidButton(
                            backdrop = it,
                            shape = ContinuousCapsule,
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = {
                                when (apState) {
                                    APApplication.State.ANDROIDPATCH_NOT_INSTALLED,
                                    APApplication.State.ANDROIDPATCH_NEED_UPDATE -> {
                                        APApplication.installApatch()
                                    }

                                    else -> {}
                                }
                            },
                            content = {
                                when (apState) {
                                    APApplication.State.ANDROIDPATCH_NOT_INSTALLED -> {
                                        Text(
                                            text =
                                                stringResource(
                                                    id =
                                                        R.string
                                                            .home_ap_cando_install
                                                )
                                        )
                                    }

                                    APApplication.State.ANDROIDPATCH_NEED_UPDATE -> {
                                        Text(
                                            text =
                                                stringResource(
                                                    id =
                                                        R.string
                                                            .home_ap_cando_update
                                                )
                                        )
                                    }

                                    APApplication.State.ANDROIDPATCH_UNINSTALLING -> {
                                        Icon(Tabler.Outline.Refresh, contentDescription = "busy")
                                    }

                                    else -> {}
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))
}

@Suppress("AssignedValueIsNeverRead")
@Composable
fun WarningCard() {
    var show by rememberSaveable { mutableStateOf(apApp.getBackupWarningState()) }
    if (show) {
        val wallpaperBackdrop = LocalWallpaperBackdrop.current
        LiquidSurface(
            backdrop = wallpaperBackdrop,
            tint = MaterialTheme.colorScheme.error,
            shape = ContinuousRoundedRectangle(16.dp),
            tonalElevation = 6.dp,
            isInteractive = false,
            onClick = {}
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .height(intrinsicSize = IntrinsicSize.Min)
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { Icon(Tabler.Filled.AlertTriangle, contentDescription = "warning") }
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterHorizontally),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = stringResource(id = R.string.patch_warnning)
                        )

                        Spacer(Modifier.width(12.dp))

                        Icon(
                            Tabler.Outline.CircleDashedX,
                            contentDescription = "",
                            modifier =
                                Modifier.clickable {
                                    show = false
                                    apApp.updateBackupWarningState(false)
                                }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

private fun getSystemVersion(): String {
    return "${Build.VERSION.RELEASE} ${if (Build.VERSION.PREVIEW_SDK_INT != 0) "Preview" else ""} (API ${Build.VERSION.SDK_INT})"
}

private fun getDeviceInfo(): String {
    var manufacturer =
        Build.MANUFACTURER[0].uppercaseChar().toString() + Build.MANUFACTURER.substring(1)
    if (!Build.BRAND.equals(Build.MANUFACTURER, ignoreCase = true)) {
        manufacturer += " " + Build.BRAND[0].uppercaseChar() + Build.BRAND.substring(1)
    }
    manufacturer += " " + Build.MODEL + " "
    return manufacturer
}

@Composable
private fun InfoCard() {
    val wallpaperBackdrop = LocalWallpaperBackdrop.current
    Spacer(Modifier.height(4.dp))
    LiquidSurface(
        backdrop = wallpaperBackdrop,
        shape = ContinuousRoundedRectangle(16.dp),
        isInteractive = false,
        onClick = {},
        tint = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
        ) {
            val contents = StringBuilder()
            val uname = Os.uname()

            @Composable
            fun InfoCardItem(label: String, content: String) {
                contents.appendLine(label).appendLine(content).appendLine()
                Text(
                    text = label, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            InfoCardItem(stringResource(R.string.home_device_info), getDeviceInfo())

            Spacer(Modifier.height(16.dp))
            InfoCardItem(stringResource(R.string.home_kernel), uname.release)

            Spacer(Modifier.height(16.dp))
            InfoCardItem(stringResource(R.string.home_system_version), getSystemVersion())

            Spacer(Modifier.height(16.dp))
            InfoCardItem(stringResource(R.string.home_fingerprint), Build.FINGERPRINT)

            Spacer(Modifier.height(16.dp))
            InfoCardItem(stringResource(R.string.home_selinux_status), getSELinuxStatus())
        }
    }
}

@Composable
fun UpdateCard() {
    val latestVersionInfo = LatestVersionInfo()
    val newVersion by
    produceState(initialValue = latestVersionInfo) {
        value = withContext(Dispatchers.IO) { checkNewVersion() }
    }
    val currentVersionCode = managerVersion.second
    val newVersionCode = newVersion.versionCode
    val newVersionUrl = newVersion.downloadUrl
    val changelog = newVersion.changelog

    val uriHandler = LocalUriHandler.current
    val title = stringResource(id = R.string.apm_changelog)
    val updateText = stringResource(id = R.string.apm_update)

    AnimatedVisibility(
        visible = newVersionCode > currentVersionCode,
        enter = fadeIn() + expandVertically(),
        exit = shrinkVertically() + fadeOut()
    ) {
        val updateDialog = rememberConfirmDialog(onConfirm = { uriHandler.openUri(newVersionUrl) })
        Column {
            WarningCard(
                message =
                    stringResource(id = R.string.home_new_apatch_found).format(newVersionCode),
                MaterialTheme.colorScheme.outlineVariant
            ) {
                if (changelog.isEmpty()) {
                    uriHandler.openUri(newVersionUrl)
                } else {
                    updateDialog.showConfirm(
                        title = title,
                        content = changelog,
                        markdown = true,
                        confirm = updateText
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
