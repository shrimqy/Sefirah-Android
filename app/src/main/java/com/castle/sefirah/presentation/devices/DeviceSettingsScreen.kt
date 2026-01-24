package com.castle.sefirah.presentation.devices

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.rememberAsyncImagePainter
import com.castle.sefirah.presentation.settings.components.SwitchPreferenceWidget
import com.castle.sefirah.presentation.settings.components.TextPreferenceWidget
import kotlinx.coroutines.launch
import sefirah.clipboard.ClipboardListener
import sefirah.common.R
import sefirah.common.util.isAccessibilityServiceEnabled
import sefirah.common.util.isNotificationListenerEnabled
import sefirah.common.util.openAppSettings
import sefirah.domain.model.PairedDevice
import sefirah.common.util.base64ToBitmap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
fun DeviceSettingsScreen(
    deviceId: String,
    viewModel: DeviceSettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToAddressScreen: () -> Unit
) {
    val device by viewModel.device.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val permissionStates by viewModel.permissionStates.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.device_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.removeDevice()
                        onNavigateBack()
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete_fill),
                            contentDescription = "Delete Device"
                        )
                    }
                }
            )
        }
    ) { padding ->
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        
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

        // Permission requesters
        val telephonyPermissionRequester = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = {
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

        device?.let { currentDevice ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Top
            ) {
                item { 
                    DeviceHeader(device = currentDevice)
                }
                
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                
                item {
                    TextPreferenceWidget(
                        title = stringResource(R.string.manage_addresses_title),
                        subtitle = stringResource(R.string.ip_address_subtitle),
                        icon = ImageVector.vectorResource(R.drawable.ic_wifi),
                        onPreferenceClick = onNavigateToAddressScreen
                    )
                }

            item {
                SwitchPermissionPrefWidget(
                    title = stringResource(R.string.clipboard_sync_preference),
                    subtitle = stringResource(R.string.clipboard_sync_subtitle),
                    filledIcon = ImageVector.vectorResource(R.drawable.ic_content_copy_fill),
                    outlinedIcon = ImageVector.vectorResource(R.drawable.ic_content_copy),
                    checked = preferences.clipboardSync && permissionStates.accessibilityGranted,
                    permission = null,
                    onRequest = {
                        if(!isAccessibilityServiceEnabled(context, "${context.packageName}/${ClipboardListener::class.java.canonicalName}") ) {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                    },
                    onCheckedChanged = { viewModel.saveClipboardSyncSettings(it) },
                    viewModel = viewModel
                )
            }

            item {
                SwitchPermissionPrefWidget(
                    title = stringResource(R.string.image_clipboard_preference),
                    subtitle = stringResource(R.string.image_clipboard_subtitle),
                    filledIcon = ImageVector.vectorResource(R.drawable.ic_file_copy_fill),
                    outlinedIcon = ImageVector.vectorResource(R.drawable.ic_file_copy),
                    checked = preferences.imageClipboard,
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
                    filledIcon = ImageVector.vectorResource(R.drawable.ic_notifications_fill),
                    outlinedIcon = ImageVector.vectorResource(R.drawable.ic_notifications),
                    checked = preferences.notificationSync && permissionStates.notificationListenerGranted,
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
                    filledIcon = ImageVector.vectorResource(R.drawable.ic_chat_fill),
                    outlinedIcon = ImageVector.vectorResource(R.drawable.ic_chat),
                    checked = preferences.messageSync && permissionStates.smsPermissionGranted,
                    permission = Manifest.permission_group.SMS,
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
                    filledIcon = ImageVector.vectorResource(R.drawable.ic_play_circle_fill),
                    outlinedIcon = ImageVector.vectorResource(R.drawable.ic_play_circle),
                    checked = preferences.mediaSession && permissionStates.notificationGranted,
                    permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.POST_NOTIFICATIONS
                    } else null,
                    onRequest = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            // Permission handled by system
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
                        filledIcon = ImageVector.vectorResource(R.drawable.ic_folder_fill),
                        outlinedIcon = ImageVector.vectorResource(R.drawable.ic_folder),
                        checked = preferences.remoteStorage && permissionStates.storageGranted,
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
            }
        }
    }

}

@Composable
private fun DeviceAvatar(avatar: String?) {
    val bitmap = remember(avatar) {
        base64ToBitmap(avatar)
    }
    
    val painter = rememberAsyncImagePainter(model = bitmap)
    
    Image(
        painter = painter,
        contentDescription = "Profile Picture",
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape)
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            )
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
    )
}

@Composable
private fun DeviceHeader(device: PairedDevice) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DeviceAvatar(avatar = device.avatar)

            Spacer(Modifier.height(16.dp))

            Text(
                text = device.deviceName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(4.dp))
            
            if (device.connectionState.isConnected && device.address != null) {
                DeviceStatusInfo(
                    icon = painterResource(R.drawable.ic_wifi),
                    text = device.address!!,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                device.lastConnected?.let {
                    DeviceStatusInfo(
                        icon = Icons.Default.AccessTime,
                        text = convertTimestampToDate(it),
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceStatusInfo(
    icon: androidx.compose.ui.graphics.painter.Painter,
    text: String,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

@Composable
private fun DeviceStatusInfo(
    icon: ImageVector,
    text: String,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

@Composable
fun SwitchPermissionPrefWidget(
    title: String,
    subtitle: String,
    filledIcon: ImageVector,
    outlinedIcon: ImageVector,
    checked: Boolean,
    permission: String?,
    onRequest: () -> Unit,
    onCheckedChanged: (Boolean) -> Unit,
    viewModel: DeviceSettingsViewModel
) {
    val context = LocalContext.current
    val activity = context as Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val hasRequestedBefore = permission?.let {
        viewModel.hasRequestedPermission(it)
            .collectAsState(initial = false).value
    } ?: false

    var canShowRationale by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner.lifecycle, permission) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                permission?.let { 
                    canShowRationale = shouldShowRequestPermissionRationale(activity, it) 
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val showSettings = permission != null && !checked && hasRequestedBefore && !canShowRationale

    val icon = if (checked) filledIcon else outlinedIcon

    SwitchPreferenceWidget(
        title = title,
        subtitle = subtitle,
        icon = icon,
        checked = checked,
        onCheckedChanged = { isChecked ->
            if (isChecked) {
                if (permission != null && !checked) {
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

private fun convertTimestampToDate(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date(timestamp))
}