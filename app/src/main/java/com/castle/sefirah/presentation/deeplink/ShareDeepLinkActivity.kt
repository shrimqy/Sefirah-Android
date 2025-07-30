package com.castle.sefirah.presentation.deeplink

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.castle.sefirah.BaseActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sefirah.domain.model.ClipboardMessage
import sefirah.network.FileTransferService
import sefirah.network.FileTransferService.Companion.ACTION_SEND_FILE
import sefirah.network.FileTransferService.Companion.ACTION_SEND_BULK_FILES
import sefirah.network.FileTransferService.Companion.EXTRA_FILE_URIS

@AndroidEntryPoint
class ShareDeepLinkActivity: BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Defer intent handling until service is bound
        observeServiceBinding()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Update the intent of the activity
        setIntent(intent)
        // If the service is already connected, handle the new intent right away.
        // Otherwise, the service connection callback in observeServiceBinding() will pick it up.
        handleIntent(intent)
    }

    private fun observeServiceBinding() {
        // Override the connection callback from BaseActivity
        setServiceConnectionCallback { isConnected ->
            if (isConnected) {
                handleIntent(intent)
            } else {
                Log.e("ShareDeepLinkActivity", "Service disconnected")
                finishAffinity()
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                Log.d("ShareDeepLinkActivity", "Handling single share intent: ${intent.type}")
                when {
                    intent.type?.startsWith("text/plain") == true -> handleText(intent)
                    intent.type?.startsWith("image/") == true -> handleSingleFileTransfer(intent)
                    intent.type?.startsWith("video/") == true -> handleSingleFileTransfer(intent)
                    intent.type?.startsWith("application/") == true -> handleSingleFileTransfer(intent)
                    else -> {
                        handleSingleFileTransfer(intent)
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                Log.d("ShareDeepLinkActivity", "Handling multiple share intent: ${intent.type}")
                when {
                    intent.type?.startsWith("text/plain") == true -> handleText(intent)
                    intent.type?.startsWith("image/") == true -> handleMultipleFileTransfer(intent)
                    intent.type?.startsWith("video/") == true -> handleMultipleFileTransfer(intent)
                    intent.type?.startsWith("application/") == true -> handleMultipleFileTransfer(intent)
                    else -> {
                        handleMultipleFileTransfer(intent)
                    }
                }
            }
            else -> {
                Log.e("ShareToPc", "Unsupported intent action: ${intent?.action}")
                finishAffinity()
            }
        }
    }

    private fun handleText(intent: Intent) {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        Log.d("ShareToPc", "Handling text share: $text")
        if (text?.isNotEmpty() == true) {
            CoroutineScope(Dispatchers.IO).launch {
                networkService?.sendMessage(ClipboardMessage("text/plain", text))
            }
        } else {
            finishAffinity()
        }
    }

    private fun handleSingleFileTransfer(intent: Intent) {
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        Log.d("ShareToPc", "Handling single file share: $uri")

        if (uri != null) {
            val serviceIntent = Intent(applicationContext, FileTransferService::class.java).apply {
                action = ACTION_SEND_FILE
                data = uri
            }
            startForegroundService(serviceIntent)
        } else {
            Log.e("ShareToPc", "Received null URI")
            finishAffinity()
        }
    }

    private fun handleMultipleFileTransfer(intent: Intent) {
        val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        Log.d("ShareToPc", "Handling multiple file share: ${uris?.size} files")

        if (!uris.isNullOrEmpty()) {
            val serviceIntent = Intent(applicationContext, FileTransferService::class.java).apply {
                action = ACTION_SEND_BULK_FILES
                putParcelableArrayListExtra(EXTRA_FILE_URIS, uris)
            }
            startForegroundService(serviceIntent)
        } else {
            Log.e("ShareToPc", "Received null or empty URIs")
            finishAffinity()
        }
    }
}
