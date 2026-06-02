package com.castle.sefirah.presentation.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SwitchPreferenceWidget(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    checkedIcon: ImageVector? = null,
    uncheckedIcon: ImageVector? = null,
    checked: Boolean = false,
    onCheckedChanged: (Boolean) -> Unit,
    onContentClick: (() -> Unit)? = null,
) {
    val resolvedIcon = when {
        checkedIcon != null && uncheckedIcon != null -> if (checked) checkedIcon else uncheckedIcon
        else -> icon
    }

    if (onContentClick == null) {
        TextPreferenceWidget(
            modifier = modifier,
            title = title,
            subtitle = subtitle,
            icon = resolvedIcon,
            widget = {
                Switch(
                    checked = checked,
                    onCheckedChange = null,
                    modifier = Modifier.padding(start = TrailingWidgetBuffer),
                )
            },
            onPreferenceClick = { onCheckedChanged(!checked) },
        )
        return
    }

    val minHeight = LocalPreferenceMinHeight.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .sizeIn(minHeight = minHeight)
            .then(
                if (!checked) {
                    Modifier.clickable(onClick = { onCheckedChanged(true) })
                } else {
                    Modifier
                },
            ),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                TextPreferenceWidget(
                    modifier = Modifier.fillMaxWidth(),
                    title = title,
                    subtitle = subtitle,
                    icon = resolvedIcon,
                    onPreferenceClick = if (checked) onContentClick else null,
                    widget = null,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = DividerSlotHorizontalPadding),
                contentAlignment = Alignment.Center,
            ) {
                VerticalDivider(
                    modifier = Modifier.height(DividerHeight),
                    color = if (checked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .then(
                        if (checked) {
                            Modifier.clickable(onClick = { onCheckedChanged(!checked) })
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Switch(
                    checked = checked,
                    onCheckedChange = null,
                    modifier = Modifier.padding(horizontal = PrefsHorizontalPadding),
                )
            }
        }
    }
}

private val DividerHeight: Dp = 32.dp
private val DividerSlotHorizontalPadding: Dp = 8.dp
