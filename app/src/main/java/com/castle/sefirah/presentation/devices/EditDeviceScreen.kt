package com.castle.sefirah.presentation.devices

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import sefirah.database.model.RemoteDeviceEntity
import sefirah.presentation.util.base64ToBitmap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@JvmOverloads
@Composable
fun EditDeviceScreen(
    deviceId: String,
    viewModel: EditDeviceViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val device by viewModel.device.collectAsState()
    var showCustomIpDialog by remember { mutableStateOf(false) }
    var customIp by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            EditDeviceTopBar(
                onNavigateBack = onNavigateBack,
                onDeleteDevice = {
                    viewModel.removeDevice()
                    onNavigateBack()
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { DeviceHeader(device = device) }
            item { IpAddressSection(
                device = device,
                selectedIp = device?.prefAddress,
                onIpSelected = viewModel::setPreferredIp,
                onAddCustomIpClick = { showCustomIpDialog = true }
            ) }
            item { NetworksSection(viewModel = viewModel) }
        }
    }

    if (showCustomIpDialog) {
        AddCustomIpDialog(
            customIp = customIp,
            onCustomIpChange = { customIp = it },
            onDismiss = {
                showCustomIpDialog = false
                customIp = ""
            },
            onConfirm = {
                viewModel.addCustomIp(customIp)
                showCustomIpDialog = false
                customIp = ""
            }
        )
    }
}

@Composable
private fun EditDeviceTopBar(
    onNavigateBack: () -> Unit,
    onDeleteDevice: () -> Unit
) {
    TopAppBar(
        title = { Text("Device Details") },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
        },
        actions = {
            IconButton(onClick = onDeleteDevice) {
                Icon(Icons.Default.Delete, "Delete Device")
            }
        }
    )
}

@Composable
private fun DeviceAvatar(avatar: String?) {
    val bitmap = remember(avatar) {
        base64ToBitmap(avatar)
    }
    
    val painter = rememberAsyncImagePainter(model = bitmap)
    
    Image(
        painter = painter,
        contentDescription = "Profile Picture",
        modifier = Modifier
            .size(100.dp)
            .clip(CircleShape)
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            )
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
    )
}

@Composable
private fun DeviceHeader(device: RemoteDeviceEntity?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DeviceAvatar(avatar = device?.avatar)

            Spacer(Modifier.height(16.dp))

            Text(
                text = device?.deviceName ?: "",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = device?.deviceId ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            device?.lastConnected?.let {
                Spacer(Modifier.height(4.dp))
                LastConnectedInfo(timestamp = it)
            }
        }
    }
}

@Composable
private fun LastConnectedInfo(timestamp: Long) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AccessTime,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Last connected: ${convertTimestampToDate(timestamp)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun IpAddressSection(
    device: RemoteDeviceEntity?,
    selectedIp: String?,
    onIpSelected: (String) -> Unit,
    onAddCustomIpClick: () -> Unit
) {
    Text(
        "IP Addresses",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            device?.ipAddresses?.forEach { ip ->
                val isSelected = ip == selectedIp
                IpAddressItem(
                    ip = ip,
                    isSelected = isSelected,
                    onSelect = { onIpSelected(ip) }
                )
            }

            OutlinedButton(
                onClick = onAddCustomIpClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Add IP Address")
            }
        }
    }
}

@Composable
private fun IpAddressItem(
    ip: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(MaterialTheme.shapes.small)
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary 
                       else MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            )
            .clickable(onClick = onSelect),
        color = if (isSelected) 
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
        else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Lan,
                    contentDescription = null,
                    tint = if (isSelected) 
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = ip,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).animateContentSize()
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun NetworksSection(
    viewModel: EditDeviceViewModel = hiltViewModel()
) {
    val associatedNetworks by viewModel.associatedNetworks.collectAsState()
    
    Text(
        "Associated Networks",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(8.dp)) {
            if (associatedNetworks.isEmpty()) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = "No networks associated",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.Wifi,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            } else {
                associatedNetworks.forEach { network ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = network.ssid,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.Wifi,
                                contentDescription = null,
                                tint = if (network.isEnabled) 
                                    MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            IconButton(
                                onClick = { viewModel.removeNetworkFromDevice(network.ssid) }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove Network",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AddCustomIpDialog(
    customIp: String,
    onCustomIpChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add IP Address") },
        text = {
            Column {
                OutlinedTextField(
                    value = customIp,
                    onValueChange = onCustomIpChange,
                    label = { Text("IP Address") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Example: 192.168.1.100",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = customIp.isNotBlank(),
                onClick = onConfirm
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) { Text("Cancel") }
        }
    )
}

private fun convertTimestampToDate(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(Date(timestamp))
}