package me.bmax.apatch.ui.theme

import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.MutableLiveData
import com.materialkolor.dynamicColorScheme
import me.bmax.apatch.APApplication
import me.bmax.apatch.ui.webui.MonetColorsProvider

@Composable
private fun SystemBarStyle(
    darkMode: Boolean,
    statusBarScrim: Color = Color.Transparent,
    navigationBarScrim: Color = Color.Transparent
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    SideEffect {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                statusBarScrim.toArgb(),
                statusBarScrim.toArgb(),
            ) { darkMode }, navigationBarStyle = when {
                darkMode -> SystemBarStyle.dark(
                    navigationBarScrim.toArgb()
                )

                else -> SystemBarStyle.light(
                    navigationBarScrim.toArgb(),
                    navigationBarScrim.toArgb(),
                )
            }
        )
    }
}

val refreshTheme = MutableLiveData(false)

@Composable
fun APatchTheme(
    wallpaper: ImageBitmap? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = APApplication.sharedPreferences

    var darkThemeFollowSys by remember {
        mutableStateOf(
            prefs.getBoolean(
                "night_mode_follow_sys",
                true
            )
        )
    }
    var nightModeEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                "night_mode_enabled",
                false
            )
        )
    }
    // Dynamic color is available on Android 12+, and custom 1t!
    var dynamicColor by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) prefs.getBoolean(
                "use_system_color_theme",
                true
            ) else false
        )
    }
    var customColorScheme by remember { mutableStateOf(prefs.getString("custom_color", "blue")) }

    val refreshThemeObserver by refreshTheme.observeAsState(false)
    if (refreshThemeObserver == true) {
        darkThemeFollowSys = prefs.getBoolean("night_mode_follow_sys", true)
        nightModeEnabled = prefs.getBoolean("night_mode_enabled", false)
        dynamicColor = prefs.getBoolean(
            "use_system_color_theme",
            true
        )
        customColorScheme = prefs.getString("custom_color", "blue")
        refreshTheme.postValue(false)
    }

    val darkTheme = if (darkThemeFollowSys) {
        isSystemInDarkTheme()
    } else {
        nightModeEnabled
    }

    val colorScheme = if (!dynamicColor) {
        if (darkTheme) {
            when (customColorScheme) {
                "amber" -> DarkAmberTheme
                "blue_grey" -> DarkBlueGreyTheme
                "blue" -> DarkBlueTheme
                "brown" -> DarkBrownTheme
                "cyan" -> DarkCyanTheme
                "deep_orange" -> DarkDeepOrangeTheme
                "deep_purple" -> DarkDeepPurpleTheme
                "green" -> DarkGreenTheme
                "indigo" -> DarkIndigoTheme
                "light_blue" -> DarkLightBlueTheme
                "light_green" -> DarkLightGreenTheme
                "lime" -> DarkLimeTheme
                "orange" -> DarkOrangeTheme
                "pink" -> DarkPinkTheme
                "purple" -> DarkPurpleTheme
                "red" -> DarkRedTheme
                "sakura" -> DarkSakuraTheme
                "teal" -> DarkTealTheme
                "yellow" -> DarkYellowTheme
                else -> DarkBlueTheme
            }
        } else {
            when (customColorScheme) {
                "amber" -> LightAmberTheme
                "blue_grey" -> LightBlueGreyTheme
                "blue" -> LightBlueTheme
                "brown" -> LightBrownTheme
                "cyan" -> LightCyanTheme
                "deep_orange" -> LightDeepOrangeTheme
                "deep_purple" -> LightDeepPurpleTheme
                "green" -> LightGreenTheme
                "indigo" -> LightIndigoTheme
                "light_blue" -> LightLightBlueTheme
                "light_green" -> LightLightGreenTheme
                "lime" -> LightLimeTheme
                "orange" -> LightOrangeTheme
                "pink" -> LightPinkTheme
                "purple" -> LightPurpleTheme
                "red" -> LightRedTheme
                "sakura" -> LightSakuraTheme
                "teal" -> LightTealTheme
                "yellow" -> LightYellowTheme
                else -> LightBlueTheme
            }
        }
    } else {
        when {
            wallpaper != null -> {
                val startTime = System.currentTimeMillis()
                val prefs = APApplication.sharedPreferences
                val seedColor = Color(prefs.getInt("theme_seed_color", Color.Blue.toArgb()))
                val colorScheme = dynamicColorScheme(isDark = darkTheme, seedColor = seedColor)
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                Log.d("DynamicColor", "Duration: $duration ms")
                colorScheme
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> DarkBlueTheme
            else -> LightBlueTheme
        }
    }

    SystemBarStyle(
        darkMode = darkTheme
    )

    val primary by animateColorAsState(colorScheme.primary)
    val onPrimary by animateColorAsState(colorScheme.onPrimary)
    val primaryContainer by animateColorAsState(colorScheme.primaryContainer)
    val onPrimaryContainer by animateColorAsState(colorScheme.onPrimaryContainer)
    val inversePrimary by animateColorAsState(colorScheme.inversePrimary)
    val secondary by animateColorAsState(colorScheme.secondary)
    val onSecondary by animateColorAsState(colorScheme.onSecondary)
    val secondaryContainer by animateColorAsState(colorScheme.secondaryContainer)
    val onSecondaryContainer by animateColorAsState(colorScheme.onSecondaryContainer)
    val tertiary by animateColorAsState(colorScheme.tertiary)
    val onTertiary by animateColorAsState(colorScheme.onTertiary)
    val tertiaryContainer by animateColorAsState(colorScheme.tertiaryContainer)
    val onTertiaryContainer by animateColorAsState(colorScheme.onTertiaryContainer)
    val background by animateColorAsState(colorScheme.background)
    val onBackground by animateColorAsState(colorScheme.onBackground)
    val surface by animateColorAsState(colorScheme.surface)
    val onSurface by animateColorAsState(colorScheme.onSurface)
    val surfaceVariant by animateColorAsState(colorScheme.surfaceVariant)
    val onSurfaceVariant by animateColorAsState(colorScheme.onSurfaceVariant)
    val surfaceTint by animateColorAsState(colorScheme.surfaceTint)
    val inverseSurface by animateColorAsState(colorScheme.inverseSurface)
    val inverseOnSurface by animateColorAsState(colorScheme.inverseOnSurface)
    val error by animateColorAsState(colorScheme.error)
    val onError by animateColorAsState(colorScheme.onError)
    val errorContainer by animateColorAsState(colorScheme.errorContainer)
    val onErrorContainer by animateColorAsState(colorScheme.onErrorContainer)
    val outline by animateColorAsState(colorScheme.outline)
    val outlineVariant by animateColorAsState(colorScheme.outlineVariant)
    val scrim by animateColorAsState(colorScheme.scrim)
    val surfaceBright by animateColorAsState(colorScheme.surfaceBright)
    val surfaceDim by animateColorAsState(colorScheme.surfaceDim)
    val surfaceContainer by animateColorAsState(colorScheme.surfaceContainer)
    val surfaceContainerHigh by animateColorAsState(colorScheme.surfaceContainerHigh)
    val surfaceContainerHighest by animateColorAsState(colorScheme.surfaceContainerHighest)
    val surfaceContainerLow by animateColorAsState(colorScheme.surfaceContainerLow)
    val surfaceContainerLowest by animateColorAsState(colorScheme.surfaceContainerLowest)
    val primaryFixed by animateColorAsState(colorScheme.primaryFixed)
    val primaryFixedDim by animateColorAsState(colorScheme.primaryFixedDim)
    val onPrimaryFixed by animateColorAsState(colorScheme.onPrimaryFixed)
    val onPrimaryFixedVariant by animateColorAsState(colorScheme.onPrimaryFixedVariant)
    val secondaryFixed by animateColorAsState(colorScheme.secondaryFixed)
    val secondaryFixedDim by animateColorAsState(colorScheme.secondaryFixedDim)
    val onSecondaryFixed by animateColorAsState(colorScheme.onSecondaryFixed)
    val onSecondaryFixedVariant by animateColorAsState(colorScheme.onSecondaryFixedVariant)
    val tertiaryFixed by animateColorAsState(colorScheme.tertiaryFixed)
    val tertiaryFixedDim by animateColorAsState(colorScheme.tertiaryFixedDim)
    val onTertiaryFixed by animateColorAsState(colorScheme.onTertiaryFixed)
    val onTertiaryFixedVariant by animateColorAsState(colorScheme.onTertiaryFixedVariant)

    val finalColorScheme by remember {
        derivedStateOf {
            ColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                inversePrimary = inversePrimary,
                secondary = secondary,
                onSecondary = onSecondary,
                secondaryContainer = secondaryContainer,
                onSecondaryContainer = onSecondaryContainer,
                tertiary = tertiary,
                onTertiary = onTertiary,
                tertiaryContainer = tertiaryContainer,
                onTertiaryContainer = onTertiaryContainer,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onSurface,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = onSurfaceVariant,
                surfaceTint = surfaceTint,
                inverseSurface = inverseSurface,
                inverseOnSurface = inverseOnSurface,
                error = error,
                onError = onError,
                errorContainer = errorContainer,
                onErrorContainer = onErrorContainer,
                outline = outline,
                outlineVariant = outlineVariant,
                scrim = scrim,
                surfaceBright = surfaceBright,
                surfaceDim = surfaceDim,
                surfaceContainer = surfaceContainer,
                surfaceContainerHigh = surfaceContainerHigh,
                surfaceContainerHighest = surfaceContainerHighest,
                surfaceContainerLow = surfaceContainerLow,
                surfaceContainerLowest = surfaceContainerLowest,
                primaryFixed = primaryFixed,
                primaryFixedDim = primaryFixedDim,
                onPrimaryFixed = onPrimaryFixed,
                onPrimaryFixedVariant = onPrimaryFixedVariant,
                secondaryFixed = secondaryFixed,
                secondaryFixedDim = secondaryFixedDim,
                onSecondaryFixed = onSecondaryFixed,
                onSecondaryFixedVariant = onSecondaryFixedVariant,
                tertiaryFixed = tertiaryFixed,
                tertiaryFixedDim = tertiaryFixedDim,
                onTertiaryFixed = onTertiaryFixed,
                onTertiaryFixedVariant = onTertiaryFixedVariant
            )
        }
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = Typography,
        content = {
            MonetColorsProvider.UpdateCss(colorScheme)
            content()
        }
    )
}
