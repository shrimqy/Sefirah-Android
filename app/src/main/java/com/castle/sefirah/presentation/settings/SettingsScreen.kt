package com.castle.sefirah.presentation.settings

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.castle.sefirah.navigation.SettingsRouteScreen
import com.castle.sefirah.presentation.settings.components.LogoHeader
import com.castle.sefirah.presentation.settings.components.SwitchPreferenceWidget
import com.castle.sefirah.presentation.settings.components.TextPreferenceWidget
import com.castle.sefirah.util.CrashLogUtil
import kotlinx.coroutines.launch
import sefirah.clipboard.ClipboardListener
import sefirah.common.R
import sefirah.common.util.getReadablePathFromUri
import sefirah.common.util.isAccessibilityServiceEnabled
import sefirah.common.util.isNotificationListenerEnabled
import sefirah.common.util.openAppSettings

@Composable
fun SettingsScreen(
    rootNavController: NavHostController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: SettingsViewModel = hiltViewModel()
    
    val permissionStates by viewModel.permissionStates.collectAsState()
    val preferencesSettings by viewModel.preferencesSettings.collectAsState()
    val localDevice by viewModel.localDevice.collectAsState()

    // State for device name dialog
    var showDeviceNameDialog by remember { mutableStateOf(false) }
    var deviceNameText by remember { mutableStateOf("") }

    // Update device name when local device changes
    LaunchedEffect(localDevice) {
        deviceNameText = localDevice?.deviceName ?: ""
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

    val uriHandler = LocalUriHandler.current

    // Permission requesters
    val permissionRequester = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { viewModel.updatePermissionStates() }
    )

    val backgroundLocationRequester = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { viewModel.updatePermissionStates() }
    )

    val telephonyPermissionRequester = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {
            viewModel.saveMessageSyncSettings(true)
            viewModel.updatePermissionStates()
        }
    )

    val contactsPermissionRequester = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {
            telephonyPermissionRequester.launch(Manifest.permission.READ_PHONE_STATE)
        }
    )

    val smsPermissionRequester = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val isGranted = permissions.entries.all { it.value }
            if (isGranted) {
                contactsPermissionRequester.launch(Manifest.permission.READ_CONTACTS)
            }
            viewModel.updatePermissionStates()
        }
    )

    val foregroundLocationRequester = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val isForegroundGranted = permissions.entries.all { it.value }
            if (isForegroundGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                backgroundLocationRequester.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            viewModel.updatePermissionStates()
        }
    )

    val storageLocation = preferencesSettings?.storageLocation ?: "\"/storage/emulated/0/Downloads\""
    val pickStorageLocation = storageLocationPicker(viewModel)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        item { LogoHeader() }

        item {
            SwitchPermissionPrefWidget(
                title = stringResource(R.string.clipboard_sync_preference),
                subtitle = stringResource(R.string.clipboard_sync_subtitle),
                icon = Icons.Filled.ContentCopy,
                checked = preferencesSettings?.clipboardSync == true && permissionStates.accessibilityGranted,
                permission = null,
                onRequest = {
                    if(!isAccessibilityServiceEnabled(context, "${context.packageName}/${ClipboardListener::class.java.canonicalName}") ) {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                },
                onCheckedChanged = { checked ->
                    viewModel.saveClipboardSyncSettings(checked)
                },
                viewModel = viewModel
            )
        }

        item {
            SwitchPermissionPrefWidget(
                title = stringResource(R.string.image_clipboard_preference),
                subtitle = stringResource(R.string.image_clipboard_subtitle),
                icon = Icons.Filled.ContentPaste,
                checked = preferencesSettings?.imageClipboard == true,
                permission = null,
                onRequest = {},
                onCheckedChanged = { checked ->
                    viewModel.saveImageClipboardSettings(checked)
                },
                viewModel = viewModel
            )
        }

        item {
            SwitchPermissionPrefWidget(
                title = stringResource(R.string.notification_sync_preference),
                subtitle = stringResource(R.string.notification_sync_subtitle),
                icon = Icons.Filled.Notifications,
                checked = preferencesSettings?.notificationSync == true && permissionStates.notificationListenerGranted,
                permission = null,
                onRequest = {
                    if (!isNotificationListenerEnabled(context)) {
                        context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    }
                },
                onCheckedChanged = { checked ->
                    viewModel.saveNotificationSyncSettings(checked)
                },
                viewModel = viewModel
            )
        }

        item {
            SwitchPermissionPrefWidget(
                title = stringResource(R.string.message_sync_preference),
                subtitle = stringResource(R.string.message_sync_subtitle),
                icon = Icons.AutoMirrored.Filled.Message,
                checked = preferencesSettings?.messageSync == true && permissionStates.smsPermissionGranted,
                permission = Manifest.permission.READ_SMS,
                onRequest = { 
                    smsPermissionRequester.launch(
                        arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS) 
                    ) 
                },
                onCheckedChanged = { checked ->
                    viewModel.saveMessageSyncSettings(checked)
                },
                viewModel = viewModel
            )
        }

        item {
            SwitchPermissionPrefWidget(
                title = stringResource(R.string.media_session_preference),
                subtitle = stringResource(R.string.media_session_subtitle),
                icon = Icons.Filled.PlayArrow,
                checked = preferencesSettings?.mediaSession == true && permissionStates.notificationGranted,
                permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.POST_NOTIFICATIONS
                } else null,
                onRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionRequester.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onCheckedChanged = { checked ->
                    viewModel.saveMediaSessionSettings(checked)
                },
                viewModel = viewModel
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            item {
                SwitchPermissionPrefWidget(
                    title = stringResource(R.string.storage_access_preference),
                    subtitle = stringResource(R.string.storage_access_subtitle),
                    icon = Icons.Default.Folder,
                    checked = preferencesSettings?.remoteStorage == true && permissionStates.storageGranted,
                    permission = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    } else null,
                    onRequest = {
                        if (!permissionStates.storageGranted) {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        }
                    },
                    onCheckedChanged = { checked ->
                        viewModel.saveRemoteStorageSettings(checked)
                    },
                    viewModel = viewModel
                )
            }
        }

        item {
            TextPreferenceWidget(
                title = stringResource(R.string.network_preference),
                subtitle = stringResource(R.string.network_subtitle),
                icon = Icons.Default.Wifi,
                onPreferenceClick = {
                    rootNavController.navigate(SettingsRouteScreen.NetworkScreen.route)
                }
            )   

        }

        item {
            TextPreferenceWidget(
                title = stringResource(R.string.permissions_preference),
                icon = Icons.Default.SettingsSuggest,
                onPreferenceClick = {
                    rootNavController.navigate(SettingsRouteScreen.PermissionScreen.route)
                }
            )

        }

        item {
            TextPreferenceWidget(
                title = stringResource(R.string.storage_location_preference),
                subtitle = getReadablePathFromUri(context, storageLocation),
                icon = Icons.Default.Storage,
                onPreferenceClick = {
                    try {
                        pickStorageLocation.launch(null)
                    } catch (_: ActivityNotFoundException) {
                    }
                }
            )
        }

        item {
            TextPreferenceWidget(
                title = stringResource(R.string.device_name_preference),
                subtitle = localDevice?.deviceName,
                icon = Icons.Default.PhoneAndroid,
                onPreferenceClick = {
                    showDeviceNameDialog = true
                }
            )
        }

        item {
            HorizontalDivider()
        }

        item {
            TextPreferenceWidget(
                title = stringResource(R.string.about),
                icon = Icons.Default.Info,
                onPreferenceClick = {
                    rootNavController.navigate(SettingsRouteScreen.AboutScreen.route)
                }
            )

        }

        item {
            TextPreferenceWidget(
                title = stringResource(R.string.help),
                icon = Icons.AutoMirrored.Filled.Help,
                onPreferenceClick = {
                    uriHandler.openUri("https://github.com/shrimqy/Sekia/blob/master/README.MD")
                }
            )
        }
        
        item {
            val scope = rememberCoroutineScope()
            val crashLogUtil = remember { CrashLogUtil(context) }
            
            TextPreferenceWidget(
                title = "Dump Logs",
                subtitle = "Save diagnostic logs to your device",
                icon = Icons.Default.BugReport,
                onPreferenceClick = {
                    scope.launch {
                        crashLogUtil.dumpLogs()
                    }
                }
            )
        }
    }

    // Device Name Dialog
    if (showDeviceNameDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceNameDialog = false },
            title = { Text(stringResource(R.string.rename_device)) },
            text = {
                OutlinedTextField(
                    value = deviceNameText,
                    label = { Text(stringResource(R.string.device_name_preference)) },
                    onValueChange = { deviceNameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = deviceNameText.isNotBlank(),
                    onClick = {
                        showDeviceNameDialog = false
                        viewModel.updateDeviceName(deviceNameText)
                    }
                ) { Text(stringResource(R.string.rename)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deviceNameText = localDevice?.deviceName.toString()
                        showDeviceNameDialog = false
                    }
                ) { Text(stringResource(R.string.cancel))  }
            }
        )
    }
}

