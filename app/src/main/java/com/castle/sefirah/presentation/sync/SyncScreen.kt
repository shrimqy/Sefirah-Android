package com.castle.sefirah.presentation.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.castle.sefirah.presentation.sync.components.DeviceItem
import sefirah.domain.model.RemoteDevice
import sefirah.presentation.components.PullRefresh
import sefirah.presentation.screens.EmptyScreen
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import sefirah.common.R
import androidx.compose.ui.res.stringResource

enum class DialogState { NONE, CONNECTION_OPTIONS, MANUAL_IP }

@Composable

fun SyncScreen(
    modifier: Modifier = Modifier,
    rootNavController: NavHostController,
) {
    val viewModel: SyncViewModel = hiltViewModel()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val dialogState = remember { mutableStateOf(DialogState.NONE) }
    val selectedDevice = remember { mutableStateOf<RemoteDevice?>(null) }
    val key = remember { mutableStateOf("") }
    val customIp = remember { mutableStateOf("") }
    val context = LocalContext.current

    PullRefresh(
        refreshing = isRefreshing,
        enabled = true,
        onRefresh = { viewModel.findServices() }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = stringResource(R.string.available_devices)) },
                    navigationIcon = {
                        IconButton(onClick = { rootNavController.navigateUp() }) {

                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
                            items(discoveredDevices) { device ->
                                val hashedSecret = viewModel.deriveSharedSecretCode(device.publicKey)
                                val rawKey = abs(ByteBuffer.wrap(hashedSecret).order(ByteOrder.LITTLE_ENDIAN).int)
                                key.value = rawKey.toString().takeLast(6).padStart(6, '0')

                                val remoteDevice = RemoteDevice(
                                    deviceId = device.deviceId,
                                    ipAddresses = device.ipAddresses,
                                    port = device.port,
                                    publicKey = device.publicKey,
                                    deviceName = device.deviceName,
                                )

                                DeviceItem(
                                    device = device,
                                    key = key.value,
                                    onClick = {
                                        selectedDevice.value = remoteDevice
                                        dialogState.value = DialogState.CONNECTION_OPTIONS
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }


    when (dialogState.value) {
        DialogState.CONNECTION_OPTIONS -> {
            AlertDialog(
                onDismissRequest = { dialogState.value = DialogState.NONE },
                title = {
                    Text(
                        stringResource(R.string.connection_options),
                        style = MaterialTheme.typography.titleLarge
                    )

                },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.connection_options_subtitle, selectedDevice.value!!.deviceName),
                            style = MaterialTheme.typography.bodyMedium
                        )


                        Text(
                            "${stringResource(R.string.key)}: ${key.value}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(top = 8.dp)
                        )

                    }
                },
                confirmButton = {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = { dialogState.value = DialogState.MANUAL_IP },
                            ) {
                                Text(
                                    text = stringResource(R.string.manual_connect),
                                )
                            }

                            TextButton(
                                onClick = {
                                    viewModel.authenticate(
                                        context,
                                        selectedDevice.value!!.copy(prefAddress = null),
                                        rootNavController
                                    )
                                    dialogState.value = DialogState.NONE
                                },

                            ) {
                                Text(
                                    text = stringResource(R.string.auto_connect),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { dialogState.value = DialogState.NONE },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.cancel),
                                maxLines = 1
                            )
                        }
                    }
                }
            )
        }

        DialogState.MANUAL_IP -> {
            AlertDialog(
                onDismissRequest = { dialogState.value = DialogState.CONNECTION_OPTIONS },
                title = { Text(stringResource(R.string.manual_connection), style = MaterialTheme.typography.titleLarge) },
                text = {
                    Column {
                        Text(

                            text = stringResource(R.string.available_ip_addresses),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(bottom = 8.dp)

                        )
                        discoveredDevices.firstOrNull { it.deviceId == selectedDevice.value?.deviceId }
                            ?.ipAddresses?.forEach { ip ->
                                OutlinedCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    onClick = { customIp.value = ip }
                                ) {
                                    Text(
                                        ip,
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }

                        OutlinedTextField(
                            value = customIp.value,
                            onValueChange = { customIp.value = it },
                            label = { Text(stringResource(R.string.custom_ip_placeholder)) },
                            placeholder = { Text("192.168.1.100") },
                            modifier = Modifier
                                .fillMaxWidth()

                                .padding(top = 8.dp),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            selectedDevice.value = selectedDevice.value?.copy(
                                prefAddress = customIp.value.ifBlank { null }
                            )
                            viewModel.authenticate(context, selectedDevice.value!!, rootNavController)
                            dialogState.value = DialogState.NONE
                        }
                    ) {
                        Text(stringResource(R.string.connect_button))
                    }
                },

                dismissButton = {
                    Button(
                        onClick = { dialogState.value = DialogState.CONNECTION_OPTIONS }
                    ) {
                        Text(stringResource(R.string.back_button))
                    }
                }
            )

        }

        DialogState.NONE -> {} // Do nothing
    }
}
