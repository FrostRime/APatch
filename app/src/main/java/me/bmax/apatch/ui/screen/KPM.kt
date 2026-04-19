package me.bmax.apatch.ui.screen

import android.app.Activity.RESULT_OK
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.PackageImport
import com.kyant.capsule.ContinuousRoundedRectangle
import com.ramcosta.composedestinations.generated.destinations.InstallScreenDestination
import com.ramcosta.composedestinations.generated.destinations.PatchesDestination
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import me.bmax.apatch.R
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.Fab
import me.bmax.apatch.ui.FabProvider
import me.bmax.apatch.ui.MenuItem
import me.bmax.apatch.ui.component.ConfirmResult
import me.bmax.apatch.ui.component.DialogData
import me.bmax.apatch.ui.component.DialogOverlay
import me.bmax.apatch.ui.component.KPModuleRemoveButton
import me.bmax.apatch.ui.component.ListItemData
import me.bmax.apatch.ui.component.LoadingDialogHandle
import me.bmax.apatch.ui.component.ModuleSettingsButton
import me.bmax.apatch.ui.component.ProvideMenuShape
import me.bmax.apatch.ui.component.SearchAppBar
import me.bmax.apatch.ui.component.UIList
import me.bmax.apatch.ui.component.rememberConfirmDialog
import me.bmax.apatch.ui.component.rememberLoadingDialog
import me.bmax.apatch.ui.viewmodel.KPModel
import me.bmax.apatch.ui.viewmodel.KPModuleViewModel
import me.bmax.apatch.ui.viewmodel.PatchesViewModel
import me.bmax.apatch.util.Su
import me.bmax.apatch.util.inputStream
import me.bmax.apatch.util.ui.LocalNavigator
import me.bmax.apatch.util.ui.LocalWallpaperBackdrop
import me.bmax.apatch.util.writeTo
import java.io.IOException

