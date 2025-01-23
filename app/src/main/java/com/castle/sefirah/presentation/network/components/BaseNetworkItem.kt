package com.castle.sefirah.presentation.network.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sefirah.database.model.NetworkEntity
import sefirah.presentation.components.padding
import sefirah.presentation.util.secondaryItemAlpha

@Composable
fun BaseNetworkItem(
    network: NetworkEntity,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClickItem: () -> Unit,
    onLongClickItem: () -> Unit,
) {
    Row(
        modifier = modifier
            .then(if (!enabled) Modifier.secondaryItemAlpha() else Modifier)
            .combinedClickable(
                onClick = onClickItem,
                onLongClick = onLongClickItem,
            )
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            imageVector = Icons.Filled.NetworkWifi,
            contentDescription = null,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
            modifier = Modifier
                .height(40.dp)
                .aspectRatio(1f),
        )
        
        Text(
            text = network.ssid,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(
                textDecoration = if (!enabled) TextDecoration.LineThrough else TextDecoration.None
            ),
            modifier = Modifier
                .padding(horizontal = MaterialTheme.padding.medium)
                .weight(1f),
        )
    }
}
