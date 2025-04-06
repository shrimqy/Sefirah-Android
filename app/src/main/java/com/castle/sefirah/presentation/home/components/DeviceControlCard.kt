package com.castle.sefirah.presentation.home.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import sefirah.domain.model.CommandType

@Composable
fun DeviceControlCard(
    onCommandSend: (CommandType) -> Unit,
    onLongClick: (CommandType) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DeviceControlButton(
                onClick = { onCommandSend(CommandType.Lock) },
                onLongClick = { onLongClick(CommandType.Lock) },
                icon = Icons.Default.Lock,
                contentDescription = "Lock Device"
            )

            DeviceControlButton(
                onClick = { onCommandSend(CommandType.Hibernate) },
                onLongClick = { onLongClick(CommandType.Hibernate) },
                icon = Icons.Default.Schedule,
                contentDescription = "Hibernate Device"
            )


            DeviceControlButton(
                onClick = { onCommandSend(CommandType.Logoff) },
                onLongClick = { onLongClick(CommandType.Logoff) },
                icon = Icons.AutoMirrored.Filled.Logout,
                contentDescription = "Logoff Device"
            )


            DeviceControlButton(
                onClick = { onCommandSend(CommandType.Restart) },
                onLongClick = { onLongClick(CommandType.Restart) },
                icon = Icons.Default.RestartAlt,
                contentDescription = "Restart Device"
            )

            DeviceControlButton(
                onClick = { onCommandSend(CommandType.Shutdown) },
                onLongClick = { onLongClick(CommandType.Shutdown) },
                icon = Icons.Default.PowerSettingsNew,
                contentDescription = "Shutdown Device"
            )
        }
    }
}

@Composable
fun DeviceControlButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.surfaceTint,
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        )
    )
}


@Composable
fun TimerDialog(
    hours: String,
    minutes: String,
    seconds: String,
    onHoursChange: (String) -> Unit,
    onMinutesChange: (String) -> Unit,
    onSecondsChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set timer") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = hours,
                        onValueChange = { onHoursChange(it.filter { char -> char.isDigit() }) },
                        label = { Text("Hours") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { onMinutesChange(it.filter { char -> char.isDigit() }) },
                        label = { Text("Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = seconds,
                        onValueChange = { onSecondsChange(it.filter { char -> char.isDigit() }) },
                        label = { Text("Seconds") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
