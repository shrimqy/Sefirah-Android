package com.castle.sefirah.presentation.devices.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SyncDisabled
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
import coil.compose.rememberAsyncImagePainter
import sefirah.domain.model.RemoteDevice
import sefirah.presentation.util.base64ToBitmap
import sefirah.common.R

@Composable
fun DeviceListCard(
    modifier: Modifier = Modifier,
    device: RemoteDevice?,
    syncStatus: Boolean,
    onCardClick: () -> Unit,
    onSyncAction: () -> Unit,
) {
    Card(
        onClick = onCardClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(),
    ) {
        Box(Modifier.padding(16.dp)) {
            device?.let { device ->
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
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
                        
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = device.deviceName,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            
                            Text(
                                text = device.prefAddress ?: device.ipAddresses.firstOrNull() ?: stringResource(R.string.no_ip),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            if(syncStatus) {
                                Text(
                                    text = stringResource(R.string.status_connected),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        IconButton(onClick = onSyncAction) {
                            Icon(
                                imageVector = if (syncStatus) Icons.Rounded.SyncDisabled else Icons.Rounded.Sync,
                                contentDescription = if (syncStatus) "Disconnect" else "Sync",
                            )
                        }
                    }
                }
            }
        }
    }
}