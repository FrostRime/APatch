package me.bmax.apatch.ui.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner


@Composable
fun ScreenShield(
    content: @Composable () -> Unit
) {
    val activity = LocalContext.current.findActivity()
    LifecycleEventObserverComposable { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> {
                activity.window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE
                )
            }

            Lifecycle.Event.ON_STOP -> {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }

            else -> Unit
        }
    }
    content()
}

fun Context.findActivity(): Activity {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    throw IllegalStateException("Activity not found in context chain")
}

@Composable
fun LifecycleEventObserverComposable(
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    onLifecycleEvent: (LifecycleOwner, Lifecycle.Event) -> Unit
) {
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { source, event ->
            onLifecycleEvent(source, event)
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}