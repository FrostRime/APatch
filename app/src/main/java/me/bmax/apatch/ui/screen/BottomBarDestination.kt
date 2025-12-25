package me.bmax.apatch.ui.screen

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.filled.Apps
import com.composables.icons.tabler.filled.Bandage
import com.composables.icons.tabler.filled.Home
import com.composables.icons.tabler.filled.Settings
import com.composables.icons.tabler.filled.User
import com.composables.icons.tabler.outline.Apps
import com.composables.icons.tabler.outline.Bandage
import com.composables.icons.tabler.outline.Home
import com.composables.icons.tabler.outline.Settings
import com.composables.icons.tabler.outline.User
import com.ramcosta.composedestinations.generated.destinations.APModuleScreenDestination
import com.ramcosta.composedestinations.generated.destinations.HomeScreenDestination
import com.ramcosta.composedestinations.generated.destinations.KPModuleScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SettingScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SuperUserScreenDestination
import com.ramcosta.composedestinations.spec.DirectionDestinationSpec
import me.bmax.apatch.R

enum class BottomBarDestination(
    val direction: DirectionDestinationSpec,
    @param:StringRes val label: Int,
    val iconSelected: ImageVector,
    val iconNotSelected: ImageVector,
    val kPatchRequired: Boolean,
    val aPatchRequired: Boolean,
) {
    Home(
        HomeScreenDestination,
        R.string.home,
        Tabler.Filled.Home,
        Tabler.Outline.Home,
        false,
        false
    ),
    KModule(
        KPModuleScreenDestination,
        R.string.kpm,
        Tabler.Filled.Bandage,
        Tabler.Outline.Bandage,
        true,
        false
    ),
    SuperUser(
        SuperUserScreenDestination,
        R.string.su_title,
        Tabler.Filled.User,
        Tabler.Outline.User,
        true,
        false
    ),
    AModule(
        APModuleScreenDestination,
        R.string.apm,
        Tabler.Filled.Apps,
        Tabler.Outline.Apps,
        false,
        true
    ),
    Settings(
        SettingScreenDestination,
        R.string.settings,
        Tabler.Filled.Settings,
        Tabler.Outline.Settings,
        false,
        false
    )
}
