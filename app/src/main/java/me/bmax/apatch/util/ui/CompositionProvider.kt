package me.bmax.apatch.util.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.compositionLocalOf
import com.ramcosta.composedestinations.navigation.DestinationsNavigator

val LocalSnackbarHost = compositionLocalOf<SnackbarHostState> {
    error("CompositionLocal LocalSnackbarController not present")
}

val LocalInnerPadding = compositionLocalOf<PaddingValues> {
    error("CompositionLocal LocalInnerPaddingController not present")
}

val LocalNavigator = compositionLocalOf<DestinationsNavigator> {
    error("No DestinationsNavigator provided! Make sure to wrap your composable with NavigationLocalProvider")
}