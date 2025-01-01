package com.castle.sefirah.presentation.deeplink

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.castle.sefirah.BaseActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import sefirah.network.FileTransferService
import sefirah.network.FileTransferService.Companion.ACTION_SEND_FILE

@AndroidEntryPoint
class ShareDeepLinkActivity: BaseActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Defer intent handling until service is bound
        observeServiceBinding()
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
        if (intent?.action == Intent.ACTION_SEND) {
            when {
                intent.type?.startsWith("image/") == true -> handleFileTransfer(intent)
                intent.type?.startsWith("video/") == true -> handleFileTransfer(intent)
                intent.type?.startsWith("application/") == true -> handleFileTransfer(intent)
                else -> {
                    Log.e("ShareToPc", "Unsupported content type: ${intent.type}")
                    finishAffinity()
                }
            }
        } else {
            Log.e("ShareToPc", "Unsupported intent action: ${intent?.action}")
            finishAffinity()
        }
    }

    private fun handleFileTransfer(intent: Intent) {
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        Log.d("ShareToPc", "Handling file share: $uri")

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
}