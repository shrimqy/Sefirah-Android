package com.castle.sefirah.presentation.settings.update

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.castle.sefirah.navigation.Graph
import com.castle.sefirah.presentation.main.ConnectionViewModel
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.string.RichTextStringStyle
import sefirah.common.R
import sefirah.presentation.components.padding
import sefirah.presentation.screens.InfoScreen

@Composable
fun NewUpdateScreen(
    rootNavController: NavController,
) {
    val backStackState = rootNavController.currentBackStackEntryAsState().value
    val backStackEntry = remember(key1 = backStackState) { rootNavController.getBackStackEntry(Graph.MainScreenGraph) }
    val connectionViewModel: ConnectionViewModel = hiltViewModel(backStackEntry)
    val newUpdate = connectionViewModel.newUpdate.collectAsState()

    val uriHandler = LocalUriHandler.current

    InfoScreen(
        icon = Icons.Outlined.NewReleases,
        headingText = stringResource(R.string.update_check_notification_update_available),
        subtitleText = newUpdate.value?.release?.version ?: "",
        acceptText = stringResource(R.string.update_check_confirm),
        onAcceptClick = { uriHandler.openUri(newUpdate.value?.release?.getDownloadLink() ?: "") },
        rejectText = stringResource(R.string.action_not_now),
        onRejectClick = { rootNavController.navigateUp() },
    ) {
        RichText(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MaterialTheme.padding.large),
            style = RichTextStyle(
                stringStyle = RichTextStringStyle(
                    linkStyle = SpanStyle(color = MaterialTheme.colorScheme.primary),
                ),
            ),
        ) {
            Markdown(content = newUpdate.value?.release?.info ?: "")

            TextButton(
                onClick = { uriHandler.openUri(newUpdate.value?.release?.releaseLink ?: "") },
                modifier = Modifier.padding(top = MaterialTheme.padding.small),
            ) {
                Text(text = stringResource(R.string.update_check_open))
                Spacer(modifier = Modifier.width(MaterialTheme.padding.extraSmall))
                Icon(imageVector = Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
            }
        }
    }
}