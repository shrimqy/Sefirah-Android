package com.castle.sefirah.presentation.common.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import sefirah.common.util.base64ToBitmap
import sefirah.domain.model.PairedDevice

@Composable
fun DevicePreview(device: PairedDevice) = DevicePreview(device.presentation, device.avatar)

@Composable
fun DevicePreview(presentation: sefirah.domain.model.DevicePresentation?, previewImage: String?) {
    val ratio = presentation?.displayRatio() ?: 1f
    val kind = presentation?.kind ?: "unknown"
    val bitmap = remember(previewImage, presentation) {
        if (presentation?.wallpaperSource in listOf("wallpaper", "userSelected")) base64ToBitmap(previewImage) else null
    }
    val height = minOf(60f, 76f / ratio)
    Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size((height * ratio).dp, height.dp)
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(if (kind == "phone") 8.dp else 3.dp))
            .padding(3.dp).clip(RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(rememberAsyncImagePainter(bitmap), null,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(when (kind) { "phone" -> "▯"; "tablet" -> "▭"; "computer" -> "▰"; else -> "?" })
        }
        if (kind == "computer") HorizontalDivider(Modifier.width(24.dp).padding(top = 3.dp), thickness = 3.dp)
    }
}
