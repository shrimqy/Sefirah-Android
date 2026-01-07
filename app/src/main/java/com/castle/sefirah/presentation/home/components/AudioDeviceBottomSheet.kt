package com.castle.sefirah.presentation.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import sefirah.domain.model.AudioDevice
import sefirah.domain.model.AudioMessageType

@Composable
fun AudioDeviceBottomSheet(
    audioDevices: List<AudioDevice>,
    onVolumeChange: (AudioDevice, Float) -> Unit,
    onDefaultDeviceSelected: (AudioDevice) -> Unit,
    onToggleMute: (AudioDevice) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            items(audioDevices) { device ->
                AudioDeviceItem(
                    device = device,
                    onVolumeChange = onVolumeChange,
                    onDeviceSelected = onDefaultDeviceSelected,
                    onToggleMute = onToggleMute
                )
            }
        }
    }
}

@Composable
fun SelectedAudioDevice(
    device: AudioDevice,
    onClick: () -> Unit,
    toggleMute: (AudioDevice) -> Unit,
    onVolumeChange: (AudioDevice, Float) -> Unit,
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        onClick = { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = device.deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Show all devices",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            VolumeSlider(
                volume = device.volume * 100,
                onVolumeChange = { newVolume->
                    onVolumeChange(device, newVolume / 100f)
                },
                isMuted = device.isMuted,
                toggleMute = { toggleMute(device) }
            )

        }
    }
}

@Composable
fun AudioDeviceItem(
    device: AudioDevice,
    onVolumeChange: (AudioDevice, Float) -> Unit,
    onDeviceSelected: (AudioDevice) -> Unit,
    onToggleMute: (AudioDevice) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (device.isSelected) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        onClick = { onDeviceSelected(device) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    selected = device.isSelected,
                    onClick = { onDeviceSelected(device) }
                )
                
                Text(
                    text = device.deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (device.isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                )
            }
            
            VolumeSlider(
                volume = device.volume * 100,
                onVolumeChange = { newVolume ->
                    onVolumeChange(device, newVolume / 100f)
                },
                isMuted = device.isMuted,
                modifier = Modifier.padding(start = 12.dp),
                toggleMute = { onToggleMute(device) }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AudioDeviceBottomSheetPreview() {
    MaterialTheme {
        val sampleDevices = listOf(
            AudioDevice(
                audioDeviceType = AudioMessageType.Active,
                deviceId = "1",
                isSelected = true,
                deviceName = "Living Room Speaker",
                volume = 0.7f,
                isMuted = false
            ),
            AudioDevice(
                audioDeviceType = AudioMessageType.Active,
                deviceId = "2",
                isSelected = false,
                deviceName = "Kitchen Speaker",
                volume = 0.5f,
                isMuted = true,
            ),
            AudioDevice(
                audioDeviceType = AudioMessageType.Active,
                deviceId = "3",
                isSelected = false,
                deviceName = "Bedroom Speaker",
                volume = 0.3f,
                isMuted = true
            )
        )
        
        AudioDeviceBottomSheet(
            audioDevices = sampleDevices,
            onVolumeChange = { _, _ -> },
            onDefaultDeviceSelected = { },
            onToggleMute = { }
        )
    }
} 