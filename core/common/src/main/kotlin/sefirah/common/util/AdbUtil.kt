package sefirah.common.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log

private const val TAG = "AdbUtil"
private const val ADB_WIFI_ENABLED = "adb_wifi_enabled"

/** USB or wireless debugging is on, so a computer may hold an ADB key this phone still trusts. */
fun isAdbDebuggingEnabled(context: Context): Boolean {
    val resolver = context.contentResolver
    return Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED, 0) == 1 ||
        Settings.Global.getInt(resolver, ADB_WIFI_ENABLED, 0) == 1
}

/**
 * Turns wireless debugging off (Android 11+). Needs WRITE_SECURE_SETTINGS, which only ADB can
 * grant (the desktop app does when it connects); without it this does nothing.
 *
 * @return true if wireless debugging was on and is now off.
 */
fun disableWirelessDebugging(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
    if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
        return false
    }

    val resolver = context.contentResolver
    if (Settings.Global.getInt(resolver, ADB_WIFI_ENABLED, 0) != 1) return false

    return try {
        Settings.Global.putInt(resolver, ADB_WIFI_ENABLED, 0).also {
            Log.i(TAG, "Turned off wireless debugging")
        }
    } catch (e: SecurityException) {
        Log.w(TAG, "Could not turn off wireless debugging", e)
        false
    }
}
