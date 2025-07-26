package com.castle.sefirah.presentation.home.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VolumeSlider(
    volume: Float,
    isMuted: Boolean,
    onVolumeChange: (Float) -> Unit,
    toggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderPosition by remember { mutableFloatStateOf(volume) }
    val interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }

    val lastSentValue = remember { mutableFloatStateOf(volume) }

    LaunchedEffect(volume) {
        sliderPosition = volume
        lastSentValue.floatValue = volume
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        IconButton(onClick = toggleMute) {
            Icon(
                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Volume Icon",
                tint = MaterialTheme.colorScheme.surfaceTint,
                modifier = Modifier.size(24.dp).padding(start = 0.dp)
            )
        }
        Slider(
            value = sliderPosition,
            onValueChange = { newValue ->
                sliderPosition = newValue

                val roundedValue = kotlin.math.round(newValue)
                // Check if the change exceeds our threshold
                if (roundedValue != lastSentValue.floatValue) {
                    onVolumeChange(roundedValue)
                    lastSentValue.floatValue = roundedValue
                }
            },
            interactionSource = interactionSource,
            valueRange = 0f..100f,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            thumb = {
                Label(
                    label = {
                        PlainTooltip(modifier = Modifier.sizeIn(45.dp, 25.dp).wrapContentWidth()) {
                            Text("${sliderPosition.toInt()}%")
                        }
                    },
                    interactionSource = interactionSource
                ) {
                    SliderDefaults.Thumb(interactionSource = interactionSource)
                }
            }
        )
    }
}

