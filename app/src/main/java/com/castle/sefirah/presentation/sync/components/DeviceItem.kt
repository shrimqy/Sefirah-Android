package com.castle.sefirah.presentation.sync.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.castle.sefirah.presentation.settings.components.LocalPreferenceHighlighted
import com.castle.sefirah.presentation.settings.components.LocalPreferenceMinHeight
import com.castle.sefirah.presentation.settings.components.PrefsHorizontalPadding
import com.castle.sefirah.presentation.settings.components.highlightBackground
import sefirah.domain.model.RemoteDevice

@Composable
fun DeviceItem(
    modifier: Modifier = Modifier,
    service: RemoteDevice,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
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
                modifier = Modifier.padding(start = PrefsHorizontalPadding, end = 8.dp).size(48.dp),
                imageVector = Icons.Rounded.Devices,
                contentDescription = "Device Icon",
                tint = MaterialTheme.colorScheme.surfaceTint,
            )
            Text(
                modifier = Modifier.padding(horizontal = PrefsHorizontalPadding),
                text = service.deviceName,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }

    }
}