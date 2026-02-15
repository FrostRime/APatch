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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import me.bmax.apatch.apApp
import me.bmax.apatch.util.listModules
import java.text.Collator
import java.util.Locale

class APModuleViewModel : ViewModel() {
    companion object {
        private const val TAG = "ModuleViewModel"
        private var checkUpdateJob: Job? = null
        private var modules by mutableStateOf<List<ModuleInfo>>(emptyList())
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    @JsonIgnoreUnknownKeys
    data class ModuleInfo(
        @SerialName("id") val id: String,
        @SerialName("name") val name: String = "",
        @SerialName("author") val author: String = "Unknown",
        @SerialName("version") val version: String = "Unknown",
        @SerialName("versionCode") val versionCode: Int = 0,
        @SerialName("description") val description: String = "",
        @SerialName("enabled") @Serializable(with = BooleanCompatSerializer::class) val enabled: Boolean,
        @SerialName("update") @Serializable(with = BooleanCompatSerializer::class) val update: Boolean,
        @SerialName("remove") @Serializable(with = BooleanCompatSerializer::class) val remove: Boolean,
        @SerialName("updateJson") val updateJson: String = "",
        @SerialName("web") @Serializable(with = BooleanCompatSerializer::class) val hasWebUi: Boolean,
        @SerialName("action") @Serializable(with = BooleanCompatSerializer::class) val hasActionScript: Boolean,
        @SerialName("metamodule") @Serializable(with = BooleanCompatSerializer::class) val metamodule: Boolean = false,
        @Transient val updateInfo: ModuleUpdateInfo? = null,
    )

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    @JsonIgnoreUnknownKeys
    @Suppress("unused")
    data class ModuleUpdateInfo(
        @SerialName("version") @Serializable(with = SanitizedVersionSerializer::class) val version: String = "",
        @SerialName("versionCode") val versionCode: Int = 0,
        @SerialName("zipUrl") val zipUrl: String = "",
        @SerialName("changelog") val changelog: String = "",
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
            )
        }.sortedWith(comparator).also {
            isRefreshing = false
        }
    }

    var isNeedRefresh by mutableStateOf(false)
        private set

    fun markNeedRefresh() {
        isNeedRefresh = true
    }

    fun fetchModuleList() {
        viewModelScope.launch(Dispatchers.IO) {
            checkUpdateJob?.cancel()
            isRefreshing = true
            val start = SystemClock.elapsedRealtime()
            val oldModules = modules

            val result = runCatching { listModules() }
                .onFailure { e ->
                    Log.e(TAG, "fetchModuleList: ", e)
                    isRefreshing = false
                }.getOrNull() ?: return@launch

            try {
                val newModules = Json.decodeFromString<List<ModuleInfo>>(result)

                if (oldModules != newModules) modules = newModules
                isNeedRefresh = false

                checkUpdateJob = viewModelScope.launch {
                    val limitedDispatcher = Dispatchers.IO.limitedParallelism(4)
                    newModules.forEachIndexed { index, module ->
                        if (module.enabled && module.updateJson.isNotEmpty() && !module.update && !module.remove) {
                            launch(limitedDispatcher) {
                                val updateInfo = runCatching { checkUpdate(module) }.getOrNull()
                                if (updateInfo != null) {
                                    modules = modules.mapIndexed { i, m ->
                                        if (i == index) m.copy(updateInfo = updateInfo) else m
                                    }
                                }
                            }
                        }
                    }
                }
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

        val updateInfo = try {
            Json.decodeFromString<ModuleUpdateInfo>(result)
        } catch (e: Exception) {
            Log.e(TAG, "parse module update info failed", e)
            return null
        }

        if (updateInfo.versionCode <= m.versionCode || updateInfo.zipUrl.isEmpty()) {
            return null
        }

        return updateInfo
    }
}

private object BooleanCompatSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BooleanCompat", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        val input = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive -> {
                when {
                    element.isString -> {
                        val str = element.content.lowercase()
                        str == "true" || str == "1"
                    }

                    element.booleanOrNull != null -> element.boolean
                    element.intOrNull != null -> element.int != 0
                    element.longOrNull != null -> element.long != 0L
                    else -> false
                }
            }

            else -> false
        }
    }

    override fun serialize(encoder: Encoder, value: Boolean) {
        encoder.encodeBoolean(value)
    }
}

private object SanitizedVersionSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SanitizedVersion", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val original = decoder.decodeString()
        return sanitizeVersionString(original)
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

private fun sanitizeVersionString(version: String): String {
    return version.replace(Regex("[^a-zA-Z0-9.\\-_]"), "_")
}