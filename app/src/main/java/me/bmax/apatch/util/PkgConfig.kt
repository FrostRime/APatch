package me.bmax.apatch.util

import android.os.Parcelable
import android.util.Log
import androidx.annotation.Keep
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import me.bmax.apatch.APApplication
import me.bmax.apatch.Natives
import java.io.File
import java.io.FileWriter

object PkgConfig {
    private const val TAG = "PkgConfig"

    private const val CSV_HEADER = "pkg,exclude,allow,uid,to_uid,sctx"

    @Immutable
    @Parcelize
    @Keep
    data class Config(
        var pkg: String = "", var exclude: Int = 0, var allow: Int = 0, var profile: Natives.Profile
    ) : Parcelable {
        companion object {
            fun fromLine(line: String): Config {
                val sp = line.split(",")
                val profile = Natives.Profile(sp[3].toInt(), sp[4].toInt(), sp[5])
                return Config(sp[0], sp[1].toInt(), sp[2].toInt(), profile)
            }
        }

        fun isDefault(): Boolean {
            return allow == 0 && exclude == 0
        }

        fun toLine(): String {
            return "${pkg},${exclude},${allow},${profile.uid},${profile.toUid},${profile.scontext}"
        }
    }

    fun readConfigs(): HashMap<Int, Config> {
        val file = File(APApplication.PACKAGE_CONFIG_FILE)
        if (!file.exists()) return hashMapOf()

        return try {
            file.bufferedReader().use { reader ->
                reader.readLine()
                reader.lineSequence()
                    .filter { it.isNotEmpty() }
                    .map { line ->
//                        if (BuildConfig.DEBUG) {
//                            Log.d(TAG, line)
//                        }
                        Config.fromLine(line)
                    }
                    .filter { !it.isDefault() }
                    .associateBy { it.profile.uid }
                    .toMutableMap() as HashMap<Int, Config>
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading configs", e)
            hashMapOf()
        }
    }

    private fun writeConfigs(configs: HashMap<Int, Config>) {
        val file = File(APApplication.PACKAGE_CONFIG_FILE)
        if (!file.parentFile?.exists()!!) file.parentFile?.mkdirs()
        val writer = FileWriter(file, false)
        writer.write(CSV_HEADER + '\n')
        configs.values.forEach {
            if (!it.isDefault()) {
                writer.write(it.toLine() + '\n')
            }
        }
        writer.flush()
        writer.close()
    }

    suspend fun changeConfig(config: Config) {
        RootExecutor.run {
            synchronized(PkgConfig.javaClass) {
                val configs = readConfigs()
                val uid = config.profile.uid

                // Root App should not be excluded
                if (config.allow == 1) {
                    config.exclude = 0
                }

                if (config.allow == 0 && configs[uid] != null && config.exclude != 0) {
                    configs.remove(uid)
                } else {
                    Log.d(TAG, "change config: $config")
                    configs[uid] = config
                }

                writeConfigs(configs)
            }
        }
    }
}
