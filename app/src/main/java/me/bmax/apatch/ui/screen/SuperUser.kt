package me.bmax.apatch.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults.Large
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.filled.ClipboardList
import com.composables.icons.tabler.outline.ClipboardList
import com.composables.icons.tabler.outline.DotsVertical
import com.composables.icons.tabler.outline.UserX
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import me.bmax.apatch.R
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.component.ProvideMenuShape
import me.bmax.apatch.ui.component.SearchAppBar
import me.bmax.apatch.ui.viewmodel.SuperUserViewModel
import me.bmax.apatch.util.PkgConfig
import me.bmax.apatch.util.isWhiteListEnabled
import me.bmax.apatch.util.reboot
import me.bmax.apatch.util.setWhiteListMode
import me.bmax.apatch.util.ui.LocalSnackbarHost

@Suppress("AssignedValueIsNeverRead")
@OptIn(ExperimentalMaterialApi::class, ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun SuperUserScreen() {
    val viewModel = viewModel<SuperUserViewModel>()
    val scope = rememberCoroutineScope()
    val snackBarHost = LocalSnackbarHost.current
    val reboot = stringResource(id = R.string.reboot)
    val rebootToApply = stringResource(id = R.string.apm_reboot_to_apply)
    var isWhiteListEnabled by rememberSaveable { mutableStateOf(isWhiteListEnabled()) }

    LaunchedEffect(Unit) {
        if (viewModel.appList.isEmpty()) {
            viewModel.fetchAppList()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackBarHost) },
        topBar = {
            SearchAppBar(
                title = { Text(stringResource(R.string.su_title)) },
                searchText = viewModel.search,
                onSearchTextChange = { viewModel.search = it },
                onClearClick = { viewModel.search = "" },
                dropdownContent = {
                    var showDropdown by remember { mutableStateOf(false) }

                    IconButton(
                        onClick = { showDropdown = true },
                    ) {
                        Icon(
                            imageVector = Tabler.Outline.DotsVertical,
                            contentDescription = stringResource(id = R.string.settings)
                        )

                        ProvideMenuShape(MaterialTheme.shapes.medium) {
                            DropdownMenu(expanded = showDropdown, onDismissRequest = {
                                showDropdown = false
                            }) {
                                DropdownMenuItem(text = {
                                    Text(stringResource(R.string.su_refresh))
                                }, onClick = {
                                    scope.launch {
                                        viewModel.fetchAppList()
                                    }
                                    showDropdown = false
                                })

                                DropdownMenuItem(text = {
                                    Text(
                                        if (viewModel.showSystemApps) {
                                            stringResource(R.string.su_hide_system_apps)
                                        } else {
                                            stringResource(R.string.su_show_system_apps)
                                        }
                                    )
                                }, onClick = {
                                    viewModel.showSystemApps = !viewModel.showSystemApps
                                    showDropdown = false
                                })
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            val state by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
            val kPatchReady = state != APApplication.State.UNKNOWN_STATE
            val aPatchReady =
                (state == APApplication.State.ANDROIDPATCH_INSTALLING || state == APApplication.State.ANDROIDPATCH_INSTALLED || state == APApplication.State.ANDROIDPATCH_NEED_UPDATE)
            if (kPatchReady && aPatchReady) {
                FloatingActionButton(
                    modifier = Modifier.clip(Large),
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        setWhiteListMode(!isWhiteListEnabled)
                        isWhiteListEnabled = !isWhiteListEnabled
                        scope.launch {
                            val result = snackBarHost.showSnackbar(
                                message = rebootToApply,
                                actionLabel = reboot,
                                duration = SnackbarDuration.Long
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                reboot()
                            }
                        }
                    }) {
                    Icon(
                        imageVector = if (isWhiteListEnabled) Tabler.Filled.ClipboardList else Tabler.Outline.ClipboardList,
                        contentDescription = null
                    )
                }
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
            onRefresh = { scope.launch { viewModel.fetchAppList() } },
            isRefreshing = viewModel.isRefreshing
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(Large),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = remember { PaddingValues(bottom = 16.dp + 56.dp) }
            ) {
                items(
                    viewModel.appList.filter { it.packageName != apApp.packageName },
                    key = { it.packageName + it.uid }) { app ->
                    AppItem(app)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppItem(
    app: SuperUserViewModel.AppInfo,
) {
    val appInfo = app.packageInfo

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (app.rootGranted) 4.dp else 1.dp,
        shape = Large
    ) {
        Column(
            modifier = Modifier.clickable(onClick = {
                if (!app.rootGranted) {
                    app.showEditProfile = !app.showEditProfile
                }
            })
        ) {
            ListItem(
                headlineContent = { Text(app.label) },
                leadingContent = {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(appInfo)
                            .memoryCacheKey(app.packageName)
                            .crossfade(true)
                            .fetcherDispatcher(Dispatchers.IO)
                            .decoderDispatcher(Dispatchers.IO)
                            .interceptorDispatcher(Dispatchers.IO)
                            .fetcherDispatcher(Dispatchers.IO)
                            .build(),
                        contentDescription = app.label,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(48.dp)
                    )
                },
                supportingContent = {
                    Column {
                        Text(app.packageName)

                        if (app.excludeApp == 1) {
                            LabelText(label = stringResource(id = R.string.su_pkg_excluded_label))
                        }
                        if (app.rootGranted) {
                            FlowRow {
                                LabelText(label = app.config.profile.uid.toString())
                                LabelText(label = app.config.profile.toUid.toString())
                                LabelText(
                                    label = when {
                                        // todo: valid scontext ?
                                        app.config.profile.scontext.isNotEmpty() -> app.config.profile.scontext
                                        else -> stringResource(id = R.string.su_selinux_via_hook)
                                    }
                                )
                            }
                        }
                    }
                },
                trailingContent = {
                    Checkbox(checked = app.rootGranted, onCheckedChange = {
                        app.rootGranted = !app.rootGranted
                        if (app.rootGranted) {
                            app.excludeApp = 0
                            app.config.allow = 1
                            app.config.exclude = 0
                            app.config.profile.scontext = APApplication.MAGISK_SCONTEXT
                        } else {
                            app.config.allow = 0
                        }
                        app.config.profile.uid = app.uid
                        PkgConfig.changeConfig(app.config)
                        if (app.config.allow == 1) {
                            Natives.grantSu(app.uid, 0, app.config.profile.scontext)
                            Natives.setUidExclude(app.uid, 0)
                        } else {
                            Natives.revokeSu(app.uid)
                            if (isWhiteListEnabled()) {
                                Natives.setUidExclude(app.uid, 1)
                            }
                        }
                    })
                },
            )

            AnimatedVisibility(
                visible = app.showEditProfile && !app.rootGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(id = R.string.su_pkg_excluded_setting_title)) },
                    leadingContent = {
                        Icon(
                            Tabler.Outline.UserX,
                            contentDescription = stringResource(id = R.string.su_pkg_excluded_setting_title)
                        )
                    },
                    supportingContent = { Text(stringResource(id = R.string.su_pkg_excluded_setting_summary)) },
                    trailingContent = {
                        Switch(
                            checked = app.excludeApp == 1,
                            onCheckedChange = {
                                if (it) {
                                    app.excludeApp = 1
                                    app.config.allow = 0
                                    app.config.profile.scontext =
                                        APApplication.DEFAULT_SCONTEXT
                                    Natives.revokeSu(app.uid)
                                } else {
                                    app.excludeApp = 0
                                }
                                app.config.exclude = app.excludeApp
                                app.config.profile.uid = app.uid
                                PkgConfig.changeConfig(app.config)
                                Natives.setUidExclude(app.uid, app.excludeApp)
                            },
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun LabelText(label: String) {
    Text(
        text = label,
        modifier = Modifier.padding(vertical = 2.dp, horizontal = 5.dp),
        style = TextStyle(
            fontSize = 8.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    )
}