package com.castle.sefirah.presentation.network

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import sefirah.presentation.components.padding
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.castle.sefirah.navigation.Graph
import com.castle.sefirah.presentation.settings.SettingsViewModel
import com.castle.sefirah.presentation.settings.components.SwitchPreferenceWidget
import sefirah.common.R
import sefirah.common.util.openAppSettings
import sefirah.database.model.NetworkEntity

@Composable
fun TrustedNetworkScreen(rootNavController: NavHostController) {
    val context = LocalContext.current
    val activity = context as Activity
    val backStackState = rootNavController.currentBackStackEntryAsState().value
    val backStackEntry = remember(key1 = backStackState) { rootNavController.getBackStackEntry(Graph.MainScreenGraph) }
    val viewModel: SettingsViewModel = hiltViewModel(backStackEntry)
    val networkList = viewModel.networkList.collectAsState()
    val trustAllNetworks = viewModel.trustAllNetworks.collectAsState()
    val permissionStates = viewModel.permissionStates.collectAsState()
    val currentWifiSsid = viewModel.currentWifiSsid.collectAsState()
    val showDeleteDialog = remember { mutableStateOf(false) }
    val selectedNetwork = remember { mutableStateOf<NetworkEntity?>(null) }

    val locationPermission = Manifest.permission.ACCESS_FINE_LOCATION
    val hasRequestedBefore = viewModel.hasRequestedPermission(locationPermission).collectAsState(initial = false).value

    val backgroundLocationRequester = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { 
            viewModel.updatePermissionStates()
            // After background permission is granted, disable trust all networks
            if (it) {
                viewModel.saveTrustAllNetworks(false)
            }
        }
    )

    val foregroundLocationRequester = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val isForegroundGranted = permissions.entries.all { it.value }
            viewModel.updatePermissionStates()
            if (isForegroundGranted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Request background location if foreground is granted
                    backgroundLocationRequester.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else {
                    // For Android 9 and below, no background permission needed
                    viewModel.saveTrustAllNetworks(false)
                }
            }
        }
    )

    val shouldShowAddButton = !trustAllNetworks.value && 
        currentWifiSsid.value != null && 
        !networkList.value.any { it.ssid == currentWifiSsid.value }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                title = { Text(stringResource(R.string.network_preference)) },
                navigationIcon = {
                    IconButton(
                        onClick = { rootNavController.navigateUp() }
                    ) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            if (shouldShowAddButton) {
                Button(
                    onClick = { viewModel.addCurrentNetworkAsTrusted() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.padding.medium)
                ) {
                    Text("Add \"${currentWifiSsid.value}\"")
                }
            }
        }
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
                    title = stringResource(R.string.trust_all_networks),
                    icon = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.ic_wifi),
                    checked = trustAllNetworks.value,
                    onCheckedChanged = { enabled ->
                        if (enabled) {
                            viewModel.saveTrustAllNetworks(true)
                        } else {
                            val hasLocationPermission = permissionStates.value.locationGranted

                            if (!hasLocationPermission) {

                                val currentPermissionDenied = ActivityCompat.checkSelfPermission(
                                    activity,
                                    locationPermission
                                ) == PackageManager.PERMISSION_DENIED
                                
                                val currentCanShowRationale = shouldShowRequestPermissionRationale(
                                    activity,
                                    locationPermission
                                )
                                
                                val currentShowSettings = currentPermissionDenied && hasRequestedBefore && !currentCanShowRationale
                                
                                if (currentShowSettings) {
                                    openAppSettings(context)
                                } else {
                                    viewModel.savePermissionRequested(locationPermission)
                                    foregroundLocationRequester.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            } else {
                                viewModel.saveTrustAllNetworks(false)
                            }
                        }
                    }
                )
            }
            
            if (!trustAllNetworks.value) {
                items(
                    items = networkList.value,
                    key = { it.ssid }
                ) { network ->
                    Text(
                        text = network.ssid,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedNetwork.value = network
                                showDeleteDialog.value = true
                            }
                            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.medium),
                    )
                }
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
fun DeleteDialog(
    network: NetworkEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text("${stringResource(R.string.delete_dialog)} \"${network.ssid}\"?") },
        confirmButton = {
            Button(
                onClick = onConfirm
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}