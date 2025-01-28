package com.castle.sefirah.presentation.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.castle.sefirah.presentation.network.components.BaseNetworkItem
import com.castle.sefirah.presentation.settings.components.SwitchPreferenceWidget
import sefirah.database.model.NetworkEntity

@Composable
fun NetworkScreen(rootNavController: NavHostController) {
    val viewModel: NetworkViewModel = hiltViewModel()
    val networkList = viewModel.networkList.collectAsState()
    val showDeleteDialog = remember { mutableStateOf(false) }
    val selectedNetwork = remember { mutableStateOf<NetworkEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                title = { Text("Network") },
                navigationIcon = {
                    IconButton(
                        onClick = { rootNavController.navigateUp() }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyColumn(
            contentPadding = contentPadding,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            item {
                SwitchPreferenceWidget(
                    title = "Allow all networks",
                    checked = true,
                    onCheckedChanged = { viewModel.updateNetworkPreference(it) },
                    subtitle = "Udp listener will be active on any connected network [Higher battery usage]",
                )
            }
            
            items(
                items = networkList.value,
                key = { it.ssid }
            ) { network ->
                NetworkItem(
                    modifier = Modifier.animateItem(),
                    network = network,
                    enabled = network.isEnabled,
                    onClickItem = { viewModel.updateNetwork(it) },
                    onLongClick = {
                        selectedNetwork.value = network
                        showDeleteDialog.value = true
                    }
                )
            }
        }
    }

    if (showDeleteDialog.value && selectedNetwork.value != null) {
        DeleteDialog(
            network = selectedNetwork.value!!,
            onDismiss = { showDeleteDialog.value = false },
            onConfirm = {
                viewModel.deleteNetwork(selectedNetwork.value!!)
                showDeleteDialog.value = false
            }
        )
    }
}

@Composable
fun NetworkItem(
    network: NetworkEntity,
    enabled: Boolean,
    onClickItem: (NetworkEntity) -> Unit,
    onLongClick: (NetworkEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseNetworkItem(
        modifier = modifier,
        network = network,
        enabled = enabled,
        onClickItem = { onClickItem(network.copy(isEnabled = !enabled)) },
        onLongClickItem = { onLongClick(network) }
    )
}

@Composable
fun DeleteDialog(
    network: NetworkEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text("Do you want to delete \"${network.ssid}\"?") },
        confirmButton = {
            Button(
                onClick = onConfirm
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}