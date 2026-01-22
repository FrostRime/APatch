package me.bmax.apatch.ui

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.graphics.createBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import me.bmax.apatch.APApplication
import me.bmax.apatch.ui.theme.APatchTheme
import me.bmax.apatch.ui.viewmodel.SuperUserViewModel
import me.bmax.apatch.ui.webui.Insets
import me.bmax.apatch.ui.webui.SuFilePathHandler
import me.bmax.apatch.ui.webui.WebViewInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
class WebUIActivity : ComponentActivity() {
    private lateinit var webViewInterface: WebViewInterface
    private var webView: WebView? = null
    private lateinit var container: FrameLayout
    private lateinit var insets: Insets
    private var insetsContinuation: CancellableContinuation<Unit>? = null
    private var isInsetsEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)

        setContent {
            APatchTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        lifecycleScope.launch {
            if (SuperUserViewModel.apps.isEmpty()) {
                SuperUserViewModel().fetchAppList()
            }
            setupWebView()
        }
    }

    private suspend fun setupWebView() {
        val moduleId = intent.getStringExtra("id")!!
        val name = intent.getStringExtra("name")!!
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            setTaskDescription(ActivityManager.TaskDescription("APatch - $name"))
        } else {
            val taskDescription =
                ActivityManager.TaskDescription.Builder().setLabel("APatch - $name").build()
            setTaskDescription(taskDescription)
        }

        val prefs = APApplication.sharedPreferences
        WebView.setWebContentsDebuggingEnabled(prefs.getBoolean("enable_web_debugging", false))

        val webRoot = File("/data/adb/modules/${moduleId}/webroot")
        insets = Insets(0, 0, 0, 0)

        container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        this.webView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        val density = resources.displayMetrics.density
        ViewCompat.setOnApplyWindowInsetsListener(container) { view, windowInsets ->
            val inset =
                windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            insets = Insets(
                top = (inset.top / density).toInt(),
                bottom = (inset.bottom / density).toInt(),
                left = (inset.left / density).toInt(),
                right = (inset.right / density).toInt()
            )
            if (isInsetsEnabled) {
                view.setPadding(0, 0, 0, 0)
            } else {
                view.setPadding(inset.left, inset.top, inset.right, inset.bottom)
            }
            insetsContinuation?.resumeWith(Result.success(Unit))
            insetsContinuation = null
            WindowInsetsCompat.CONSUMED
        }
        container.addView(this.webView)

        suspendCancellableCoroutine { cont ->
            insetsContinuation = cont
            cont.invokeOnCancellation {
                insetsContinuation = null
            }
            setContentView(container)

            if (insets != Insets(0, 0, 0, 0)) {
                cont.resumeWith(Result.success(Unit))
                insetsContinuation = null
            }
        }

        val webViewAssetLoader = WebViewAssetLoader.Builder()
            .setDomain("mui.kernelsu.org")
            .addPathHandler(
                "/",
                SuFilePathHandler(this, webRoot, { insets }, { enable -> enableInsets(enable) })
            )
            .build()

        val webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url

                // Handle ksu://icon/[packageName] to serve app icon via WebView
                if (url.scheme.equals("ksu", ignoreCase = true) && url.host.equals(
                        "icon",
                        ignoreCase = true
                    )
                ) {
                    val packageName = url.path?.substring(1)
                    if (!packageName.isNullOrEmpty()) {
                        val icon = handleIconRequest(packageName)
                        if (icon != null) {
                            return icon
                        }
                    }
                }

                return webViewAssetLoader.shouldInterceptRequest(url)
            }
        }

        webView?.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            webViewInterface = WebViewInterface(this@WebUIActivity, this)
            addJavascriptInterface(webViewInterface, "ksu")
            setWebViewClient(webViewClient)
            loadUrl("https://mui.kernelsu.org/index.html")
        }
    }

    fun enableInsets(enable: Boolean = true) {
        runOnUiThread {
            if (isInsetsEnabled != enable) {
                isInsetsEnabled = enable
                ViewCompat.requestApplyInsets(container)
            }
        }
    }

    private fun handleIconRequest(packageName: String): WebResourceResponse? {
        try {
            val appInfo =
                SuperUserViewModel.apps.find { it.packageName == packageName } ?: return null

            val pm = packageManager
            val drawable = appInfo.packageInfo.applicationInfo?.loadIcon(pm)

            val inputStream = drawable?.let { drawableToPngStream(it) }

            return WebResourceResponse("image/png", null, inputStream)
        } catch (_: Exception) {
            return null
        }
    }

    private fun drawableToPngStream(drawable: Drawable): ByteArrayInputStream {
        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
            val bmp = createBitmap(width, height)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        }

        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }

        return ByteArrayInputStream(stream.toByteArray())
    }
}