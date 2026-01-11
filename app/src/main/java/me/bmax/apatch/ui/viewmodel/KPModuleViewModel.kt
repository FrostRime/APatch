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
import me.bmax.apatch.util.HanziToPinyin
import java.text.Collator
import java.util.Locale

class KPModuleViewModel : ViewModel() {
    companion object {
        private const val TAG = "KPModuleViewModel"
        private var modules by mutableStateOf<List<KPModel.KPMInfo>>(emptyList())
    }

    var isRefreshing by mutableStateOf(false)
        private set

    val moduleList by derivedStateOf {
        val comparator = compareBy(Collator.getInstance(Locale.getDefault()), KPModel.KPMInfo::name)
        modules.sortedWith(comparator).also {
            isRefreshing = false
        }
    }

    var searchText by mutableStateOf("")

    val filteredModuleList by derivedStateOf {
        moduleList.filter {
            it.name.lowercase().contains(searchText.lowercase()) || it.name.lowercase()
                .contains(searchText.lowercase()) || HanziToPinyin.getInstance()
                .toPinyinString(it.name).contains(searchText.lowercase()) || it.name.contains(
                searchText,
                ignoreCase = true
            ) ||
                    it.description.contains(searchText, ignoreCase = true) ||
                    it.author.contains(searchText, ignoreCase = true)
        }
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
                        description ?: ""
                    )
                    info
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
        }
    }
}
