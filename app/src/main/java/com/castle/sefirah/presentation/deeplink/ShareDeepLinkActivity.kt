package com.castle.sefirah.presentation.deeplink

import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sefirah.domain.model.ClipboardMessage
import sefirah.domain.repository.DeviceManager
import sefirah.domain.repository.NetworkManager
import sefirah.network.NetworkService
import javax.inject.Inject

@AndroidEntryPoint
class ShareDeepLinkActivity : ComponentActivity() {
    @Inject lateinit var networkManager: NetworkManager
    @Inject lateinit var deviceManager: DeviceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                Log.d(TAG, "Handling single share intent: ${intent.type}")
                when {
                    intent.type?.startsWith("text/plain") == true -> handleText(intent)
                    else -> {
                        handleSingleFileTransfer(intent)
                    }
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                Log.d(TAG, "Handling multiple share intent: ${intent.type}")
                when {
                    intent.type?.startsWith("text/plain") == true -> handleText(intent)
                    else -> {
                        handleMultipleFileTransfer(intent)
                    }
                }
            }

            else -> {
                Log.e(TAG, "Unsupported intent action: ${intent?.action}")
                finishAffinity()
            }
        }
    }

    private fun handleText(intent: Intent) {
        val text = intent.getStringArrayListExtra(Intent.EXTRA_TEXT)
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString("\n") { it.toString() }
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)

        Log.d(TAG, "Handling text share: $text")
        if (!text.isNullOrEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                networkManager.sendClipboardMessage(ClipboardMessage("text/plain", text))
            }
        } else {
            finishAffinity()
        }
    }

    private fun handleSingleFileTransfer(intent: Intent) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        Log.d(TAG, "Handling single file share: $uri")

        pickTargetDevice { targetDeviceId ->
            if (targetDeviceId == null) {
                Log.e(TAG, "No connected device available for transfer")
                finishAffinity()
            } else {
                uri?.let { sendAndFinish(targetDeviceId, listOf(it)) }
                    ?: finishAffinity()
            }
        }
    }

    private fun handleMultipleFileTransfer(intent: Intent?) {
        val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        } ?: run {
            Log.e(TAG, "No URIs provided in intent")
            finishAffinity()
            return
        }

        Log.d(TAG, "Handling multiple file share: ${uris.size} files")

        pickTargetDevice { targetDeviceId ->
            if (targetDeviceId == null) {
                Log.e(TAG, "No connected device available for bulk transfer")
                finishAffinity()
            } else {
                sendAndFinish(targetDeviceId, uris)
            }
        }
    }

    private fun sendAndFinish(deviceId: String, uris: List<Uri>) {
        val intent = Intent(this, NetworkService::class.java).apply {
            action = NetworkService.Companion.Actions.SEND_FILES.name
            putExtra(NetworkService.DEVICE_ID_EXTRA, deviceId)

            clipData = ClipData.newRawUri(null, uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startService(intent)
        finishAffinity()
    }

    private fun pickTargetDevice(onSelected: (String?) -> Unit) {
        val connectedDevices = deviceManager.pairedDevices.value.filter { it.connectionState.isConnected }

        when (connectedDevices.size) {
            0 -> onSelected(null)
            1 -> onSelected(connectedDevices.first().deviceId)
            else -> {
                val deviceNames = connectedDevices.map { it.deviceName }
                AlertDialog.Builder(this)
                    .setTitle("Send to device")
                    .setItems(deviceNames.toTypedArray()) { dialog, which ->
                        onSelected(connectedDevices[which].deviceId)
                    }
                    .setOnCancelListener {
                        finishAffinity()
                    }.show()
            }
        }
    }

    companion object {
        const val TAG = "DeepLinkActivity"
    }
}
