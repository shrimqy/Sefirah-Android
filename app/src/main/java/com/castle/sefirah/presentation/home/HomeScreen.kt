package com.castle.sefirah.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.castle.sefirah.presentation.home.components.AudioDeviceBottomSheet
import com.castle.sefirah.presentation.home.components.DeviceCard
import com.castle.sefirah.presentation.home.components.DeviceControlCard
import com.castle.sefirah.presentation.home.components.MediaCard
import com.castle.sefirah.presentation.home.components.SelectedAudioDevice
import com.castle.sefirah.presentation.main.ConnectionViewModel
import kotlinx.coroutines.launch
import sefirah.domain.model.ConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    rootNavController: NavHostController,
    connectionViewModel: ConnectionViewModel
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val deviceDetails by connectionViewModel.deviceDetails.collectAsState()
    val connectionState by connectionViewModel.connectionState.collectAsState()

    // Collect the new state flows
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
            density = LocalDensity.current
        )
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetContent = {
            AudioDeviceBottomSheet(
                audioDevices = audioDevices,
                onVolumeChange = viewModel::onVolumeChange,
                onDefaultDeviceSelected = { device ->
                    viewModel.setDefaultDevice(device)
                }
            )
        },
        content = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                item(key = "devices") {
                    DeviceCard(
                        onclick = { deviceDetails?.deviceId?.let {
                            rootNavController.navigate(route = "device?deviceId=${it}")
                        } },
                        device = deviceDetails,
                        onSyncAction = { connectionViewModel.toggleSync(!connectionState.isConnectedOrConnecting) },
                        connectionState = connectionState,
                        navController = rootNavController
                    )
                }

                if (connectionState == ConnectionState.Connected && actions.isNotEmpty()) {
                    item(key = "device_control") {
                        DeviceControlCard(
                            actions = actions,
                            onActionClick = { action ->
                                viewModel.sendAction(action)
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
                            toggleMute = { viewModel.toggleMute(it) },
                        )
                    }
                }
            }
        }
    )
}
