package sefirah.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import sefirah.common.util.createTempFile
import sefirah.common.util.getFileProviderUri
import sefirah.domain.model.ClipboardMessage
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardHandler @Inject constructor(
    private val context: Context
) {
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    
    fun setClipboard(clipboard: ClipboardMessage) {
        try {
            val clip: ClipData = when {
                clipboard.clipboardType == "text/plain" -> {
                    ClipData.newPlainText("Received clipboard", clipboard.content)
                }

                clipboard.clipboardType.startsWith("image/") -> {
                    val imageBytes = Base64.decode(clipboard.content, Base64.DEFAULT)
                    val extension = clipboard.clipboardType.substringAfter('/').lowercase()

                    val tempFile = createTempFile(context, "sefirah_clipboard_image", extension)
                    FileOutputStream(tempFile).use { it.write(imageBytes) }

                    val uri = getFileProviderUri(context, tempFile)
                    ClipData.newUri(context.contentResolver, "Received image", uri)
                }

                else -> {
                    ClipData.newPlainText("Received clipboard", clipboard.content)
                }
            }
            clipboardManager.setPrimaryClip(clip)
        } catch (ex: Exception) {
            Log.e(TAG, "Exception handling clipboard", ex)
        }
    }

    fun setClipboardUri(uri: Uri) {
        try {
            val clip = ClipData.newUri(context.contentResolver, "Received file", uri)
            clipboardManager.setPrimaryClip(clip)
            Log.d(TAG, "File added to clipboard: $uri")
        } catch (ex: Exception) {
            Log.e(TAG, "Exception setting clipboard URI", ex)
        }
    }

    companion object {
        private const val TAG = "ClipboardHandler"
    }
}
