package sefirah.common.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.getSystemService

data class PermissionStates(
    val notificationGranted: Boolean = false,
    val batteryGranted: Boolean = false,
    val locationGranted: Boolean = false,
    val storageGranted: Boolean = false,
    val accessibilityGranted: Boolean = false,
    val notificationListenerGranted: Boolean = false,
    val readSensitiveNotificationsGranted: Boolean = false,
    val smsPermissionGranted: Boolean = false
)

fun checkNotificationPermission(
    context: Context,
    onGranted: (String) -> Unit = {}
): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            onGranted(Manifest.permission.POST_NOTIFICATIONS)
        }
        granted
    } else {
        true
    }
}

fun checkBatteryOptimization(context: Context): Boolean {
    return context.getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(context.packageName)
        ?: false
}

fun checkLocationPermissions(
    context: Context,
    onGranted: (String) -> Unit = {}
): Boolean {
    val hasFineLocation = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    } else {
        true // Background location permission not required for Android 9 and below
    }
    
    if (hasFineLocation) {
        onGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        onGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    if (hasBackgroundLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        onGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }
    
    return hasFineLocation && hasBackgroundLocation
}

fun checkStoragePermission(
    context: Context,
    onGranted: (String) -> Unit = {}
): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        val granted = context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            onGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        granted
    }
}

fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

fun isAccessibilityServiceEnabled(context: Context, accessibilityServiceName: String?): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    return accessibilityServiceName?.let { enabledServices?.contains(it) } == true
}

fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    return flat?.contains(context.packageName) == true
}

fun smsPermissionGranted(
    context: Context,
    onGranted: (String) -> Unit = {}
): Boolean {
    val readSmsPermission = context.checkSelfPermission(Manifest.permission.READ_SMS) ==
        PackageManager.PERMISSION_GRANTED
    val sendSmsPermission = context.checkSelfPermission(Manifest.permission.SEND_SMS) ==
        PackageManager.PERMISSION_GRANTED
    val contactsPermission = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED
    val telephonyPermission = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
        PackageManager.PERMISSION_GRANTED
    
    val allGranted = readSmsPermission && sendSmsPermission && contactsPermission && telephonyPermission
    
    if (allGranted) {
        onGranted(Manifest.permission.READ_SMS)
        onGranted(Manifest.permission.SEND_SMS)
        onGranted(Manifest.permission.READ_CONTACTS)
        onGranted(Manifest.permission.READ_PHONE_STATE)
    }
    
    return allGranted
}


