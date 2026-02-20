package me.bmax.apatch.ui.screen

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.filled.PlayerPlay
import com.composables.icons.tabler.outline.PackageImport
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import com.ramcosta.composedestinations.generated.destinations.ExecuteAPMActionScreenDestination
import com.ramcosta.composedestinations.generated.destinations.InstallScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.topjohnwu.superuser.io.SuFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.Fab
import me.bmax.apatch.ui.FabProvider
import me.bmax.apatch.ui.WebUIActivity
import me.bmax.apatch.ui.component.ConfirmResult
import me.bmax.apatch.ui.component.ListItemData
import me.bmax.apatch.ui.component.ModuleRemoveButton
import me.bmax.apatch.ui.component.ModuleSettingsButton
import me.bmax.apatch.ui.component.ModuleUpdateButton
import me.bmax.apatch.ui.component.SearchAppBar
import me.bmax.apatch.ui.component.UIList
import me.bmax.apatch.ui.component.WarningCard
import me.bmax.apatch.ui.component.rememberConfirmDialog
import me.bmax.apatch.ui.component.rememberLoadingDialog
import me.bmax.apatch.ui.viewmodel.APModuleViewModel
import me.bmax.apatch.util.DownloadListener
import me.bmax.apatch.util.download
import me.bmax.apatch.util.hasMagisk
import me.bmax.apatch.util.reboot
import me.bmax.apatch.util.toggleModule
import me.bmax.apatch.util.ui.LocalInnerPadding
import me.bmax.apatch.util.ui.LocalSnackbarHost
import me.bmax.apatch.util.ui.LocalWallpaperBackdrop
import me.bmax.apatch.util.uninstallModule
import okhttp3.Request

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun APModuleScreen(
    navigator: DestinationsNavigator,
    setFab: FabProvider
) {
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current
    val wallpaperBackdrop = LocalWallpaperBackdrop.current

    val state by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
    if (state != APApplication.State.ANDROIDPATCH_INSTALLED && state != APApplication.State.ANDROIDPATCH_NEED_UPDATE) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row {
                Text(
                    text = stringResource(id = R.string.apm_not_installed),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        return
    }

    val viewModel = viewModel<APModuleViewModel>()

    val webUILauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { viewModel.fetchModuleList() }

    //TODO: FIXME -> val isSafeMode = Natives.getSafeMode()
    val isSafeMode = false
    val hasMagisk = hasMagisk()
    val hideInstallButton = isSafeMode || hasMagisk
    val loadingDialog = rememberLoadingDialog()
    val confirmDialog = rememberConfirmDialog()

    val scope = rememberCoroutineScope()

    val failedEnable = stringResource(R.string.apm_failed_to_enable)
    val failedDisable = stringResource(R.string.apm_failed_to_disable)
    val failedUninstall = stringResource(R.string.apm_uninstall_failed)
    val successUninstall = stringResource(R.string.apm_uninstall_success)
    val reboot = stringResource(id = R.string.reboot)
    val rebootToApply = stringResource(id = R.string.apm_reboot_to_apply)
    val moduleStr = stringResource(id = R.string.apm)
    val uninstall = stringResource(id = R.string.apm_remove)
    val cancel = stringResource(id = android.R.string.cancel)
    val moduleUninstallConfirm = stringResource(id = R.string.apm_uninstall_confirm)
    val metaModuleUninstallConfirm = stringResource(R.string.metamodule_uninstall_confirm)
    val updateText = stringResource(R.string.apm_update)
    val changelogText = stringResource(R.string.apm_changelog)
    val downloadingText = stringResource(R.string.apm_downloading)
    val startDownloadingText = stringResource(R.string.apm_start_downloading)
    val onInstallModule: (Uri) -> Unit = { uri ->
        navigator.navigate(InstallScreenDestination(uri, ModuleType.APM))
    }

    val selectZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            navigator.navigate(InstallScreenDestination(uri, ModuleType.APM))
            viewModel.markNeedRefresh()
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.moduleList.isEmpty() || viewModel.isNeedRefresh) {
            viewModel.fetchModuleList()
        }
        setFab(if (hideInstallButton) null else run {
            Fab(
                icon = Tabler.Outline.PackageImport,
                onClick = {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "application/zip"
                    }
                    selectZipLauncher.launch(intent)
                }
            )
        })
    }

    var showSearch by remember { mutableStateOf(false) }
    LaunchedEffect(Unit, showSearch) {
    }

    suspend fun onModuleUpdate(
        module: APModuleViewModel.ModuleInfo,
        changelogUrl: String,
        downloadUrl: String,
        fileName: String
    ) {
        val changelog = loadingDialog.withLoading {
            withContext(Dispatchers.IO) {
                runCatching {
                    if (Patterns.WEB_URL.matcher(changelogUrl).matches()) {
                        apApp.okhttpClient.newCall(
                            Request.Builder().url(changelogUrl).build()
                        ).execute().use { it.body?.string().orEmpty() }
                    } else {
                        changelogUrl
                    }
                }.getOrDefault("")
            }
        }


        if (changelog.isNotEmpty()) {
            // changelog is not empty, show it and wait for confirm
            val confirmResult = confirmDialog.awaitConfirm(
                changelogText,
                content = changelog,
                markdown = true,
                confirm = updateText,
            )

            if (confirmResult != ConfirmResult.Confirmed) {
                return
            }
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(
                context, startDownloadingText.format(module.name), Toast.LENGTH_SHORT
            ).show()
        }

        val downloading = downloadingText.format(module.name)
        withContext(Dispatchers.IO) {
            download(
                context,
                downloadUrl,
                fileName,
                downloading,
                onDownloaded = onInstallModule,
                onDownloading = {
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, downloading, Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }

    suspend fun onModuleUninstall(module: APModuleViewModel.ModuleInfo) {
        val formatter =
            if (module.metamodule) metaModuleUninstallConfirm else moduleUninstallConfirm
        val confirmResult = confirmDialog.awaitConfirm(
            moduleStr,
            content = formatter.format(module.name),
            confirm = uninstall,
            dismiss = cancel
        )
        if (confirmResult != ConfirmResult.Confirmed) {
            return
        }

        val success = loadingDialog.withLoading {
            withContext(Dispatchers.IO) {
                uninstallModule(module.id)
            }
        }

        if (success) {
            viewModel.fetchModuleList()
        }
        val message = if (success) {
            successUninstall.format(module.name)
        } else {
            failedUninstall.format(module.name)
        }
        val actionLabel = if (success) {
            reboot
        } else {
            null
        }
        val result = snackBarHost.showSnackbar(
            message = message, actionLabel = actionLabel, duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) {
            reboot()
        }
    }

    val moduleListState = rememberLazyListState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            SearchAppBar(
                searchText = viewModel.search,
                onSearchTextChange = { viewModel.search = it },
                searchBarPlaceHolderText = stringResource(R.string.search_modules),
                wallpaperBackdrop = wallpaperBackdrop
            )
        },
        snackbarHost = {
            SnackbarHost(
                snackBarHost,
                modifier = Modifier.padding(LocalInnerPadding.current)
            )
        }) { innerPadding ->
        when {
            hasMagisk -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.apm_magisk_conflict),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            else -> {
                val authorString = stringResource(R.string.apm_author)
                val metaWarning by produceState<String?>(null, viewModel.moduleList) {
                    value =
                        withContext(Dispatchers.IO) { getMetaModuleWarningText(viewModel, context) }
                }

                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(horizontal = 12.dp)
                        .padding(top = 8.dp)
                ) {
                    metaWarning?.let {
                        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            MetaModuleWarningCard(it)
                        }
                    }

                    UIList(
                        items = {
                            viewModel.moduleList.map { module ->
                                val updateInfo = module.updateInfo
                                ListItemData(
                                    title = {
                                        ModuleTitleWithMeta(module)
                                    },
                                    showCheckBox = { true },
                                    subtitle = "${module.version}, $authorString ${module.author}",
                                    description = module.description,
                                    onCheckChange = if (!module.remove && !module.update) {
                                        {
                                            scope.launch {
                                                val success = loadingDialog.withLoading {
                                                    withContext(Dispatchers.IO) {
                                                        toggleModule(module.id, it)
                                                    }
                                                }
                                                if (success) {
                                                    viewModel.fetchModuleList()

                                                    val result = snackBarHost.showSnackbar(
                                                        message = rebootToApply,
                                                        actionLabel = reboot,
                                                        duration = SnackbarDuration.Long
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        reboot()
                                                    }
                                                } else {
                                                    val message =
                                                        if (!it) failedDisable else failedEnable
                                                    snackBarHost.showSnackbar(message.format(module.name))
                                                }
                                            }
                                        }
                                    } else null,
                                    checked = { module.enabled },
                                    actions = {
                                        ModuleActionButtons(
                                            backdrop = it,
                                            module = module,
                                            updateUrl = module.updateInfo?.zipUrl ?: "",
                                            navigator = navigator,
                                            onUpdate = {
                                                scope.launch {
                                                    updateInfo?.let { info ->
                                                        onModuleUpdate(
                                                            module,
                                                            info.changelog,
                                                            info.zipUrl,
                                                            "${module.name}-${info.version}.zip"
                                                        )
                                                    }
                                                }
                                            },
                                            onUninstall = {
                                                scope.launch { onModuleUninstall(module) }
                                            },
                                            onSettings = {
                                                val id = module.id
                                                val name = module.name
                                                webUILauncher.launch(
                                                    Intent(
                                                        context, WebUIActivity::class.java
                                                    ).setData("apatch://webui/$id".toUri())
                                                        .putExtra("id", id)
                                                        .putExtra("name", name)
                                                )
                                            },
                                            viewModel = viewModel,
                                        )
                                    }
                                )
                            }
                        },
                        onRefresh = { viewModel.fetchModuleList() },
                        isRefreshing = viewModel.isRefreshing,
                        backdrop = wallpaperBackdrop,
                        state = moduleListState
                    )
                }
            }
        }
    }
    DownloadListener(context, onInstallModule)
}

