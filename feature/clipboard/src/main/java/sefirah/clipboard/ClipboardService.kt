package sefirah.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import sefirah.domain.model.ClipboardMessage
import sefirah.domain.repository.NetworkManager
import javax.inject.Inject

class ClipboardService @Inject constructor(
    private val context: Context,
) : ClipboardHandler {

    override fun start() {
        // Start the service
        val intent = Intent(context, ClipboardListener::class.java)
        context.startService(intent)
    }

    override fun stop() {
        // Stop the service
        val intent = Intent(context, ClipboardListener::class.java)
        context.stopService(intent)
    }

    override fun setClipboard(clipboard: ClipboardMessage) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Received clipboard", clipboard.content)
        clipboardManager.setPrimaryClip(clip)
    }

}