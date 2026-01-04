package me.bmax.apatch.util

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import me.bmax.apatch.APApplication
import me.bmax.apatch.apApp
import rikka.parcelablelist.ParcelableListSlice
import java.io.File

object Pkg {
    private const val TAG = "Pkg"
    private val cache = HashMap<String, PackageInfo>()

    fun fromLine(line: String): PackageInfo? {
        cache[line]?.let { return it }
        val packageName = line.substringBefore(" ")
        return try {
            cache[line] =
                apApp.packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
            cache[line]
        } catch (_: Exception) {
            null
        }
    }

    fun readPackages(): ParcelableListSlice<PackageInfo> {
        val file = File(APApplication.PACKAGES_LIST_PATH)
        if (!file.exists()) return ParcelableListSlice(emptyList())
        return try {
            val packages = file.bufferedReader().use { reader ->
                reader.lineSequence()
                    .filter { it.isNotEmpty() }
                    .mapNotNull { fromLine(it) }.toList()
            }
            ParcelableListSlice(packages)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading packages", e)
            ParcelableListSlice(emptyList())
        }
    }
}