private const val TAG = "KernelPatchModule"
private lateinit var targetKPMToControl: KPModel.KPMInfo
private val installedModuleStages =
    listOf("boot-completed", "service", "post-fs-data", "post-mount")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KPModuleScreen(
    setFab: FabProvider
) {
    val dialog: MutableState<DialogData?> = remember {
        mutableStateOf(
            null
        )
    }

    val state by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
    if (state == APApplication.State.UNKNOWN_STATE) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row {
                Text(
                    text = stringResource(id = R.string.kpm_kp_not_installed),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        return
    }

    val viewModel = viewModel<KPModuleViewModel>()
    val kpModuleListState = rememberLazyListState()
    val moduleAuthor = stringResource(id = R.string.kpm_author)
    val scope = rememberCoroutineScope()
    val moduleStr = stringResource(id = R.string.kpm)
    val moduleUninstallConfirm = stringResource(id = R.string.kpm_unload_confirm)
    val uninstall = stringResource(id = R.string.kpm_unload)
    val cancel = stringResource(id = android.R.string.cancel)
    val navigator = LocalNavigator.current

    val confirmDialog = rememberConfirmDialog()
    val loadingDialog = rememberLoadingDialog()

    val kpmControlDialog = kpmControlDialog(dialog)

    suspend fun onModuleUninstall(module: KPModel.KPMInfo) {
        val confirmResult = confirmDialog.awaitConfirm(
            moduleStr,
            content = moduleUninstallConfirm.format(module.name),
            confirm = uninstall,
            dismiss = cancel
        )
        if (confirmResult != ConfirmResult.Confirmed) {
            return
        }

        val success = loadingDialog.withLoading {
            Su.exec {
                try {
                    Natives.unloadKernelPatchModule(module.name)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }

        if (success) {
            viewModel.fetchModuleList()
        }
    }

    val context = LocalContext.current
    val wallpaperBackdrop = LocalWallpaperBackdrop.current

    val moduleInstall = stringResource(id = R.string.kpm_install)
    val moduleEmbed = stringResource(id = R.string.kpm_embed)
    val successToastText = stringResource(id = R.string.kpm_load_toast_succ)
    val failToastText = stringResource(id = R.string.kpm_load_toast_failed)

    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode != RESULT_OK) {
            return@rememberLauncherForActivityResult
        }
        val data = it.data ?: return@rememberLauncherForActivityResult
        val uri = data.data ?: return@rememberLauncherForActivityResult

        Log.i(TAG, "select zip result: $uri")

        navigator.navigate(InstallScreenDestination(uri, ModuleType.KPM))
    }

    val selectKpmInstallLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode != RESULT_OK) {
            return@rememberLauncherForActivityResult
        }
        val data = it.data ?: return@rememberLauncherForActivityResult
        val uri = data.data ?: return@rememberLauncherForActivityResult

        // todo: args
        scope.launch {
            val rc = installModule(loadingDialog, context, uri, "") == 0
            val toastText = if (rc) successToastText else failToastText
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context, toastText, Toast.LENGTH_SHORT
                ).show()
            }
            viewModel.markNeedRefresh()
            viewModel.fetchModuleList()
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.moduleList.isEmpty() || viewModel.isNeedRefresh) {
            viewModel.fetchModuleList()
        }

        setFab(run {
            val options = listOf(moduleInstall, moduleEmbed)
            Fab(
                icon = Tabler.Outline.PackageImport,
                menuItems = (
                        options.map { label ->
                            MenuItem(title = label, onClick = {
                                when (label) {
                                    moduleInstall -> {
                                        val intent = Intent(Intent.ACTION_GET_CONTENT)
                                        intent.type = "*/*"
                                        selectKpmInstallLauncher.launch(intent)
                                    }

                                    moduleEmbed -> {
                                        navigator.navigate(PatchesDestination(PatchesViewModel.PatchMode.PATCH_AND_INSTALL))
                                    }
                                }
                            })
                        }
                        )
            )
        })
    }

    val allModules by remember {
        derivedStateOf {
            viewModel.installedModuleList + viewModel.moduleList.filter { !it.isInstalled }
        }
    }
    val listItems by remember {
        derivedStateOf {
            allModules.map { module ->
                ListItemData(
                    title = {
                        Row {
                            Text(
                                text = module.name,
                                maxLines = 2,
                                fontWeight = FontWeight.SemiBold,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            if (module.isInstalled) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.tertiary
                                ) {
                                    Text(
                                        text = installedModuleStages[module.stage].uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        modifier = Modifier.padding(
                                            horizontal = 4.dp,
                                            vertical = 1.dp
                                        ),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onTertiary
                                    )
                                }
                            }
                        }
                    },
                    subtitle = { "${module.version}, $moduleAuthor ${module.author}" },
                    description = module.description,
                    showCheckBox = { module.isInstalled },
                    onCheckChange = if (module.isInstalled) {
                        { checked ->
                            scope.launch {
                                val success = Su.exec {
                                    Natives.changeInstalledKpmModuleState(module.name, checked)
                                    if (!checked) {
                                        try {
                                            Natives.unloadKernelPatchModule(module.name)
                                            true
                                        } catch (_: Exception) {
                                            false
                                        }
                                    } else {
                                        true
                                    }
                                }
                                if (success) {
                                    viewModel.fetchModuleList()
                                }
                            }
                        }
                    } else null,
                    checked = { module.isInstalled && module in viewModel.moduleList },
                    actions = {
                        Row(
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            ModuleSettingsButton(
                                backdrop = it,
                                onClick = {
                                    targetKPMToControl = module
                                    dialog.value = kpmControlDialog
                                })

                            Spacer(modifier = Modifier.width(12.dp))

                            KPModuleRemoveButton(
                                backdrop = it, enabled = true, onClick = {
                                    scope.launch {
                                        if (module.isInstalled) {
                                            Su.exec {
                                                Natives.uninstallKpmModule(module.name)
                                            }
                                        }
                                        onModuleUninstall(module)
                                    }
                                })
                        }
                    },
                    key = {
                        module.name
                    }
                )
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            SearchAppBar(
                searchText = viewModel.search,
                onSearchTextChange = { viewModel.search = it },
                searchBarPlaceHolderText = stringResource(R.string.search_modules),
                wallpaperBackdrop = wallpaperBackdrop
            )
        }) { innerPadding ->
        UIList(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
                .padding(top = 8.dp),
            onRefresh = { viewModel.fetchModuleList() },
            isRefreshing = viewModel.isRefreshing,
            items = listItems,
            blurWallpaperBackdrop = wallpaperBackdrop,
            state = kpModuleListState
        )
    }
    DialogOverlay(dialog)
}

