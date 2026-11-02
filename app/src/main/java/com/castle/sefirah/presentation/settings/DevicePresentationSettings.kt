package com.castle.sefirah.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.castle.sefirah.presentation.settings.components.SwitchPreferenceWidget
import sefirah.common.R
import sefirah.domain.model.DevicePresentation

@Composable
fun DevicePresentationSettings(viewModel: SettingsViewModel) {
    val current = viewModel.presentation
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null) viewModel.selectPresentationImage(it)
    }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(stringResource(R.string.device_presentation_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.device_presentation_description))
        com.castle.sefirah.presentation.common.components.DevicePreview(
            viewModel.presentationPreview ?: current, viewModel.presentationPreview?.wallpaper)
        PresentationChoice(stringResource(R.string.device_presentation_kind), current.kind, DevicePresentation.kinds) {
            viewModel.updatePresentation(current.copy(kind = it))
        }
        PresentationChoice(stringResource(R.string.device_presentation_orientation), current.orientation, DevicePresentation.orientations) {
            viewModel.updatePresentation(current.copy(orientation = it))
        }
        PresentationChoice(stringResource(R.string.device_presentation_ratio), current.aspectRatio, DevicePresentation.ratios) {
            viewModel.updatePresentation(current.copy(aspectRatio = it))
        }
        SwitchPreferenceWidget(title = stringResource(R.string.share_wallpaper_preview), checked = viewModel.shareWallpaper,
            onCheckedChanged = viewModel::shareWallpaper)
        TextButton(onClick = { picker.launch("image/*") }) { Text(stringResource(R.string.choose_presentation_image)) }
        TextButton(onClick = viewModel::useSystemWallpaper) { Text(stringResource(R.string.use_system_wallpaper)) }
    }
}

@Composable
private fun PresentationChoice(label: String, value: String, choices: List<String>, change: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("$label: ${presentationLabel(value)}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(text = { Text(presentationLabel(choice)) }, onClick = { open = false; change(choice) })
            }
        }
    }
}

@Composable
private fun presentationLabel(value: String): String = when (value) {
    "unknown" -> stringResource(R.string.presentation_unknown)
    "phone" -> stringResource(R.string.presentation_phone)
    "tablet" -> stringResource(R.string.presentation_tablet)
    "computer" -> stringResource(R.string.presentation_computer)
    "portrait" -> stringResource(R.string.presentation_portrait)
    "landscape" -> stringResource(R.string.presentation_landscape)
    else -> value
}
