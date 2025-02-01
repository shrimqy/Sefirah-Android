package com.castle.sefirah.presentation.sync.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.castle.sefirah.presentation.settings.components.LocalPreferenceHighlighted
import com.castle.sefirah.presentation.settings.components.LocalPreferenceMinHeight
import com.castle.sefirah.presentation.settings.components.PrefsHorizontalPadding
import com.castle.sefirah.presentation.settings.components.PrefsVerticalPadding
import com.castle.sefirah.presentation.settings.components.highlightBackground
import sefirah.network.DiscoveredDevice

@Composable
fun DeviceItem(
    modifier: Modifier = Modifier,
    device: DiscoveredDevice,
    key: String,
    onClick: () -> Unit
) {
    val highlighted = LocalPreferenceHighlighted.current
    val minHeight = LocalPreferenceMinHeight.current

    Row(
        modifier = modifier
            .highlightBackground(highlighted)
            .sizeIn(minHeight = minHeight)
            .clickable(enabled = true, onClick = { onClick.invoke() })
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier
                .padding(start = PrefsHorizontalPadding, end = 16.dp)
                .size(38.dp),
            imageVector = Icons.Rounded.DesktopWindows,
            contentDescription = "Device Icon",
            tint = MaterialTheme.colorScheme.surfaceTint,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = PrefsVerticalPadding),
        ) {
            Text(
                text = device.deviceName,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Key: $key",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

}