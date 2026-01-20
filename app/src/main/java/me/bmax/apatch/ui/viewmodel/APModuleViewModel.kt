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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.bmax.apatch.apApp
import me.bmax.apatch.util.HanziToPinyin
import me.bmax.apatch.util.listModules
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.util.Locale

class APModuleViewModel : ViewModel() {
    companion object {
        private const val TAG = "ModuleViewModel"
        private var modules by mutableStateOf<List<ModuleInfo>>(emptyList())
    }

    data class ModuleInfo(
        val id: String,
        val name: String,
        val author: String,
        val version: String,
        val versionCode: Int,
        val description: String,
        val enabled: Boolean,
        val update: Boolean,
        val remove: Boolean,
        val updateJson: String,
        val hasWebUi: Boolean,
        val hasActionScript: Boolean,
        val metamodule: Boolean,
        val updateInfo: ModuleUpdateInfo? = null,
    )

    @Suppress("unused")
    data class ModuleUpdateInfo(
        val version: String,
        val versionCode: Int,
        val zipUrl: String,
        val changelog: String,
    )

    var search by mutableStateOf("")
    var isRefreshing by mutableStateOf(false)
        private set

    val moduleList by derivedStateOf {
        val collator = Collator.getInstance(Locale.getDefault())

        val comparator = compareByDescending<ModuleInfo> { it.metamodule && it.enabled }
            .thenBy(collator) { it.id }

        modules.filter {
            it.id.contains(search, true) || it.name.contains(
                search,
                true
            ) || HanziToPinyin.getInstance()
                .toPinyinString(it.name)?.contains(search, true) == true
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
            val start = SystemClock.elapsedRealtime()
            val oldModules = modules

            val result = runCatching { listModules() }
                .onFailure { e ->
                    Log.e(TAG, "fetchModuleList: ", e)
                    isRefreshing = false
                }.getOrNull() ?: return@launch

            try {
                val array = JSONArray(result)
                val newModules = List(array.length()) { i ->
                    val obj = array.getJSONObject(i)
                    ModuleInfo(
                        id = obj.getString("id"),
                        name = obj.optString("name"),
                        author = obj.optString("author", "Unknown"),
                        version = obj.optString("version", "Unknown"),
                        versionCode = obj.optInt("versionCode", 0),
                        description = obj.optString("description"),
                        enabled = obj.getBoolean("enabled"),
                        update = obj.getBoolean("update"),
                        remove = obj.getBoolean("remove"),
                        updateJson = obj.optString("updateJson"),
                        hasWebUi = obj.getBooleanCompat("web"),
                        hasActionScript = obj.getBooleanCompat("action"),
                        metamodule = obj.getBooleanCompat("metamodule")
                    )
                }

                if (oldModules != newModules) modules = newModules
                isNeedRefresh = false

                val updatedModules = coroutineScope {
                    newModules.map { module ->
                        if (module.enabled && module.updateJson.isNotEmpty() && !module.update && !module.remove) {
                            async(Dispatchers.IO) {
                                module.copy(updateInfo = runCatching { checkUpdate(module) }.getOrNull())
                            }
                        } else null
                    }.mapIndexed { index, deferred ->
                        deferred?.await() ?: newModules[index]
                    }
                }

                modules = updatedModules
            } catch (e: Exception) {
                Log.e(TAG, "parse modules failed", e)
            } finally {
                isRefreshing = false
                Log.i(
                    TAG,
                    "load cost: ${SystemClock.elapsedRealtime() - start}, modules: ${modules.size}"
                )
            }
        }
    }

    private fun sanitizeVersionString(version: String): String {
        return version.replace(Regex("[^a-zA-Z0-9.\\-_]"), "_")
    }

    fun checkUpdate(m: ModuleInfo): ModuleUpdateInfo? {
        if (m.updateJson.isEmpty() || m.remove || m.update || !m.enabled) {
            return null
        }
        // download updateJson
        val result = kotlin.runCatching {
            val url = m.updateJson
            Log.i(TAG, "checkUpdate url: $url")
            val response = apApp.okhttpClient
                .newCall(
                    okhttp3.Request.Builder()
                        .url(url)
                        .build()
                ).execute()
            Log.d(TAG, "checkUpdate code: ${response.code}")
            if (response.isSuccessful) {
                response.body?.string() ?: ""
            } else {
                ""
            }
        }.getOrDefault("")
        Log.i(TAG, "checkUpdate result: $result")

        if (result.isEmpty()) {
            return null
        }

        val updateJson = kotlin.runCatching {
            JSONObject(result)
        }.getOrNull() ?: return null

        val version = sanitizeVersionString(updateJson.optString("version", ""))
        val versionCode = updateJson.optInt("versionCode", 0)
        val zipUrl = updateJson.optString("zipUrl", "")
        val changelog = updateJson.optString("changelog", "")
        if (versionCode <= m.versionCode || zipUrl.isEmpty()) {
            return null
        }

        return ModuleUpdateInfo(version, versionCode, zipUrl, changelog)
    }
}

private fun JSONObject.getBooleanCompat(key: String, default: Boolean = false): Boolean {
    if (!has(key)) return default
    return when (val value = opt(key)) {
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true) || value == "1"
        is Number -> value.toInt() != 0
        else -> default
    }
}