package com.castle.sefirah.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.castle.sefirah.presentation.home.components.AudioDeviceBottomSheet
import com.castle.sefirah.presentation.home.components.DeviceControlCard
import com.castle.sefirah.presentation.home.components.MediaCard
import com.castle.sefirah.presentation.home.components.SelectedAudioDevice
import com.castle.sefirah.presentation.home.components.SwipeableDevicesCard
import com.castle.sefirah.presentation.main.ConnectionViewModel
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    rootNavController: NavHostController,
    connectionViewModel: ConnectionViewModel
) {
    val viewModel: HomeViewModel = hiltViewModel()

    val pairedDevicesList by connectionViewModel.pairedDevices.collectAsState()

    val activeSessions by viewModel.activeSessions.collectAsState()
    val audioDevices by viewModel.audioDevices.collectAsState()
    val actions by viewModel.actions.collectAsState()

    // Bottom sheet state
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = SheetState(
            skipPartiallyExpanded = true,
            initialValue = SheetValue.Hidden,
            skipHiddenState = false,
            positionalThreshold = { 0.5f },
            velocityThreshold = { 0.5f },
        )
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetContent = {
            AudioDeviceBottomSheet(
                audioDevices = audioDevices,
                onVolumeChange = viewModel::onVolumeChange,
                onDefaultDeviceSelected = viewModel::setDefaultDevice,
                onToggleMute = viewModel::toggleMute
            )
        },
        content = {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "devices") {
                    SwipeableDevicesCard(
                        devices = pairedDevicesList,
                        onDeviceSelected = { device ->
                            connectionViewModel.selectDevice(device)
                        },
                        onSyncAction = { device ->
                            val deviceConnectionState = device.connectionState
                            connectionViewModel.toggleSync(!deviceConnectionState.isConnectedOrConnecting)
                        },
                        onDeviceClick = { device ->
                            rootNavController.navigate(route = "device?deviceId=${device.deviceId}")
                        },
                        navController = rootNavController
                    )
                }

                if (actions.isNotEmpty()) {
                    item(key = "device_control") {
                        DeviceControlCard(
                            actions = actions,
                            onActionClick = { action ->
                                viewModel.sendMessageToSelectedDevice(action)
                            }
                        )
                    }
                }

                if (activeSessions.isNotEmpty()) {
                    item(key = "media_sessions") {
                        MediaCard(
                            sessions = activeSessions,
                            onPlayPauseClick = viewModel::onPlayPause,
                            onSkipNextClick = viewModel::onNext,
                            onSkipPreviousClick = viewModel::onPrevious,
                            onSeekChange = viewModel::onSeek
                        )
                    }
                }

                val selectedDevice = audioDevices.find { it.isSelected } ?: audioDevices.firstOrNull()
                selectedDevice?.let {
                    item(key = "selected_audio_device") {
                        SelectedAudioDevice(
                            device = selectedDevice,
                            onClick = {
                                scope.launch {
                                    if (scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden)
                                        scaffoldState.bottomSheetState.expand()
                                    else
                                        scaffoldState.bottomSheetState.hide()
                                }
                            },
                            onVolumeChange = viewModel::onVolumeChange,
                            toggleMute = viewModel::toggleMute,
                        )
                    }
                }
            }
        }
    )
}
