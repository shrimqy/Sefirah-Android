package com.castle.sefirah.presentation.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.castle.sefirah.navigation.SyncRoute
import com.castle.sefirah.presentation.sync.components.DeviceItem
import sefirah.common.R
import sefirah.domain.model.QrCodeConnectionData
import sefirah.network.util.QrCodeParser
import sefirah.presentation.components.PullRefresh
import sefirah.presentation.screens.EmptyScreen

@Composable
fun SyncScreen(
    modifier: Modifier = Modifier,
    rootNavController: NavHostController,
) {
    val viewModel: SyncViewModel = hiltViewModel()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val savedStateHandle = rootNavController
        .currentBackStackEntry
        ?.savedStateHandle
    
    val qrConnectionData by savedStateHandle?.getStateFlow<QrCodeConnectionData?>("qr_code_result", null)
        ?.collectAsState() ?: remember { mutableStateOf(null) }

    // QR Code Connection Dialog
    qrConnectionData?.let { connectionData ->
        var customIp by remember(connectionData) { 
            mutableStateOf(connectionData.addresses.firstOrNull() ?: "")
        }
        
        AlertDialog(
            onDismissRequest = { 
                savedStateHandle?.set("qr_code_result", null as QrCodeConnectionData?)
            },
            title = { 
                Text(
                    text = "Connect to ${connectionData.deviceName}",
                    style = MaterialTheme.typography.titleLarge
                ) 
            },
            text = {
                Column {
                    connectionData.addresses.forEach { ip ->
                        Card(
                            onClick = { customIp = ip },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.outlinedCardColors(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = ip,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customIp,
                        onValueChange = { customIp = it },
                        label = { Text(stringResource(R.string.custom_ip_text_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = customIp.isNotBlank(),
                    onClick = {
                        val connectionDetails = QrCodeParser.toConnectionDetails(connectionData, customIp)
                        viewModel.connectFromQrCode(connectionDetails, rootNavController)
                        savedStateHandle?.set("qr_code_result", null as QrCodeConnectionData?)
                    }
                ) {
                    Text(stringResource(R.string.connect_button))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        savedStateHandle?.set("qr_code_result", null as QrCodeConnectionData?)
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    PullRefresh(
        refreshing = isRefreshing,
        enabled = true,
        onRefresh = { viewModel.refresh() }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = stringResource(R.string.available_devices)) },
                    navigationIcon = {
                        IconButton(onClick = { rootNavController.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                rootNavController.navigate(SyncRoute.QrCodeScannerScreen.route)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan QR Code"
                            )
                        }
                    }
                )
            }
        ) { contentPadding ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                Text(
                    text = stringResource(R.string.sync_screen_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )


                when {
                    discoveredDevices.isEmpty() -> {
                        EmptyScreen(message = stringResource(R.string.no_device))
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            items(
                                items = discoveredDevices.values.toList(),
                                key = { it.deviceName
                            }) { device ->
                                DeviceItem(
                                    device = device,
                                    onClick = {
                                        viewModel.pair(device, rootNavController)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
