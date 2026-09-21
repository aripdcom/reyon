package com.aripd.reyon.platform

import android.content.Context
import android.content.pm.PackageManager

/** Kurulu sürümün adı (ör. "1.0.0"); Hakkında ekranı bunu gösterir. */
fun installedVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
} catch (e: PackageManager.NameNotFoundException) {
    "0.0.0"
}
