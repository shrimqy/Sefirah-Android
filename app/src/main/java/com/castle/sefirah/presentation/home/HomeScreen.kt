package com.castle.sefirah.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.castle.sefirah.navigation.Graph
import com.castle.sefirah.presentation.home.components.AudioDeviceBottomSheet
import com.castle.sefirah.presentation.home.components.DeviceCard
import com.castle.sefirah.presentation.home.components.MediaCard
import com.castle.sefirah.presentation.home.components.SelectedAudioDevice
import com.castle.sefirah.presentation.main.ConnectionViewModel
import kotlinx.coroutines.launch
import sefirah.domain.model.ConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    rootNavController: NavHostController,
) {
    val backStackState = rootNavController.currentBackStackEntryAsState().value
    val backStackEntry = remember(key1 = backStackState) { rootNavController.getBackStackEntry(Graph.MainScreenGraph) }
    val connectionViewModel: ConnectionViewModel = hiltViewModel(backStackEntry)

    val viewModel: HomeViewModel = hiltViewModel()
    val deviceDetails by connectionViewModel.deviceDetails.collectAsState()
    val connectionState by connectionViewModel.connectionState.collectAsState()

    // Collect the new state flows
    val activeSessions by viewModel.activeSessions.collectAsState()
    val audioDevices by viewModel.audioDevices.collectAsState()

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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        DeviceCard(
                            onclick = { deviceDetails?.deviceId?.let {
                                rootNavController.navigate(route = "device?deviceId=${it}")
                            } },
                            device = deviceDetails,
                            onSyncAction = { connectionViewModel.toggleSync(connectionState == ConnectionState.Disconnected()) },
                            connectionState = connectionState,
                            navController = rootNavController
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

                // Show audio device summary card if devices are available
                if (audioDevices.isNotEmpty()) {
                    item(key = "audio_device_summary") {
                        SelectedAudioDevice(
                            audioDevices = audioDevices,
                            onClick = {
                                scope.launch {
                                    if (scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden)
                                        scaffoldState.bottomSheetState.expand()
                                    else
                                        scaffoldState.bottomSheetState.hide()
                                }
                            },
                            onVolumeChange = viewModel::onVolumeChange
                        )
                    }
                }
            }
        }
    )
}

