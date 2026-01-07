package com.castle.sefirah.presentation.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.castle.sefirah.presentation.common.components.DeviceCard
import com.castle.sefirah.presentation.main.ConnectionViewModel
import sefirah.common.R
import sefirah.presentation.screens.EmptyScreen

@Composable
fun DeviceScreen(
    rootNavController: NavHostController,
    searchQuery: String = "",
    connectionViewModel: ConnectionViewModel
) {
    val devices by connectionViewModel.pairedDevices.collectAsState()

    val filteredDevices by remember(devices, searchQuery) {
        derivedStateOf {
            if (searchQuery.isEmpty()) devices
            else devices.filter { device ->
                device.deviceName.contains(searchQuery, ignoreCase = true) ||
                device.addresses.any { it.address.contains(searchQuery, ignoreCase = true) }
            }
        }
    }

    if (filteredDevices.isEmpty()) {
        EmptyScreen(message = stringResource(R.string.no_device))
    } else {
        LazyColumn(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(
                items = filteredDevices,
                key = { it.deviceId }
            ) { device ->
                DeviceCard(
                    device = device,
                    onSyncAction = { connectionViewModel.connect(device) },
                    onClick = {
                        rootNavController.navigate(route = "device?deviceId=${device.deviceId}")
                    }
                )
            }
        }
    }
}