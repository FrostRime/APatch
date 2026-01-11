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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import me.bmax.apatch.APApplication
import me.bmax.apatch.BuildConfig
import me.bmax.apatch.Natives
import me.bmax.apatch.apApp
import me.bmax.apatch.util.HanziToPinyin
import me.bmax.apatch.util.Pkg
import me.bmax.apatch.util.PkgConfig
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
        var showEditProfile by mutableStateOf(false)

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
                .contains(search.lowercase()) || HanziToPinyin.getInstance()
                .toPinyinString(it.label).contains(search.lowercase())
        }.filter {
            it.uid == 2000 // Always show shell
                    || showSystemApps || it.packageInfo.applicationInfo!!.flags.and(ApplicationInfo.FLAG_SYSTEM) == 0
        }.sortedByDescending {
            it.uid == 2000
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    suspend fun resetAppList() {
        isRefreshing = true
        withContext(Dispatchers.IO) {
            val content = newSingleThreadContext("SyncWorker")
            async(content) {
                Natives.su()
                Pkg.readPackages().list.map {
                    val uid = it.applicationInfo!!.uid
                    Natives.revokeSu(uid)
                    Natives.setUidExclude(uid, 0)
                }
                val file = File(APApplication.PACKAGE_CONFIG_FILE)
                if (!file.parentFile?.exists()!!) file.parentFile?.mkdirs()
                FileChannel.open(file.toPath(), StandardOpenOption.WRITE).use { channel ->
                    channel.truncate(0)
                }
            }.await()
        }
        fetchAppList()
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    suspend fun fetchAppList() {
        isRefreshing = true

        withContext(Dispatchers.IO) {
            val uids = Natives.suUids().toList()
            Log.d(TAG, "all allows: $uids")
            val content = newSingleThreadContext("SyncWorker")
            val nativeDataDeferred = async(content) {
                Natives.su()
                var startTime = if (BuildConfig.DEBUG) {
                    System.currentTimeMillis()
                } else {
                    null
                }
                val configs = PkgConfig.readConfigs()

                if (BuildConfig.DEBUG) {
                    startTime?.let {
                        Log.d(
                            TAG,
                            "read configs in ${System.currentTimeMillis() - it}ms"
                        )
                    }
                }
                startTime = if (BuildConfig.DEBUG) {
                    System.currentTimeMillis()
                } else {
                    null
                }
                val apps = Pkg.readPackages()

                if (BuildConfig.DEBUG) {
                    startTime?.let {
                        Log.d(
                            TAG,
                            "read packages in ${System.currentTimeMillis() - it}ms"
                        )
                    }
                }
                Pair(configs, apps)
            }
            val (configs, packages) = nativeDataDeferred.await()

            Log.d(TAG, "all configs: $configs")

            val newApps = packages.list.map {
                val appInfo = it.applicationInfo
                val uid = appInfo!!.uid
                val actProfile = if (uids.contains(uid)) Natives.suProfile(uid) else null
                val packageName = it.packageName
                val exclude = Natives.isUidExcluded(uid)
                Log.d(TAG, "uid: $uid: $exclude")
                val config = configs.getOrDefault(
                    uid,
                    PkgConfig.Config(
                        packageName,
                        exclude,
                        0,
                        Natives.Profile(uid = uid)
                    )
                )
                config.allow = 0

                // from kernel
                if (actProfile != null) {
                    config.allow = 1
                    config.profile = actProfile
                }
                AppInfo(
                    label = appInfo.loadLabel(apApp.packageManager).toString(),
                    packageInfo = it,
                    packageName = packageName,
                    uid = uid,
                    config = config
                )
            }

            synchronized(appsLock) {
                apps = emptyList()
                apps = newApps
            }
        }
    }
}
