/*
 * Acknowledgment:
 * Portions of this code are adapted from XClipper by Kaustubh Patange.
 * Licensed under the Apache License 2.0.
 */

package sefirah.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sefirah.domain.model.ClipboardMessage
import sefirah.domain.repository.NetworkManager
import javax.inject.Inject

@AndroidEntryPoint
class ClipboardChangeActivity : FragmentActivity() {
    @Inject lateinit var networkManager: NetworkManager

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        lifecycleScope.launch {
            /** Seems like adding a delay is giving [ClipboardManager] time to capture
             *  clipboard text.
             */
            delay(500)
            if (hasFocus) {
                val data = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()
                sendClip(data)
                finish()
            }
        }
        super.onWindowFocusChanged(hasFocus)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        onWindowFocusChanged(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.e("ClipboardChangeActivity", "Destroyed()")
    }

    private fun sendClip(data: String?) {
        if (data != null) {
            CoroutineScope(Dispatchers.IO).launch {
                networkManager.sendMessage(ClipboardMessage("text/plain", data))
            }
        }
    }

    companion object {
        fun launch(context: Context) = with(context) {
            val intent = Intent(this, ClipboardChangeActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }
}