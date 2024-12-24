package com.castle.sefirah.presentation.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import sefirah.presentation.components.padding

internal class PermissionStep : OnboardingStep {

    private var notificationGranted by mutableStateOf(false)
    private var batteryGranted by mutableStateOf(false)
    private var locationGranted by mutableStateOf(false)
    private var storageGranted by mutableStateOf(false)
    private var accessibilityGranted by mutableStateOf(false)
    private var notificationListenerGranted by mutableStateOf(false)

    override val isComplete: Boolean
        get() = notificationGranted && batteryGranted && locationGranted && 
                storageGranted && accessibilityGranted && notificationListenerGranted

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        DisposableEffect(lifecycleOwner.lifecycle) {
            val observer = object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    checkAllPermissions(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp)
                .padding(horizontal = MaterialTheme.padding.medium)
        ) {
            Row(    
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SettingsSuggest,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(bottom = MaterialTheme.padding.small)
                        .size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Required Permissions",  
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Notification Permission (Android 13+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val permissionRequester = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestPermission(),
                            onResult = { /* handled in onResume */ }
                        )
                        PermissionItem(
                            title = "Notifications",
                            subtitle = "For connection status updates",
                            granted = notificationGranted,
                            onRequest = { permissionRequester.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        )
                        HorizontalDivider()
                    }

                    // Battery Optimization
                    PermissionItem(
                        title = "Background Battery Usage",
                        subtitle = "Allow the app to maintain stable connections by disabling battery optimizations",
                        granted = batteryGranted,
                        onRequest = {
                            @SuppressLint("BatteryLife")
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    )

                    // Location Permission
                    val locationPermissionRequester = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                        onResult = { /* handled in onResume */ }
                    )
                    PermissionItem(
                        title = "Location Permission",
                        subtitle = "Required for WiFi network discovery",
                        granted = locationGranted,
                        onRequest = { locationPermissionRequester.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                    )

                    // Storage Permission
                    PermissionItem(
                        title = "Storage Access",
                        subtitle = "Required for managing files on your device",
                        granted = storageGranted,
                        onRequest = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            } else {
                                // For older versions, request legacy storage permissions
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        }
                    )

                    // Accessibility Service
                    PermissionItem(
                        title = "Accessibility Service",
                        subtitle = "Required for simulating gestures when casting",
                        granted = accessibilityGranted,
                        onRequest = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    )

                    // Notification Listener
                    PermissionItem(
                        title = "Notification Access",
                        subtitle = "Required for notification synchronization",
                        granted = notificationListenerGranted,
                        onRequest = {
                            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                        }
                    )
                }
            }
        }
    }

    private fun checkAllPermissions(context: Context) {
        // Check Notification Permission (Android 13+)
        notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == 
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        // Check Battery Optimization
        batteryGranted = context.getSystemService<PowerManager>()!!
            .isIgnoringBatteryOptimizations(context.packageName)

        // Check Location Permission
        locationGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == 
                PackageManager.PERMISSION_GRANTED

        // Check Storage Permission
        storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == 
                    PackageManager.PERMISSION_GRANTED
        }

        // Check Accessibility Service
//        accessibilityGranted = isAccessibilityServiceEnabled(context)

        // Check Notification Listener
        notificationListenerGranted = isNotificationListenerEnabled(context)
    }

//    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
//        val accessibilityServiceName = "${context.packageName}/${ScreenHandler::class.java.canonicalName}"
//        val enabledServices = Settings.Secure.getString(
//            context.contentResolver,
//            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
//        )
//        return enabledServices?.contains(accessibilityServiceName) == true
//    }

    private fun isNotificationListenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver, 
            "enabled_notification_listeners"
        )
        return flat?.contains(context.packageName) == true
    }

    @Composable
    private fun PermissionItem(
        title: String,
        subtitle: String,
        granted: Boolean,
        onRequest: () -> Unit
    ) {
        ListItem(
            headlineContent = { 
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            supportingContent = {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                if (granted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    TextButton(onClick = onRequest) {
                        Text("Grant")
                    }
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            )
        )
    }
}
