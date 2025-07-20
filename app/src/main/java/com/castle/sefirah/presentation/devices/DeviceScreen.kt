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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.castle.sefirah.presentation.devices.components.DeviceListCard
import com.castle.sefirah.presentation.main.ConnectionViewModel
import sefirah.common.R
import sefirah.presentation.screens.EmptyScreen

@Composable
fun DeviceScreen(
    rootNavController: NavHostController,
    searchQuery: String = "",
    connectionViewModel: ConnectionViewModel
) {
    val devicesViewModel: DeviceViewModel = hiltViewModel()

    // Update search query in ViewModel
    LaunchedEffect(searchQuery) {
        devicesViewModel.updateSearchQuery(searchQuery)
    }

    val deviceDetails by devicesViewModel.deviceDetails.collectAsState()
    val syncStatus by devicesViewModel.syncStatus.collectAsState()

    if (deviceDetails.isEmpty()) {
        EmptyScreen(message = stringResource(R.string.no_device))
    } else {
        LazyColumn(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            items(
                items = deviceDetails,
                key = { it.deviceId }
            ) { device ->
                Column(modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                ) {
                    DeviceListCard(
                        device = device,
                        syncStatus = syncStatus,
                        onSyncAction = {
                            connectionViewModel.connect(device)
                        },
                        modifier = Modifier.padding(bottom = 8.dp),
                        onCardClick = {
                            rootNavController.navigate(route = "device?deviceId=${device.deviceId}")
                        }
                    )
                }
            }
        }
    }
}