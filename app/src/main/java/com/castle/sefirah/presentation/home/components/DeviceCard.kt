package com.castle.sefirah.presentation.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Battery2Bar
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SyncDisabled
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import sefirah.common.R
import com.castle.sefirah.navigation.SyncRoute
import sefirah.domain.model.ConnectionState
import sefirah.domain.model.RemoteDevice
import sefirah.presentation.util.base64ToBitmap

@Composable
fun DeviceCard(
    modifier: Modifier = Modifier,
    onclick: () -> Unit,
    device: RemoteDevice?,
    connectionState: ConnectionState,
    onSyncAction: () -> Unit,
    batteryLevel: Int? = null,
    navController: NavController
) {
    Card(
        onClick = { onclick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            Modifier.padding(16.dp)) {
            device?.let { device ->
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Circular profile picture
                    Image(
                        painter = rememberAsyncImagePainter(model = base64ToBitmap(device.avatar)),
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                    )
                    Spacer(Modifier.width(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                    Column(
                        verticalArrangement = Arrangement.Center
                    ) {
                            Text(
                                text = device.deviceName,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Row {
                                batteryLevel?.let { level ->
                                    Icon(
                                        imageVector = Icons.Rounded.Battery2Bar,
                                        contentDescription = "Battery Icon",
                                    )
                                    Text(text = "$level%")
                                }
                            }
                            val connectionStateText : String = when (connectionState) {
                                ConnectionState.Connected -> stringResource(R.string.status_connected)
                                ConnectionState.Connecting -> stringResource(R.string.status_connecting)
                                is ConnectionState.Disconnected -> { stringResource(R.string.status_disconnected) }
                                is ConnectionState.Error -> { stringResource(R.string.status_disconnected) }
                            }
                            Text(text = connectionStateText)
                        }
                        IconButton(onClick = onSyncAction) {
                            Icon(
                                imageVector = if (connectionState == ConnectionState.Connected) Icons.Rounded.SyncDisabled else Icons.Rounded.Sync,
                                contentDescription = if (connectionState == ConnectionState.Connected) "Stop sync" else "Start sync",
                                tint = MaterialTheme.colorScheme.surfaceTint,
                            )
                        }
                    }
                }

            } ?: EmptyPlaceholder(navController)
        }
    }
}

@Composable
fun EmptyPlaceholder(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column (
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