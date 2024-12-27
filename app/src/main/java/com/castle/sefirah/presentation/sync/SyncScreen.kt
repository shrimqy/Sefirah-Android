package com.castle.sefirah.presentation.sync

import android.util.Base64
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.castle.sefirah.presentation.sync.components.DeviceItem
import com.castle.sefirah.navigation.Graph
import sefirah.domain.model.RemoteDevice
import sefirah.presentation.components.PullRefresh
import sefirah.presentation.screens.EmptyScreen
import sefirah.presentation.screens.LoadingScreen
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    modifier: Modifier = Modifier,
    rootNavController: NavHostController,
) {
    val scope = rememberCoroutineScope()
    val viewModel: SyncViewModel = hiltViewModel()
    val services by viewModel.services.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val showDialog = remember { mutableStateOf(false) }
    val selectedService = remember { mutableStateOf<RemoteDevice?>(null) }

    val context = LocalContext.current

    PullRefresh(
        refreshing = isRefreshing,
        enabled = true,
        onRefresh = { viewModel.findServices() }
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(text = "Available Devices") },
                        navigationIcon = {
                            IconButton(
                                onClick = { rootNavController.navigateUp() }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        }
                    )
                    
                    Text(
                        text = "Devices running the Windows app will appear here:",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 16.dp)
                    )
                }
            }
        ) { contentPadding ->
            when {
                isRefreshing -> LoadingScreen()
                services.isEmpty() -> { EmptyScreen(message = "No Devices found") }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.background)
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        contentPadding = contentPadding
                    ) {
                        items(services) { service ->
                            DeviceItem(
                                service = service,
                                onClick = {
                                    selectedService.value = service
                                    showDialog.value = true
                                })
                        }
                    }
                }
            }
        }
    }


    if (showDialog.value && selectedService.value != null) {
        val hashedSecret = viewModel.deriveSharedSecretCode(selectedService.value!!.publicKey)
        val derivedKey = abs(ByteBuffer.wrap(hashedSecret).order(ByteOrder.LITTLE_ENDIAN).int) % 1_000_000
        derivedKey.toString().padStart(6, '0')
        AlertDialog(
            onDismissRequest = { showDialog.value = false },
            title = { Text("Connect") },
            text = {
                Column {
                    Text("Do you want to connect to ${selectedService.value?.deviceName}?")
                    Text("Key: $derivedKey")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        Log.d("Service", "Connecting to service: ${selectedService.value}")
                        viewModel.authenticate(context, selectedService.value!!, Base64.encodeToString(hashedSecret, Base64.DEFAULT), rootNavController)
                        rootNavController.navigate(route = Graph.MainScreenGraph)
                        showDialog.value = false
                    }
                ) {
                    Text("Connect")
                }
            },
            dismissButton = {
                Button(
                    onClick = { showDialog.value = false }
                ) {
                    Text("Cancel")
                }
            }
        )

    }
}