private fun getFileName(contextResolver: ContentResolver, uri: Uri): String {
    return contextResolver.query(uri, null, null, null, null)?.use {
        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1) {
            it.moveToFirst()
            it.getString(nameIndex)
        } else null
    } ?: uri.path?.substringAfterLast("/") ?: ((1..4).map { ('a'..'z').random() }
        .joinToString("") + ".kpm")
}

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
suspend fun installModule(
    loadingDialog: LoadingDialogHandle,
    context: Context,
    uri: Uri,
    args: String
): Int {
    return loadingDialog.withLoading {
        withContext(Dispatchers.IO) {
            run {
                val kpmDir: ExtendedFile =
                    FileSystemManager.getLocal().getFile(apApp.filesDir.parent, "kpm")
                kpmDir.deleteRecursively()
                kpmDir.mkdirs()
                val kpm = kpmDir.getChildFile(getFileName(context.contentResolver, uri))
                Log.d(TAG, "save tmp kpm: ${kpm.path}")
                try {
                    uri.inputStream().buffered().writeTo(kpm)
                } catch (e: IOException) {
                    Log.e(TAG, "Copy kpm error: $e")
                    return@withContext -1
                }
                val rc = Su.exec {
                    Natives.installKpmModule(kpm.path, args).toInt()
                }
                Log.d(TAG, "install ${kpm.path} rc: $rc")
                rc
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun kpmControlDialog(dialog: MutableState<DialogData?>): DialogData {
    var controlParam by remember { mutableStateOf("") }
    var enable by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val loadingDialog = rememberLoadingDialog()
    val outMsgStringRes = stringResource(id = R.string.kpm_control_outMsg)
    val okStringRes = stringResource(id = R.string.kpm_control_ok)
    val failedStringRes = stringResource(id = R.string.kpm_control_failed)

    var showStageDropdown by remember { mutableStateOf(false) }
    var bottomSheetText by remember { mutableStateOf(Pair("", "")) }

    lateinit var controlResult: Natives.KPMCtlRes

    suspend fun onModuleControl(module: KPModel.KPMInfo) {
        loadingDialog.withLoading {
            withContext(Dispatchers.IO) {
                controlResult = Natives.kernelPatchModuleControl(module.name, controlParam)
            }
        }

        bottomSheetText = if (controlResult.rc >= 0) {
            Pair(okStringRes, "${outMsgStringRes}: ${controlResult.outMsg}")
        } else {
            Pair(failedStringRes, "${outMsgStringRes}: ${controlResult.outMsg}")
        }
    }


    return DialogData {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                Modifier
                    .padding(PaddingValues(bottom = 16.dp))
                    .align(Alignment.Start)
            ) {
                Text(
                    text = stringResource(id = R.string.kpm_control_dialog_title),
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            Box(
                Modifier
                    .weight(weight = 1f, fill = false)
                    .align(Alignment.Start)
            ) {
                Text(
                    text = stringResource(id = R.string.kpm_control_dialog_content),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Box(
                contentAlignment = Alignment.CenterEnd,
            ) {
                OutlinedTextField(
                    value = controlParam,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    onValueChange = {
                        controlParam = it
                        enable = controlParam.isNotBlank()
                    },
                    shape = MaterialTheme.shapes.large,
                    label = { Text(stringResource(id = R.string.kpm_control_paramters)) },
                    visualTransformation = VisualTransformation.None,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (targetKPMToControl.isInstalled) {
                    Box {
                        TextButton(onClick = {
                            showStageDropdown = true
                        }) {
                            Text(installedModuleStages[targetKPMToControl.stage])
                        }

                        ProvideMenuShape(ContinuousRoundedRectangle(16.dp)) {
                            DropdownMenu(
                                expanded = showStageDropdown,
                                onDismissRequest = { showStageDropdown = false }
                            ) {
                                for (stage in installedModuleStages) {
                                    DropdownMenuItem(
                                        text = { Text(stage) },
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                Su.exec {
                                                    try {
                                                        Natives.changeInstalledKpmModuleStage(
                                                            targetKPMToControl.name,
                                                            installedModuleStages.indexOf(stage)
                                                                .toByte()
                                                        )
                                                    } finally {
                                                        showStageDropdown = false
                                                        if (!enable) {
                                                            dialog.value = null
                                                        }
                                                        targetKPMToControl.stage =
                                                            installedModuleStages.indexOf(stage)
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { dialog.value = null }) {
                    Text(stringResource(id = android.R.string.cancel))
                }

                Button(onClick = {
                    dialog.value = null

                    scope.launch { onModuleControl(targetKPMToControl) }

                }, enabled = enable) {
                    Text(stringResource(id = android.R.string.ok))
                }
            }
        }

        if (bottomSheetText.second.isNotEmpty()) {
            ModalBottomSheet(
                onDismissRequest = {
                    @Suppress("AssignedValueIsNeverRead")
                    bottomSheetText = Pair("", "")
                },
                contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
                content = {
                    Column(Modifier.padding(24.dp)) {
                        Text(bottomSheetText.first, style = MaterialTheme.typography.titleLarge)
                        Text(bottomSheetText.second, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            )
        }
    }
}
