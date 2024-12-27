package sefirah.network.util

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import sefirah.domain.model.ApplicationInfo
import sefirah.presentation.util.bitmapToBase64
import sefirah.presentation.util.drawableToBitmap

fun getInstalledApps(packageManager: PackageManager): List<ApplicationInfo> {
    val appsWithPermission = mutableListOf<ApplicationInfo>()
    val packageInfos: List<PackageInfo> = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)

    for (packageInfo in packageInfos) {
        val packageName = packageInfo.packageName

        // Check if the app has POST_NOTIFICATIONS permission
        val hasPostNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.checkPermission(
                android.Manifest.permission.POST_NOTIFICATIONS,
                packageName
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            TODO("VERSION.SDK_INT < TIRAMISU")
        }

        if (hasPostNotificationPermission) {
            val appName = try {
                val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(applicationInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                "Unknown App"
            }

            // Get app icon
            val appIcon = try {
                val appIconDrawable = packageManager.getApplicationIcon(packageName)
                if (appIconDrawable is BitmapDrawable) {
                    val appIconBitmap = appIconDrawable.bitmap
                    bitmapToBase64(appIconBitmap)
                } else {
                    // Convert to Bitmap if it's not already a BitmapDrawable
                    val appIconBitmap = drawableToBitmap(appIconDrawable)
                    bitmapToBase64(appIconBitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }

            // Add to the list of apps with notification access
            appsWithPermission.add(ApplicationInfo(packageName, appName, appIcon))
        }
    }

    return appsWithPermission
}