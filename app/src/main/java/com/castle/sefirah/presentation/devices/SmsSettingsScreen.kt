package com.castle.sefirah.presentation.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.castle.sefirah.presentation.settings.components.SwitchPreferenceWidget
import com.castle.sefirah.presentation.settings.components.TextPreferenceWidget
import sefirah.common.R

@Composable
fun SmsSettingsScreen(
    rootNavController: NavHostController,
    onNavigateBack: () -> Unit,
) {
    val previousEntry = rootNavController.previousBackStackEntry ?: return
    val viewModel: DeviceSettingsViewModel = hiltViewModel(previousEntry)
    val preferences by viewModel.preferences.collectAsState()
    var showConvertDialog by remember { mutableStateOf(false) }

    val convertEntries = stringArrayResource(R.array.convert_to_mms_after_entries)
    val convertValues = stringArrayResource(R.array.convert_to_mms_after_values)
    val selectedIndex = convertValues.indexOf(preferences.convertToMmsAfter.toString()).coerceAtLeast(0)
    val convertSubtitle = convertEntries.getOrElse(selectedIndex) { convertEntries.first() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            R.string.preference_settings_with_name,
                            stringResource(R.string.message_sync_preference),
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                SwitchPreferenceWidget(
                    title = stringResource(R.string.set_long_text_as_mms_title),
                    checkedIcon = ImageVector.vectorResource(R.drawable.ic_chat_fill),
                    uncheckedIcon = ImageVector.vectorResource(R.drawable.ic_chat),
                    checked = preferences.longTextAsMms,
                    onCheckedChanged = { checked ->
                        viewModel.saveLongTextAsMmsSettings(checked)
                    },
                )
            }

            item {
                SwitchPreferenceWidget(
                    title = stringResource(R.string.set_group_message_as_mms_title),
                    checkedIcon = ImageVector.vectorResource(R.drawable.ic_chat_fill),
                    uncheckedIcon = ImageVector.vectorResource(R.drawable.ic_chat),
                    checked = preferences.groupMessageAsMms,
                    onCheckedChanged = { checked ->
                        viewModel.saveGroupMessageAsMmsSettings(checked)
                    },
                )
            }

            item {
                TextPreferenceWidget(
                    title = stringResource(R.string.convert_to_mms_after_title),
                    subtitle = convertSubtitle,
                    onPreferenceClick = { showConvertDialog = true },
                )
            }
        }
    }

    if (showConvertDialog) {
        AlertDialog(
            onDismissRequest = { showConvertDialog = false },
            title = { Text(stringResource(R.string.convert_to_mms_after_title)) },
            text = {
                Column {
                    convertEntries.forEachIndexed { index, entry ->
                        val value = convertValues[index].toInt()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = preferences.convertToMmsAfter == value,
                                    onClick = {
                                        viewModel.saveConvertToMmsAfterSettings(value)
                                        showConvertDialog = false
                                    },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = preferences.convertToMmsAfter == value,
                                onClick = {
                                    viewModel.saveConvertToMmsAfterSettings(value)
                                    showConvertDialog = false
                                },
                            )
                            Text(
                                text = entry,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .clickable {
                                        viewModel.saveConvertToMmsAfterSettings(value)
                                        showConvertDialog = false
                                    },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConvertDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
