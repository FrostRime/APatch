package me.bmax.apatch.ui.viewmodel

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bmax.apatch.Natives
import me.bmax.apatch.util.RootExecutor
import java.text.Collator
import java.util.Locale

class KPModuleViewModel : ViewModel() {
    companion object {
        private const val TAG = "KPModuleViewModel"
        private var modules by mutableStateOf<List<KPModel.KPMInfo>>(emptyList())
        private var installedModules by mutableStateOf<List<KPModel.KPMInfo>>(emptyList())
    }

    var search by mutableStateOf("")
    var isRefreshing by mutableStateOf(false)
        private set


    val moduleList by derivedStateOf {
        val comparator = compareBy(
            comparator = Collator.getInstance(Locale.getDefault()),
            selector = KPModel.KPMInfo::name
        )

        modules.filter {
            it.name.contains(search, true) || it.name.contains(
                search,
                true
            )
        }.sortedWith(comparator)
    }

    val installedModuleList by derivedStateOf {
        val comparator = compareBy(
            comparator = Collator.getInstance(Locale.getDefault()),
            selector = KPModel.KPMInfo::name
        )

        installedModules.filter {
            it.name.contains(search, true) || it.name.contains(
                search,
                true
            )
        }.sortedWith(comparator)
    }

    var isNeedRefresh by mutableStateOf(false)
        private set

    fun markNeedRefresh() {
        isNeedRefresh = true
    }

    fun fetchModuleList() {
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing = true
            val oldModuleList = modules
            val start = SystemClock.elapsedRealtime()

            kotlin.runCatching {
                val installedModulesName = RootExecutor.run {
                    Natives.installedKpmList()
                }

                installedModulesName.forEach { Log.d(TAG, "installed kpm: $it") }
                var names = Natives.kernelPatchModuleList()
                if (Natives.kernelPatchModuleNum() <= 0)
                    names = ""
                val nameList = names.split('\n').toList()
                Log.d(TAG, "kpm list: $nameList")
                modules = nameList.filter { it.isNotEmpty() }.map { it ->
                    val infoline = Natives.kernelPatchModuleInfo(it)
                    val spi = infoline.split('\n')
                    val name = spi.find { it.startsWith("name=") }?.removePrefix("name=")
                    val version = spi.find { it.startsWith("version=") }?.removePrefix("version=")
                    val license = spi.find { it.startsWith("license=") }?.removePrefix("license=")
                    val author = spi.find { it.startsWith("author=") }?.removePrefix("author=")
                    val description =
                        spi.find { it.startsWith("description=") }?.removePrefix("description=")
                    val args = spi.find { it.startsWith("args=") }?.removePrefix("args=")
                    val info = KPModel.KPMInfo(
                        KPModel.ExtraType.KPM,
                        name ?: "",
                        "",
                        args ?: "",
                        version ?: "",
                        license ?: "",
                        author ?: "",
                        description ?: "",
                        isInstalled = installedModulesName.contains(name),
                    )
                    info
                }

                installedModules = emptyList()

                installedModules = installedModulesName.map { name ->
                    modules.find { it.name == name }
                        ?: KPModel.KPMInfo(
                            KPModel.ExtraType.KPM,
                            name,
                            "",
                            "unknown",
                            "unknown",
                            "unknown",
                            "unknown",
                            "unknown",
                            isInstalled = true
                        )
                }

                isNeedRefresh = false
            }.onFailure { e ->
                Log.e(TAG, "fetchModuleList: ", e)
                isRefreshing = false
            }

            // when both old and new is kotlin.collections.EmptyList
            // moduleList update will don't trigger
            if (oldModuleList === modules) {
                isRefreshing = false
            }

            Log.i(TAG, "load cost: ${SystemClock.elapsedRealtime() - start}, modules: $modules")
            isRefreshing = false
        }
    }
}
