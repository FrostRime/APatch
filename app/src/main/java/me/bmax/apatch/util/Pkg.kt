package me.bmax.apatch.util

import android.content.pm.PackageInfo
import android.util.Log
import me.bmax.apatch.APApplication
import me.bmax.apatch.apApp
import rikka.parcelablelist.ParcelableListSlice
import java.io.File

object Pkg {
    private const val TAG = "Pkg"

    fun fromLine(line: String): PackageInfo? {
        val sp = line.split(" ")
        val packageName = sp[0]
        return try {
            Log.d(TAG, packageName)
            apApp.packageManager.getPackageInfo(packageName, 0)
        } catch (_: Exception) {
            null
        }
    }

    fun readPackages(): ParcelableListSlice<PackageInfo> {
        val packages = ArrayList<PackageInfo>()
        val file = File(APApplication.PACKAGES_LIST_PATH)
        if (file.exists()) {
            file.useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotEmpty()) {
                        val p = fromLine(line)
                        if (p != null) {
                            packages.add(p)
                        }
                    }
                }
            }
        }
        return ParcelableListSlice(packages)
    }
}
