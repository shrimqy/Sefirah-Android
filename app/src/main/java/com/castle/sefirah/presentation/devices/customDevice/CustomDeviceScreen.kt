package com.castle.sefirah.presentation.devices.customDevice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.castle.sefirah.presentation.devices.deviceDetails.AddCustomIpDialog
import com.castle.sefirah.presentation.settings.components.TextPreferenceWidget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDeviceScreen(
    rootNavController: NavHostController,
    viewModel: CustomDeviceViewModel = hiltViewModel()
) {
    val customIps = viewModel.customIps.collectAsState()
    val customIpsStatus = viewModel.customIpsStatus.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var newIpAddress by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                title = { Text("Custom Device") },
                navigationIcon = {
                    IconButton(onClick = { rootNavController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "Add new address")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(customIps.value) { ip ->
                val status = customIpsStatus.value[ip]
                TextPreferenceWidget(
                    title = ip,
                    subtitle = when {
                        status == null -> "Checking..."
                        status.isReachable -> "Pinged in ${status.responseTime}ms"
                        else -> "Not reachable"
                    },
                    widget = {
                        IconButton(onClick = { viewModel.deleteCustomIp(ip) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }

    if (showAddDialog) {
        AddCustomIpDialog(
            customIp = newIpAddress,
            onCustomIpChange = { newIpAddress = it },
            onDismiss = {
                showAddDialog = false
                newIpAddress = ""
            },
            onConfirm = {
                viewModel.addCustomIp(newIpAddress)
                showAddDialog = false
                newIpAddress = ""
            }
        )
    }
}