@OptIn(ExperimentalMaterial3Api::class)
private fun getMetaModuleWarningText(
    viewModel: APModuleViewModel,
    context: Context
): String? {
    val hasSystemModule = viewModel.moduleList.any { module ->
        SuFile.open("/data/adb/modules/${module.id}/system").exists()
    }

    if (!hasSystemModule) return null

    val metaProp = SuFile.open("/data/adb/metamodule/module.prop").exists()
    val metaRemoved = SuFile.open("/data/adb/metamodule/remove").exists()
    val metaDisabled = SuFile.open("/data/adb/metamodule/disable").exists()

    return when {
        !metaProp ->
            context.getString(R.string.no_meta_module_installed)

        metaProp && metaRemoved ->
            context.getString(R.string.meta_module_removed)

        metaProp && metaDisabled ->
            context.getString(R.string.meta_module_disabled)

        else -> null
    }
}

@Composable
private fun ModuleTitleWithMeta(module: APModuleViewModel.ModuleInfo) {
    val decoration =
        if (module.remove) TextDecoration.LineThrough else if (module.update) TextDecoration.Underline else TextDecoration.None
    val fontStyle = if (module.remove || module.update) FontStyle.Italic else FontStyle.Normal

    SubcomposeLayout { constraints ->
        val spacingPx = 6.dp.roundToPx()
        val metaPlaceable = if (module.metamodule) {
            subcompose("meta") {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.tertiary
                ) {
                    Text(
                        text = "META",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiary
                    )
                }
            }.first().measure(Constraints(0, constraints.maxWidth, 0, constraints.maxHeight))
        } else null

        val reserved = (metaPlaceable?.width ?: 0) + if (metaPlaceable != null) spacingPx else 0
        var nameTextLayout: TextLayoutResult? = null
        val namePlaceable = subcompose("name") {
            Text(
                text = module.name,
                maxLines = 2,
                textDecoration = decoration,
                fontStyle = fontStyle,
                fontWeight = FontWeight.SemiBold,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { nameTextLayout = it }
            )
        }.first().measure(Constraints(0, (constraints.maxWidth - reserved).coerceAtLeast(0)))

        val height = maxOf(namePlaceable.height, metaPlaceable?.height ?: 0)
        layout(namePlaceable.width + reserved, height) {
            namePlaceable.placeRelative(0, 0)
            metaPlaceable?.placeRelative(
                (nameTextLayout?.getLineRight(nameTextLayout.lineCount - 1)?.toInt()
                    ?: namePlaceable.width) + spacingPx,
                (height - metaPlaceable.height) / 2
            )
        }
    }
}

