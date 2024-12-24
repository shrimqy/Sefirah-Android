package com.castle.sefirah.presentation.home.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeSlider(
    volume: Float,
    onVolumeChange: (Int) -> Unit
) {
    var sliderPosition by remember { mutableFloatStateOf(volume) }
    val interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }

    // Update sliderPosition whenever the volume parameter changes
    LaunchedEffect(volume) {
        sliderPosition = volume
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Volume Icon",
                tint = MaterialTheme.colorScheme.surfaceTint,
                modifier = Modifier.size(24.dp)
            )
            Slider(
                value = sliderPosition,
                onValueChange = { newValue ->
                    sliderPosition = newValue
                    onVolumeChange(newValue.toInt())
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
}