package com.castle.sefirah.presentation.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.castle.sefirah.navigation.SyncRoute
import com.castle.sefirah.presentation.common.components.DeviceCard
import sefirah.common.R
import sefirah.domain.model.PairedDevice

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
fun SwipeableDevicesCard(
    devices: List<PairedDevice>,
    onDeviceSelected: (PairedDevice) -> Unit,
    onSyncAction: (PairedDevice) -> Unit,
    onDeviceClick: (PairedDevice) -> Unit,
    navController: NavController
) {
    val pagerState = rememberPagerState(initialPage = 0) { devices.size }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (devices.isEmpty()) {
            EmptyDeviceCard(navController)
            return@Box
        }

        // Notify when page changes
        LaunchedEffect(pagerState.currentPage) {
            val selectedDevice = devices.getOrNull(pagerState.currentPage)
            selectedDevice?.let { onDeviceSelected(it) }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            HorizontalPager(
                state = pagerState,
                pageSpacing = 16.dp
            ) { page ->
                val device = devices[page]
                key(device.deviceId) {
                    DeviceCard(
                        device = device,
                        onSyncAction = { onSyncAction(device) },
                        onClick = { onDeviceClick(device) },
                    )
                }
            }

            if (devices.size > 1) {
                FlowingPageIndicator(
                    pageCount = devices.size,
                    currentPage = pagerState.currentPage,
                    currentPageOffset = pagerState.currentPageOffsetFraction,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyDeviceCard(navController: NavController) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp, 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Button(
                    onClick = {
                        navController.navigate(route = SyncRoute.SyncScreen.route)
                    },
                ) {
                    Text(stringResource(R.string.add_device))
                }
            }
        }
    }
}