@Composable
fun SwitchPermissionPrefWidget(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    permission: String?,
    onRequest: () -> Unit,
    onCheckedChanged: (Boolean) -> Unit,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()

    var permissionCheckTrigger by remember { mutableIntStateOf(0) }
    val permissionStates by viewModel.permissionStates.collectAsState()

    val hasRequestedBefore = if (permission != null) {
        viewModel.hasRequestedPermission(permission)
            .collectAsState(initial = false).value
    } else {
        false
    }

    // Recalculate permission state when trigger changes
    val permissionDenied = remember(permissionCheckTrigger) {
        permission?.let {
            ActivityCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_DENIED
        } ?: false
    }

    val showSettings = permission != null && 
        permissionDenied && 
        hasRequestedBefore && 
        !shouldShowRequestPermissionRationale(activity, permission)

    // Update UI when returning from permission request
    LaunchedEffect(permissionStates) {
        permissionCheckTrigger++
    }

    SwitchPreferenceWidget(
        title = title,
        subtitle = subtitle,
        icon = icon,
        checked = checked,
        onCheckedChanged = { isChecked ->
            if (isChecked) {
                if (permission != null && permissionDenied) {
                    if (showSettings) {
                        openAppSettings(context)
                    } else {
                        permission.let { viewModel.savePermissionRequested(it) }
                        onRequest()
                    }
                } else {
                    onRequest()
                }
            }
            
            scope.launch {
                viewModel.updatePermissionStates()
                onCheckedChanged(isChecked)
            }
        }
    )
}

@Composable
fun storageLocationPicker(viewModel: SettingsViewModel): ManagedActivityResultLauncher<Uri?, Uri?> {
    val context = LocalContext.current
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

            // For some reason InkBook devices do not implement the SAF properly. Persistable URI grants do not
            // work. However, simply retrieving the URI and using it works fine for these devices. Access is not
            // revoked after the app is closed or the device is restarted.
            // This also holds for some Samsung devices. Thus, we simply execute inside of a try-catch block and
            // ignore the exception if it is thrown.
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: SecurityException) {
                Log.e("File Picker", "$e")
            }

            viewModel.updateStorageLocation(uri.toString())
        }
    }
}

