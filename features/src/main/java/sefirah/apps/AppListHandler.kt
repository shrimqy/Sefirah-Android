package sefirah.apps

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.BitmapDrawable
import sefirah.common.util.bitmapToBase64
import sefirah.common.util.drawableToBitmap
import sefirah.domain.interfaces.NetworkManager
import sefirah.domain.model.ApplicationInfo
import sefirah.domain.model.ApplicationList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppListHandler @Inject constructor(
    private val context: Context,
    private val networkManager: NetworkManager,
) {
    fun handleRequest(deviceId: String) {
        networkManager.sendMessage(deviceId, ApplicationList(installedApps().toList()))
    }

    fun sendInstalledApps(deviceId: String) {
        for (app in installedApps()) {
            if (!networkManager.sendMessage(deviceId, app)) {
                return
            }
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun installedApps(): Sequence<ApplicationInfo> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        return packageManager.queryIntentActivities(intent, 0)
            .sortedBy { it.loadLabel(packageManager).toString() }
            .asSequence()
            .map { toApplicationInfo(it, packageManager) }
    }

    private fun toApplicationInfo(
        packageInfo: ResolveInfo,
        packageManager: PackageManager,
    ): ApplicationInfo {
        val appName = packageInfo.loadLabel(packageManager).toString()
        val packageName = packageInfo.activityInfo.packageName
        val appIcon = try {
            val appIconDrawable = packageInfo.loadIcon(packageManager)
            if (appIconDrawable is BitmapDrawable) {
                bitmapToBase64(appIconDrawable.bitmap)
            } else {
                bitmapToBase64(drawableToBitmap(appIconDrawable))
            }
        } catch (_: Exception) {
            null
        }
        return ApplicationInfo(packageName, appName, appIcon)
    }
}
