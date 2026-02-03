package me.bmax.apatch.ui.screen

//import androidx.compose.material3.OutlinedTextField
import android.os.Build
import android.system.Os
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults.Large
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.dropUnlessResumed
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.filled.AlertTriangle
import com.composables.icons.tabler.filled.Eye
import com.composables.icons.tabler.outline.ArrowAutofitDown
import com.composables.icons.tabler.outline.Checks
import com.composables.icons.tabler.outline.CircleDashedX
import com.composables.icons.tabler.outline.DeviceUnknown
import com.composables.icons.tabler.outline.EyeOff
import com.composables.icons.tabler.outline.Forbid2
import com.composables.icons.tabler.outline.HelpCircle
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Reload
import com.composables.icons.tabler.outline.Wand
import com.ramcosta.composedestinations.generated.destinations.AboutScreenDestination
import com.ramcosta.composedestinations.generated.destinations.InstallModeSelectScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import me.bmax.apatch.R
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.component.ProvideMenuShape
import me.bmax.apatch.ui.component.WarningCard
import me.bmax.apatch.ui.component.rememberConfirmDialog
import me.bmax.apatch.util.LatestVersionInfo
import me.bmax.apatch.util.Version
import me.bmax.apatch.util.Version.getManagerVersion
import me.bmax.apatch.util.checkNewVersion
import me.bmax.apatch.util.getSELinuxStatus
import me.bmax.apatch.util.reboot
import me.bmax.apatch.util.rootShellForResult

private val managerVersion = getManagerVersion()

