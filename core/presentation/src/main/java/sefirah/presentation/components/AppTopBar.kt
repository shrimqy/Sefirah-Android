package sefirah.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun AppTopBar(
    items: List<NavigationItem>,
    selectedItem: Int,
    onSearchQueryChange: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }

    LaunchedEffect(text) {
        onSearchQueryChange(text)
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        title = {
            if (!active) {
                Text(text = items[selectedItem].text)
            } else {
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
                            IconButton(onClick = {
                                text = ""
                                active = false
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Back icon"
                                )
                            }
                        }
                    },
                    trailingIcon = {
                        if (active && text.isNotEmpty()) {
                            IconButton(onClick = { text = "" }) {
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
            if (!active && selectedItem == 1) {
                IconButton(onClick = { active = true }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Icon"
                    )
                }
            }
        }
    )
}