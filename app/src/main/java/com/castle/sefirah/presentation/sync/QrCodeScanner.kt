package com.castle.sefirah.presentation.sync

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.castle.sefirah.navigation.SyncRoute
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import sefirah.network.util.QrCodeParser
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
                CameraPreview(
                    onQrCodeScanned = { qrCodeData ->
                        // Parse QR code and navigate back to SyncScreen with the parsed data
                        val connectionData = QrCodeParser.parseQrCode(qrCodeData)
                        if (connectionData != null) {
                            rootNavController.getBackStackEntry(SyncRoute.SyncScreen.route)
                                .savedStateHandle["qr_code_result"] = connectionData
                        }
                        rootNavController.popBackStack()
                    },
                    lifecycleOwner = lifecycleOwner
                )
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

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(
    onQrCodeScanned: (String) -> Unit,
    lifecycleOwner: LifecycleOwner
) {
    val context = LocalContext.current
    var scanned by remember { mutableStateOf(false) }

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

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    val barcodeScanner = BarcodeScanning.getClient()

                    imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                        if (!scanned) {
                            val mediaImage = imageProxy.image
                            if (mediaImage == null) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )

                            barcodeScanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        when (barcode.valueType) {
                                            Barcode.TYPE_URL, Barcode.TYPE_TEXT -> {
                                                barcode.rawValue?.let { qrCodeData ->
                                                    if (!scanned) {
                                                        scanned = true
                                                        onQrCodeScanned(qrCodeData)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
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

