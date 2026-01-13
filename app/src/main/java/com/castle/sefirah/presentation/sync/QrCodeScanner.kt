package com.castle.sefirah.presentation.sync

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.castle.sefirah.navigation.SyncRoute
import sefirah.network.util.QrCodeParser
import zxingcpp.BarcodeReader
import java.util.concurrent.Executors

@Composable
fun QrCodeScanner(
    rootNavController: NavHostController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            permissionDenied = true
        }
    }

    LaunchedEffect(Unit) {
        val permissionStatus = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        )
        hasCameraPermission = permissionStatus == PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            permissionDenied -> {
                Text(
                    text = "Camera permission is required to scan QR codes. Please grant permission in settings.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }
            hasCameraPermission -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    CameraPreview(
                        onQrCodeScanned = { qrCodeData ->
                            // Parse QR code and navigate back to SyncScreen with the parsed data
                            QrCodeParser.parseQrCode(qrCodeData)?.let { parsedData ->
                                runCatching {
                                    rootNavController.getBackStackEntry(SyncRoute.SyncScreen.route)
                                }.getOrNull()?.let { syncScreenEntry ->
                                    syncScreenEntry.savedStateHandle["qr_code_result"] = parsedData
                                    rootNavController.popBackStack(SyncRoute.SyncScreen.route, inclusive = false)
                                }
                            }
                        },
                        lifecycleOwner
                    )
                    QrCodeScanningOverlay()
                }
            }
        }

        // Close button
        IconButton(
            onClick = { rootNavController.popBackStack() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CameraPreview(
    onQrCodeScanned: (String) -> Unit,
    lifecycleOwner: LifecycleOwner
) {
    val context = LocalContext.current
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val resolutionSelector = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                android.util.Size(1280, 720),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    val barcodeReader = BarcodeReader()
                    barcodeReader.options = BarcodeReader.Options(
                        formats = setOf(BarcodeReader.Format.QR_CODE),
                        tryHarder = true,
                        tryRotate = true,
                        tryInvert = true,
                        tryDownscale = true
                    )

                    imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { image ->
                        image.use {
                            try {
                                val results = barcodeReader.read(it)
                                if (results.isNotEmpty()) {
                                    results.first().text?.let { qrCodeText ->
                                        executor.execute {
                                            onQrCodeScanned(qrCodeText)
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, executor)

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun QrCodeScanningOverlay() {
    val strokeWidth = 4.dp
    val cornerRadius = 24.dp
    val arcRadius = 40.dp
    val arcOffset = 20.dp
    val overlayColor = MaterialTheme.colorScheme.primary
    val overlayAlpha = 0.6f
    val scanningAreaRatio = 0.7f

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scanningBounds = calculateScanningBounds(size, scanningAreaRatio)
            val radiusPx = cornerRadius.toPx()
            val arcRadiusPx = arcRadius.toPx()
            val arcOffsetPx = arcOffset.toPx()
            val strokeWidthPx = strokeWidth.toPx()

            drawDimmedOverlay(scanningBounds, radiusPx, overlayAlpha)
            drawCornerArcs(scanningBounds, overlayColor, arcRadiusPx, arcOffsetPx, strokeWidthPx)
        }
    }
}

private data class ScanningBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

private fun calculateScanningBounds(size: Size, ratio: Float): ScanningBounds {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val scanningSize = size.width * ratio.coerceAtMost(size.height * ratio)
    val halfSize = scanningSize / 2

    return ScanningBounds(
        left = centerX - halfSize,
        top = centerY - halfSize,
        right = centerX + halfSize,
        bottom = centerY + halfSize
    )
}

private fun DrawScope.drawDimmedOverlay(
    bounds: ScanningBounds,
    cornerRadius: Float,
    alpha: Float
) {
    val fullScreenPath = Path().apply {
        addRect(Rect(0f, 0f, this@drawDimmedOverlay.size.width, this@drawDimmedOverlay.size.height))
    }

    val cutoutPath = Path().apply {
        addRoundRect(
            RoundRect(
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom,
                radiusX = cornerRadius,
                radiusY = cornerRadius
            )
        )
    }

    val overlayPath = Path.combine(
        operation = PathOperation.Difference,
        path1 = fullScreenPath,
        path2 = cutoutPath
    )

    drawPath(
        path = overlayPath,
        color = Color.Black.copy(alpha = alpha)
    )
}

private fun DrawScope.drawCornerArcs(
    bounds: ScanningBounds,
    color: Color,
    arcRadius: Float,
    arcOffset: Float,
    strokeWidth: Float
) {
    val cornerConfigs = listOf(
        CornerConfig(180f, bounds.left - arcRadius + arcOffset, bounds.top - arcRadius + arcOffset),
        CornerConfig(270f, bounds.right - arcRadius - arcOffset, bounds.top - arcRadius + arcOffset),
        CornerConfig(90f, bounds.left - arcRadius + arcOffset, bounds.bottom - arcRadius - arcOffset),
        CornerConfig(0f, bounds.right - arcRadius - arcOffset, bounds.bottom - arcRadius - arcOffset)
    )

    cornerConfigs.forEach { config ->
        drawArc(
            color = color,
            startAngle = config.startAngle,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(config.x, config.y),
            size = Size(arcRadius * 2, arcRadius * 2),
            style = Stroke(strokeWidth)
        )
    }
}

private data class CornerConfig(
    val startAngle: Float,
    val x: Float,
    val y: Float
)

