package com.castle.sefirah.presentation.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.getSystemService
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import sefirah.clipboard.ClipboardListener
import sefirah.common.util.isAccessibilityServiceEnabled
import sefirah.common.util.openAppSettings
import sefirah.presentation.components.padding

internal class PermissionStep : OnboardingStep {

    private var notificationGranted by mutableStateOf(false)
    private var batteryGranted by mutableStateOf(false)
    private var locationGranted by mutableStateOf(false)
    private var storageGranted by mutableStateOf(false)
    private var accessibilityGranted by mutableStateOf(false)
    private var notificationListenerGranted by mutableStateOf(false)
    private var readMediaGranted by mutableStateOf(false)

    override val isComplete: Boolean
        get() = (notificationGranted && storageGranted) &&
                (notificationListenerGranted || readMediaGranted || batteryGranted || locationGranted || accessibilityGranted)


    @Composable
    override fun Content() {
        val viewModel: OnboardingViewModel = hiltViewModel()
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

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MaterialTheme.padding.medium)
        ) {
            if (viewModel.readAppEntry() == true) {
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
                            permission = Manifest.permission.POST_NOTIFICATIONS,
                            granted = notificationGranted,
                            onRequest = { permissionRequester.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        )
                    }

                    // Location Permission
                    val backgroundLocationRequester = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                        onResult = { /* handled in onResume */ }
                    )

                    val foregroundLocationRequester = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions(),
                        onResult = { permissions ->
                            // Check if foreground permissions were granted
                            val isForegroundGranted = permissions.entries.all { it.value }
                            if (isForegroundGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                // If foreground permissions granted, request background permission
                                backgroundLocationRequester.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            }
                        }
                    )

                    PermissionItem(
                        title = "Location Permission",
                        subtitle = "Required for WiFi network discovery",
                        permission = Manifest.permission.ACCESS_FINE_LOCATION,
                        granted = locationGranted,
                        onRequest = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                // First request foreground permissions
                                foregroundLocationRequester.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            } else {
                                // For Android 9 and below, request only foreground location
                                foregroundLocationRequester.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        }
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val mediaPermissionRequester = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestPermission(),
                            onResult = { /* handled in onResume */ }
                        )
                        
                        PermissionItem(
                            title = "Media Access",
                            subtitle = "Required for accessing the media files",
                            permission = Manifest.permission.READ_MEDIA_IMAGES,
                            granted = readMediaGranted,
                            onRequest = { 
                                mediaPermissionRequester.launch(Manifest.permission.READ_MEDIA_IMAGES)
                            }
                        )
                    }

                    HorizontalDivider()

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
                        subtitle = "Required for detecting clipboard",
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


    private fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
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

        // Check Location Permissions
        val hasFineLocation = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true // Background location permission not required for Android 9 and below
        }
        locationGranted = hasFineLocation && hasBackgroundLocation

        // Check Storage Permission
        storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        }

//         Check Accessibility Service
        accessibilityGranted = isAccessibilityServiceEnabled(
            context,
            "${context.packageName}/${ClipboardListener::class.java.canonicalName}"
        )

        // Check Notification Listener
        notificationListenerGranted = isNotificationListenerEnabled(context)

        // Add this new permission check
        readMediaGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            // For older versions, we already have storage permission
            storageGranted
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityServiceName = "${context.packageName}/${ClipboardListener::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(accessibilityServiceName) == true
    }

    private fun isNotificationListenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return flat?.contains(context.packageName) == true
    }
}

@Composable
fun PermissionItem(
    title: String,
    subtitle: String,
    permission: String? = null,
    granted: Boolean,
    onRequest: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionCheckTrigger by remember { mutableIntStateOf(0) }
    val hasRequestedBefore = if (permission != null) {
        viewModel.hasRequestedPermission(permission)
            .collectAsState(initial = false).value
    } else {
        false
    }

    // Force recomposition when permission result comes back
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionCheckTrigger++ // Force recomposition
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    // These will be recalculated when permissionCheckTrigger changes
    val permissionDenied = remember(permissionCheckTrigger) {
        permission?.let {
            ActivityCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_DENIED
        } ?: false
    }

    val canShowRationale = remember(permissionCheckTrigger) {
        permission?.let {
            shouldShowRequestPermissionRationale(activity, it)
        } ?: false
    }

    val showSettings = if (permission == null) {
        false
    } else {
        permissionDenied && hasRequestedBefore && !canShowRationale
    }

    ListItem(
        headlineContent = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
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
                TextButton(
                    onClick = {
                        if (showSettings) {
                            openAppSettings(context)
                        } else {
                            permission?.let { viewModel.savePermissionRequested(it) }
                            onRequest()
                            permissionCheckTrigger++
                        }
                    }
                ) {
                    Text(if (showSettings) "Settings" else "Grant")
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
