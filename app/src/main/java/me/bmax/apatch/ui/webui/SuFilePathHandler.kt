package me.bmax.apatch.ui.webui

import android.content.Context
import android.util.Log
import android.webkit.WebResourceResponse
import androidx.annotation.WorkerThread
import androidx.webkit.WebViewAssetLoader.PathHandler
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import me.bmax.apatch.ui.webui.MimeUtil.getMimeFromFileName
import me.bmax.apatch.util.createRootShell
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

/**
 * Handler class to open files from file system by root access
 * For more information about android storage please refer to
 * [Android Developers
 * Docs: Data and file storage overview](https://developer.android.com/guide/topics/data/data-storage).
 *
 *
 * To avoid leaking user or app data to the web, make sure to choose `directory`
 * carefully, and assume any file under this directory could be accessed by any web page subject
 * to same-origin rules.
 *
 *
 * A typical usage would be like:
 * <pre class="prettyprint">
 * File publicDir = new File(context.getFilesDir(), "public");
 * // Host "files/public/" in app's data directory under:
 * // http://appassets.androidplatform.net/public/...
 * WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
 * .addPathHandler("/public/", new InternalStoragePathHandler(context, publicDir))
 * .build();
</pre> *
 */
class SuFilePathHandler(
    directory: File,
    insetsSupplier: InsetsSupplier,
    onInsetsRequestedListener: OnInsetsRequestedListener
) : PathHandler {
    private val mDirectory: File

    private val mShell: Shell
    private val mInsetsSupplier: InsetsSupplier
    private val mOnInsetsRequestedListener: OnInsetsRequestedListener

    /**
     * Creates PathHandler for app's internal storage.
     * The directory to be exposed must be inside either the application's internal data
     * directory [Context.getDataDir] or cache directory [Context.getCacheDir].
     * External storage is not supported for security reasons, as other apps with
     * [android.Manifest.permission.WRITE_EXTERNAL_STORAGE] may be able to modify the
     * files.
     *
     *
     * Exposing the entire data or cache directory is not permitted, to avoid accidentally
     * exposing sensitive application files to the web. Certain existing subdirectories of
     * [Context.getDataDir] are also not permitted as they are often sensitive.
     * These files are (`"app_webview/"`, `"databases/"`, `"lib/"`,
     * `"shared_prefs/"` and `"code_cache/"`).
     *
     *
     * The application should typically use a dedicated subdirectory for the files it intends to
     * expose and keep them separate from other files.
     *
     * @param context                   [Context] that is used to access app's internal storage.
     * @param directory                 the absolute path of the exposed app internal storage directory from
     * which files can be loaded.
     * @param insetsSupplier            [InsetsSupplier] to provide window insets for styling web content.
     * @param onInsetsRequestedListener [OnInsetsRequestedListener] to notify when insets are requested.
     * @throws IllegalArgumentException if the directory is not allowed.
     */
    init {
        try {
            mInsetsSupplier = insetsSupplier
            mOnInsetsRequestedListener = onInsetsRequestedListener
            mDirectory = File(getCanonicalDirPath(directory))
            require(isAllowedInternalStorageDir()) {
                ("The given directory \"" + directory
                        + "\" doesn't exist under an allowed app internal storage directory")
            }
            mShell = createRootShell(true)
        } catch (e: IOException) {
            throw IllegalArgumentException(
                "Failed to resolve the canonical path for the given directory: "
                        + directory.path, e
            )
        }
    }

    @Throws(IOException::class)
    private fun isAllowedInternalStorageDir(): Boolean {
        val dir: String = getCanonicalDirPath(mDirectory)

        for (forbiddenPath in FORBIDDEN_DATA_DIRS) {
            if (dir.startsWith(forbiddenPath)) {
                return false
            }
        }
        return true
    }

    /**
     * Opens the requested file from the exposed data directory.
     *
     *
     * The matched prefix path used shouldn't be a prefix of a real web path. Thus, if the
     * requested file cannot be found or is outside the mounted directory a
     * [WebResourceResponse] object with a `null` [InputStream] will be
     * returned instead of `null`. This saves the time of falling back to network and
     * trying to resolve a path that doesn't exist. A [WebResourceResponse] with
     * `null` [InputStream] will be received as an HTTP response with status code
     * `404` and no body.
     *
     *
     * The MIME type for the file will be determined from the file's extension using
     * [java.net.URLConnection.guessContentTypeFromName]. Developers should ensure that
     * files are named using standard file extensions. If the file does not have a
     * recognised extension, `"text/plain"` will be used by default.
     *
     * @param path the suffix path to be handled.
     * @return [WebResourceResponse] for the requested file.
     */
    @WorkerThread
    override fun handle(path: String): WebResourceResponse {
        if ("internal/insets.css" == path) {
            mOnInsetsRequestedListener.onInsetsRequested(true)
            val css = mInsetsSupplier.get().css
            return WebResourceResponse(
                "text/css",
                "utf-8",
                ByteArrayInputStream(css.toByteArray(StandardCharsets.UTF_8))
            )
        }
        try {
            val file: File? = getCanonicalFileIfChild(mDirectory, path)
            if (file != null) {
                val `is`: InputStream = openFile(file, mShell)
                val mimeType: String = guessMimeType(path)
                return WebResourceResponse(mimeType, null, `is`)
            } else {
                Log.e(
                    TAG, String.format(
                        "The requested file: %s is outside the mounted directory: %s", path,
                        mDirectory
                    )
                )
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error opening the requested path: $path", e)
        }
        return WebResourceResponse(null, null, null)
    }

    fun interface InsetsSupplier {
        fun get(): Insets
    }

    fun interface OnInsetsRequestedListener {
        fun onInsetsRequested(enable: Boolean)
    }

    companion object {
        /**
         * Default value to be used as MIME type if guessing MIME type failed.
         */
        const val DEFAULT_MIME_TYPE: String = "text/plain"
        private const val TAG = "SuFilePathHandler"

        /**
         * Forbidden subdirectories of [Context.getDataDir] that cannot be exposed by this
         * handler. They are forbidden as they often contain sensitive information.
         *
         *
         * Note: Any future addition to this list will be considered breaking changes to the API.
         */
        private val FORBIDDEN_DATA_DIRS: Array<String> =
            arrayOf<String>("/data/data", "/data/system")

        @Throws(IOException::class)
        fun getCanonicalDirPath(file: File): String {
            var canonicalPath = file.getCanonicalPath()
            if (!canonicalPath.endsWith("/")) canonicalPath += "/"
            return canonicalPath
        }

        @Throws(IOException::class)
        fun getCanonicalFileIfChild(parent: File, child: String): File? {
            val parentCanonicalPath: String = getCanonicalDirPath(parent)
            val childCanonicalPath = File(parent, child).getCanonicalPath()
            if (childCanonicalPath.startsWith(parentCanonicalPath)) {
                return File(childCanonicalPath)
            }
            return null
        }

        @Throws(IOException::class)
        private fun handleSvgzStream(
            path: String,
            stream: InputStream
        ): InputStream {
            return if (path.endsWith(".svgz")) GZIPInputStream(stream) else stream
        }

        @Throws(IOException::class)
        fun openFile(file: File, shell: Shell): InputStream {
            val suFile = SuFile(file.absolutePath)
            suFile.shell = shell
            val fis = SuFileInputStream.open(suFile)
            return handleSvgzStream(file.path, fis)
        }

        /**
         * Use [MimeUtil.getMimeFromFileName] to guess MIME type or return the
         * [.DEFAULT_MIME_TYPE] if it can't guess.
         *
         * @param filePath path of the file to guess its MIME type.
         * @return MIME type guessed from file extension or [.DEFAULT_MIME_TYPE].
         */
        fun guessMimeType(filePath: String): String {
            val mimeType = getMimeFromFileName(filePath)
            return mimeType ?: DEFAULT_MIME_TYPE
        }
    }
}