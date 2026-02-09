package me.bmax.apatch.ui.screen

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.PackageImport
import com.ramcosta.composedestinations.generated.destinations.InstallScreenDestination
import com.ramcosta.composedestinations.generated.destinations.PatchesDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
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
import me.bmax.apatch.ui.component.KPModuleRemoveButton
import me.bmax.apatch.ui.component.ListItemData
import me.bmax.apatch.ui.component.LoadingDialogHandle
import me.bmax.apatch.ui.component.ModuleSettingsButton
import me.bmax.apatch.ui.component.SearchAppBar
import me.bmax.apatch.ui.component.UIList
import me.bmax.apatch.ui.component.pinnedScrollBehavior
import me.bmax.apatch.ui.component.rememberConfirmDialog
import me.bmax.apatch.ui.component.rememberLoadingDialog
import me.bmax.apatch.ui.viewmodel.KPModel
import me.bmax.apatch.ui.viewmodel.KPModuleViewModel
import me.bmax.apatch.ui.viewmodel.PatchesViewModel
import me.bmax.apatch.util.Su
import me.bmax.apatch.util.inputStream
import me.bmax.apatch.util.writeTo
import java.io.IOException

private const val TAG = "KernelPatchModule"
private lateinit var targetKPMToControl: KPModel.KPMInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KPModuleScreen(
    navigator: DestinationsNavigator,
    setFab: FabProvider
) {
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
    val scrollBehavior = pinnedScrollBehavior()
    val kpModuleListState = rememberLazyListState()
    val moduleAuthor = stringResource(id = R.string.kpm_author)
    val scope = rememberCoroutineScope()
    val moduleStr = stringResource(id = R.string.kpm)
    val moduleUninstallConfirm = stringResource(id = R.string.kpm_unload_confirm)
    val uninstall = stringResource(id = R.string.kpm_unload)
    val cancel = stringResource(id = android.R.string.cancel)

    val confirmDialog = rememberConfirmDialog()
    val loadingDialog = rememberLoadingDialog()

    val showKPMControlDialog = remember { mutableStateOf(false) }
    KPMControlDialog(showDialog = showKPMControlDialog)

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

    val moduleLoad = stringResource(id = R.string.kpm_load)
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

    val selectKpmLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode != RESULT_OK) {
            return@rememberLauncherForActivityResult
        }
        val data = it.data ?: return@rememberLauncherForActivityResult
        val uri = data.data ?: return@rememberLauncherForActivityResult

        // todo: args
        scope.launch {
            val rc = loadModule(loadingDialog, uri, "")
            val toastText = if (rc == 0) successToastText else "$failToastText: $rc"
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context, toastText, Toast.LENGTH_SHORT
                ).show()
            }
            viewModel.markNeedRefresh()
            viewModel.fetchModuleList()
        }
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
            val rc = installModule(loadingDialog, uri, "") == 0
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
            val options = listOf(moduleEmbed, moduleInstall, moduleLoad)
            Fab(
                icon = Tabler.Outline.PackageImport,
                menuItems = (
                        options.map { label ->
                            MenuItem(title = label, onClick = {
                                when (label) {
                                    moduleEmbed -> {
                                        navigator.navigate(PatchesDestination(PatchesViewModel.PatchMode.PATCH_AND_INSTALL))
                                    }

                                    moduleInstall -> {
                                        //                                        val intent = Intent(Intent.ACTION_GET_CONTENT)
                                        //                                        intent.type = "application/zip"
                                        //                                        selectZipLauncher.launch(intent)
                                        val intent = Intent(Intent.ACTION_GET_CONTENT)
                                        intent.type = "*/*"
                                        selectKpmInstallLauncher.launch(intent)
                                    }

                                    moduleLoad -> {
                                        val intent = Intent(Intent.ACTION_GET_CONTENT)
                                        intent.type = "*/*"
                                        selectKpmLauncher.launch(intent)
                                    }
                                }
                            })
                        }
                        )
            )
        })
    }

    Scaffold(topBar = {
        SearchAppBar(
            searchText = viewModel.search,
            onSearchTextChange = { viewModel.search = it },
            scrollBehavior = scrollBehavior,
            searchBarPlaceHolderText = stringResource(R.string.search_modules)
        )
    }) { innerPadding ->
        val uiListData =
            remember(viewModel.moduleList, viewModel.installedModuleList, viewModel.search) {
                val allModules =
                    viewModel.installedModuleList + viewModel.moduleList.filter { !it.isInstalled }
                allModules.map { module ->
                    ListItemData(
                        title = {
                            Text(
                                text = module.name,
                                maxLines = 2,
                                fontWeight = FontWeight.SemiBold,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        subtitle = "${module.version}, $moduleAuthor ${module.author}",
                        description = module.description,
                        showCheckBox = module.isInstalled,
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
                        checked = module.isInstalled && module in viewModel.moduleList,
                        actions = {
                            Row(
                                modifier = Modifier
                                    .padding(bottom = 12.dp)
                                    .padding(horizontal = 16.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(modifier = Modifier.weight(1f))

                                ModuleSettingsButton(onClick = {
                                    targetKPMToControl = module
                                    showKPMControlDialog.value = true
                                })

                                Spacer(modifier = Modifier.width(12.dp))

                                KPModuleRemoveButton(enabled = true, onClick = {
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
                        }
                    )
                }
            }
        UIList(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
                .padding(bottom = 8.dp),
            onRefresh = { viewModel.fetchModuleList() },
            isRefreshing = viewModel.isRefreshing,
            items = uiListData,
            scrollBehavior = scrollBehavior,
            state = kpModuleListState
        )
    }
}

suspend fun loadModule(loadingDialog: LoadingDialogHandle, uri: Uri, args: String): Int {
    val rc = loadingDialog.withLoading {
        withContext(Dispatchers.IO) {
            run {
                val kpmDir: ExtendedFile =
                    FileSystemManager.getLocal().getFile(apApp.filesDir.parent, "kpm")
                kpmDir.deleteRecursively()
                kpmDir.mkdirs()
                val rand = (1..4).map { ('a'..'z').random() }.joinToString("")
                val kpm = kpmDir.getChildFile("${rand}.kpm")
                Log.d(TAG, "save tmp kpm: ${kpm.path}")
                var rc = -1
                try {
                    uri.inputStream().buffered().writeTo(kpm)
                    rc = Natives.loadKernelPatchModule(kpm.path, args).toInt()
                } catch (e: IOException) {
                    Log.e(TAG, "Copy kpm error: $e")
                }
                Log.d(TAG, "load ${kpm.path} rc: $rc")
                rc
            }
        }
    }
    return rc
}

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
suspend fun installModule(loadingDialog: LoadingDialogHandle, uri: Uri, args: String): Int {
    return loadingDialog.withLoading {
        withContext(Dispatchers.IO) {
            run {
                val kpmDir: ExtendedFile =
                    FileSystemManager.getLocal().getFile(apApp.filesDir.parent, "kpm")
                kpmDir.deleteRecursively()
                kpmDir.mkdirs()
                val rand = (1..4).map { ('a'..'z').random() }.joinToString("")
                val kpm = kpmDir.getChildFile("${rand}.kpm")
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
fun KPMControlDialog(showDialog: MutableState<Boolean>) {
    var controlParam by remember { mutableStateOf("") }
    var enable by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val loadingDialog = rememberLoadingDialog()
    val outMsgStringRes = stringResource(id = R.string.kpm_control_outMsg)
    val okStringRes = stringResource(id = R.string.kpm_control_ok)
    val failedStringRes = stringResource(id = R.string.kpm_control_failed)

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


    if (showDialog.value) {
        BasicAlertDialog(
            onDismissRequest = { showDialog.value = false }, properties = DialogProperties(
                decorFitsSystemWindows = true,
                usePlatformDefaultWidth = false,
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
                Column(modifier = Modifier.padding(PaddingValues(all = 24.dp))) {
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
                        modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showDialog.value = false }) {
                            Text(stringResource(id = android.R.string.cancel))
                        }

                        Button(onClick = {
                            showDialog.value = false

                            scope.launch { onModuleControl(targetKPMToControl) }

                        }, enabled = enable) {
                            Text(stringResource(id = android.R.string.ok))
                        }
                    }
                }
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
