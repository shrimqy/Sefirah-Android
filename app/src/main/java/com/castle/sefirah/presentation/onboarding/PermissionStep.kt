package com.castle.sefirah.presentation.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.castle.sefirah.presentation.settings.SettingsViewModel
import sefirah.common.R
import sefirah.common.util.openAppSettings
import sefirah.presentation.components.padding

internal class PermissionStep : OnboardingStep {
    @Composable
    override fun Content(viewModel: SettingsViewModel) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        val permissionStates by viewModel.permissionStates.collectAsState()

        var permissionRationaleDialog by remember { 
            mutableStateOf<PermissionRationaleDialog?>(null) 
        }

        // Update permissions on resume
        DisposableEffect(lifecycleOwner.lifecycle) {
            val observer = object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    viewModel.updatePermissionStates()
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
            if (!viewModel.appEntryValue) {
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
                        text = stringResource(R.string.required_permissions),
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
                            title = stringResource(R.string.notifications),
                            subtitle = stringResource(R.string.notification_permission_rationale),
                            permission = Manifest.permission.POST_NOTIFICATIONS,
                            granted = permissionStates.notificationGranted,
                            onRequest = { permissionRequester.launch(Manifest.permission.POST_NOTIFICATIONS) },
                            viewModel = viewModel
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
                        title = stringResource(R.string.location_permission),
                        subtitle = stringResource(R.string.location_permission_rationale),
                        permission = Manifest.permission.ACCESS_FINE_LOCATION,
                        granted = permissionStates.locationGranted,
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
                        },
                        viewModel = viewModel
                    )


                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val mediaPermissionRequester = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestPermission(),
                            onResult = { /* handled in onResume */ }
                        )

                        PermissionItem(
                            title = stringResource(R.string.media_access),
                            subtitle = stringResource(R.string.media_access_rationale),
                            permission = Manifest.permission.READ_MEDIA_IMAGES,
                            granted = permissionStates.readMediaGranted,
                            onRequest = {
                                mediaPermissionRequester.launch(Manifest.permission.READ_MEDIA_IMAGES)
                            },
                            viewModel = viewModel
                        )
                    }

                    HorizontalDivider()

                    // Battery Optimization
                    PermissionItem(
                        title = stringResource(R.string.background_battery_usage),
                        subtitle = stringResource(R.string.background_battery_usage_rationale),
                        granted = permissionStates.batteryGranted,
                        onRequest = {
                            @SuppressLint("BatteryLife")
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        },
                        viewModel = viewModel
                    )



                    // Storage Permission
                    PermissionItem(
                        title = stringResource(R.string.storage_access),
                        subtitle = stringResource(R.string.storage_access_rationale),
                        granted = permissionStates.storageGranted,
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
                        },
                        viewModel = viewModel
                    )


                    // Accessibility Service
                    PermissionItem(
                        title = stringResource(R.string.accessibility_service),
                        subtitle = stringResource(R.string.accessibility_service_rationale),
                        granted = permissionStates.accessibilityGranted,
                        onRequest = {
                            permissionRationaleDialog = PermissionRationaleDialog(
                                show = true,
                                permissionScreen = {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                },
                                restrictedSettings = R.string.accessibility_service
                            )
                        },
                        viewModel = viewModel
                    )

                    // Notification Listener
                    PermissionItem(
                        title = stringResource(R.string.notification_access),
                        subtitle = stringResource(R.string.notification_access_rationale),
                        granted = permissionStates.notificationListenerGranted,
                        onRequest = {
                            permissionRationaleDialog = PermissionRationaleDialog(
                                show = true,
                                permissionScreen = {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                },
                                restrictedSettings = R.string.notification_access
                            )
                        },
                        viewModel = viewModel
                    )
                }
            }

            permissionRationaleDialog?.let { dialog ->
                if (dialog.show) {
                    AlertDialog(
                        onDismissRequest = { permissionRationaleDialog = null },
                        title = { Text(stringResource(R.string.restricted_settings_title)) },
                        text = { 
                            val settingName = stringResource(dialog.restrictedSettings)
                            Text(
                                stringResource(
                                    R.string.restricted_settings_instruction,
                                    settingName,
                                    settingName
                                )
                            )
                        },
                        confirmButton = {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    TextButton(
                                        onClick = { openAppSettings(context) },
                                    ) {
                                        Text(
                                            text = stringResource(R.string.app_info),
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            dialog.permissionScreen()
                                            permissionRationaleDialog = null
                                        }
                                    ) {
                                        Text(stringResource(dialog.restrictedSettings))
                                    }
                                }
                            }
                        },
                        dismissButton = {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { permissionRationaleDialog = null }
                            ) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }
            }
        }
    }
    companion object {
        data class PermissionRationaleDialog(
            val show: Boolean,
            val permissionScreen: () -> Unit,
            val restrictedSettings: Int
        )
    }
}

@Composable
fun PermissionItem(
    title: String,
    subtitle: String,
    permission: String? = null,
    granted: Boolean,
    onRequest: () -> Unit,
    viewModel: SettingsViewModel
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
                    Text(if (showSettings) stringResource(R.string.settings) else stringResource(R.string.grant))
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
