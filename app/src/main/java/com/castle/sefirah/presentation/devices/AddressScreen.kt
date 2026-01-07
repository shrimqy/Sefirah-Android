package com.castle.sefirah.presentation.devices

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import sefirah.common.R
import sefirah.domain.model.AddressEntry
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun AddressScreen(
    rootNavController: NavHostController,
    onNavigateBack: () -> Unit
) {
    val previousEntry = rootNavController.previousBackStackEntry ?: return
    val viewModel: DeviceSettingsViewModel = hiltViewModel(previousEntry)
    
    val device by viewModel.device.collectAsState()
    val lazyListState = rememberLazyListState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage_addresses_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add IP")
            }
        }
    ) { paddingValues ->
        device?.let { currentDevice ->
            if (currentDevice.addresses.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_ip_addresses),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                AddressContent(
                    addresses = currentDevice.addresses,
                    lazyListState = lazyListState,
                    paddingValues = paddingValues,
                    onToggleEnabled = { viewModel.toggleIpEnabled(it) },
                    onRemove = { viewModel.removeIp(it) },
                    onReorder = { address, newPriority -> viewModel.updateIpPriority(address, newPriority) }
                )
            }
        }
    }

    if (showAddDialog) {
        AddAddressDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { ip ->
                viewModel.addCustomIp(ip)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddressContent(
    addresses: List<AddressEntry>,
    lazyListState: LazyListState,
    paddingValues: PaddingValues,
    onToggleEnabled: (String) -> Unit,
    onRemove: (String) -> Unit,
    onReorder: (String, Int) -> Unit
) {
    val addressesState = remember { addresses.sortedBy { it.priority }.toMutableStateList() }
    
    val reorderableState = rememberReorderableLazyListState(lazyListState, paddingValues) { from, to ->
        val item = addressesState.removeAt(from.index)
        addressesState.add(to.index, item)
        onReorder(item.address, to.index)
    }

    LaunchedEffect(addresses) {
        if (!reorderableState.isAnyItemDragging) {
            addressesState.clear()
            addressesState.addAll(addresses.sortedBy { it.priority })
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        state = lazyListState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = addressesState,
            key = { entry -> entry.address }
        ) { entry ->
            ReorderableItem(reorderableState, entry.address) {
                AddressListItem(
                    entry = entry,
                    canDelete = addressesState.size > 1,
                    onToggleEnabled = { onToggleEnabled(entry.address) },
                    onDelete = { onRemove(entry.address) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.AddressListItem(
    entry: AddressEntry,
    canDelete: Boolean,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .then(
                if (entry.isEnabled) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleEnabled)
                .padding(vertical = 12.dp)
                .padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = null,
                modifier = Modifier
                    .padding(12.dp)
                    .draggableHandle(),
                tint = MaterialTheme.colorScheme.outline
            )
            
            Spacer(Modifier.width(8.dp))
            
            Text(
                text = entry.address,
                style = MaterialTheme.typography.bodyLarge,
                color = if (entry.isEnabled) 
                    MaterialTheme.colorScheme.primary
                else 
                    MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f)
            )
            
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AddAddressDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var address by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_ip_address)) },
        text = {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text(stringResource(R.string.custom_ip_text_label)) },
                placeholder = { Text("192.168.1.100") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    if (address.isNotBlank()) {
                        onAdd(address.trim())
                    }
                },
                enabled = address.isNotBlank()
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}