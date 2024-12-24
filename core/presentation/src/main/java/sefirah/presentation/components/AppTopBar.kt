package sefirah.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import sefirah.presentation.R

@Composable
fun AppTopBar(
    items: List<NavigationItem>,
    selectedItem: Int,
    onNewDeviceClick: ()-> Unit,
) {
    var isOverflowExpanded by remember {
        mutableStateOf(false)
    }

    var text by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        title = {
            if (!active) {
                Text(text = items[selectedItem].text)
            }
            else {
                SearchBar(
                    query = text,
                    onQueryChange = { text = it },
                    onSearch = { active = false },
                    active = active,
                    onActiveChange = { active = it },
                    placeholder = {
                        if (selectedItem == 1) {
                            Text(text = "Search Devices")
                        } else {
                            Text(text = "Search Settings")
                        }
                    },
                    leadingIcon = {
                        if (!active) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search Icon"
                            )
                        } else {
                            IconButton(onClick = { active = false }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Back icon"
                                )
                            }
                        }
                    },
                    trailingIcon = {
                        if (active && text.isNotEmpty()) {
                            IconButton(onClick = { if (text.isNotEmpty()) text = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear Icon"
                                )
                            }
                        }
                    }
                )
            }
        },
        actions = {
            Row {
                if (!active && selectedItem == 1) {
                    IconButton(onClick = { active = true }) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search Icon")
                    }
                }

                if (selectedItem == 1) {
                    IconButton(onClick = { isOverflowExpanded = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_overflow_24dp),
                            contentDescription = "Overflow"
                        )
                    }
                }

                DropdownMenu(
                    expanded = isOverflowExpanded,
                    onDismissRequest = { isOverflowExpanded = false },
                    shape = RoundedCornerShape(16.dp),
                    offset = DpOffset(x = (-9).dp, y = 0.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text(text = "Add a New Device") },
                        onClick = { onNewDeviceClick() },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Devices,
                                contentDescription = "Import Icon"
                            )
                        }
                    )
                }
            }
        }
    )
}