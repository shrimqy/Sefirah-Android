package sefirah.network.util

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import sefirah.domain.model.ApplicationInfo
import sefirah.domain.model.ApplicationList
import sefirah.presentation.util.bitmapToBase64
import sefirah.presentation.util.drawableToBitmap

@SuppressLint("QueryPermissionsNeeded")
fun getInstalledApps(packageManager: PackageManager): ApplicationList {
    val appsList = mutableListOf<ApplicationInfo>()

    val intent = Intent(Intent.ACTION_MAIN, null)
    intent.addCategory(Intent.CATEGORY_LAUNCHER)
    val activities = packageManager.queryIntentActivities(intent, 0)

    for (packageInfo in activities) {
        val appName = packageInfo.loadLabel(packageManager).toString()
        val packageName = packageInfo.activityInfo.packageName
        val appIcon = try {
            val appIconDrawable = packageInfo.loadIcon(packageManager)
            if (appIconDrawable is BitmapDrawable) {
                bitmapToBase64(appIconDrawable.bitmap)
            } else {
                bitmapToBase64(drawableToBitmap(appIconDrawable))
            }
        } catch (e: Exception) {
            null
        }
        appsList.add(ApplicationInfo(packageName, appName, appIcon))
    }
    return ApplicationList(appsList.sortedBy { it.appName })
}