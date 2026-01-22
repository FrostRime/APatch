package me.bmax.apatch.ui.viewmodel

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Parcelable
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import me.bmax.apatch.APApplication
import me.bmax.apatch.BuildConfig
import me.bmax.apatch.Natives
import me.bmax.apatch.apApp
import me.bmax.apatch.util.Pkg
import me.bmax.apatch.util.PkgConfig
import me.bmax.apatch.util.RootExecutor
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.text.Collator
import java.util.Locale


class SuperUserViewModel : ViewModel() {
    companion object {
        private const val TAG = "SuperUserViewModel"
        private val appsLock = Any()

        var apps by mutableStateOf<List<AppInfo>>(emptyList())
    }

    @Immutable
    @Parcelize
    data class AppInfo(
        val label: String,
        val packageName: String,
        val uid: Int,
        val packageInfo: PackageInfo,
        val config: PkgConfig.Config
    ) : Parcelable {
        @IgnoredOnParcel
        var rootGranted by mutableStateOf(config.allow != 0)

        @IgnoredOnParcel
        var excludeApp by mutableIntStateOf(config.exclude)
    }

    var search by mutableStateOf("")
    var showSystemApps by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
        private set

    private val sortedList by derivedStateOf {
        val comparator = compareBy<AppInfo> {
            when {
                it.config.allow != 0 -> 0
                it.config.exclude == 1 -> 1
                else -> 2
            }
        }.then(compareBy(Collator.getInstance(Locale.getDefault()), AppInfo::label))
        apps.sortedWith(comparator)
    }

    val appList by derivedStateOf {
        sortedList.filter {
            it.label.lowercase().contains(search.lowercase()) || it.packageName.lowercase()
                .contains(search.lowercase())
        }.filter {
            it.uid == 2000 // Always show shell
                    || showSystemApps || it.packageInfo.applicationInfo!!.flags.and(ApplicationInfo.FLAG_SYSTEM) == 0
        }.sortedByDescending {
            it.uid == 2000
        }
    }

    suspend fun resetAppList() {
        isRefreshing = true
        try {
            RootExecutor.run {
                Pkg.readPackages().list.forEach {
                    val uid = it.applicationInfo!!.uid
                    Natives.revokeSu(uid)
                    Natives.setUidExclude(uid, 0)
                }

                val file = File(APApplication.PACKAGE_CONFIG_FILE)
                if (file.parentFile?.exists() == false) file.parentFile?.mkdirs()
                FileChannel.open(file.toPath(), StandardOpenOption.WRITE).use { channel ->
                    channel.truncate(0)
                }
            }
        } finally {
            fetchAppList()
        }
    }

    suspend fun fetchAppList() {
        isRefreshing = true
        try {
            val (configs, packages) = RootExecutor.run {
                val startTimeDebug = if (BuildConfig.DEBUG) System.currentTimeMillis() else null

                val configs = PkgConfig.readConfigs()
                if (BuildConfig.DEBUG) {
                    startTimeDebug?.let {
                        Log.d(TAG, "read configs in ${System.currentTimeMillis() - it}ms")
                    }
                }

                val apps = Pkg.readPackages()
                if (BuildConfig.DEBUG) {
                    startTimeDebug?.let {
                        Log.d(TAG, "read packages in ${System.currentTimeMillis() - it}ms")
                    }
                }

                Pair(configs, apps)
            }

            val uids = Natives.suUids().toList()
            Log.d(TAG, "all allows: $uids")

            val newApps = packages.list.map { pkg ->
                val appInfo = pkg.applicationInfo!!
                val uid = appInfo.uid
                val actProfile = if (uids.contains(uid)) Natives.suProfile(uid) else null
                val packageName = pkg.packageName
                val exclude = Natives.isUidExcluded(uid)

                val config = configs.getOrDefault(
                    uid, PkgConfig.Config(
                        packageName, exclude, 0, Natives.Profile(uid = uid)
                    )
                ).also {
                    it.allow = if (actProfile != null) 1 else 0
                    it.profile = actProfile ?: it.profile
                }

                AppInfo(
                    label = appInfo.loadLabel(apApp.packageManager).toString(),
                    packageInfo = pkg,
                    packageName = packageName,
                    uid = uid,
                    config = config
                )
            }

            synchronized(appsLock) {
                apps = newApps
            }
        } finally {
            isRefreshing = false
        }
    }
}
