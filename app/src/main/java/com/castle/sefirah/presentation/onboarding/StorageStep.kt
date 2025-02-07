package com.castle.sefirah.presentation.onboarding

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.castle.sefirah.presentation.settings.SettingsViewModel
import com.castle.sefirah.presentation.settings.storageLocationPicker
import sefirah.common.R
import sefirah.common.util.getReadablePathFromUri
import sefirah.presentation.components.padding

internal class StorageStep : OnboardingStep {
    @Composable
    override fun Content(viewModel: SettingsViewModel) {
        val context = LocalContext.current
        val preferencesSettings by viewModel.preferencesSettings.collectAsState()
        val pickStorageLocation = storageLocationPicker(viewModel)
        val storageLocation = preferencesSettings?.storageLocation ?: "/storage/emulated/0/Downloads"

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = MaterialTheme.padding.medium),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(bottom = MaterialTheme.padding.small)
                        .size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = stringResource(R.string.choose_storage_location),
                    style = MaterialTheme.typography.headlineSmall,
                )

            }

            Spacer(modifier = Modifier.height(8.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.storage_location_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )


                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "${stringResource(R.string.selected_folder_label)}: ${getReadablePathFromUri(context, storageLocation)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    FilledTonalButton(
                        onClick = {
                            try {
                                pickStorageLocation.launch(null)
                            } catch (_: ActivityNotFoundException) {
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.select_folder_button),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}