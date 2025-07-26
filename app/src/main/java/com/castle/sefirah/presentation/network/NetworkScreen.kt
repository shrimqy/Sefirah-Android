package com.castle.sefirah.presentation.network

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.castle.sefirah.navigation.Graph
import com.castle.sefirah.presentation.network.components.BaseNetworkItem
import com.castle.sefirah.presentation.settings.SettingsViewModel
import com.castle.sefirah.presentation.settings.components.ListPreferenceWidget
import sefirah.common.R
import sefirah.common.util.openAppSettings
import sefirah.database.model.NetworkEntity
import sefirah.domain.model.DiscoveryMode

@Composable
fun NetworkScreen(rootNavController: NavHostController) {
    val context = LocalContext.current
    val activity = context as Activity
    val backStackState = rootNavController.currentBackStackEntryAsState().value
    val backStackEntry = remember(key1 = backStackState) { rootNavController.getBackStackEntry(Graph.MainScreenGraph) }
    val viewModel: SettingsViewModel = hiltViewModel(backStackEntry)
    val networkList = viewModel.networkList.collectAsState()
    val discoveryMode = viewModel.discoveryMode.collectAsState()
    val permissionStates = viewModel.permissionStates.collectAsState()
    val showDeleteDialog = remember { mutableStateOf(false) }
    val selectedNetwork = remember { mutableStateOf<NetworkEntity?>(null) }

    // Permission requesters
    val backgroundLocationRequester = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { viewModel.updatePermissionStates() }
    )

    val foregroundLocationRequester = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val isForegroundGranted = permissions.entries.all { it.value }
            if (isForegroundGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                backgroundLocationRequester.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            viewModel.updatePermissionStates()
            viewModel.saveDiscoveryMode(DiscoveryMode.AUTO)
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                title = { Text(stringResource(R.string.network)) },
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
                ListPreferenceWidget(
                    value = discoveryMode.value,
                    title = stringResource(R.string.device_discovery),
                    subtitle = when (discoveryMode.value) {
                        DiscoveryMode.AUTO -> stringResource(R.string.auto_discovery)
                        DiscoveryMode.ALWAYS_ON -> stringResource(R.string.always_on)
                        DiscoveryMode.DISABLED -> stringResource(R.string.disabled)
                    },
                    icon = Icons.Default.Wifi,
                    entries = mapOf(
                        DiscoveryMode.AUTO to stringResource(R.string.auto_discovery),
                        DiscoveryMode.ALWAYS_ON to stringResource(R.string.always_on),
                        DiscoveryMode.DISABLED to stringResource(R.string.disabled)
                    ),
                    onValueChange = { mode ->
                        // Check if location permission is needed for discovery modes
                        if (mode == DiscoveryMode.AUTO) {
                            val hasLocationPermission = permissionStates.value.locationGranted
                            
                            if (!hasLocationPermission) {
                                val permissionDenied = ActivityCompat.checkSelfPermission(
                                    activity, 
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_DENIED
                                
                                if (permissionDenied && !shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
                                    openAppSettings(context)
                                } else {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        // First request foreground permissions
                                        foregroundLocationRequester.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    } else {
                                        // For Android 9 and below, request only foreground location
                                        foregroundLocationRequester.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                }
                            } else {
                                viewModel.saveDiscoveryMode(mode)
                            }
                        } else {
                            viewModel.saveDiscoveryMode(mode)
                        }
                    }
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