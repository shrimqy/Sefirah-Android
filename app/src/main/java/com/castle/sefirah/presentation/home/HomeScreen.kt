package com.castle.sefirah.presentation.home

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.castle.sefirah.presentation.home.components.DeviceCard
import com.castle.sefirah.presentation.home.components.MediaPlaybackCard
import com.castle.sefirah.presentation.home.components.VolumeSlider

@Composable
fun HomeScreen(
    navController: NavController
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val deviceDetails by viewModel.deviceDetails.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val context = LocalContext.current
    val playbackData by viewModel.playbackData.collectAsState()

    LazyColumn(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
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
                    onSyncAction = { viewModel.toggleSync(!syncStatus) },
                    syncStatus = syncStatus,
                    navController = navController
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

