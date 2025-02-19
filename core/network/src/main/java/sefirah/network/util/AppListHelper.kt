package sefirah.network.util

import android.annotation.SuppressLint
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import sefirah.domain.model.ApplicationInfo
import sefirah.presentation.util.bitmapToBase64
import sefirah.presentation.util.drawableToBitmap

@SuppressLint("QueryPermissionsNeeded")
fun getInstalledApps(packageManager: PackageManager): List<ApplicationInfo> {
    val appsWithPermission = mutableListOf<ApplicationInfo>()
    val packageInfos: List<PackageInfo> = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)

    for (packageInfo in packageInfos) {
        val packageName = packageInfo.packageName
        
        // Skip system apps
        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        if ((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
            continue
        }

        // For Android 13+, check POST_NOTIFICATIONS permission
        // For older versions, just check if it has a launch intent
        val shouldIncludeApp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.checkPermission(
                android.Manifest.permission.POST_NOTIFICATIONS,
                packageName
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Include app if it can be launched
            packageManager.getLaunchIntentForPackage(packageName) != null
        }

        if (shouldIncludeApp) {
            val appName = try {
                packageManager.getApplicationLabel(applicationInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                "Unknown App"
            }

            // Get app icon
            val appIcon = try {
                val appIconDrawable = packageManager.getApplicationIcon(packageName)
                if (appIconDrawable is BitmapDrawable) {
                    bitmapToBase64(appIconDrawable.bitmap)
                } else {
                    bitmapToBase64(drawableToBitmap(appIconDrawable))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            appsWithPermission.add(ApplicationInfo(packageName, appName, appIcon))
        }
    }

    return appsWithPermission
}