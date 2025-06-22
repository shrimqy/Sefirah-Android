package com.castle.sefirah.presentation.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import sefirah.domain.model.ActionType
import sefirah.domain.model.CommandType
import sefirah.presentation.components.Button
import sefirah.presentation.components.TextButton

@Composable
fun DeviceControlCard(
    onCommandSend: (ActionType) -> Unit,
    onLongClick: (ActionType) -> Unit,
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
                onClick = { onCommandSend(ActionType.Lock) },
                onLongClick = { onLongClick(ActionType.Lock) },
                icon = Icons.Default.Lock,
            )

            DeviceControlButton(
                onClick = { onCommandSend(ActionType.Hibernate) },
                onLongClick = { onLongClick(ActionType.Hibernate) },
                icon = Icons.Default.Schedule,
            )


            DeviceControlButton(
                onClick = { onCommandSend(ActionType.Logoff) },
                onLongClick = { onLongClick(ActionType.Logoff) },
                icon = Icons.AutoMirrored.Filled.Logout,
            )


            DeviceControlButton(
                onClick = { onCommandSend(ActionType.Restart) },
                onLongClick = { onLongClick(ActionType.Restart) },
                icon = Icons.Default.RestartAlt,
            )

            DeviceControlButton(
                onClick = { onCommandSend(ActionType.Shutdown) },
                onLongClick = { onLongClick(ActionType.Shutdown) },
                icon = Icons.Default.PowerSettingsNew,
            )
        }
    }
}

@Composable
fun RowScope.DeviceControlButton(
    icon: ImageVector,
    color: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    TextButton (
        onClick = onClick,
        modifier = Modifier.weight(1f),
        onLongClick = onLongClick,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}


@Composable
fun TimerDialog(
    title: String,
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
        title = { Text("Set timer for $title") },
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
