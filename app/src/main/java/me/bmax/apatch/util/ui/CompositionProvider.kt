package me.bmax.apatch.util.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

internal fun missingCompositionLocalError(name: String): String =
    """
    No $name provided! 
    Make sure to wrap your composable with CompositionLocalProvider:
    CompositionLocalProvider($name provides yourValue) { ... }
    """.trimIndent()

val LocalSnackbarHost = compositionLocalOf<SnackbarHostState> {
    error(missingCompositionLocalError("LocalSnackbarHost"))
}

val LocalInnerPadding = compositionLocalOf<PaddingValues> {
    error(missingCompositionLocalError("LocalInnerPadding"))
}

val LocalNavigator = compositionLocalOf<DestinationsNavigator> {
    error(missingCompositionLocalError("LocalNavigator"))
}

val LocalWidgetOpacity = compositionLocalOf<MutableState<Float>> {
    error(missingCompositionLocalError("LocalWidgetOpacity"))
}

val LocalWallpaper = compositionLocalOf<MutableState<ImageBitmap?>> {
    error(missingCompositionLocalError("LocalWallpaper"))
}

val LocalWallpaperBackdrop = compositionLocalOf<LayerBackdrop> {
    error(missingCompositionLocalError("LocalWallpaperBackdrop"))
}