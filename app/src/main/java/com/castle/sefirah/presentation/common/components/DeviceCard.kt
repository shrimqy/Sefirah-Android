package com.castle.sefirah.presentation.common.components

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import sefirah.common.R
import sefirah.domain.model.ConnectionState
import sefirah.domain.model.PairedDevice
import sefirah.common.util.base64ToBitmap

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeviceCard(
    device: PairedDevice,
    onClick: () -> Unit,
    onSyncAction: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(),
    ) {
        Box(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val avatarBitmap = remember(device.avatar) {
                    base64ToBitmap(device.avatar)
                }
                Image(
                    painter = rememberAsyncImagePainter(model = avatarBitmap),
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
                    val connectionStateText: String = when (device.connectionState) {
                        is ConnectionState.Connected -> stringResource(R.string.status_connected)
                        is ConnectionState.Connecting -> stringResource(R.string.status_connecting)
                        else -> stringResource(R.string.status_disconnected)
                    }
                    Text(text = connectionStateText, color = MaterialTheme.colorScheme.primary)
                }

                IconToggleButton(
                    checked = device.connectionState.isConnected,
                    onCheckedChange = { onSyncAction() },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                ) {
                    Icon(
                        painter = if (device.connectionState.isConnected) painterResource(R.drawable.ic_sync_disabled) else painterResource(R.drawable.ic_sync),
                        contentDescription = if (device.connectionState.isConnected) "Stop sync" else "Start sync",
                    )
                }
            }

        }
    }
}