@Composable
private fun ModuleActionButtons(
    backdrop: Backdrop,
    module: APModuleViewModel.ModuleInfo,
    updateUrl: String,
    navigator: DestinationsNavigator,
    onUpdate: (APModuleViewModel.ModuleInfo) -> Unit,
    onUninstall: (APModuleViewModel.ModuleInfo) -> Unit,
    onSettings: (APModuleViewModel.ModuleInfo) -> Unit,
    viewModel: APModuleViewModel
) {
    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (module.hasActionScript) {
            val colorScheme by rememberUpdatedState(MaterialTheme.colorScheme)
            FilledTonalButton(
                modifier = Modifier.drawBackdrop(
                backdrop = backdrop,
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                highlight = {
                    Highlight(
                        alpha = 0f
                    )
                },
                shape = { ContinuousCapsule },
                shadow = {
                    Shadow(
                        alpha = 0f
                    )
                },
                onDrawSurface = {
                    drawRect(colorScheme.secondaryContainer, blendMode = BlendMode.Hue)
                    drawRect(colorScheme.secondaryContainer.copy(alpha = 0.75f))
                }
            ),
                onClick = {
                    navigator.navigate(ExecuteAPMActionScreenDestination(module.id))
                    viewModel.markNeedRefresh()
                }, enabled = true, contentPadding = PaddingValues(12.dp)
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    imageVector = Tabler.Filled.PlayerPlay,
                    contentDescription = stringResource(id = R.string.apm_action)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        if (module.hasWebUi) {
            ModuleSettingsButton(backdrop = backdrop, onClick = { onSettings(module) })
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (updateUrl.isNotEmpty()) {
            ModuleUpdateButton(backdrop = backdrop, onClick = { onUpdate(module) })

            Spacer(modifier = Modifier.width(12.dp))
        }
        ModuleRemoveButton(
            backdrop = backdrop,
            enabled = !module.remove,
            onClick = { onUninstall(module) })
    }
}

@Suppress("AssignedValueIsNeverRead")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetaModuleWarningCard(
    text: String
) {
    var show by remember { mutableStateOf(true) }

    AnimatedVisibility(
        visible = show,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        WarningCard(
            message = text,
            onClose = {
                show = false
            }
        )
    }
}