package com.castle.sefirah.presentation.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.castle.sefirah.presentation.devices.components.DeviceListCard
import sefirah.presentation.screens.EmptyScreen

@Composable
fun DevicesScreen(
    rootNavController: NavHostController,
) {
    val devicesViewModel: DevicesViewModel = hiltViewModel()
    val deviceDetails by devicesViewModel.deviceDetails.collectAsState()
    val syncStatus by devicesViewModel.syncStatus.collectAsState()
    val lastConnected by devicesViewModel.lastConnected.collectAsState()

    if (deviceDetails.isEmpty()) {
        EmptyScreen(message = "No devices found")
    } else {
        LazyColumn(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            items(deviceDetails) { device ->
                Column(modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                ) {
                    DeviceListCard(
                        device = device,
                        syncStatus = syncStatus,
                        onSyncAction = {
                            // Handle sync action here
                            if (syncStatus) {
                                // Perform disconnect action
                            } else {
                                // Perform sync action
                            }
                        },
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}