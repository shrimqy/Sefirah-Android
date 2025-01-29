package com.castle.sefirah.presentation.about

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.castle.sefirah.BuildConfig
import com.castle.sefirah.presentation.settings.components.LogoHeader
import com.castle.sefirah.presentation.settings.components.TextPreferenceWidget
import sefirah.common.R
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AboutScreen(rootNavController: NavController, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                title = { Text(stringResource(id = R.string.about)) },
                navigationIcon = {
                    IconButton(
                        onClick = { rootNavController.navigateUp() }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        }
    ) { contentPadding ->
        LazyColumn(contentPadding = contentPadding) {
            item {
                LogoHeader()
            }

            item {
                TextPreferenceWidget(
                    title = stringResource(R.string.version),
                    subtitle = "${BuildConfig.VERSION_NAME} (${SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(BuildConfig.BUILD_TIME))})",
                    onPreferenceClick = {
                        // TODO
                    },
                )
            }

            item {
                TextPreferenceWidget(
                    title = stringResource(R.string.whats_new),
                    onPreferenceClick = { uriHandler.openUri("https://github.com/shrimqy/Sefirah-Android/releases") },
                )
            }

            item {
                TextPreferenceWidget(
                    title = "Github",
                    onPreferenceClick = { uriHandler.openUri("https://github.com/shrimqy/Sefirah-Android") },
                )
            }
        }
    }
}