@Composable
fun HomeScreen(navigator: DestinationsNavigator) {
    val kpState by APApplication.kpStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
    val apState by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopBar(
                onInstallClick =
                    dropUnlessResumed {
                        navigator.navigate(InstallModeSelectScreenDestination)
                    },
                kpState
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
                    .clip(Large),
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

@Suppress("AssignedValueIsNeverRead")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onInstallClick: () -> Unit,
    kpState: APApplication.State
) {
    var showDropdownReboot by remember { mutableStateOf(false) }

    TopAppBar(
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
    )
}

@Composable
private fun KStatusCard(
    kpState: APApplication.State,
    apState: APApplication.State,
    navigator: DestinationsNavigator
) {
    val showAuthFailedTipDialog = remember { mutableStateOf(false) }
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

    val cardBackgroundColor =
        when (kpState) {
            APApplication.State.KERNELPATCH_INSTALLED -> {
                MaterialTheme.colorScheme.primary
            }

            APApplication.State.KERNELPATCH_NEED_UPDATE,
            APApplication.State.KERNELPATCH_NEED_REBOOT -> {
                MaterialTheme.colorScheme.secondary
            }

            else -> {
                MaterialTheme.colorScheme.secondaryContainer
            }
        }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(intrinsicSize = IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ElevatedCard(
                modifier = Modifier
                    .aspectRatio(1f, true)
                    .fillMaxHeight(),
                shape = Large,
                onClick = {
                    if (kpState != APApplication.State.KERNELPATCH_INSTALLED) {
                        navigator.navigate(InstallModeSelectScreenDestination)
                    }
                },
                colors = CardDefaults.elevatedCardColors(containerColor = cardBackgroundColor),
                elevation =
                    CardDefaults.cardElevation(
                        defaultElevation =
                            if (kpState == APApplication.State.UNKNOWN_STATE) 0.dp else 6.dp
                    )
            ) {
                Box(
                    propagateMinConstraints = true
                ) {
                    when (kpState) {
                        APApplication.State.KERNELPATCH_INSTALLED -> {
                            Icon(
                                Tabler.Outline.Checks,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                tint = LocalContentColor.current.copy(alpha = 0.4f),
                                contentDescription = stringResource(R.string.home_working)
                            )
                        }

                        APApplication.State.KERNELPATCH_NEED_UPDATE,
                        APApplication.State.KERNELPATCH_NEED_REBOOT -> {
                            Icon(
                                Tabler.Outline.ArrowAutofitDown,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                tint = LocalContentColor.current.copy(alpha = 0.4f),
                                contentDescription =
                                    stringResource(R.string.home_need_update)
                            )
                        }

                        else -> {
                            Icon(
                                Tabler.Outline.DeviceUnknown,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                tint = LocalContentColor.current.copy(alpha = 0.4f),
                                contentDescription = "Unknown"
                            )
                        }
                    }

                    Column(
                        Modifier
                            .padding(12.dp)
                            .fillMaxSize()
                    ) {
                        Spacer(Modifier.weight(1f))

                        when (kpState) {
                            APApplication.State.KERNELPATCH_INSTALLED -> {
                                Text(
                                    text = stringResource(R.string.home_working),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            APApplication.State.KERNELPATCH_NEED_UPDATE,
                            APApplication.State.KERNELPATCH_NEED_REBOOT -> {
                                Text(
                                    text = stringResource(R.string.home_need_update),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text =
                                        stringResource(
                                            R.string.kpatch_version_update,
                                            Version.installedKPVString(),
                                            Version.buildKPVString()
                                        ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            else -> {
                                Text(
                                    text = stringResource(R.string.home_install_unknown),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = contentColorFor(cardBackgroundColor)
                                )
                                Text(
                                    text = stringResource(R.string.home_install_unknown_summary),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = contentColorFor(cardBackgroundColor)
                                )
                            }
                        }
                        if (kpState != APApplication.State.UNKNOWN_STATE &&
                            kpState != APApplication.State.KERNELPATCH_NEED_UPDATE &&
                            kpState != APApplication.State.KERNELPATCH_NEED_REBOOT
                        ) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text =
                                    "${Version.installedKPVString()} " +
                                            if (apState !=
                                                APApplication.State
                                                    .ANDROIDPATCH_NOT_INSTALLED
                                            )
                                                "[FULL]"
                                            else "[HALF]",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val suPatchUnknown = kpState == APApplication.State.UNKNOWN_STATE
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = Large,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(
                            enabled = !suPatchUnknown,
                            onClick = {
                                showResetSuPathDialog.value = true
                            })
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.home_su_path),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (suPatchUnknown) "Unknown" else Natives.suPath(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }

                val managerUnknown =
                    apState == APApplication.State.UNKNOWN_STATE || apState == APApplication.State.ANDROIDPATCH_NOT_INSTALLED
                ElevatedCard(
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = Large,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable {
                            if (managerUnknown) {
                                showAuthKeyDialog.value = true
                            } else {
                                navigator.navigate(AboutScreenDestination)
                            }
                        }) {
                    Column(
                        Modifier
                            .padding(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.home_apatch_version),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (managerUnknown) stringResource(R.string.home_install_unknown_summary) else managerVersion.second.toString() + " (" + managerVersion.first + ")",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AStatusCard(apState: APApplication.State) {
    ElevatedCard(
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = run { MaterialTheme.colorScheme.secondaryContainer }
            ),
        shape = Large
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
                    style = MaterialTheme.typography.titleMedium
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
                        Button(
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
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Suppress("AssignedValueIsNeverRead")
@Composable
fun WarningCard() {
    var show by rememberSaveable { mutableStateOf(apApp.getBackupWarningState()) }
    if (show) {
        ElevatedCard(
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors =
                CardDefaults.elevatedCardColors(
                    containerColor = run { MaterialTheme.colorScheme.error }
                ),
            shape = Large
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
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
                            text = stringResource(id = R.string.patch_warnning),
                        )

                        Spacer(Modifier.width(12.dp))

                        Icon(
                            Tabler.Outline.CircleDashedX,
                            contentDescription = "",
                            modifier =
                                Modifier.clickable {
                                    show = false
                                    apApp.updateBackupWarningState(false)
                                },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
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
    ElevatedCard(shape = Large) {
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
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold
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
    }
}
