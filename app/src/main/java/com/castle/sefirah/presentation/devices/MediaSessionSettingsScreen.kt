package com.castle.sefirah.presentation.devices

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.castle.sefirah.presentation.settings.components.SwitchPreferenceWidget
import sefirah.common.R

@Composable
fun MediaSessionSettingsScreen(
    rootNavController: NavHostController,
    onNavigateBack: () -> Unit,
) {
    val previousEntry = rootNavController.previousBackStackEntry ?: return
    val viewModel: DeviceSettingsViewModel = hiltViewModel(previousEntry)
    val preferences by viewModel.preferences.collectAsState()
    val permissionStates by viewModel.permissionStates.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            R.string.preference_settings_with_name,
                            stringResource(R.string.media_session_preference),
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                SwitchPermissionPrefWidget(
                    title = stringResource(R.string.media_session_notification_preference),
                    subtitle = stringResource(R.string.media_session_notification_subtitle),
                    checkedIcon = ImageVector.vectorResource(R.drawable.ic_auto_read_play_fill),
                    uncheckedIcon = ImageVector.vectorResource(R.drawable.ic_auto_read_play),
                    granted = permissionStates.notificationGranted,
                    checked = preferences.mediaSessionNotification && permissionStates.notificationGranted,
                    permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.POST_NOTIFICATIONS
                    } else null,
                    onRequest = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            // Permission handled by system
                        }
                    },
                    onCheckedChanged = { checked ->
                        viewModel.saveMediaSessionNotificationSettings(checked)
                    },
                    viewModel = viewModel,
                )
            }

            item {
                SwitchPreferenceWidget(
                    title = stringResource(R.string.remote_volume_control_preference),
                    subtitle = stringResource(R.string.remote_volume_control_subtitle),
                    checkedIcon = ImageVector.vectorResource(R.drawable.ic_adjust_fill),
                    uncheckedIcon = ImageVector.vectorResource(R.drawable.ic_adjust),
                    checked = preferences.remoteVolumeControl,
                    onCheckedChanged = { checked ->
                        viewModel.saveRemoteVolumeControlSettings(checked)
                    },
                )
            }
        }
    }
}
