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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import me.bmax.apatch.Natives
import me.bmax.apatch.apApp
import me.bmax.apatch.util.HanziToPinyin
import me.bmax.apatch.util.Pkg
import me.bmax.apatch.util.PkgConfig
import java.text.Collator
import java.util.Locale


class SuperUserViewModel : ViewModel() {
    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private val workerDispatcher = newSingleThreadContext("SyncWorker")
    private val refreshMutex = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCleared() {
        super.onCleared()
        workerDispatcher.close()
    }

    companion object {
        private const val TAG = "SuperUserViewModel"

        var apps by mutableStateOf<List<AppInfo>>(emptyList())
    }

    @Immutable
    @Parcelize
    data class AppInfo(
        val label: String, val packageInfo: PackageInfo, val config: PkgConfig.Config
    ) : Parcelable {
        val packageName: String
            get() = packageInfo.packageName
        val uid: Int
            get() = packageInfo.applicationInfo!!.uid


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
        apps.sortedWith(comparator).also {
            isRefreshing = false
        }
    }

    val appList by derivedStateOf {
        sortedList.filter {
            it.label.lowercase().contains(search.lowercase()) || it.packageName.lowercase()
                .contains(search.lowercase()) || HanziToPinyin.getInstance()
                .toPinyinString(it.label).contains(search.lowercase())
        }.filter {
            it.uid == 2000 // Always show shell
                    || showSystemApps || it.packageInfo.applicationInfo!!.flags.and(ApplicationInfo.FLAG_SYSTEM) == 0
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    suspend fun fetchAppList() {
        if (!refreshMutex.tryLock()) return
        try {
            withContext(Dispatchers.Main) {
                isRefreshing = true
            }

            withContext(Dispatchers.IO) {
                val uids = Natives.suUids().toList()
                Log.d(TAG, "all allows: $uids")
                val nativeDataDeferred = async(workerDispatcher) {
                    Natives.su()
                    val configs = PkgConfig.readConfigs()
                    val packages = Pkg.readPackages()
                    Pair(configs, packages)
                }
                val (configs, packages) = nativeDataDeferred.await()

                Log.d(TAG, "all configs: $configs")

                val newApps = packages.list.map {
                    val appInfo = it.applicationInfo
                    val uid = appInfo!!.uid
                    val actProfile = if (uids.contains(uid)) Natives.suProfile(uid) else null
                    val config = configs.getOrDefault(
                        uid,
                        PkgConfig.Config(
                            appInfo.packageName,
                            Natives.isUidExcluded(uid),
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
                        config = config
                    )
                }

                withContext(Dispatchers.Main) {
                    apps = newApps
                }
            }
        } finally {
            // Ensure the UI state is reset and the lock is released, even if an error occurs
            withContext(Dispatchers.Main) {
                isRefreshing = false
            }
            refreshMutex.unlock()
        }
    }
}
