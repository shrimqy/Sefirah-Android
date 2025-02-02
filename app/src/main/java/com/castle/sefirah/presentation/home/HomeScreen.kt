package com.castle.sefirah.presentation.home

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.castle.sefirah.navigation.Graph
import com.castle.sefirah.presentation.home.components.DeviceCard
import com.castle.sefirah.presentation.home.components.MediaPlaybackCard
import com.castle.sefirah.presentation.home.components.VolumeSlider
import com.castle.sefirah.presentation.main.ConnectionViewModel
import sefirah.domain.model.ConnectionState

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
    val playbackData by viewModel.playbackData.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        item(
            key = "devices",
            contentType = { 0 }
        ) {
            Column(modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
            ) {
                DeviceCard(
                    device = deviceDetails,
                    onSyncAction = { connectionViewModel.toggleSync(connectionState == ConnectionState.Disconnected()) },
                    connectionState = connectionState,
                    navController = rootNavController
                )
            }
        }

        item(key = "media_playback") {
            playbackData?.volume?.let {
                MediaPlaybackCard(
                    playbackData = playbackData,
                    onPlayPauseClick = { viewModel.onPlayPause() },
                    onSkipNextClick = { viewModel.onNext() },
                    onSkipPreviousClick = { viewModel.onPrevious() }
                )
            }
        }

        item(key = "volume_slider") {
            Log.d("playback", playbackData.toString())
            playbackData?.volume?.let { volume -> VolumeSlider(volume = volume, onVolumeChange = { viewModel.onVolumeChange(it) }) }
        }
    }
}

